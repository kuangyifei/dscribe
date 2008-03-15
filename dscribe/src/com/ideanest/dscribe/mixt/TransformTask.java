package com.ideanest.dscribe.mixt;

import static org.junit.Assert.assertEquals;

import java.text.MessageFormat;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.junit.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;

public class TransformTask extends TaskBase {
	
	private static final Logger LOG = Logger.getLogger(TransformTask.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Engine.RULES_NS,
			"mod", Engine.MOD_NS,
			"notes", Namespace.NOTES
	);
	
	private Folder workspace, prevspace;

	@Override
	protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
	}
	
	@Phase
	public void transform() throws RuleBaseException, TransformException {
		// copy over modstore from prevspace, if available
		for (Document doc : prevspace.query().unordered("//mod:mods").nodes().documents()) {
			cycle().inherit(doc);
		}
		
		// TODO: adjust mod:*/@doc reference to inherited docs that changed names
		
		try {
			// run transform!
			initEngine().executeTransform(cycle().uninheritedWorkspaceDocuments());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			cycle().checkInterrupt();
		}

		// TODO: merge precedence data back into global configuration
	}

	private Engine initEngine() throws RuleBaseException {
		try {
			expandRules();
			// TODO: make sure rule IDs are globally unique to allow merging precedence data
			assignRuleIDs();
			return new Engine(workspace, prevspace, workspace, null);
		} catch (RuleBaseException e) {
			LOG.error("error in the ruleset, reverting to last known good set from previous cycle", e);
			revertRules();
			try {
				return new Engine(workspace, prevspace, workspace, null);
			} catch (RuleBaseException e1) {
				// running a clean cycle wouldn't help, since an error in the current ruleset is what got us here in the first place
				throw new RuleBaseException("error in ruleset from previous cycle, no usable ruleset available, aborting", e1);
			}
		}
	}

	private void revertRules() {
		for (XMLDocument doc : workspace.query().unordered("//ruleset").nodes().documents()) {
			doc.delete();
		}
		for (XMLDocument doc : prevspace.query().unordered("//ruleset").nodes().documents()) {
			doc.copy(workspace.children().create(prevspace.relativePath(doc.folder().path())), Name.keepAdjust());
		}
	}

	private void assignRuleIDs() {
		for (Node rule : workspace.query().unordered("//rule[not(@xml:id)]").nodes()) {
			String id;
			ItemList oldIds = prevspace.query().unordered("let $names := ($_1/@name, $_1/alias/@name) return //rule[$names = (@name, alias/@name)]/@xml:id", rule);
			if (oldIds.size() == 1) {
				id = oldIds.get(0).value();
			} else {
				String ruleName = rule.query().single("@name").value();
				if (oldIds.size() > 1) LOG.warn("multiple old IDs match rule '" + ruleName + "' and its aliases, generating new ID");
				id = cycle().generateUid("r" + acronymize(ruleName));
			}
			rule.update().attr("xml:id", id).commit();
		}
	}
	
	private String acronymize(String name) {
		if (name.length() < 1) throw new IllegalArgumentException("name to acronymize is empty");
		StringBuilder acronym = new StringBuilder();
		StringTokenizer tokenizer = new StringTokenizer(name, "-_ .");
		if (tokenizer.countTokens() >= 2) {
			while(tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken();
				if (token.length() > 0) {
					char c = token.charAt(0);
					if (Character.isLetter(c)) acronym.append(Character.toLowerCase(c));
				}
			}
		} else {
			int i=0, k=-1;
			for ( ; i < name.length(); i++) {
				char c = name.charAt(i);
				if (Character.isLetter(c)) {
					acronym.append(Character.toLowerCase(c));
					i++;
					k = i;
					break;
				}
			}
			for ( ; i < name.length() && acronym.length() < 4; i++) {
				char c = name.charAt(i);
				if (Character.isUpperCase(c) || Character.isTitleCase(c)) acronym.append(Character.toLowerCase(c));
			}
			if (acronym.length() == 1) {
				assert k != -1;
				for (i=k; i < name.length() && acronym.length() < 3; i++) {
					char c = name.charAt(i);
					if (Character.isLetter(c)) acronym.append(Character.toLowerCase(c));
				}
			}
		}
		return acronym.toString();
	}
	
	private void expandRules() throws RuleBaseException {
		// capture error state instead of immediately throwing exception, gives chance to report as many errors as possible
		boolean error = false;
		
		// 1. ensure rule names (including aliases) are unique
		for (String duplicateName : workspace.query().unordered(
				"for $name in (//rule/@name/string(), //rule/alias/@name/string()) \n" +
				"where count(//rule[@name = $name or alias/@name = $name]) > 1" +
				"return $name").values()) {
			LOG.error("multiple rules with same name or alias '" + duplicateName + "'");
			error = true;
		}
		if (error) throw new RuleBaseException("multiple rules with same name or alias");
		
		// 2. apply extensions to rules; need to iterate in case an extension targets an alias introduced by another
		int numExtensionsApplied = 0;
		boolean appliedExtensions;
		do {
			appliedExtensions = false;
			for (Node extension : workspace.query().unordered("//extend").nodes()) {
				Node target = workspace.query().optional("let $name := $_1/@name/string() return //rule[@name = $name or alias/@name = $name]", extension).node();
				if (!target.extant()) continue;
				ItemList conflictingRuleNames = workspace.query().unordered("let $aliases := $_1/alias/@name/string() return //rule[@name = $aliases or alias/@name = $aliases][not(. is $_2)]/@name", extension, target);
				if (conflictingRuleNames.size() > 0) {
					LOG.error("rule extension for '" + target.query().single("@name").value() + "' introduces aliases that would confound target rule with rules " + conflictingRuleNames);
					error = true;
					continue;
				}
				target.append().nodes(extension.query().all("*").nodes()).commit();
				numExtensionsApplied++;
				appliedExtensions = true;
			}
			if (error) throw new RuleBaseException("rule extension introduced confounding aliases");
		} while (appliedExtensions);
		
		// 3. remove duplicate aliases
		ItemList duplicateAliases = workspace.query().unordered(	"//alias[@name = (parent::rule/@name, following-sibling::alias/@name)]");
		int numDuplicateAliasesDeleted = duplicateAliases.size();
		duplicateAliases.deleteAllNodes();
		
		if (numExtensionsApplied > 0 || numDuplicateAliasesDeleted > 0) {
			LOG.info(MessageFormat.format("{0,choice,0#|1#applied 1 extension|1<applied {0,number,integer} extensions}{1,choice,0#|0>{0,choice,0#|0>, }{1,choice,1#deleted 1 duplicate alias|1>deleted {1,number,integer} duplicate aliases}}",
					new Object[]{numExtensionsApplied, numDuplicateAliasesDeleted}));
		}
	}
	
	@Deprecated
	public static class _TestAcronymize {
		private TransformTask self;
		@Before public void setUp() {self = new TransformTask();}
		@After public void tearDown() {self = null;}
		@Test public void test1() {
			assertEquals("ast", self.acronymize("a-silly-test"));
		}
		@Test public void test2() {
			assertEquals("ast", self.acronymize("A-silly-Test"));
		}
		@Test public void test3() {
			assertEquals("st", self.acronymize("__stupid-test.5"));
		}
		@Test public void test4() {
			assertEquals("bbb", self.acronymize("blah BoOh bee"));
		}
		@Test public void test5() {
			assertEquals("", self.acronymize("1d 3g.98X-1_"));
		}
		@Test public void test6() {
			assertEquals("ast", self.acronymize("ASillyTest"));
		}
		@Test public void test7() {
			assertEquals("ast", self.acronymize("aSillyTest"));
		}
		@Test public void test8() {
			assertEquals("avst", self.acronymize("AVerySillyTestIndeed"));
		}
		@Test public void test9() {
			assertEquals("mmm", self.acronymize("$moneyMoneyMoney"));
		}
		@Test public void test10() {
			assertEquals("sil", self.acronymize("sillytest"));
		}
		@Test public void test11() {
			assertEquals("si", self.acronymize("si"));
		}
		@Test public void test12() {
			assertEquals("st", self.acronymize("SillyTest"));
		}
	}

}
