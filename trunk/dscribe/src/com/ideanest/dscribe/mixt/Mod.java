package com.ideanest.dscribe.mixt;

import static org.hamcrest.Matchers.*;
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


public class Mod {
	
	static final Logger LOG = Logger.getLogger(Mod.class);
	static final NamespaceMap MOD_NAMESPACE = new NamespaceMap("", Engine.MOD_NS);
	static final NamespaceMap EMPTY_NAMESPACES = new NamespaceMap();
	
	final Rule rule;
	final int stage;
	final Mod parent;
	
	private Set<QName> boundVariables;
	private List<Node> references = Collections.emptyList();
	private Seg seg;
	private Node node;
	private QueryService supplementQuery;
	
	private Shim self;  // for testing only

	Mod(Rule rule) {
		this.rule = rule;
		this.stage = -1;
		this.parent = null;
		initDefaultShim();
	}
	
	Mod(Mod parent) {
		this.rule = parent.rule;
		this.stage = parent.stage+1;
		this.parent = parent;
		initDefaultShim();
	}
	
	private interface Shim {
		QueryService prepScopeClone(QueryService queryService);
		Mod deriveChild(Block block, String key);
	}
	
	private void initDefaultShim() {
		this.self = new Shim() {
			public QueryService prepScopeClone(QueryService queryService) {
				return Mod.this.prepScopeClone(queryService);
			}
			public Mod deriveChild(Block block, String key) {
				return Mod.this.deriveChild(block, key);
			}
		};
	}
	
	Mod binder(QName varName) {
		if (boundVariables != null && boundVariables.contains(varName)) return this;
		return parent.binder(varName);
	}
	
	Map<QName, Resource> variableBindings() {
		return parent.variableBindings();
	}

	public QueryService globalScope() {
		return rule.globalScope();
	}
	
	/**
	 * Take the given query service (or the workspace query service if the given one is <code>null</code>)
	 * and bind all the variables defined by previous blocks in the mod chain.  Return a query service ready
	 * to run verification queries on.
	 *
	 * @param qs the query service to use as a base, or <code>null</code> to use the workspace query service
	 * @return a clone of the given query service with variables from previous blocks bound and namespace map cleared
	 */
	public QueryService scope(QueryService qs) {
		if (qs == null) qs = globalScope();
		return parent.prepScopeClone(qs);
	}
	
	QueryService prepScopeClone(QueryService qs) {
		return qs
				.clone(new NamespaceMap(), Collections.unmodifiableMap(variableBindings()))
				.importSameModulesAs(globalScope());
	}
	
	public void bindVariable(QName name, Resource value) throws TransformException {
		Map<QName, Resource> variableBindings = variableBindings();
		if (variableBindings.containsKey(name)) throw new TransformException("cannot rebind variable " + name);
		variableBindings.put(name, value);
		if (boundVariables == null) boundVariables = new TreeSet<QName>();
		boundVariables.add(name);
	}
	
	Seg seg() {return seg;}
	Node node() {return node;}
	public String key() {return parent.key();}
	public QueryService supplementQuery() {return supplementQuery;}
	
	public Folder workspace() {
		return rule.engine.workspace().cloneWithoutNamespaceBindings();
	}
	
	public List<Node> references() {
		return references;
	}
	
	/**
	 * Return the nearest segment implementing the given type by traversing through
	 * this mod's ancestor mods. This mod is not itself included in the search.
	 * 
	 * @param <T> the type of segment to look for
	 * @param clazz the type of segment to look for
	 * @return the nearest segment implementing the desired type
	 * @throws TransformException if a segment implementing the desired type cannot be found
	 */
	public <T> T nearest(Class<T> clazz) throws TransformException {
		return clazz.cast(parent.nearestAncestorOrSelfImplementing(clazz).seg);
	}
	
	Mod nearestAncestorOrSelfImplementing(Class<?> clazz) throws TransformException {
		if (clazz.isInstance(seg)) return this;
		return parent.nearestAncestorOrSelfImplementing(clazz);
	}
	
	int resolveChildren(Block block, boolean lastBlock, QueryService touchedScope, List<Mod> children) throws TransformException {
		QueryService scope = self.prepScopeClone(touchedScope == null ? rule.globalScope() : touchedScope);
		
		Builder modBuilder;
		if (block instanceof KeyBlock) {
			modBuilder = new KeyMod.Builder(this, block, lastBlock, scope);
			((KeyBlock) block).resolve((KeyMod.Builder) modBuilder);
		} else {
			modBuilder = new Mod.Builder(this, block, lastBlock, scope);
			((LinearBlock) block).resolve(modBuilder);
		}
		
		if (children != null) children.addAll(modBuilder.children);
		
		return modBuilder.childCount;
	}
	
	Mod deriveChild(Block block, String key) {
		assert block instanceof KeyBlock || key == null;
		Mod mod = block instanceof KeyBlock ? new KeyMod(this, key) : new Mod(this);
		mod.seg = block.createSeg(mod);
		return mod;
	}
	
	Mod restoreChild(Block block, Node modNode) throws TransformException {
		LOG.debug("restoring child of " + this);
		Mod mod = self.deriveChild(block, modNode.query().optional("@xml:id").value());
		mod.setNode(modNode);
		mod.restore();
		return mod;
	}
	
	private static QName MOD_REFERENCE = QName.parse("reference", MOD_NAMESPACE);

	void restore() throws TransformException {
		final int storedStage = node.query().single("@stage").intValue();
		if (this.stage != storedStage)
			throw new IllegalArgumentException("stage mismatch on node restore, given " + stage + ", stored " + storedStage);
		
		references = new ArrayList<Node>();
		for (Node child : node.query().all("*").nodes()) {
			if (!child.qname().equals(MOD_REFERENCE)) continue;
			ItemList refs = rule.engine.workspace().query().all(
					"let $docname := concat($_1, '/', $_2/@doc) " +
					"return if (doc-available($docname)) then doc($docname)/id($_2/@refid) else ()",
					Database.ROOT_PREFIX + rule.engine.workspace().path(), child);
			if (refs.isEmpty()) throw new TransformException("failed to resolve reference (referent gone): " + child);
			if (refs.size() > 1) throw new TransformException("failed to resolve reference (" + refs.size() + " referents): " + child);
			references.add(refs.get(0).node());
		}
		references = Collections.unmodifiableList(references);
		
		seg.restore();
	}
	
	private void setNode(Node myNode) {
		node = myNode;
		node.namespaceBindings().put("", Engine.MOD_NS);
		supplementQuery = myNode.query();
		supplementQuery.namespaceBindings().replaceWith(EMPTY_NAMESPACES);
	}
	
	public List<String> affectedIds() {
		// return node.query().all("affected/@refid").values().asList();
		List<String> ids = new ArrayList<String>();
		for (Node child : node.query().all("*").nodes()) {
			if ("affected".equals(child.qname().getLocalPart()) && Engine.MOD_NS.equals(child.qname().getNamespaceURI())) {
				ids.add(child.query().single("@refid").value());
			}
		}
		return ids;
	}
	
	void verify() throws TransformException {
		LOG.debug("verifying " + this);
		try {
			seg.verify();
		} catch (SortingException e) {
			for (Node orderedNode : rule.globalScope().namespace("mod", Engine.MOD_NS)
					.unordered("/id($_1/mod:order/@refid)", node).nodes()) {
				rule.engine.eventuallySort(orderedNode);
			}
		}
	}
	
	QueryService.QueryAnalysis analyze() throws TransformException {
		LOG.debug("analyzing " + this);
		return seg.analyze();
	}
	
	@Override public String toString() {
		return "mod<" + key() + ", stage " + stage + "> of " + rule;
	}
	
	
	static Mod bootstrap(Rule rule) {
		Mod root = new KeyMod(rule) {
			@Override public String toString() {return "mod<root, stage -1> of " + rule;} 
			@Override Mod nearestAncestorOrSelfImplementing(Class<?> clazz) throws TransformException {
				throw new TransformException("no ancestor found that implements " + clazz);
			}
		};
		root.node = rule.engine.modStore().query().optional("mods[@rule=$_1]", rule.id).node();
		if (!root.node.extant()) {
			root.node = rule.engine.modStore().append().elem("mods").attr("rule", rule.id).attr("xml:id", root.key()).end("mods").commit();
		}
		return root;
	}
	
	
	/**
	 * A mod builder enables a linear block to derive a child mod from a parent mod.  The
	 * workflow is to call various parameter-setting methods on the builder, and finish with
	 * a single call to the {@link #commit()} method, which actually creates the child mod.
	 * 
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public static class Builder {
		
		final Mod parent;
		private final Block block;
		private final QueryService scope;
		private final boolean lastBlock;
		
		private Set<String> dependentDocNames, unverifiedDocNames, affectedNodeIds, createdDocNames;
		private List<Node> references, orders;
		private ElementBuilder<org.w3c.dom.Node> supplement;
		
		private final List<Mod> children = new ArrayList<Mod>();
		int childCount;
		
		Builder(Mod parent, Block block, boolean lastBlock, QueryService scope) {
			this.parent = parent;
			this.block = block;
			this.lastBlock = lastBlock;
			this.scope = scope;
			reset();
		}

		void reset() {
			dependentDocNames = new TreeSet<String>();
			unverifiedDocNames = new TreeSet<String>();
			createdDocNames = new TreeSet<String>();
			affectedNodeIds = new TreeSet<String>();
			references = new ArrayList<Node>(1);
			orders = new ArrayList<Node>(1);
			supplement = null;
		}
		
		/**
		 * Return the mod that will be the parent of all mods created by this builder.
		 *
		 * @return the parent mod for built children
		 */
		public Mod parent() {return parent;}
		
		/**
		 * Return the nearest segment implementing the given type by traversing through the ancestor mod chain,
		 * starting at the parent of the mod being built.
		 *
		 * @param <T> the type of the segment to look for
		 * @param clazz the type of the segment to look for
		 * @return a dependency modifier that can be used to declare the dependency as unverified; call <code>get()</code> to retrieve the actual segment
		 * @throws TransformException if a segment implementing the desired type cannot be found
		 */
		public <T> AncestorDependencyModifier<T> dependOnNearest(Class<T> clazz) throws TransformException {
			Mod implementor = parent().nearestAncestorOrSelfImplementing(clazz);
			return dependOn(implementor, new AncestorDependencyModifier<T>(clazz.cast(implementor.seg)));
		}
		
		/**
		 * Create and store the mod being built using the parameters previously specified on this builder, then
		 * reset the builder for the creation of another mod with the same parent.
		 *
		 * @throws TransformException
		 */
		public void commit() throws TransformException {
			try {
				// TODO: if this is the last block, check for unverified dependencies in the whole chain
				checkChildCount();
				childCount++;
				
				// This assertion is too expensive (up to 20% of query time is spent checking it!)
				// assert parent.node().query().single("every $m in mod satisfies $m/@xml:id").booleanValue();
				String key = key();
				if (key == null || !parent.node().query().exists("/id($_1)/.. intersect .", key)) {
					Node childNode = writeMod(key);
					if (!lastBlock) children.add(parent.restoreChild(block, childNode));
				}
			} finally {
				reset();
			}
		}

		private Node writeMod(String key) {
			ElementBuilder<Node> builder = parent.node().append().namespace("", Engine.MOD_NS);
			builder.elem("mod")
				.attrIf(key != null, "xml:id", key)
				.attr("stage", parent.stage + 1);
			for (String docName : dependentDocNames) {
				builder.elem("dependency")
					.attr("kind", unverifiedDocNames.contains(docName) ? "unverified" : "verified")
					.attr("doc", docName)
				.end("dependency");
			}
			for (String docName : createdDocNames) builder.elem("created").attr("doc", docName).end("created");
			for (String nodeId : affectedNodeIds) builder.elem("affected").attr("refid", nodeId).end("affected");
			for (Node node : references)
				builder.elem("reference")
					.attr("refid", node.query().single("@xml:id").value())
					.attr("doc", parent.rule.engine.relativePath(node.document()))
				.end("reference");
			Set<String> orderIdsWritten = new HashSet<String>();
			for (Node node : orders) {
				String id = node.query().single("@xml:id").value();
				if (orderIdsWritten.add(id)) {
					builder.elem("order")
						.attr("refid", id)
						.attr("doc", parent.rule.engine.relativePath(node.document()))
					.end("order");
				}
			}
			if (supplement != null) builder.node(supplement.commit());
			return builder.end("mod").commit();
		}
		
		void checkChildCount() throws TransformException {
			if (childCount > 0) throw new TransformException("linear block should resolve to at most one mod");
		}
		
		String key() {
			return null;
		}
		
		public QueryService openScope() {
			return scope;
		}
		
		public QueryService closedScope() {
			return parent.prepScopeClone(parent.node().database().query());
		}
		
		public QueryService customScope(QueryService qs) {
			return parent.prepScopeClone(qs);
		}
		
		/**
		 * Return an element builder that can be used to add supplemental information to be stored
		 * with the mod being built.  There is one supplemental builder per mod built, and it will be
		 * committed when the mod builder itself is committed.
		 * 
		 * @return this mod builder's supplemental element builder; don't commit it
		 */
		public ElementBuilder<?> supplement() {
			if (supplement == null) supplement = ElementBuilder.createScratch(null);
			return supplement;
		}
		
		/**
		 * Declare that the mod being built depends on the given document.  The dependency is
		 * assumed to be verifiable by default.
		 *
		 * @param doc the document the mod being built depends on
		 * @return a dependency modifier that lets you mark this dependency as unverified
		 */
		public DependencyModifier dependOn(XMLDocument doc) {
			return dependOn(Collections.singleton(doc), new DependencyModifier());
		}
		
		private DependencyModifier dependOn(Collection<XMLDocument> docs, DependencyModifier depMod) {
			for (XMLDocument doc : docs) depMod.add(parent.rule.engine.relativePath(doc));
			return depMod;
		}
		
		/**
		 * Declare that the mod being built depends on the given ancestor mod, adding all of that
		 * mod's unverified dependencies to the one being built.  The dependency is assumed to be
		 * verifiable by default.
		 *
		 * @param ancestor an ancestor of the mod being built, starting with this builder's parent
		 * @return a dependency modifier that lets you mark this dependency as unverified
		 */
		public DependencyModifier dependOn(Mod ancestor) {
			return dependOn(ancestor, new DependencyModifier());
		}
		
		private <T extends DependencyModifier> T dependOn(Mod ancestor, T depMod) {
			if (!(ancestor.rule == parent.rule && ancestor.stage <= parent.stage)) throw new IllegalArgumentException("given mod is not an ancestor: " + ancestor);
			// TODO: catch more non-ancestor mods?
			Map<QName,Object> varMap = Collections.emptyMap();
			depMod.addAll(ancestor.node().query().clone(MOD_NAMESPACE, varMap)
					.unordered("dependency[@kind='unverified']/@doc").values().asList());
			return depMod;
		}
		
		/**
		 * Declare that the mod being built depends on the given list of variables.
		 * This will automatically declare dependencies on the mods that have bound these variables,
		 * as well as on the parent documents of any persistent nodes that are bound to the variables.
		 * The dependency is assumed to be verifiable by default.
		 *
		 * @param variables the names of the variables to depend on
		 * @return a dependency modifier that lets you mark this dependency as unverified
		 */
		public DependencyModifier dependOn(Collection<QName> variables) {
			DependencyModifier depMod = new DependencyModifier();
			for (QName varName : variables) {
				Resource value = parent.variableBindings().get(varName);
				Collection<XMLDocument> docs = null;
				if (value instanceof Node) {
					try {
						docs = Collections.singleton(((Node) value).document());
					} catch (UnsupportedOperationException e) {
						// node may be in-memory, in which case there's no doc to depend on -- that's fine
					}
				} else if (value instanceof XMLDocument) {
					docs = Collections.singleton((XMLDocument) value);
				} else if (value instanceof ItemList) {
					docs = new ArrayList<XMLDocument>();
					for (Item item : ((ItemList) value)) {
						if (item instanceof Node) {
							try {
								docs.add(((Node) item).document());
							} catch (UnsupportedOperationException e) {
								// node may be in-memory, in which case there's no doc to depend on -- that's fine
							}
						}
					}
					if (docs.isEmpty()) docs = null;
				}
				if (docs != null) dependOn(docs, depMod);
				dependOn(parent.binder(varName), depMod);
			}
			return depMod;
		}
		
		/**
		 * Declare that the mod being built has created the given document.
		 *
		 * @param doc the document created by the mod being built
		 */
		public void create(XMLDocument doc) {
			createdDocNames.add(parent.rule.engine.workspace().relativePath(doc.path()));
		}
		
		/**
		 * Declare that the mod being built affects the given node, so that if the mod is
		 * invalidated the node needs to be recomputed.
		 *
		 * @param node the persistent node that's modified by the mod being built
		 */
		public void affect(Node node) {
			try {
				affectedNodeIds.add(node.query().single("@xml:id").value());
			} catch (DatabaseException e) {
				if (node.query().exists("@xml:id")) throw e;
				throw new IllegalArgumentException("affected node doesn't have an xml:id");
			}
		}
		
		/**
		 * Declare that the mod being built has an opinion about the ordering of the given node within its
		 * parent.  The seg corresponding to the mod must implement OrderProvider.
		 *
		 * @param node the persistent node whose order relative to its siblings is affected by the mod being built
		 */
		public void order(Node node) {
			if (!parent.rule.engine.ensureWorkspaceNodeHasXmlId(node)) throw new IllegalArgumentException("ordered node doesn't have an xml:id: " + node);
			Node parentNode;
			try {
				parentNode = node.query().single("..").node();
			} catch (DatabaseException e) {
				throw new IllegalArgumentException("ordered node doesn't have a parent: " + node);
			}
			if (!parent.rule.engine.ensureWorkspaceNodeHasXmlId(parentNode)) throw new IllegalArgumentException("parent of ordered node doesn't have an xml:id: " + parentNode);
			orders.add(parentNode);
			parent.rule.engine.eventuallySort(parentNode);
		}
		
		/**
		 * Declare that the mod being built references the given node, and hence also depends
		 * on the node's parent document.  The dependency must be verifiable.
		 *
		 * @param node the node that's referenced by the mod being built
		 */
		public void reference(Node node) {
			if (!parent.rule.engine.ensureWorkspaceNodeHasXmlId(node))
				throw new IllegalArgumentException("node referenced by " + parent.rule + " doesn't have an xml:id: " + node);
			references.add(node);
			dependOn(node.document());
		}
		
		/**
		 * Generate a new ID, suitable for an xml:id attribute, based on the parent mod's key and
		 * the stage of the mod being built.  If a non-negative serial number is given, include it in
		 * the ID as well.  The generated ID will be different from IDs generated in other rules,
		 * from those generated by other key mod chains in the current rule, and from those generated
		 * by other stages in the current rule (even if there's no interspersed key mods).  However,
		 * calls to this method that span builder commits will keep returning the same value, so it's only
		 * appropriate to call it from linear blocks.  The generated reproducible IDs can be assigned to nodes
		 * created by the block.
		 *
		 * @param serial a non-negative serial number if the block needs to generate multiple IDs; ignored if negative
		 * @return an ID string with the properties above and a syntax appropriate for an xml:id attribute
		 */
		public String generateId(int serial) {
			String id = parent.key() + (parent.stage+1) + (serial >= 0 ? "-" + serial : "") + ".";
//			assert !parent.rule.engine.workspace().query().exists("/id($_1)", id)
//					: "generated id '" + id + "' already assigned to element " + parent.rule.engine.workspace().query().unordered("/id($_1)", id);
			return id;
		}
		
		public class DependencyModifier {
			private final Set<String> docNames = new TreeSet<String>();
			
			void add(String docName) {
				docNames.add(docName);
				dependentDocNames.add(docName);
			}
			
			void addAll(Collection<String> docNames_) {
				docNames.addAll(docNames_);
				dependentDocNames.addAll(docNames_);
			}
			
			/**
			 * Mark the previous dependency as unverified.  Unverified dependencies can never cause
			 * a seg's verification to fail, and so cannot be used to validate stored mods.  All dependencies
			 * must be verified by the time the last block of a rule is reached.
			 * 
			 * @return this dependency modifier, for chaining calls 
			 * @throws TransformException if called while building the last mod of a rule
			 */
			public DependencyModifier unverified() throws TransformException {
				if (lastBlock && !docNames.isEmpty())
					throw new TransformException("cannot have unverified dependencies in a rule's last block");
				unverifiedDocNames.addAll(docNames);
				return this;
			}
		}
		
		public class AncestorDependencyModifier<T> extends DependencyModifier {
			private final T seg;
			public AncestorDependencyModifier(T seg) {
				this.seg = seg;
			}
			public T get() {
				return seg;
			}
			@Override public AncestorDependencyModifier<T> unverified() throws TransformException {
				super.unverified();
				return this;
			}
		}

	}

	@Deprecated @RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static abstract class _Test extends DatabaseTestCase {
		protected static Field rule_engine, rule_id, mod_rule, mod_stage, modBuilder_lastBlock;
		@BeforeClass public static void initializeFieldAccessors() throws SecurityException, NoSuchFieldException {
			rule_engine = Rule.class.getDeclaredField("engine");
			rule_engine.setAccessible(true);
			rule_id = Rule.class.getDeclaredField("id");
			rule_id.setAccessible(true);
			mod_rule = Mod.class.getDeclaredField("rule");
			mod_rule.setAccessible(true);
			mod_stage = Mod.class.getDeclaredField("stage");
			mod_stage.setAccessible(true);
			modBuilder_lastBlock = Mod.Builder.class.getDeclaredField("lastBlock");
			modBuilder_lastBlock.setAccessible(true);
		}
		
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		protected Node modStore;
		protected Engine engine;
		protected Rule rule;
		protected Mod parentMod;
		protected Block block;
		protected QueryService resolutionScope;
		protected XMLDocument doc1;
		protected String doc1Name;
		protected Map<QName, Resource> parentModBindings = new TreeMap<QName, Resource>();
		protected Folder workspace;
		
		@Before public void setupContext() throws IllegalArgumentException, IllegalAccessException {
			workspace = db.createFolder("/workspace");
			doc1 = db.getFolder("/workspace").documents().load(Name.generate(), Source.xml(
					"<foo><e1 xml:id='e1'><e2 xml:id='e2'/></e1></foo>"));
			doc1Name = db.getFolder("/workspace").relativePath(doc1.path());
			workspace.namespaceBindings().put("", "http://example.com");
			engine = mockery.mock(Engine.class);
			
			modStore = db.getFolder("/").documents().load(
					Name.generate(), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
			modStore.namespaceBindings().put("", Engine.MOD_NS);
			
			rule = mockery.mock(Rule.class);
			rule_engine.set(rule, engine);
			rule_id.set(rule, "r1");
			
			parentMod = mockery.mock(Mod.class);
			mod_rule.set(parentMod, rule);
			mod_stage.set(parentMod, 3);
			
			block = mockery.mock(Block.class);
			resolutionScope = db.getFolder("/").query();

			mockery.checking(new Expectations() {{
				allowing(engine).relativePath(doc1);  will(returnValue(doc1Name));
				allowing(engine).modStore();  will(returnValue(modStore));
				allowing(engine).workspace();  will(returnValue(workspace));
				allowing(rule).globalScope();  will(returnValue(workspace.query()));
				allowing(engine).ensureWorkspaceNodeHasXmlId(doc1.query().single("//e1").node());  will(returnValue(true));
				allowing(engine).ensureWorkspaceNodeHasXmlId(doc1.query().single("//e2").node());  will(returnValue(true));
				allowing(parentMod).key();  will(returnValue("_r1.e13."));
				allowing(parentMod).variableBindings();  will(returnValue(parentModBindings));
			}});	
		}
	}
	
	@Deprecated public static class _BuilderTest extends _Test {
		private Builder builder;
		
		@Before public void setupBuilder() throws IllegalArgumentException, IllegalAccessException {
			builder = new Builder(parentMod, block, false, resolutionScope);
		}
		
		private void setLastBlock() throws Exception {
			modBuilder_lastBlock.set(builder, true);
		}
		
		@Test public void resetsParameterFields() {
			builder.reset();
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}
		
		@Test public void key() {
			assertNull(builder.key());
		}
		
		@Test public void supplement() {
			ElementBuilder<?> supplement = builder.supplement();
			assertNotNull(supplement);
			assertSame(supplement, builder.supplement());
		}
		
		@Test public void dependOnDoc() {
			builder.dependOn(doc1);
			assertEquals(Collections.singleton(doc1Name), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
		
		@Test public void dependOnDocUnverified() throws TransformException {
			builder.dependOn(doc1).unverified();
			assertEquals(Collections.singleton(doc1Name), builder.dependentDocNames);
			assertEquals(Collections.singleton(doc1Name), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}

		@Test(expected = TransformException.class)
		public void dependOnDocUnverifiedLastBlock() throws Exception {
			setLastBlock();
			builder.dependOn(doc1).unverified();
		}
		
		private Mod createAncestor(int n) throws IllegalAccessException {
			final Mod ancestor = mockery.mock(Mod.class, "ancestorMod" + n);
			mod_rule.set(ancestor, rule);
			mod_stage.set(ancestor, 2);
			final Node ancestorData = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" +
					"  <mod>" + 
					"    <dependency kind='verified' doc='d" + n + "v.xml'/>" + 
					"    <dependency kind='unverified' doc='d" + n + "u.xml'/>" + 
					"  </mod>" +
					"</modstore>"
			)).query().namespace("", Engine.MOD_NS).single("//mod").node();
			mockery.checking(new Expectations() {{
				allowing(ancestor).node();  will(returnValue(ancestorData));
			}});
			return ancestor;
		}
		
		@Test public void dependOnAncestor() throws Exception {
			builder.dependOn(createAncestor(1));
			assertEquals(Collections.singleton("d1u.xml"), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
	
		@Test public void dependOnAncestorUnverified() throws Exception {
			builder.dependOn(createAncestor(1)).unverified();
			assertEquals(Collections.singleton("d1u.xml"), builder.dependentDocNames);
			assertEquals(Collections.singleton("d1u.xml"), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
		
		@Test public void dependOnVariables() throws Exception {
			final QName a = new QName(null, "a", null), b = new QName(null, "b", null), c = new QName(null, "c", null);
			parentModBindings.put(a, doc1.query().single("'not a node'"));
			parentModBindings.put(b, doc1.query().single("<inmem/>"));
			parentModBindings.put(c, doc1.root());
			final Mod ancestor1 = createAncestor(1), ancestor2 = createAncestor(2), ancestor3 = createAncestor(3);
			mockery.checking(new Expectations() {{
				allowing(parentMod).binder(a);  will(returnValue(ancestor1));
				allowing(parentMod).binder(b);  will(returnValue(ancestor2));
				allowing(parentMod).binder(c);  will(returnValue(ancestor3));
			}});
			builder.dependOn(Arrays.asList(a, b, c));
			assertEquals(new TreeSet<String>(Arrays.asList(doc1Name, "d1u.xml", "d2u.xml", "d3u.xml")), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
		
		@Test public void dependOnVariables2() throws Exception {
			final QName a = new QName(null, "a", null);
			parentModBindings.put(a, doc1.query().all("(<inmem/>, //*[@xml:id])"));
			final Mod ancestor1 = createAncestor(1);
			mockery.checking(new Expectations() {{
				allowing(parentMod).binder(a);  will(returnValue(ancestor1));
			}});
			builder.dependOn(Arrays.asList(a));
			assertEquals(new TreeSet<String>(Arrays.asList(doc1Name, "d1u.xml")), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
		
		@Test public void dependOnVariables3() throws Exception {
			final QName a = new QName(null, "a", null);
			parentModBindings.put(a, doc1);
			final Mod ancestor1 = createAncestor(1);
			mockery.checking(new Expectations() {{
				allowing(parentMod).binder(a);  will(returnValue(ancestor1));
			}});
			builder.dependOn(Arrays.asList(a));
			assertEquals(new TreeSet<String>(Arrays.asList(doc1Name, "d1u.xml")), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
		
		@Test public void affect() {
			builder.affect(doc1.query().single("//e1").node());
			assertEquals(Collections.emptySet(), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.singleton("e1"), builder.affectedNodeIds);
			assertEquals(Collections.emptyList(), builder.references);
		}
		
		@Test(expected = IllegalArgumentException.class)
		public void affectNoId() {
			builder.affect(doc1.root());
		}
		
		@Test public void reference() {
			builder.reference(doc1.query().single("//e1").node());
			assertEquals(Collections.singleton(doc1Name), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.singletonList(doc1.query().single("//e1").node()), builder.references);
		}

		@Test public void referenceGenerateId() {
			mockery.checking(new Expectations() {{
				one(engine).ensureWorkspaceNodeHasXmlId(doc1.root()); will(new Action() {
					@Override public Object invoke(Invocation invocation) throws Throwable {
						doc1.root().update().attr("xml:id", "x-123").commit();
						return true;
					}
					@Override public void describeTo(Description description) {
						description.appendText("add an xml:id to doc1.root()");
					}
				});
			}});
			builder.reference(doc1.root());
			assertEquals(Collections.singleton(doc1Name), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.singletonList(doc1.root()), builder.references);
		}

		@Test(expected = IllegalArgumentException.class)
		public void referenceNoId() {
			mockery.checking(new Expectations() {{
				one(engine).ensureWorkspaceNodeHasXmlId(doc1.root()); will(returnValue(false));
			}});
			builder.reference(doc1.root());
		}
		
		@Test public void order() {
			mockery.checking(new Expectations() {{
				one(engine).eventuallySort(doc1.query().single("//e1").node());
			}});
			builder.order(doc1.query().single("//e2").node());
			assertEquals(Collections.emptySet(), builder.dependentDocNames);
			assertEquals(Collections.emptySet(), builder.unverifiedDocNames);
			assertEquals(Collections.emptySet(), builder.affectedNodeIds);
			assertEquals(Collections.singletonList(doc1.query().single("//e1").node()), builder.orders);
		}
		
		@Test(expected = IllegalArgumentException.class)
		public void orderNoId() {
			final Node e3 = doc1.query().single("//e2").node().append().elem("e3").end("e3").commit();
			mockery.checking(new Expectations() {{
				one(engine).ensureWorkspaceNodeHasXmlId(e3); will(returnValue(false));
			}});
			builder.order(e3);
		}
		
		@Test(expected = IllegalArgumentException.class)
		public void orderNoParentId() {
			final Node e3 = doc1.query().single("//e2").node().append().elem("e3").elem("e4").attr("xml:id", "e4").end("e4").end("e3").commit();
			final Node e4 = doc1.query().single("//e4").node();
			mockery.checking(new Expectations() {{
				one(engine).ensureWorkspaceNodeHasXmlId(e4); will(returnValue(true));
				one(engine).ensureWorkspaceNodeHasXmlId(e3); will(returnValue(false));
			}});
			builder.order(e4);
		}
		
		@Test(expected = IllegalArgumentException.class)
		public void orderNoParent() {
			mockery.checking(new Expectations() {{
				one(engine).ensureWorkspaceNodeHasXmlId(doc1.root()); will(returnValue(true));
			}});
			builder.order(doc1.root());
		}
		
		@Test public void generateId() {
			assertEquals(builder.generateId(-1), builder.generateId(-2));
			assertThat(builder.generateId(0), not(equalTo(builder.generateId(1))));			
		}
		
		@Test public void writeMod() throws Exception {
			final Node parentModNode = modStore.append().elem("mod").attr("stage", 3).end("mod").commit();
			mockery.checking(new Expectations() {{
				allowing(parentMod).node(); will(returnValue(parentModNode));
			}});

			builder.dependentDocNames.add("d1v.xml");
			builder.dependentDocNames.add("d1u.xml");
			builder.unverifiedDocNames.add("d1u.xml");
			builder.affectedNodeIds.add("e2");
			builder.references.add(doc1.query().single("//e1").node());
			builder.orders.add(doc1.query().single("//e1").node());
			builder.orders.add(doc1.query().single("//e1").node());
			builder.supplement().elem("checksum").end("checksum").elem("foobar").end("foobar");
			
			Node targetNode = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<mod xmlns='" + Engine.MOD_NS + "' stage='4'>" +
					"	<dependency kind='unverified' doc='d1u.xml'/>" +
					"	<dependency kind='verified' doc='d1v.xml'/>" +
					"	<affected refid='e2'/>" +
					"	<reference refid='e1' doc='" + doc1Name + "'/>" +
					"	<order refid='e1' doc='" + doc1Name + "'/>" +
					"	<checksum xmlns=''/><foobar xmlns=''/>" +
					"</mod>"
					)).root();
			
			builder.writeMod(null);
			
			Node writtenNode = parentModNode.query().single("mod").node();
			if (!db.query().single("deep-equal($_1, $_2)", targetNode, writtenNode).booleanValue()) {
				fail("mismatch\n\nExpected:\n" + targetNode + "\n\nActual:\n" + writtenNode + "\n");
			}
		}
		
		@Test(expected = TransformException.class)
		public void twoCommitsFail() throws TransformException {
			final Seg seg = mockery.mock(Seg.class);
			final Mod child = mockery.mock(Mod.class, "mod1");
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
				one(seg).restore();
				one(parentMod).restoreChild(with(equalTo(block)), with(any(Node.class)));
				will(returnValue(child));
			}});
			builder.commit();
			builder.commit();
		}
		
		@Test public void commitNewNode() throws TransformException {
			final Mod child = mockery.mock(Mod.class, "mod1");
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
				one(parentMod).restoreChild(with(equalTo(block)), with(any(Node.class)));
				will(returnValue(child));
			}});
			final Node refNode = doc1.query().single("//e1").node();
			builder.references.add(refNode);
			builder.commit();
			assertEquals(1, builder.childCount);
			Node modNode = modStore.query().optional("mod[xs:integer(@stage)=4]").node();
			assertTrue(modNode.extant());
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}

		@Test public void commitExtantNodeEarlierStage() throws TransformException {
			final Mod child = mockery.mock(Mod.class, "mod1");
			modStore.append().elem("mod").attr("xml:id", "_r1.e13.").attr("stage", 4).end("mod").commit();
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
				one(parentMod).restoreChild(with(equalTo(block)), with(any(Node.class)));
				will(returnValue(child));
			}});
			builder.commit();
			assertEquals(1, builder.childCount);
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}
	}
	
	@Deprecated @RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static class _ModTest extends _Test {
		@Test public void constructorFromRule() {
			Mod mod = new Mod(rule);
			assertSame(rule, mod.rule);
			assertEquals(-1, mod.stage);
			assertNull(mod.parent);
		}
		
		@Test public void constructorFromParentMod() {
			Mod mod = new Mod(parentMod);
			assertSame(rule, mod.rule);
			assertEquals(4, mod.stage);
			assertSame(parentMod, mod.parent);
		}
		
		@Test public void createBootstrap() {
			Mod mod = Mod.bootstrap(rule);
			assertTrue(mod instanceof KeyMod);
			assertSame(rule, mod.rule);
			assertEquals(-1, mod.stage);
			assertNull(mod.parent);
		}
		
		@Test public void bootstrapAppendChild() {
			assertEquals(modStore, Mod.bootstrap(rule).node().query().single("..").node());
		}
		
		@Test(expected = TransformException.class)
		public void bootstrapNearestAncestorOrSelfImplementing() throws TransformException {
			Mod.bootstrap(rule).nearestAncestorOrSelfImplementing(Cloneable.class);
		}
		
		@Test public void binder() {
			final QName a = new QName(null, "a", null);
			mockery.checking(new Expectations() {{
				one(parentMod).binder(a);  will(returnValue(parentMod));
			}});
			Mod mod = new Mod(parentMod);
			assertSame(parentMod, mod.binder(a));
		}
		
		@Test public void bindVariable1() throws TransformException {
			final QName a = new QName(null, "a", null);
			Mod mod = new Mod(parentMod);
			mod.bindVariable(a, doc1);
			assertSame(mod, mod.binder(a));
			assertEquals(Collections.singleton(a), mod.boundVariables);
		}
		
		@Test(expected = TransformException.class)
		public void bindVariable2() throws TransformException {
			final QName a = new QName(null, "a", null);
			parentModBindings.put(a, doc1);
			Mod mod = new Mod(parentMod);
			mod.bindVariable(a, doc1);
		}

		@Test public void variableBindings() throws TransformException {
			Mod mod = new Mod(parentMod);
			assertSame(parentModBindings, mod.variableBindings());
		}
		
		@Test public void prepScopeClone() {
			final QName a = new QName(null, "a", null);
			Item value = doc1.query().single("//e1");
			parentModBindings.put(a, value);
			Mod mod = new Mod(parentMod);
			QueryService scope = mod.prepScopeClone(modStore.query());
			assertEquals(new NamespaceMap(), scope.namespaceBindings());
			assertEquals(doc1.query().single("//e1"), scope.single("$a"));
		}
		
		@Test public void workspace() {
			Mod mod = new Mod(parentMod);
			Folder cleanWorkspace = mod.workspace();
			assertEquals(workspace, cleanWorkspace);
		}
		
		@Test public void references() {
			Mod mod = new Mod(parentMod);
			assertEquals(Collections.emptyList(), mod.references());
		}
		
		@Test public void scopeNonNull() {
			final QueryService source = modStore.query(), result = source.clone();
			mockery.checking(new Expectations() {{
				one(parentMod).prepScopeClone(source);  will(returnValue(result));
			}});
			Mod mod = new Mod(parentMod);
			assertSame(result, mod.scope(source));
		}

		@Test public void scopeNull() {
			final QueryService result = modStore.query().clone();
			mockery.checking(new Expectations() {{
				one(parentMod).prepScopeClone(with(notNullValue(QueryService.class)));
					will(returnValue(result));
			}});
			Mod mod = new Mod(parentMod);
			assertSame(result, mod.scope(null));
		}
		
		@Test(expected = TransformException.class)
		public void isItselfNearestAncestorImplementing() throws TransformException {
			Mod mod = new Mod(parentMod);
			class Foo extends Seg implements Cloneable {
				Foo(Mod mod) {super(mod);}
			}
			Foo foo = new Foo(mod);
			mod.seg = foo;
			mockery.checking(new Expectations() {{
				one(parentMod).nearestAncestorOrSelfImplementing(Cloneable.class);
					will(throwException(new TransformException()));
			}});
			assertSame(foo, mod.nearest(Cloneable.class));
		}

		@Test public void hasNearestAncestorImplementing() throws TransformException {
			Mod mod = new Mod(parentMod);
			class Foo extends Seg implements Cloneable {
				Foo(Mod mod) {super(mod);}
			}
			parentMod.seg = new Foo(mod);
			mockery.checking(new Expectations() {{
				one(parentMod).nearestAncestorOrSelfImplementing(Cloneable.class);
					will(returnValue(parentMod));
			}});
			assertSame(parentMod.seg, mod.nearest(Cloneable.class));
		}
		
		@Test public void verifyNoOrder() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.node = modStore.append()
				.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					.elem("order").attr("refid", "e1").attr("doc", doc1.name()).end("order")
				.end("mod").commit();
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).verify();
			}});
			mod.verify();
		}

		@Test public void verifyWithOrder() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.node = modStore.append()
				.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					.elem("order").attr("refid", "e1").attr("doc", doc1.name()).end("order")
					.elem("mod").attr("stage", 5)
						.elem("order").attr("refid", "e2").attr("doc", doc1.name()).end("order")
					.end("mod")
				.end("mod").commit();
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).verify(); will(throwException(new SortingException()));
				one(engine).eventuallySort(doc1.query().single("/id('e1')").node());
			}});
			mod.verify();
		}

		@Test public void analyze() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).analyze();
			}});
			mod.analyze();
		}
		
		@Test public void deriveChild() {
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
			}});
			Mod mod = new Mod(parentMod);
			Mod child = mod.deriveChild(block, null);
			assertSame(mod, child.parent);
			assertSame(seg, child.seg);
		}

		@Test public void deriveKeyChild() {
			block = mockery.mock(KeyBlock.class);
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
			}});
			Mod mod = new Mod(parentMod);
			Mod child = mod.deriveChild(block, "foo");
			assertTrue(child instanceof KeyMod);
			assertSame(mod, child.parent);
			assertSame(seg, child.seg);
		}

		@Test(expected = IllegalArgumentException.class)
		public void deriveKeyChildBadKey() {
			block = mockery.mock(KeyBlock.class);
			new Mod(parentMod).deriveChild(block, null);
		}
		
		private static class CheckModBuilderParams<E extends Mod.Builder> extends TypeSafeMatcher<E> {
			private final Mod mod;
			private final Block block;
			private final boolean lastBlock;
			private final QueryService scope;
			CheckModBuilderParams(Mod mod, Block block, boolean lastBlock, QueryService scope) {
				this.mod = mod;
				this.block = block;
				this.lastBlock = lastBlock;
				this.scope = scope;
			}
			@Override public boolean matchesSafely(E item) {
				return item.parent == mod && item.block == block && item.lastBlock == lastBlock && item.scope == scope;
			}
			public void describeTo(Description description) {
				description.appendText("is for mod " + mod + ", with " + (lastBlock ? "last " : "") + "block " + block);
			}
		}
		
		@Test public void resolveChildren() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4).attr("status", "new")
					.end("mod").commit());
			final LinearBlock linearBlock = mockery.mock(LinearBlock.class);
			mod.self = mockery.mock(Mod.Shim.class);
			mockery.checking(new Expectations() {{
				one(linearBlock).resolve(with(new CheckModBuilderParams<Mod.Builder>(mod, linearBlock, false, modStore.query())));
				one(mod.self).prepScopeClone(with(same(workspace.query())));
				will(returnValue(modStore.query()));
			}});
			mod.resolveChildren(linearBlock, false, null, null);
		}

		@Test public void resolveChildrenLastBlock() throws TransformException {
			final Mod mod = new Mod(parentMod);
			final LinearBlock linearBlock = mockery.mock(LinearBlock.class);
			mod.self = mockery.mock(Mod.Shim.class);
			mockery.checking(new Expectations() {{
				one(linearBlock).resolve(with(new CheckModBuilderParams<Mod.Builder>(mod, linearBlock, true, workspace.query())));
				one(mod.self).prepScopeClone(with(same(modStore.query())));
				will(returnValue(workspace.query()));
			}});
			mod.resolveChildren(linearBlock, true, modStore.query(), null);
		}

		@Test public void resolveChildrenKeyBlock() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4).attr("status", "new")
					.end("mod").commit());
			final KeyBlock keyBlock = mockery.mock(KeyBlock.class);
			mod.self = mockery.mock(Mod.Shim.class);
			mockery.checking(new Expectations() {{
				one(keyBlock).resolve(with(new CheckModBuilderParams<KeyMod.Builder>(mod, keyBlock, false, modStore.query())));
				one(mod.self).prepScopeClone(with(same(modStore.query())));
				will(returnValue(modStore.query()));
			}});
			mod.resolveChildren(keyBlock, false, modStore.query(), null);
		}
		
		@Test public void restoreChild() throws TransformException {
			block = mockery.mock(KeyBlock.class);
			final Mod mod = new Mod(parentMod);
			mod.self = mockery.mock(Mod.Shim.class);
			final Mod child = mockery.mock(Mod.class, "child");
			mockery.checking(new Expectations() {{
				one(mod.self).deriveChild(block, "_r1.e13.g23.");  will(returnValue(child));
				one(child).restore();
			}});
			Node childNode = modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").end("mod")
					.commit();
			assertSame(child, mod.restoreChild(block, childNode));
			assertSame(childNode, child.node);
		}
		
		private Action checkThatNamespaceBindingsAreEmpty(final NamespaceMap bindings) {
			return new Action() {
				public Object invoke(Invocation invocation) throws Throwable {
					assertEquals(new NamespaceMap(), bindings);
					return null;
				}
				public void describeTo(Description description) {
					description.appendText("check that namespace bindings are empty");
				}
			};
		}
		
		@Test public void restoreNoReferences() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4).attr("satus", "new")
					.end("mod").commit());
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).restore();  will(checkThatNamespaceBindingsAreEmpty(mod.supplementQuery().namespaceBindings()));
			}});
			mod.restore();
			assertTrue(mod.references().isEmpty());
			assertEquals(Engine.MOD_NS, mod.node.namespaceBindings().get(""));
		}

		@Test(expected = IllegalArgumentException.class)
		public void restoreWrongStage() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 5)
					.end("mod").commit());
			mod.restore();
		}
		
		@Test public void restoreWithReferences() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					  .elem("reference").attr("refid", "e1").attr("doc", doc1Name).end("reference")
					.end("mod").commit());
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).restore();  will(checkThatNamespaceBindingsAreEmpty(mod.supplementQuery().namespaceBindings()));
			}});
			mod.restore();
			assertEquals(Collections.singletonList(doc1.root().query().single("e1").node()), mod.references());
			assertEquals(Engine.MOD_NS, mod.node.namespaceBindings().get(""));
		}

		@Test(expected = TransformException.class)
		public void restoreWithBadReferencePath() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					  .elem("reference").attr("refid", "e1").attr("doc", "foo/doc").end("reference")
					.end("mod").commit());
			mod.restore();
		}
		
		@Test public void affected() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.setNode(modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					  .elem("affected").attr("refid", "e1").end("affected")
					.end("mod").commit());
			assertEquals(Collections.singletonList("e1"), mod.affectedIds());
		}

	}
}