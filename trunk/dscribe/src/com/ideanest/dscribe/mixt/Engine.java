package com.ideanest.dscribe.mixt;

import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;

public class Engine {
	
	static final Logger LOG = Logger.getLogger(Engine.class);
	
	private final Map<String,Rule> ruleMap = new HashMap<String,Rule>();
	private final List<Rule> rules = new ArrayList<Rule>();
	
	private final Folder workspace;
	private final QueryService globalScope;
	private final QueryService utilQuery;

	private final Node modStore;

	private boolean didWork;
	private final Accumulator<Document> modifiedDocs = new Accumulator<Document>(); 

	Counter
		numCycles = Counter.english("cycle", "cycles", null),
		numBlocksVerified = Counter.english("block", "blocks", "verified"),
		numBlocksResolved = Counter.english("block", "blocks", "resolved"),
		numModsCompleted = Counter.english("mod", "mods", "completed"),
		numModsWithdrawn = Counter.english("mod", "mods", "withdrawn");
	
	private Counter
		modCountFormatter = Counter.english("mod", "mods", null),
		affectedCountFormatter = Counter.english("affected element", "affected elements", null);
	
	private final Document.Listener modifiedDocListener = new Document.Listener() {
		public void handle(org.exist.fluent.Document.Event ev) {
			if (ev.document.equals(modStore.document())) {
				didWork = true;
			} else {
				modifiedDocs.add(ev.document);
			}
		}
	};

	Engine(Folder rulespace, Folder prevrulespace, Folder workspace, Collection<Document> initialModifiedDocs) throws RuleBaseException {
		LOG.debug("initializing engine");
		
		this.workspace = workspace;
		utilQuery = workspace.database().query();
		globalScope = workspace.query();
		globalScope.namespaceBindings().sever();
		globalScope.namespaceBindings().clear();
		
		this.modStore = initModStore();
		parseRules(rulespace, prevrulespace);
		modifiedDocs.addAll(initialModifiedDocs);	// MUST happen after parsing rules, otherwise locators in wrong position
		
		LOG.debug("withdrawing mods of obsolete rules");
		withdrawMods(workspace.query().unordered("//mod:mod[not(exists(id(@rule)))]"));	// TODO: is the context for id() right here?
		LOG.debug("withdrawing mods on obsolete documents");
		withdrawMods(workspace.query().unordered("//mod:mod[not(doc-available(.//dependency/@doc))]"));

		// TODO: sort rules into best-effort dependency order
		
		workspace.listeners().add(
				EnumSet.of(Trigger.AFTER_CREATE, Trigger.AFTER_UPDATE),
				modifiedDocListener);
	}
	
	private Node initModStore() {
		Node modsRoot = workspace.query().optional("mod:mods").node();
		if (!modsRoot.extant()) modsRoot = workspace.documents().build(Name.adjust("modStore.xml"))
			.elem("mod:mods").end("mod:mods").commit().root();
		modsRoot.namespaceBindings().clear();
		modsRoot.namespaceBindings().put("", Namespace.MOD);
		modsRoot.namespaceBindings().put("mod", Namespace.MOD);
		return modsRoot;
	}

	private void parseRules(Folder rulespace, Folder prevrulespace) throws RuleBaseException {
		LOG.debug("parsing rules");
		ItemList ruleItems = rulespace.query().all("//rule");
		for (Node ruleDef : ruleItems.nodes()) {
			Node prevDef = prevrulespace.query().optional("/id($_1/@xml:id)", ruleDef).node();
			Rule rule = new Rule(ruleDef, prevDef, this, modifiedDocs.anchor());
			ruleMap.put(rule.id, rule);
			rules.add(rule);
		}
		LOG.debug("parsed " + ruleItems.size() + " rules");
	}
	
	String relativePath(Document doc) {
		return workspace.relativePath(doc.path());
	}

	Folder workspace() {return workspace;}
	QueryService globalScope() {return globalScope;}
	QueryService utilQuery() {return utilQuery;}
	Node modStore() {return modStore;}

	/**
	 * Run one transformation cycle.
	 *
	 * @return whether at least one more cycle is needed to complete the transformation
	 * @throws TransformException 
	 */
	boolean executeCycle() throws TransformException {
		// TODO: detect livelock loops 
		
		numCycles.increment();
		LOG.debug("running MIXT transformation cycle " + numCycles.value());
		
		didWork = false;
		for (Rule rule : rules) rule.process();

		if (!didWork) {
			LOG.info("MIXT transformation complete; "
					+ numCycles + ", " + numBlocksResolved + ", "
					+ numBlocksVerified + ", " + numModsCompleted + ", " + numModsWithdrawn);
			workspace.listeners().remove(modifiedDocListener);
		}
		return didWork;
	}

	void withdrawMod(String key) {
		LOG.debug("withdrawing mod[" + key + "]");
		withdrawMods(modStore.query().unordered("/id($_1)/self::mod", key));
	}
	
	void withdrawRule(String ruleId) {
		LOG.debug("withdrawing all mods for rule[" + ruleId + "]");
		withdrawMods(modStore.query().unordered("//mod[@rule=$_1]", ruleId));
	}

	void withdrawMods(ItemList newMods)	{
		ItemList mods = workspace.query().unordered("()");
		ItemList affected = workspace.query().unordered("()");
		
		while (newMods.size() > 0) {
			newMods = modStore.query().unordered("$_1 union //mod[ancestor/@refid=$_1/@xml:id]", newMods);
			ItemList newAffected = workspace.query().unordered("/id($_1//mod:affected/@refid)", newMods);
			affected = utilQuery.unordered("$_1 union $_2", affected, newAffected);
			mods = utilQuery.unordered("$_1 union $_2", mods, newMods);
			newMods = modStore.query().unordered(
					"//mod[.//reference/@refid=$_1/descendant-or-self::*/@xml:id] except $_2",
					newAffected, mods);
		}
		
		for (String ruleId : mods.query().unordered("distinct-values(@rule)").values()) {
			Rule rule = ruleMap.get(ruleId);
			if (rule != null) rule.addTouched(workspace.query().unordered("/id(mod:mod[@rule=$_1]//mod:dependency/@refid)", ruleId).nodes().documents());
		}
		
		LOG.debug("deleted " + modCountFormatter.format(mods.size()) + " and " + affectedCountFormatter.format(affected.size()));
		
		numModsWithdrawn.increment(mods.size());
		affected.deleteAllNodes();
		mods.deleteAllNodes();
	}
	
}
