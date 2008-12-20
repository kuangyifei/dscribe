package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.hamcrest.*;
import org.jmock.*;
import org.jmock.api.*;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.*;
import org.junit.runner.RunWith;

import com.ideanest.dscribe.mixt.blocks.*;
import com.ideanest.dscribe.mixt.test.Matchers;


public class Engine {
	public static final String RECORD_NS = "http://ideanest.com/dscribe/ns/record";
	public static final String MOD_NS = "http://ideanest.com/dscribe/ns/mod";
	public static final String MIXT_NS = "http://ideanest.com/dscribe/ns/mixt";

	static final Logger LOG = Logger.getLogger(Engine.class);
	private static final String VERSION = "1";
	
	public static class Stats {
		public final Counter
				numCycles = Counter.english("cycle", "cycles", null),
				numBlocksVerified = Counter.english("block", "blocks", "verified"),
				numBlocksResolved = Counter.english("block", "blocks", "resolved"),
				numModsRestored = Counter.english("mod", "mods", "restored"),
				numModsCompleted = Counter.english("mod", "mods", "completed"),
				numModsWithdrawn = Counter.english("mod", "mods", "withdrawn"),
				numOrdersChecked = Counter.english("order", "orders", "checked"),
				numElementsMoved = Counter.english("element", "elements", "moved");
	}
	
	private final Map<String,Rule> ruleMap = new HashMap<String,Rule>();
	private final List<Rule> rules = new ArrayList<Rule>();
	
	private final Folder workspace, modulespace;
	private final QueryService globalScope;
	private final QueryService utilQuery;
	private final Node modStore;

	private String autoGenIdPrefix;
	private boolean didWork, docsModified;
	final Stats stats = new Stats();
	private final Accumulator<XMLDocument> modifiedDocs = new Accumulator<XMLDocument>(); 
	private final SortController sortController;

	private final Random random = new Random();
	private final Counter
		modCountFormatter = Counter.english("mod", "mods", null),
		affectedCountFormatter = Counter.english("affected element", "affected elements", null);
	
	public Engine(Folder rulespace, Resource prevrulespace, Folder workspace, Node modStore) throws RuleBaseException {
		LOG.debug("initializing engine");
		
		this.workspace = workspace;
		this.workspace.namespaceBindings().put("mod", Engine.MOD_NS);
		
		// TODO: putting modulespace into a shared rulespace would blow up concurrent engine runs
		this.modulespace = rulespace.children().create("modules");
		for (Document doc : this.modulespace.documents()) doc.delete();
		
		utilQuery = workspace.database().query();
		// Need to clone the workspace to avoid wiping out namespace bindings of the folder's shared query service.
		globalScope = workspace.cloneWithoutNamespaceBindings().query();
		
		this.modStore = modStore;
		modStore.namespaceBindings().put("", Engine.MOD_NS);
		modStore.namespaceBindings().put("mod", Engine.MOD_NS);
		
		rulespace.namespaceBindings().put("", Engine.MIXT_NS);
		prevrulespace.namespaceBindings().put("", Engine.MIXT_NS);
		prevrulespace.namespaceBindings().put("record", Engine.MIXT_NS);
		
		this.sortController = new SortController(this, modifiedDocs.anchor());

		Collection<QName> namesOfModifiedFunctions = parseFunctions(rulespace, prevrulespace);
		invalidateIncompatibleBlocks(prevrulespace);
		assignRuleIds(rulespace, prevrulespace);
		parseRules(rulespace, prevrulespace, namesOfModifiedFunctions);
		
		LOG.debug("withdrawing mods of obsolete rules");
		withdrawMods(modStore.query().unordered("for $mods in mods where not(exists($_1/id($mods/@rule))) return $mods", rulespace));
		LOG.debug("withdrawing mods on obsolete documents");
		// must run in workspace context to provide correct base URI for doc-available()
		withdrawMods(workspace.query()
				.unordered("$_1//mod:mod[some $dep in ./mod:dependency/@doc satisfies not(doc-available($dep))]", modStore));

		// TODO: sort rules into best-effort dependency order
	}
	
	// Constructor for testing only.
	private Engine(Folder workspace, Node modStore) {
		this.workspace = workspace;
		utilQuery = workspace.database().query();
		globalScope = workspace.cloneWithoutNamespaceBindings().query();
		this.modulespace = workspace.database().createFolder("/modules-temp");
		if (modStore != null) {
			modStore.namespaceBindings().clear();
			modStore.namespaceBindings().put("", Engine.MOD_NS);
			modStore.namespaceBindings().put("mod", Engine.MOD_NS);
		}
		this.modStore = modStore;
		sortController = new SortController(this, modifiedDocs.anchor());
	}
	
	private static class PrevFunctionInfo {
		final QName name;
		final Query.Items query;
		final String args;		
		PrevFunctionInfo(Node def) throws RuleBaseException {
			this.query = new Query.Items(def);
			this.name = query.parseQName(def.query().single("@name").value());
			this.args = def.query().optional("@args").valueWithDefault("");
		}
	}
	
	private static class FunctionInfo extends PrevFunctionInfo {
		private final Collection<QName> referencedFunctionNames;
		private final Collection<FunctionInfo> referencedFunctions = new ArrayList<FunctionInfo>();
		private boolean modified;
		
		FunctionInfo(Node def) throws RuleBaseException {
			super(def);
			if (!query.namespaceBindings().equals(def.query().single("..").node().inScopeNamespaces())) {
				throw new RuleBaseException("function " + name + " must not declare additional namespace bindings");
			}
			this.referencedFunctionNames = query.analyze(def.database().query()).requiredFunctions();
		}
		
		void initModified(Map<QName, PrevFunctionInfo> prevFunctions) {
			PrevFunctionInfo prevFnInfo = prevFunctions.get(name);
			this.modified = !(prevFnInfo != null && args.equals(prevFnInfo.args) && query.equals(prevFnInfo.query));
		}
		
		void resolveReferencedFunctions(Map<QName, FunctionInfo> functions) {
			for (QName refName : referencedFunctionNames) {
				FunctionInfo refFnInfo = functions.get(refName);
				if (refFnInfo != null) referencedFunctions.add(refFnInfo);
			}
		}
		
		boolean propagateModified() {
			if (!this.modified) {
				for (FunctionInfo refFnInfo : referencedFunctions) {
					if (refFnInfo.modified) {
						modified = true;
						return true;
					}
				}
			}
			return false;
		}
		
		boolean isModified() {
			return modified;
		}
		
		void appendTo(StringBuilder source) {
			source.append("declare function " + name.getTag() + "(" + args + ") {\n");
			source.append(query.contentsAsString());
			source.append("\n};\n");
		}
		
		void appendModuleHeaderTo(StringBuilder source) throws RuleBaseException {
			// TODO: should escape characters when writing out namespaces
			if (name.getPrefix() == null || name.getPrefix().isEmpty()) {
				throw new RuleBaseException("function namespace " + name.getNamespaceURI() + " must be assigned to a prefix");
			}
			source.append("module namespace " + name.getPrefix() + " = '" + name.getNamespaceURI() + "';\n");
			for (Map.Entry<String, String> entry : query.namespaceBindings().getCombinedMap().entrySet()) {
				if (entry.getValue().equals(name.getNamespaceURI())) continue;
				if (entry.getKey().isEmpty()) {
					source.append("declare default element namespace '" + entry.getValue() + "';\n");					
				} else {
					source.append("declare namespace " + entry.getKey() + " = '" + entry.getValue() + "';\n");
				}
			}
		}
	}
	
	private Set<QName> parseFunctions(Resource rulespace, Resource prevrulespace) throws RuleBaseException {
		LOG.debug("parsing functions");
		
		Map<QName, FunctionInfo> functions = new TreeMap<QName, FunctionInfo>();
		for (Node def : rulespace.query().all("//function").nodes()) {
			FunctionInfo fnInfo = new FunctionInfo(def);
			if (functions.containsKey(fnInfo.name)) {
				throw new RuleBaseException("function overloading not yet supported: " + fnInfo.name.getTag());
			}
			functions.put(fnInfo.name, fnInfo);
		}
		
		assembleFunctionModules(functions);		
		Set<QName> namesOfModifiedFunctions = analyzeModifiedFunctions(prevrulespace, functions);
		
		LOG.debug("parsed " + functions.size() + " functions");
		return namesOfModifiedFunctions;
	}
	
	private void assembleFunctionModules(Map<QName, FunctionInfo> functions) throws RuleBaseException {
		Map<String, StringBuilder> moduleSources = new TreeMap<String, StringBuilder>();
		for (FunctionInfo fnInfo : functions.values()) {
			final String namespaceURI = fnInfo.name.getNamespaceURI();
			StringBuilder source = moduleSources.get(namespaceURI);
			if (source == null) {
				source = new StringBuilder();
				moduleSources.put(namespaceURI, source);
				fnInfo.appendModuleHeaderTo(source);
			}
			fnInfo.appendTo(source);
		}
		for (StringBuilder source : moduleSources.values()) {
			Document doc = modulespace.documents().load(Name.generate(".xq"), Source.blob(source.toString()));
			globalScope.importModule(doc);
		}
	}
	
	private Set<QName> analyzeModifiedFunctions(Resource prevrulespace, Map<QName, FunctionInfo> functions) throws RuleBaseException {
		Map<QName, PrevFunctionInfo> prevFunctions = new HashMap<QName, PrevFunctionInfo>();
		for (Node def : prevrulespace.query().unordered("//function").nodes()) {
			PrevFunctionInfo prevFnInfo = new PrevFunctionInfo(def);
			prevFunctions.put(prevFnInfo.name, prevFnInfo);
		}
		
		for (FunctionInfo fnInfo : functions.values()) {
			fnInfo.initModified(prevFunctions);
			fnInfo.resolveReferencedFunctions(functions);
		}
		
		boolean changed;
		do {
			changed = false;
			for (FunctionInfo fnInfo : functions.values()) changed |= fnInfo.propagateModified();
		} while (changed);
		
		Set<QName> namesOfModifiedFunctions = new TreeSet<QName>();
		for (FunctionInfo fnInfo : functions.values()) {
			if (fnInfo.isModified()) namesOfModifiedFunctions.add(fnInfo.name);
		}
		return namesOfModifiedFunctions;
	}

	private void parseRules(Resource rulespace, Resource prevrulespace, Collection<QName> namesOfModifiedFunctions) throws RuleBaseException {
		LOG.debug("parsing rules");
		ItemList ruleItems = rulespace.query().all("//rule");
		for (Node ruleDef : ruleItems.nodes()) {
			Node prevDef = prevrulespace.query().optional("/id($_1/@xml:id)", ruleDef).node();
			Rule rule = new Rule(ruleDef, prevDef, this, modifiedDocs.anchor(), namesOfModifiedFunctions);
			ruleMap.put(rule.id, rule);
			rules.add(rule);
		}
		LOG.debug("parsed " + ruleItems.size() + " rules");
	}
	
	private void invalidateIncompatibleBlocks(Resource prevrulespace) {
		LOG.debug("invalidating blocks with incompatible previous versions");
		for (QName badBlockName : Rule.verifyBlockTypeVersions(prevrulespace)) {
			for (Node badBlock : prevrulespace.query()
					.namespace("", badBlockName.getNamespaceURI()).presub()
					.all("//$1", badBlockName.getLocalPart()).nodes()) {
				// Adding a spurious attribute to the block will cause it to mismatch when
				// the rule compares it to the current one later.
				badBlock.update().namespace("record", Engine.RECORD_NS)
						.attr("record:version-mismatch", "true").commit();
			}
		}		
	}
	
	private void assignRuleIds(Resource rulespace, Resource prevrulespace) {
		LOG.debug("assigning IDs to rules that lack them");
		for (Node rule : rulespace.query().unordered("//rule[not(@xml:id)]").nodes()) {
			String id;
			ItemList oldIds = prevrulespace.query().unordered("let $names := ($_1/@name/string(), $_1/alias/@name/string()) return //rule[$names = (@name, alias/@name)]/@xml:id", rule);
			if (oldIds.size() == 1) {
				id = oldIds.get(0).value();
			} else {
				String ruleName = rule.query().single("@name").value();
				if (oldIds.size() > 1) LOG.warn("multiple old IDs match rule '" + ruleName + "' and its aliases, generating new ID");
				id = generateUniqueId("r-" + acronymize(ruleName), workspace.database().query(rulespace, prevrulespace));
			}
			rule.update().attr("xml:id", id).commit();
		}
	}
	
	private static String acronymize(String name) {
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
	
	/**
	 * Return whether the currently loaded MIXT processing code is the same version as
	 * that used for the last run of the transformation.
	 * 
	 * @param prevrulespace an ancestor of the resource to which versions were recorded on the last run
	 * @return <code>true</code> if the engine version matches, <code>false</code> otherwise
	 */
	public static boolean isSameVersionAs(Resource prevrulespace) {
		return VERSION.equals(prevrulespace.query().namespace("record", Engine.RECORD_NS)
				.optional("//record:engine/@version").value());
	}
	
	/**
	 * Record the versions of the various currently loaded pieces of MIXT processing code.
	 * 
	 * @param builder a builder on the resource to write to
	 */
	public static void recordVersions(ElementBuilder<?> builder) {
		builder.namespace("record", Engine.RECORD_NS)
			.elem("record:engine").attr("version", VERSION).end("record:engine");
		Rule.writeBlockTypeVersions(builder);
	}
	
	String relativePath(Document doc) {
		return workspace.relativePath(doc.path());
	}

	Folder workspace() {return workspace;}
	QueryService globalScope() {return globalScope;}
	QueryService customScope(Collection<? extends Resource> context) {
		return globalScope.database().query(context).importSameModulesAs(globalScope);
	}
	QueryService utilQuery() {return utilQuery;}
	Node modStore() {return modStore;}
	public Stats stats() {return stats;}
		
	Rule findRule(String ruleId) {
		return ruleMap.get(ruleId);
	}
	
	public void autoGenerateIdsWithPrefix(String prefix) {
		if (!(Character.isLetter(prefix.charAt(0)) || prefix.charAt(0) == '_'))
			throw new IllegalArgumentException("xml id prefix must start with letter or underscore, got '" + prefix + "'");
		autoGenIdPrefix = prefix;
	}
	
	boolean ensureWorkspaceNodeHasXmlId(Node node) {
		if (node.query().exists("@xml:id")) return true;
		if (autoGenIdPrefix == null) return false;
		node.update().attr("xml:id", generateUniqueId(autoGenIdPrefix, globalScope)).commit();
		return true;
	}
	
	String generateUniqueId(String prefix, QueryService scope) {
		if (!prefix.isEmpty() && !scope.exists("/id($_1)", prefix)) return prefix;
		int range = 8;
		String id;
		do {
			id = prefix + "-" + Integer.toString(random.nextInt(range), Character.MAX_RADIX);
			if (range < Integer.MAX_VALUE / 2) range *= 2; range = Integer.MAX_VALUE;
		} while (scope.exists("/id($_1)", id));
		return id;
	}
	
	void eventuallySort(Node node) {
		sortController.eventuallySort(node);
	}

	private final Document.Listener modifiedDocListener = new Document.Listener() {
		public void handle(org.exist.fluent.Document.Event ev) {
			if (ev.document.equals(modStore.document())) {
				didWork = true;
			} else {
				try {
					modifiedDocs.add(ev.document.xml());
					docsModified = true;
				} catch (DatabaseException e) {
					// must have been a blob document, ignore
				}
			}
		}
	};

	/**
	 * Run the transformation.
	 * @param initialModifiedDocs the docs that may have been modified since the last run; if <code>null</code>, don't do incremental processing
	 * @return the date as of which the transformation is current; any documents modified after this date may not have been considered
	 * @throws TransformException 
	 * @throws InterruptedException 
	 */
	public Date executeTransform(Collection<XMLDocument> initialModifiedDocs) throws TransformException, InterruptedException {
		if (initialModifiedDocs != null) modifiedDocs.addAll(initialModifiedDocs);	// MUST happen after all initial locators have been anchored
		workspace.listeners().add(	EnumSet.of(Trigger.AFTER_CREATE, Trigger.AFTER_UPDATE), modifiedDocListener);
		if (!workspace.contains(modStore.document())) modStore.document().listeners().add(Trigger.AFTER_UPDATE, modifiedDocListener);
		
		Date lastRun;
		try {
			do {
				lastRun = new Date();
				stats.numCycles.increment();
				LOG.debug("running MIXT transformation cycle " + stats.numCycles.value());
				didWork = false;
				docsModified = false;
				for (Rule rule : rules) {
					if (Thread.interrupted()) throw new InterruptedException();
					rule.process(stats.numCycles.value() == 1 && initialModifiedDocs == null);
				}
				if (Thread.interrupted()) throw new InterruptedException();
				sortController.executeEndOfCycle();
				// TODO: detect livelock loops 				
			} while (didWork || docsModified);
			assert sortController.done();
			LOG.info("MIXT transformation complete; "
					+ stats.numCycles + ", " + stats.numBlocksResolved + ", "
					+ stats.numBlocksVerified + ", " + stats.numModsCompleted + ", " + stats.numModsWithdrawn);
			return lastRun;
		} finally {
			workspace.listeners().remove(modifiedDocListener);
			if (!workspace.contains(modStore.document())) modStore.document().listeners().remove(modifiedDocListener);
		}
	}
	
	void withdrawMod(Node modNode) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("withdrawing mod " + modNode.query().single("./ancestor-or-self::*[@xml:id][1]/@xml:id").value() + " at stage " + modNode.query().single("@stage").value());
		}
		withdrawMods(modNode.query().unordered("self::*"));
	}
	
	void withdrawRule(String ruleId) {
		LOG.debug("withdrawing all mods for rule[" + ruleId + "]");
		withdrawMods(modStore.query().unordered("mods[@rule=$_1]", ruleId));
	}

	void withdrawMods(ItemList newMods)	{
		ItemList mods = workspace.query().all("()");
		ItemList affected = workspace.query().all("()");
		
		while (newMods.size() > 0) {
			ItemList newAffected = workspace.query().unordered("/id(distinct-values($_1//mod:affected/@refid))", newMods);
			affected = utilQuery.unordered("$_1 union $_2", affected, newAffected);
			mods = utilQuery.unordered("$_1 union $_2", mods, newMods);
			// TODO: refactor query to use idref() once we can declare @refid as type IDREF
			newMods = modStore.query().unordered(
					"//mod[reference/@refid=$_1/descendant-or-self::*/@xml:id] except $_2",
					newAffected, mods);
		}
		
		// Touch only the dependencies of the highest withdrawn mods, since any children will be resolved in global scope anyway.
		mods = mods.query().namespace("", Engine.MOD_NS).unordered("$_1 except $_1/descendant::*", mods);
		
		for (String ruleId : utilQuery.unordered("distinct-values($_1/ancestor-or-self::*/@rule)", mods).values()) {
			Rule rule = ruleMap.get(ruleId);
			if (rule == null) continue;
			ItemList docPaths = utilQuery.unordered("distinct-values($_1[ancestor-or-self::*/@rule=$_2]/mod:dependency/@doc)", mods, ruleId);
			List<Document> docs = new ArrayList<Document>(docPaths.size());
			for (String docPath : docPaths.values()) {
				if (workspace.documents().contains(docPath)) docs.add(workspace.documents().get(docPath));
			}
			rule.addTouched(docs);
		}
		
		for (Node sortedNode : workspace.query().unordered("/id(distinct-values($_1//mod:order/@refid))", mods).nodes()) {
			eventuallySort(sortedNode);
		}
		
		LOG.debug("deleting " + modCountFormatter.format(mods.size()) + " and " + affectedCountFormatter.format(affected.size()));
		
		stats.numModsWithdrawn.increment(
				mods.query().namespace("", Engine.MOD_NS).single("count(descendant-or-self::mod)").intValue());
		affected.deleteAllNodes();
		mods.deleteAllNodes();
	}
	
	@Deprecated @RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static class _Test extends DatabaseTestCase {
		private static Field firstDifferentStage;
		@BeforeClass public static void setupAccessors() throws SecurityException, NoSuchFieldException {
			firstDifferentStage = Rule.class.getDeclaredField("firstDifferentStage");
			firstDifferentStage.setAccessible(true);
		}
		
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		private Folder workspace, rulespace, prevrulespace;
		
		@Before public void createSpaces() {
			workspace = db.createFolder("/workspace");
			workspace.namespaceBindings().put("mod", Engine.MOD_NS);
			rulespace = db.createFolder("/rulespace");
			rulespace.namespaceBindings().put("", Engine.MIXT_NS);
			prevrulespace = db.createFolder("/prevrulespace");
			prevrulespace.namespaceBindings().put("", Engine.MIXT_NS);
		}
		
		@Test public void invalidateIncompatibleBlocks() {
			db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<root xmlns='" + Engine.RECORD_NS + "'>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.For' version='" + new For().version() + "'/>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.With' version='" + new With().version() + "foo'/>" +
					"</root>"));
			db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1'>" +
					"    <for each='$x'>/foo</for>" +
					"    <with some='$y'>$x/bar</with>" +
					"    <insert><xyz/></insert>" +
					"  </rule>" +
					"</rules>"));
			Engine engine = new Engine(workspace, null);
			engine.invalidateIncompatibleBlocks(db.getFolder("/"));
			Node rule = db.getFolder("/").query()
					.namespace("", Engine.MIXT_NS)
					.namespace("record", Engine.RECORD_NS)
					.single("/id('r1')").node();
			assertFalse(rule.query().exists("for[@record:version-mismatch]"));
			assertTrue(rule.query().exists("with[@record:version-mismatch]"));
			assertTrue(rule.query().exists("insert[@record:version-mismatch]"));
		}
		
		@Test public void recordAndVerifyVersion() {
			ElementBuilder<?> builder = db.getFolder("/").documents().build(Name.generate()).elem("root");
			Engine.recordVersions(builder);
			builder.end("root").commit();
			assertTrue(Engine.isSameVersionAs(db.getFolder("/")));
			assertTrue(db.getFolder("/").query().namespace("", Engine.RECORD_NS).exists("//block-type"));
		}
		
		@Test public void verifyBadVersion() {
			db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<root xmlns='" + Engine.RECORD_NS + "'>" +
					"  <engine version='" + Engine.VERSION + "foo'/>" +
					"</root>"));
			assertFalse(Engine.isSameVersionAs(db.getFolder("/")));
		}
		
		@Test public void analyzeModifiedFunctions() throws RuleBaseException {
			String ns_x = "http://example.com", ns_y = "http://ideanest.com";
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + Engine.MIXT_NS + "' xmlns:x='" + ns_x + "' xmlns:y='" + ns_y + "'>" +
					"	<function name = 'x:f1'> /foo </function>" +
					"	<function name = 'x:f2' args = '$a'> /bar[@a=$a] </function>" +
					"	<function name = 'x:f3'> x:f1() </function>" +
					"	<function name = 'x:f4'> /foo </function>" +
					"	<function name = 'x:f5'> /foo </function>" +
					"	<function name = 'x:f6'> x:f4() </function>" +
					"	<function name = 'x:f7'> x:f6() </function>" +
					"	<function name = 'x:f8'> /foo </function>" +
					"	<function name = 'x:f9' args = '$a, $b'> /bar[@a=$a][@b=$b] </function>" +
					"</rules>"
			));
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + Engine.MIXT_NS + "' xmlns:x='" + ns_x + "' xmlns:y='" + ns_y + "'>" +
					"	<function name = 'x:f1'> /foo </function>" +
					"	<function name = 'x:f2' args = '$a'> /bar[@a=$a] </function>" +
					"	<function name = 'x:f3'> x:f1() </function>" +
					"	<function name = 'x:f4'> /bar </function>" +
					"	<function name = 'x:f6'> x:f4() </function>" +
					"	<function name = 'x:f7'> x:f6() </function>" +
					"	<function name = 'y:f8'> /foo </function>" +
					"	<function name = 'x:f9' args = '$b, $a'> /bar[@a=$a][@b=$b] </function>" +
					"</rules>"
			));
			Collection<FunctionInfo> defs = new ArrayList<Engine.FunctionInfo>();
			for (Node node : rulespace.query().all("//function").nodes()) {
				defs.add(new FunctionInfo(node));
			}
			Map<QName, FunctionInfo> functions = new TreeMap<QName, Engine.FunctionInfo>();
			for (FunctionInfo fnInfo : defs) functions.put(fnInfo.name, fnInfo);
			Engine engine = new Engine(workspace, null);
			assertThat(
					engine.analyzeModifiedFunctions(prevrulespace, functions),
					Matchers.collection(
							new QName(ns_x, "f4", null), new QName(ns_x, "f5", null), new QName(ns_x, "f6", null),
							new QName(ns_x, "f7", null), new QName(ns_x, "f8", null), new QName(ns_x, "f9", null)));
		}
		
		@Test public void assembleFunctionModules() throws RuleBaseException {
			String ns_x = "http://example.com", ns_y = "http://ideanest.com";
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + Engine.MIXT_NS + "' xmlns:x='" + ns_x + "' xmlns:y='" + ns_y + "'>" +
					"	<function name = 'x:f1'> /foo </function>" +
					"	<function name = 'y:f8'> /foo </function>" +
					"	<function name = 'x:f9' args = '$b, $a'> /bar[@a=$a][@b=$b] </function>" +
					"</rules>"
			));
			Collection<FunctionInfo> defs = new ArrayList<Engine.FunctionInfo>();
			for (Node node : rulespace.query().all("//function").nodes()) {
				defs.add(new FunctionInfo(node));
			}
			Map<QName, FunctionInfo> functions = new TreeMap<QName, Engine.FunctionInfo>();
			for (FunctionInfo fnInfo : defs) functions.put(fnInfo.name, fnInfo);
			Engine engine = new Engine(workspace, null);
			engine.assembleFunctionModules(functions);
			Collection<String> modules = new ArrayList<String>();
			for (Document doc : engine.modulespace.documents()) modules.add(doc.contentsAsString());
			assertThat(modules, Matchers.collection(
					"module namespace x = '" + ns_x + "';\n" +
						"declare default element namespace '" + Engine.MIXT_NS + "';\n" +
						"declare namespace y = '" + ns_y + "';\n" +
						"declare function x:f1() {\n" +
						" /foo \n" +
						"};\n" +
						"declare function x:f9($b, $a) {\n" +
						" /bar[@a=$a][@b=$b] \n" +
						"};\n",
					"module namespace y = '" + ns_y + "';\n" +
						"declare default element namespace '" + Engine.MIXT_NS + "';\n" +
						"declare namespace x = '" + ns_x + "';\n" +
						"declare function y:f8() {\n" +
						" /foo \n" +
						"};\n"
			));
		}
		
		@Test public void parseRules() throws RuleBaseException, IllegalArgumentException, IllegalAccessException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
			Engine engine = new Engine(workspace, modStore);
			engine.parseRules(rulespace, prevrulespace, new ArrayList<QName>());
			assertEquals(1, engine.rules.size());
			assertSame(engine.rules.get(0), engine.ruleMap.get("r1"));
			assertEquals(0, firstDifferentStage.get(engine.rules.get(0)));
		}
		
		@Test public void assignRuleIDs_generateFreshID() {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule name='myrule'><create-doc>foobar</create-doc></rule>" +
					"	<rule xml:id='r2' name='mysecondrule'/>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertTrue(rulespace.query().exists("//rule[@name='mysecondrule'][@xml:id='r2']"));
			assertTrue(rulespace.query().exists("//rule[@name='myrule']/@xml:id"));
			assertFalse("r2".equals(rulespace.query().single("//rule[@name='myrule']/@xml:id").value()));
		}
		
		@Test public void assignRuleIDs_copyPrevID() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='myrule'/>" +
					"  <rule xml:id='r2' name='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@name='myrule']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_matchAliasToName() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='myrule'/>" +
					"  <rule xml:id='r2' name='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule name='foo'><alias name='myrule'/><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@name='foo']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_matchNameToAlias() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='foo'><alias name='myrule'/></rule>" +
					"  <rule xml:id='r2' name='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@name='myrule']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_matchAliasToAlias() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='foo'><alias name='myrule'/></rule>" +
					"  <rule xml:id='r2' name='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule name='bar'><alias name='myrule'/><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@name='bar']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_multipleMatch() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='foo'><alias name='myrule'/></rule>" +
					"  <rule xml:id='r2' name='myrule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertTrue(rulespace.query().exists("//rule[@name='myrule']/@xml:id"));
			assertFalse("r1".equals(rulespace.query().single("//rule[@name='myrule']/@xml:id").value()));
			assertFalse("r2".equals(rulespace.query().single("//rule[@name='myrule']/@xml:id").value()));
		}
		
		@Test public void create() throws RuleBaseException, IllegalArgumentException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"	<mod xml:id='_r1.1'/>" +
					"	<mod xml:id='_r1.2'><dependency doc='foo'/></mod>" +
					"	<mod xml:id='_r1.3'><dependency doc='bar/foo'/></mod>" +
					"	<mod xml:id='_r1.4'><dependency doc='bar/baz'/></mod>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"	<mod xml:id='_r2.1'/>" +
					"</mods>" +
					"</modstore>")).root();
			modStore.namespaceBindings().put("", Engine.MOD_NS);
			modStore.namespaceBindings().put("mod", Engine.MOD_NS);
			workspace.documents().load(Name.create("foo"), Source.xml("<foo/>"));
			workspace.children().create("bar").documents().load(Name.create("foo"), Source.xml("<foo/>"));
			Engine engine = new Engine(rulespace, prevrulespace, workspace, modStore);
			assertEquals(1, engine.rules.size());
			assertTrue("withdrew mod of live rule", workspace.query().exists("/id('_r1.1')"));
			assertTrue("withdrew mod of available documents", workspace.query().exists("/id('_r1.2')"));
			assertTrue("withdrew mod of available documents", workspace.query().exists("/id('_r1.3')"));
			assertFalse("did not withdraw mod of unavailable document", workspace.query().exists("/id('_r1.4')"));
			assertFalse("did not withdraw mod of dead rule", workspace.query().exists("/id('_r2.1')"));
		}
		
		@Test public void ensureWorkspaceNodeHasXmlId() {
			Engine engine = new Engine(workspace, null);
			assertTrue(engine.ensureWorkspaceNodeHasXmlId(workspace.documents().load(Name.generate(), Source.xml("<foo xml:id='f'/>")).root()));
		}
		
		@Test public void ensureWorkspaceNodeHasXmlId_noIdNoAutogen() {
			Engine engine = new Engine(workspace, null);
			assertFalse(engine.ensureWorkspaceNodeHasXmlId(workspace.documents().load(Name.generate(), Source.xml("<foo/>")).root()));
		}
		
		@Test public void ensureWorkspaceNodeHasXmlId_noIdWithAutogen() {
			Engine engine = new Engine(workspace, null);
			engine.autoGenerateIdsWithPrefix("mixt-");
			Node node = workspace.documents().load(Name.generate(), Source.xml("<foo/>")).root();
			assertTrue(engine.ensureWorkspaceNodeHasXmlId(node));
			assertTrue(node.query().single("@xml:id").value().startsWith("mixt-"));
		}
		
		@Test public void executeTransform() throws TransformException, InterruptedException {
			final XMLDocument olddoc1 = workspace.documents().load(Name.create("olddoc1"), Source.xml("<bar/>"));
			final XMLDocument olddoc2 = workspace.documents().load(Name.create("olddoc2"), Source.xml("<bar/>"));
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
			final Rule rule = mockery.mock(Rule.class);
			Engine engine = new Engine(workspace, modStore);
			final Accumulator.Locator<XMLDocument> locator = engine.modifiedDocs.anchor();
			mockery.checking(new Expectations() {{
				exactly(2).of(rule).process(false); will(onConsecutiveCalls(
						new Action() {
							public Object invoke(Invocation invocation) throws Throwable {
								assertEquals(Collections.singleton(olddoc1), locator.catchUp());
								workspace.documents().load(Name.create("newdoc"), Source.xml("<foo/>"));
								olddoc2.root().append().elem("bar").end("bar").commit();
								modStore.append().elem("mod").end("mod").commit();
								return null;
							}
							public void describeTo(Description description) {}
						},
						returnValue(null)
				));
			}});
			engine.rules.add(rule);
			engine.executeTransform(Collections.singleton(olddoc1));
			assertEquals(new HashSet<Document>(Arrays.asList(new Document[] {olddoc2, workspace.documents().get("newdoc")})), locator.catchUp());
			assertEquals(2, engine.stats.numCycles.value());
		}
		
		@Test(expected = InterruptedException.class)
		public void executeTransformCanBeInterrupted() throws TransformException, InterruptedException {
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
			final Rule rule = mockery.mock(Rule.class);
			Engine engine = new Engine(workspace, modStore);
			mockery.checking(new Expectations() {{
				one(rule).process(false); will(onConsecutiveCalls(
						new Action() {
							public Object invoke(Invocation invocation) throws Throwable {
								modStore.append().elem("mod").end("mod").commit();
								Thread.currentThread().interrupt();
								return null;
							}
							public void describeTo(Description description) {}
						},
						returnValue(null)
				));
			}});
			engine.rules.add(rule);
			engine.executeTransform(Collections.<XMLDocument>emptySet());
		}
		
		@Test public void withdrawModsSimple() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'/>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithDescendants() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'>" +
					"  	<mod xml:id='m1.1'/>" +
					"	</mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1.1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(2, engine.stats.numModsWithdrawn.value());
		}
		
		@Test public void withdrawModsWithAffectedDocs() {
			workspace.documents().load(Name.generate(), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithAffectedDocsAndDependencies() {
			final Document doc = workspace.documents().load(Name.create("a"), Source.xml(
			"<foo><baz xml:id='a1'/></foo>"));
			workspace.documents().load(Name.create("b"), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			workspace.documents().load(Name.create("c"), Source.xml(
					"<foo><baz xml:id='c1'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/><dependency doc='a'/>" +
					"  	<mod xml:id='m1.1'><dependency doc='c'/></mod>" +
					"	</mod>" +
					"  <mod xml:id='m2'><dependency doc='b'/></mod>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				one(r1).addTouched(with(aCollectionOf(doc)));
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(2, engine.stats.numModsWithdrawn.value());
		}
		
		@Test public void withdrawModsWithAffectedDocsAndKnockOnReferences() {
			workspace.documents().load(Name.generate(), Source.xml(
					"<foo><bar xml:id='b1'><baz xml:id='b1a'/></bar><bar xml:id='b2'/><xyz xml:id='b3'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"  <mod xml:id='m3'><reference refid='b1'/></mod>" +
					"  <mod xml:id='m4'><reference refid='b1a'/><affected refid='b3'/></mod>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m3')"));
			assertFalse(modStore.query().exists("/id('m4')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertFalse(workspace.query().exists("/id('b3')"));
			assertEquals(3, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithOrderedNodes() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
			final Document doc = workspace.documents().load(Name.create("b"), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><order refid='b1' doc='b'/></mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			final Engine engine = new Engine(workspace, modStore);
			Field sortControllerField = Engine.class.getDeclaredField("sortController");
			sortControllerField.setAccessible(true);
			sortControllerField.set(engine, mockery.mock(SortController.class));
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
				one(engine.sortController).eventuallySort(doc.query().single("/id('b1')").node());
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertTrue(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawRule() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'/>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawRule("r1");
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawMod() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'/>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMod(modStore.query().single("/id('m1')").node());
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		private <E> Matcher<Collection<E>> aCollectionOf(E... items) {
			final Set<E> itemList = new HashSet<E>(Arrays.asList(items));
			return new TypeSafeMatcher<Collection<E>>() {
				@Override public boolean matchesSafely(Collection<E> item) {
					return new HashSet<E>(item).equals(itemList);
				}
				public void describeTo(Description description) {
					description.appendText("collection of  " + itemList);
				}
			};
		}

		private <E> Matcher<Collection<E>> anEmptyCollection(Class<E> clazz) {
			return new TypeSafeMatcher<Collection<E>>() {
				@Override public boolean matchesSafely(Collection<E> item) {
					return item.isEmpty();
				}
				public void describeTo(Description description) {
					description.appendText("an empty collection");
				}
			};
		}
	}
	
	@Deprecated
	public static class _TestAcronymize {
		@Test public void test1() {
			assertEquals("ast", Engine.acronymize("a-silly-test"));
		}
		@Test public void test2() {
			assertEquals("ast", Engine.acronymize("A-silly-Test"));
		}
		@Test public void test3() {
			assertEquals("st", Engine.acronymize("__stupid-test.5"));
		}
		@Test public void test4() {
			assertEquals("bbb", Engine.acronymize("blah BoOh bee"));
		}
		@Test public void test5() {
			assertEquals("", Engine.acronymize("1d 3g.98X-1_"));
		}
		@Test public void test6() {
			assertEquals("ast", Engine.acronymize("ASillyTest"));
		}
		@Test public void test7() {
			assertEquals("ast", Engine.acronymize("aSillyTest"));
		}
		@Test public void test8() {
			assertEquals("avst", Engine.acronymize("AVerySillyTestIndeed"));
		}
		@Test public void test9() {
			assertEquals("mmm", Engine.acronymize("$moneyMoneyMoney"));
		}
		@Test public void test10() {
			assertEquals("sil", Engine.acronymize("sillytest"));
		}
		@Test public void test11() {
			assertEquals("si", Engine.acronymize("si"));
		}
		@Test public void test12() {
			assertEquals("st", Engine.acronymize("SillyTest"));
		}
	}

}
