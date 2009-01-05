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
		
		this.modStore = modStore;
		modStore.namespaceBindings().put("", Engine.MOD_NS);
		modStore.namespaceBindings().put("mod", Engine.MOD_NS);
		
		rulespace.namespaceBindings().put("", Engine.MIXT_NS);
		prevrulespace.namespaceBindings().put("", Engine.MIXT_NS);
		prevrulespace.namespaceBindings().put("record", Engine.MIXT_NS);
		
		this.sortController = new SortController(this, modifiedDocs.anchor());

		LOG.debug("parsing functions");
		Map<XMLDocument, Module> modules = parseFunctions(rulespace);
		for (Module module : modules.values()) {
			module.resolveReferencedFunctions();
			module.saveTo(modulespace);
		}
		Map<XMLDocument, Module> prevModules;
		try {
			prevModules = parseFunctions(prevrulespace);
		} catch (RuleBaseException e) {
			LOG.warn("error parsing previous function definitions, continuing as if no previous functions were defined");
			prevModules = Collections.emptyMap();
		}
		
		invalidateIncompatibleBlocks(prevrulespace);
		assignRuleIds(rulespace, prevrulespace);
		parseRules(rulespace, prevrulespace, modules, prevModules);
		
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
		this.modulespace = workspace.database().createFolder("/modules-temp");
		if (modStore != null) {
			modStore.namespaceBindings().clear();
			modStore.namespaceBindings().put("", Engine.MOD_NS);
			modStore.namespaceBindings().put("mod", Engine.MOD_NS);
		}
		this.modStore = modStore;
		sortController = new SortController(this, modifiedDocs.anchor());
	}
	
	static class Module {
		private static final String LOCAL_NS = "http://www.w3.org/2005/xquery-local-functions";
		private final Map<QName, Function> functions = new TreeMap<QName, Function>();
		private final NamespaceMap namespaceBindings;
		private Document moduleDoc;
		
		Module(XMLDocument rulesDoc) {
			this.namespaceBindings = rulesDoc.root().inScopeNamespaces();
		}
		
		void defineFunction(Node def) throws RuleBaseException {
			Function fn = new Function(def);
			if (!def.inScopeNamespaces().equals(namespaceBindings)) {
				throw new RuleBaseException("function " + fn.name.getTag() + " must not declare additional namespace bindings");
			}
			if (functions.containsKey(fn.name)) {
				throw new RuleBaseException("function overloading not yet supported: " + fn.name.getTag());
			}
			functions.put(fn.name, fn);
		}
		
		private Function getFunction(QName name) {
			return functions.get(name);
		}
		
		void saveTo(Folder modulespace) {
			if (functions.isEmpty()) return;
			StringBuilder source = new StringBuilder();
			appendModuleHeaderTo(source);
			for (Function fn : functions.values()) fn.appendTo(source);
			moduleDoc = modulespace.documents().load(Name.generate(".xq"), Source.blob(source.toString()));
		}
		
		Document source() {
			return moduleDoc;
		}
		
		void resolveReferencedFunctions() {
			for (Function fn : functions.values()) fn.resolveReferencedFunctions(this);
		}
		
		boolean areFunctionsModified(Set<QName> calledFunctionNames, Module prevModule) {
			if (calledFunctionNames.isEmpty()) return false;
			if (prevModule == null) return true;
			for (QName fname : calledFunctionNames) {
				Function fn = functions.get(fname);
				if (fn == null || fn.isModified(prevModule)) return true;
			}
			return false;
		}
		
		private void appendModuleHeaderTo(StringBuilder out) {
			// TODO: should escape characters when writing out namespaces
			out.append("module namespace local = '" + LOCAL_NS +"';\n");
			for (Map.Entry<String, String> entry : namespaceBindings.getCombinedMap().entrySet()) {
				if (entry.getValue().equals(MIXT_NS)) continue;
				if (entry.getKey().isEmpty()) {
					out.append("declare default element namespace '" + entry.getValue() + "';\n");					
				} else {
					out.append("declare namespace " + entry.getKey() + " = '" + entry.getValue() + "';\n");
				}
			}
		}
		
		private static class Function implements Comparable<Function> {
			private final QName name;
			private final Query.Items query;
			private final String args;		
			private final Set<QName> referencedFunctionNames;
			
			private Set<Function> allReferencedFunctionsAndSelf = new TreeSet<Function>();
			
			Function(Node def) throws RuleBaseException {
				this.query = new Query.Items(def);
				this.name = new QName(LOCAL_NS, def.query().single("@name").value(), "local");
				this.args = def.query().optional("@args").valueWithDefault("");
				this.referencedFunctionNames = query.analyze(def.database().query()).requiredFunctions();
				allReferencedFunctionsAndSelf.add(this);
			}
			
			boolean isModified(Module prevModule) {
				for (Function fn : allReferencedFunctionsAndSelf) {
					Function prevFn = prevModule.getFunction(fn.name);
					if (!(prevFn != null && fn.args.equals(prevFn.args) && fn.query.equals(prevFn.query))) return true;
				}
				return false;
			}
			
			void resolveReferencedFunctions(Module module) {
				for (QName refName : referencedFunctionNames) {
					Function refFn = module.getFunction(refName);
					if (refFn != null) allReferencedFunctionsAndSelf.add(refFn);
				}
				
				Set<Function> moreFunctions = new TreeSet<Function>();
				do {
					moreFunctions.clear();
					for (Function refFn : allReferencedFunctionsAndSelf) {
						moreFunctions.addAll(refFn.allReferencedFunctionsAndSelf);
					}
				} while (allReferencedFunctionsAndSelf.addAll(moreFunctions));
			}
			
			void appendTo(StringBuilder out) {
				out.append("declare function " + name.getTag() + "(" + args + ") {\n");
				out.append(query.contentsAsString());
				out.append("\n};\n");
			}

			@Override public int compareTo(Function o) {
				return name.compareTo(o.name);
			}
			
		}
	}
	
	private Map<XMLDocument, Module> parseFunctions(Resource rulespace) throws RuleBaseException {
		Map<XMLDocument, Module> modules = new HashMap<XMLDocument, Module>();
		for (Node rulesRoot : rulespace.query().unordered("//rules").nodes()) {
			Module module = new Module(rulesRoot.document());
			modules.put(rulesRoot.document(), module);
			for (Node fnDef : rulesRoot.query().unordered("//function").nodes()) {
				module.defineFunction(fnDef);
			}
		}
		return modules;
	}

	private void parseRules(Resource rulespace, Resource prevrulespace, Map<XMLDocument, Module> modules, Map<XMLDocument, Module> prevModules) throws RuleBaseException {
		LOG.debug("parsing rules");
		ItemList ruleItems = rulespace.query().all("//rule");
		for (Node ruleDef : ruleItems.nodes()) {
			Node prevDef = prevrulespace.query().optional("/id($_1/@xml:id)", ruleDef).node();
			Rule rule = new Rule(
					ruleDef, prevDef, this, modifiedDocs.anchor(),
					modules.get(ruleDef.document()), prevDef.extant() ? prevModules.get(prevDef.document()) : null);
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
			ItemList oldIds = prevrulespace.query().unordered("let $names := ($_1/@desc/string(), $_1/alias/@desc/string()) return //rule[$names = (@desc, alias/@desc)]/@xml:id", rule);
			if (oldIds.size() == 1) {
				id = oldIds.get(0).value();
			} else {
				String ruleName = rule.query().single("@desc").value();
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
		node.update().attr("xml:id", generateUniqueId(autoGenIdPrefix, workspace.query())).commit();
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
			ItemList newAffected = workspace.query().unordered("/id($_1//mod:affected/@refid)", newMods);
			affected = utilQuery.unordered("$_1 union $_2", affected, newAffected);
			mods = utilQuery.unordered("$_1 union $_2", mods, newMods);
			// TODO: refactor query to use idref() once we can declare @refid as type IDREF
			List<String> newAffectedDocNames = new ArrayList<String>();
			for (Document doc : newAffected.nodes().documents()) newAffectedDocNames.add(workspace.relativePath(doc.path()));
			newMods = modStore.query().unordered(
					"//reference[@doc = $_4][let $refid := @refid return exists($_3/id($refid)/ancestor-or-self::* intersect $_1)]/parent::mod except $_2",
					newAffected, mods, workspace, newAffectedDocNames);
		}
		
		// Touch only the dependencies of the highest withdrawn mods, since any children will be resolved in global scope anyway.
		mods = mods.query().namespace("", Engine.MOD_NS).unordered("$_1 except $_1/descendant::*", mods);
		
		for (String ruleId : utilQuery.unordered("distinct-values($_1/ancestor-or-self::*/@rule)", mods).values()) {
			Rule rule = ruleMap.get(ruleId);
			if (rule == null) continue;
			ItemList docPaths = utilQuery.unordered("distinct-values($_1[ancestor-or-self::*/@rule=$_2]/mod:dependency/@doc)", mods, ruleId);
			List<XMLDocument> docs = new ArrayList<XMLDocument>(docPaths.size());
			for (String docPath : docPaths.values()) {
				if (workspace.documents().contains(docPath)) docs.add(workspace.documents().get(docPath).xml());
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
		
		@Test public void parseRules() throws RuleBaseException, IllegalArgumentException, IllegalAccessException {
			XMLDocument rulesDoc = rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
			Engine engine = new Engine(workspace, modStore);
			engine.parseRules(rulespace, prevrulespace, Collections.singletonMap(rulesDoc, new Module(rulesDoc)), Collections.<XMLDocument, Module>emptyMap());
			assertEquals(1, engine.rules.size());
			assertSame(engine.rules.get(0), engine.ruleMap.get("r1"));
			assertEquals(0, firstDifferentStage.get(engine.rules.get(0)));
		}
		
		@Test public void assignRuleIDs_generateFreshID() {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule desc='myrule'><create-doc>foobar</create-doc></rule>" +
					"	<rule xml:id='r2' desc='mysecondrule'/>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertTrue(rulespace.query().exists("//rule[@desc='mysecondrule'][@xml:id='r2']"));
			assertTrue(rulespace.query().exists("//rule[@desc='myrule']/@xml:id"));
			assertFalse("r2".equals(rulespace.query().single("//rule[@desc='myrule']/@xml:id").value()));
		}
		
		@Test public void assignRuleIDs_copyPrevID() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='myrule'/>" +
					"  <rule xml:id='r2' desc='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule desc='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@desc='myrule']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_matchAliasToName() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='myrule'/>" +
					"  <rule xml:id='r2' desc='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule desc='foo'><alias desc='myrule'/><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@desc='foo']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_matchNameToAlias() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='foo'><alias desc='myrule'/></rule>" +
					"  <rule xml:id='r2' desc='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule desc='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@desc='myrule']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_matchAliasToAlias() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='foo'><alias desc='myrule'/></rule>" +
					"  <rule xml:id='r2' desc='some other rule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule desc='bar'><alias desc='myrule'/><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertEquals("r1", rulespace.query().single("//rule[@desc='bar']/@xml:id").value());
		}
		
		@Test public void assignRuleIDs_multipleMatch() {
			prevrulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='foo'><alias desc='myrule'/></rule>" +
					"  <rule xml:id='r2' desc='myrule'/>" +
					"</rules>"
			));
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule desc='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Engine engine = new Engine(workspace, null);
			engine.assignRuleIds(rulespace, prevrulespace);
			assertTrue(rulespace.query().exists("//rule[@desc='myrule']/@xml:id"));
			assertFalse("r1".equals(rulespace.query().single("//rule[@desc='myrule']/@xml:id").value()));
			assertFalse("r2".equals(rulespace.query().single("//rule[@desc='myrule']/@xml:id").value()));
		}
		
		@Test public void create() throws RuleBaseException, IllegalArgumentException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Engine.MIXT_NS + "'>" +
					"  <rule xml:id='r1' desc='myrule'><create-doc>foobar</create-doc></rule>" +
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
			final XMLDocument doc = workspace.documents().load(Name.create("a"), Source.xml(
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
			workspace.documents().load(Name.create("doc1"), Source.xml(
					"<foo><bar xml:id='b1'><baz xml:id='b1a'/></bar><bar xml:id='b2'/><xyz xml:id='b3'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"  <mod xml:id='m3'><reference refid='b1' doc='doc1'/></mod>" +
					"  <mod xml:id='m4'><reference refid='b1a' doc='doc1'/><affected refid='b3'/></mod>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
				allowing(r1).addTouched(with(anEmptyCollection(XMLDocument.class)));
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
	
	@Deprecated @RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static class _TestModule extends DatabaseTestCase {
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		
		private XMLDocument makeRules(String fn) {
			Folder folder = db.getFolder("/");
			folder.namespaceBindings().put("", Engine.MIXT_NS);
			return folder.documents().load(
					Name.generate(), Source.xml("<rules xmlns='" + Engine.MIXT_NS + "' xmlns:ex='http://example.com'>" + fn + "</rules>"));
		}
		
		private Engine.Module defineModule(String functions) throws RuleBaseException {
			XMLDocument rulesDoc = makeRules(functions);
			Engine.Module module = new Engine.Module(rulesDoc);
			for (Node def : rulesDoc.query().unordered("//function").nodes()) module.defineFunction(def);
			return module;
		}
		
		@Test public void defineFunctionGood() throws RuleBaseException {
			Engine.Module module = defineModule("<function name='foo' args='$a, $b'>$a[@c=local:bar($b)]</function>");
			QName fnName = new QName(Engine.Module.LOCAL_NS, "foo", null);
			assertEquals(Collections.singleton(fnName), module.functions.keySet());
			Engine.Module.Function fn = module.getFunction(fnName);
			assertEquals(fnName, fn.name);
			assertEquals("$a, $b", fn.args);
			assertEquals(Collections.singleton(new QName(Engine.Module.LOCAL_NS, "bar", null)), fn.referencedFunctionNames);
		}
		
		@Test(expected = RuleBaseException.class)
		public void defineFunctionExtraNamespaces() throws RuleBaseException {
			defineModule("<function xmlns:x='http://example.com' name='foo' args='$a, $b'>$a[@c=local:bar($b)]</function>");
		}

		@Test(expected = RuleBaseException.class)
		public void defineFunctionOverload() throws RuleBaseException {
			defineModule("<function name='foo' args='$a, $b'>$a[@c=local:bar($b)]</function><function name='foo'>'foo'</function>");
		}
		
		@Test public void saveTo() throws RuleBaseException {
			Folder modulespace = db.createFolder("/modules");
			Engine.Module module = defineModule(
					"<function name='foo' args='$a, $b'>$a[@c=local:bar($b)]</function>" +
					"<function name='bar'>'bar'</function>");
			module.saveTo(modulespace);
			assertEquals(
					"module namespace local = '" + Engine.Module.LOCAL_NS + "';\n" +
					"declare namespace ex = 'http://example.com';\n" +
					"declare function local:bar() {\n" +
					"'bar'\n" +
					"};\n" +
					"declare function local:foo($a, $b) {\n" +
					"$a[@c=local:bar($b)]\n" +
					"};\n",
					module.source().contentsAsString());
		}
		
		@Test public void areFunctionsModified() throws RuleBaseException {
			Engine.Module module = defineModule(
					"	<function name = 'f1'> /foo </function>" +
					"	<function name = 'f2' args = '$a'> /bar[@a=$a] </function>" +
					"	<function name = 'f3'> local:f1() </function>" +
					"	<function name = 'f4'> /foo </function>" +
					"	<function name = 'f5'> /foo </function>" +
					"	<function name = 'f6'> local:f4() </function>" +
					"	<function name = 'f7'> local:f6() </function>" +
					"	<function name = 'f8'> /foo </function>" +
					"	<function name = 'f9' args = '$a, $b'> /bar[@a=$a][@b=$b] </function>"
			);
			module.resolveReferencedFunctions();
			Engine.Module prevModule1 = defineModule(
					"	<function name = 'f1'> /foo </function>" +
					"	<function name = 'f2' args = '$a'> /bar[@a=$a] </function>" +
					"	<function name = 'f3'> local:f1() </function>" +
					"	<function name = 'f4'> /bar </function>" +
					"	<function name = 'f6'> local:f4() </function>" +
					"	<function name = 'f7'> local:f6() </function>" +
					"	<function name = 'ex:f8'> /foo </function>" +
					"	<function name = 'f9' args = '$b, $a'> /bar[@a=$a][@b=$b] </function>"
			);
			Engine.Module prevModule2 = defineModule(
					"	<function name = 'f1'> /bar </function>"
			);
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "foo", null)), null));
			assertFalse(module.areFunctionsModified(
					Collections.<QName>emptySet(), prevModule1));
			
			assertFalse(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f1", null)), prevModule1));
			assertFalse(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f2", null)), prevModule1));
			assertFalse(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f3", null)), prevModule1));
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f4", null)), prevModule1));
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f5", null)), prevModule1));
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f6", null)), prevModule1));
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f7", null)), prevModule1));
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f8", null)), prevModule1));
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f9", null)), prevModule1));
			
			assertTrue(module.areFunctionsModified(
					Collections.singleton(new QName(Engine.Module.LOCAL_NS, "f1", null)), prevModule2));
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
