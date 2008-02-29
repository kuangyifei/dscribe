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
	static final NamespaceMap MOD_NAMESPACE = new NamespaceMap("", Transformer.MOD_NS);
	static final NamespaceMap EMPTY_NAMESPACES = new NamespaceMap();
	
	final Rule rule;
	final int stage;
	final Mod parent;
	
	private boolean restored;
	private Set<String> boundVariables;
	private List<Node> references;
	private Seg seg;
	private Node node;
	
	private Shim self;  // for testing only

	Mod(Rule rule) {
		this.rule = rule;
		this.stage = -1;
		this.parent = null;
		this.restored = true;
		initDefaultShim();
	}
	
	Mod(Mod parent) {
		this.rule = parent.rule;
		this.stage = parent.stage+1;
		this.parent = parent;
		this.restored = false;
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
	
	Mod binder(String varName) {
		if (boundVariables != null && boundVariables.contains(varName)) return this;
		return parent.binder(varName);
	}
	
	Map<String,Object> variableBindings() {
		return parent.variableBindings();
	}

	public QueryService globalScope() {
		return rule.engine.globalScope();
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
		if (qs == null) qs = workspace().query();
		return parent.prepScopeClone(qs);
	}
	
	QueryService prepScopeClone(QueryService qs) {
		return qs.clone(new NamespaceMap(), Collections.unmodifiableMap(variableBindings()));
	}
	
	public void bindVariable(String name, Object value) throws TransformException {
		Map<String,Object> variableBindings = variableBindings();
		if (variableBindings.containsKey(name)) throw new TransformException("cannot rebind variable " + name);
		variableBindings.put(name, value);
		if (boundVariables == null) boundVariables = new TreeSet<String>();
		boundVariables.add(name);
	}
	
	public String key() {
		return parent.key();
	}
	
	public Node node() {
		return node;
	}
	
	public Folder workspace() {
		return rule.engine.workspace().cloneWithoutNamespaceBindings();
	}
	
	public List<Node> references() {
		if (references == null) return Collections.emptyList();
		return Collections.unmodifiableList(references);
	}
	
	/**
	 * Return the nearest segment implementing the given type by traversing through this mod's ancestor mods.
	 * This mod is not itself included in the search.
	 *
	 * @param <T> the type of the segment to look for
	 * @param clazz the type of the segment to look for
	 * @return the nearest segment implementing the desired type
	 * @throws TransformException if a segment implementing the desired type cannot be found
	 */
	public <T> T nearestAncestorImplementing(Class<T> clazz) throws TransformException {
		return parent.nearestAncestorOrSelfImplementing(clazz);
	}
	
	<T> T nearestAncestorOrSelfImplementing(Class<T> clazz) throws TransformException {
		if (clazz.isInstance(seg)) return clazz.cast(seg);
		return parent.nearestAncestorOrSelfImplementing(clazz);
	}
	
	Collection<Mod> resolveChildren(Block block, boolean lastBlock, QueryService touchedScope) throws TransformException {
		QueryService scope = self.prepScopeClone((touchedScope == null || !restored) ? rule.engine.globalScope() : touchedScope);
		
		Builder modBuilder;
		if (block instanceof KeyBlock) {
			modBuilder = new KeyMod.Builder(this, block, lastBlock, scope);
			((KeyBlock) block).resolve((KeyMod.Builder) modBuilder);
		} else {
			modBuilder = new Mod.Builder(this, block, lastBlock, scope);
			((LinearBlock) block).resolve(modBuilder);
		}

		return modBuilder.children;
	}
	
	Mod deriveChild(Block block, String key) {
		assert block instanceof KeyBlock || key == null;
		Mod mod = block instanceof KeyBlock ? new KeyMod(this, key) : new Mod(this);
		mod.seg = block.createSeg(mod);
		return mod;
	}
	
	Mod restoreChild(Block block, Node modNode) throws TransformException {
		Mod mod = self.deriveChild(block, modNode.query().optional("@xml:id").value());
		mod.node = modNode;
		mod.restore();
		return mod;
	}

	void restore() throws TransformException {
		node.namespaceBindings().put("", Transformer.MOD_NS);
		final int storedStage = node.query().single("@stage").intValue();
		if (this.stage != storedStage)
			throw new IllegalArgumentException("stage mismatch on node restore, given " + stage + ", stored " + storedStage);
		
		ItemList refNodes = node.query().all("reference");
		if (refNodes.size() > 0) {
			references = new ArrayList<Node>(refNodes.size());
			for (Node refNode : refNodes.nodes()) {
				Node referencedNode = rule.engine.globalScope().optional("/id($_1/@refid)", refNode).node();
				if (!referencedNode.extant()) throw new TransformException("missing referenced node " + refNode.query().single("@refid").value());
				final String actualPath = rule.engine.relativePath(referencedNode.document());
				final String storedPath = refNode.query().single("@doc").value();
				if (!actualPath.equals(storedPath))
					throw new TransformException("doc mismatch on reference, resolves to '" + actualPath + "' but stored as '" + storedPath + "'");
				references.add(referencedNode);
			}
		}
		
		node.namespaceBindings().replaceWith(EMPTY_NAMESPACES);
		seg.restore();
		node.namespaceBindings().put("", Transformer.MOD_NS);
		restored = true;
	}
	
	void verify() throws TransformException {
		LOG.debug("verifying " + this);
		seg.verify();
	}
	
	void analyze() throws TransformException {
		LOG.debug("analyzing " + this);
		seg.analyze();
	}
	
	@Override public String toString() {
		return "mod<" + key() + ", stage " + stage + "> of " + rule;
	}
	
	
	static Mod bootstrap(Rule rule) {
		Mod root = new KeyMod(rule) {
			@Override public String toString() {return "rootmod[" +rule + "]";} 
			@Override <T> T nearestAncestorOrSelfImplementing(Class<T> clazz) throws TransformException {
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
		
		private Set<String>dependentDocNames, unverifiedDocNames, affectedNodeIds;
		private List<Node> references;
		private ElementBuilder<org.w3c.dom.Node> supplement;
		
		private List<Mod> children = new ArrayList<Mod>(1);
		
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
			affectedNodeIds = new TreeSet<String>();
			references = new ArrayList<Node>(1);
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
		 * @return the nearest segment implementing the desired type
		 * @throws TransformException if a segment implementing the desired type cannot be found
		 */
		public <T> T nearestAncestorImplementing(Class<T> clazz) throws TransformException {
			return parent().nearestAncestorOrSelfImplementing(clazz);
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
				checkChildrenSize();
				Mod mod = createChild();
				mod.references = references;
				
				assert !parent.node().query().exists("mod[not(@xml:id)]");
				// TODO: consider keeping child if we'll need to restore it in the next stage anyway
				if (parent.node().query().exists("mod[@xml:id=$_1]", mod.key())) return;
				
				writeData(mod);
				mod.node.namespaceBindings().replaceWith(EMPTY_NAMESPACES);
				mod.seg = block.createSeg(mod);
				mod.seg.restore();
				mod.node.namespaceBindings().put("", Transformer.MOD_NS);
				children.add(mod);
			} finally {
				reset();
			}
		}

		private void writeData(Mod mod) {
			ElementBuilder<Node> builder = parent.node().append().namespace("", Transformer.MOD_NS);
			builder.elem("mod").attrIf(mod instanceof KeyMod, "xml:id", mod.key()).attr("stage", mod.stage);
			for (String docName : dependentDocNames) {
				builder.elem("dependency")
					.attr("kind", unverifiedDocNames.contains(docName) ? "unverified" : "verified")
					.attr("doc", docName)
				.end("dependency");
			}
			for (String nodeId : affectedNodeIds) builder.elem("affected").attr("refid", nodeId).end("affected");
			for (Node node : references)
				builder.elem("reference")
					.attr("refid", node.query().single("@xml:id").value())
					.attr("doc", parent.rule.engine.relativePath(node.document()))
				.end("reference");
			if (supplement != null) builder.node(supplement.commit());
			mod.node = builder.end("mod").commit();
		}
		
		void checkChildrenSize() throws TransformException {
			if (!children.isEmpty()) throw new TransformException("linear block should resolve to at most one mod");
		}
		
		Mod createChild() {
			return new Mod(parent);
		}
		
		public QueryService scope() {
			return scope;
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
		public DependencyModifier dependOn(Document doc) {
			return dependOn(doc, new DependencyModifier());
		}
		
		private DependencyModifier dependOn(Document doc, DependencyModifier depMod) {
			depMod.add(parent.rule.engine.relativePath(doc));
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
		
		private DependencyModifier dependOn(Mod ancestor, DependencyModifier depMod) {
			if (!(ancestor.rule == parent.rule && ancestor.stage <= parent.stage)) throw new IllegalArgumentException("given mod is not an ancestor: " + ancestor);
			// TODO: catch more non-ancestor mods?
			Map<String,Object> varMap = Collections.emptyMap();
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
		 * @param variables the names of the variables to depend on, '$' prefixes included
		 * @return a dependency modifier that lets you mark this dependency as unverified
		 */
		public DependencyModifier dependOn(Collection<String> variables) {
			DependencyModifier depMod = new DependencyModifier();
			for (String varName : variables) {
				Document doc = null;
				try {
					Object value = parent.variableBindings().get(varName);
					if (value instanceof Node) doc = ((Node) value).document();
				} catch (DatabaseException e) {
					// node may be in-memory, in which case there's no doc to depend on -- that's fine
				}
				if (doc != null) dependOn(doc, depMod);
				dependOn(parent.binder(varName), depMod);
			}
			return depMod;
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
		 * Declare that the mod being built references the given node, and hence also depends
		 * on the node's parent document.  The dependency must be verifiable.
		 *
		 * @param node the node that's referenced by the mod being built
		 */
		public void reference(Node node) {
			if (!parent.rule.engine.ensureNodeHasXmlId(node)) throw new IllegalArgumentException("referenced node doesn't have an xml:id: " + node);
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
			if (parent.rule.engine.globalScope().exists("/id($_1)", id))
				LOG.error("generated id '" + id + "' already assigned to element " + parent.rule.engine.globalScope().unordered("/id($_1)", id));
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
			 * @throws TransformException if called while building the last mod of a rule
			 */
			public void unverified() throws TransformException {
				if (lastBlock) throw new TransformException("cannot have unverified dependencies in a rule's last block");
				unverifiedDocNames.addAll(docNames);
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
		protected Map<String, Object> parentModBindings = new TreeMap<String, Object>();
		protected Folder workspace;
		
		@Before public void setupContext() throws IllegalArgumentException, IllegalAccessException {
			workspace = db.createFolder("/workspace");
			doc1 = db.getFolder("/workspace").documents().load(Name.generate(), Source.xml("<foo><e1 xml:id='e1'/></foo>"));
			doc1Name = db.getFolder("/workspace").relativePath(doc1.path());
			workspace.namespaceBindings().put("", "http://example.com");
			engine = mockery.mock(Engine.class);
			
			modStore = db.getFolder("/").documents().load(
					Name.generate(), Source.xml("<modstore xmlns='" + Transformer.MOD_NS + "'/>")).root();
			modStore.namespaceBindings().put("", Transformer.MOD_NS);
			
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
				allowing(engine).globalScope();  will(returnValue(workspace.query()));
				allowing(engine).ensureNodeHasXmlId(doc1.query().single("//e1").node());  will(returnValue(true));
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
		
		@Test public void createChild() {
			Mod child = builder.createChild();
			assertSame(parentMod, child.parent);
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
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"  <mod>" + 
					"    <dependency kind='verified' doc='d" + n + "v.xml'/>" + 
					"    <dependency kind='unverified' doc='d" + n + "u.xml'/>" + 
					"  </mod>" +
					"</modstore>"
			)).query().namespace("", Transformer.MOD_NS).single("//mod").node();
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
			parentModBindings.put("a", "not a node");
			parentModBindings.put("b", doc1.root());
			final Mod ancestor1 = createAncestor(1), ancestor2 = createAncestor(2);
			mockery.checking(new Expectations() {{
				allowing(parentMod).binder("a");  will(returnValue(ancestor1));
				allowing(parentMod).binder("b");  will(returnValue(ancestor2));
			}});
			builder.dependOn(Arrays.asList("a", "b"));
			assertEquals(new TreeSet<String>(Arrays.asList(doc1Name, "d1u.xml", "d2u.xml")), builder.dependentDocNames);
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
				one(engine).ensureNodeHasXmlId(doc1.root()); will(new Action() {
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
				one(engine).ensureNodeHasXmlId(doc1.root()); will(returnValue(false));
			}});
			builder.reference(doc1.root());
		}
		
		@Test public void generateId() {
			assertEquals(builder.generateId(-1), builder.generateId(-2));
			assertThat(builder.generateId(0), not(equalTo(builder.generateId(1))));			
		}
		
		@Test public void writeData() throws Exception {
			final Node parentModNode = modStore.append().elem("mod").attr("stage", 2).end("mod").commit();
			final Mod mod = mockery.mock(Mod.class, "childMod");
			mod_stage.set(mod, 3);
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(parentModNode));
				allowing(mod).key();  will(returnValue(null));
			}});

			builder.dependentDocNames.add("d1v.xml");
			builder.dependentDocNames.add("d1u.xml");
			builder.unverifiedDocNames.add("d1u.xml");
			builder.affectedNodeIds.add("e2");
			builder.references.add(doc1.query().single("//e1").node());
			builder.supplement().elem("checksum").end("checksum").elem("foobar").end("foobar");
			
			Node targetNode = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<mod xmlns='" + Transformer.MOD_NS + "' stage='3'>" +
					"	<dependency kind='unverified' doc='d1u.xml'/>" +
					"	<dependency kind='verified' doc='d1v.xml'/>" +
					"	<affected refid='e2'/>" +
					"	<reference refid='e1' doc='" + doc1Name + "'/>" +
					"	<checksum xmlns=''/><foobar xmlns=''/>" +
					"</mod>"
					)).root();
			
			builder.writeData(mod);
			
			Node writtenNode = parentModNode.query().single("mod").node();
			if (!db.query().single("deep-equal($_1, $_2)", targetNode, writtenNode).booleanValue()) {
				fail("mismatch\n\nExpected:\n" + targetNode + "\n\nActual:\n" + writtenNode + "\n");
			}
		}
		
		@Test(expected = TransformException.class)
		public void twoCommitsFail() throws TransformException {
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
				one(seg).restore();
			}});
			builder.commit();
			builder.commit();
		}
		
		@Test public void commitNewNode() throws TransformException {
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
				one(seg).restore();
			}});
			final Node refNode = doc1.query().single("//e1").node();
			builder.references.add(refNode);
			builder.commit();
			assertEquals(1, builder.children.size());
			Mod childMod = builder.children.get(0);
			assertSame(seg, childMod.seg);
			assertEquals(Collections.singletonList(refNode), childMod.references);
			Node modNode = modStore.query().optional("mod[xs:integer(@stage)=4]").node();
			assertTrue(modNode.extant());
			assertEquals(modNode, childMod.node);
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}

		@Test public void commitExtantNodeEarlierStage() throws TransformException {
			modStore.append().elem("mod").attr("xml:id", "_r1.e13.").attr("stage", 4).end("mod").commit();
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
			}});
			builder.commit();
			assertEquals(0, builder.children.size());
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
			assertTrue(mod.restored);
		}
		
		@Test public void constructorFromParentMod() {
			Mod mod = new Mod(parentMod);
			assertSame(rule, mod.rule);
			assertEquals(4, mod.stage);
			assertSame(parentMod, mod.parent);
			assertFalse(mod.restored);
		}
		
		@Test public void createBootstrap() {
			Mod mod = Mod.bootstrap(rule);
			assertTrue(mod instanceof KeyMod);
			assertSame(rule, mod.rule);
			assertEquals(-1, mod.stage);
			assertNull(mod.parent);
			assertTrue(mod.restored);
		}
		
		@Test public void bootstrapAppendChild() {
			assertEquals(modStore, Mod.bootstrap(rule).node().query().single("..").node());
		}
		
		@Test(expected = TransformException.class)
		public void bootstrapNearestAncestorOrSelfImplementing() throws TransformException {
			Mod.bootstrap(rule).nearestAncestorOrSelfImplementing(Cloneable.class);
		}
		
		@Test public void binder() {
			mockery.checking(new Expectations() {{
				one(parentMod).binder("$a");  will(returnValue(parentMod));
			}});
			Mod mod = new Mod(parentMod);
			assertSame(parentMod, mod.binder("$a"));
		}
		
		@Test public void bindVariable1() throws TransformException {
			Object value = new Object();
			Mod mod = new Mod(parentMod);
			mod.bindVariable("$a", value);
			assertSame(mod, mod.binder("$a"));
			assertEquals(Collections.singleton("$a"), mod.boundVariables);
		}
		
		@Test(expected = TransformException.class)
		public void bindVariable2() throws TransformException {
			Object value = new Object();
			parentModBindings.put("$a", value);
			Mod mod = new Mod(parentMod);
			mod.bindVariable("$a", value);
		}

		@Test public void variableBindings() throws TransformException {
			Mod mod = new Mod(parentMod);
			assertSame(parentModBindings, mod.variableBindings());
		}
		
		@Test public void prepScopeClone() {
			Object value = doc1.query().single("//e1");
			parentModBindings.put("$a", value);
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
			assertSame(foo, mod.nearestAncestorImplementing(Cloneable.class));
		}

		@Test public void hasNearestAncestorImplementing() throws TransformException {
			Mod mod = new Mod(parentMod);
			class Foo extends Seg implements Cloneable {
				Foo(Mod mod) {super(mod);}
			}
			class Bar extends Seg {
				Bar(Mod mod) {super(mod);}
			}
			final Foo foo = new Foo(mod);
			mod.seg = new Bar(mod);
			mockery.checking(new Expectations() {{
				one(parentMod).nearestAncestorOrSelfImplementing(Cloneable.class);
					will(returnValue(foo));
			}});
			assertSame(foo, mod.nearestAncestorImplementing(Cloneable.class));
		}
		
		@Test public void verify() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).verify();
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
		
		private static class SetModBuilderChildren implements Action {
			private final List<Mod> children;
			SetModBuilderChildren(Mod... children) {
				this.children = Arrays.asList(children);
			}
			public Object invoke(Invocation invocation) throws Throwable {
				((Mod.Builder) invocation.getParameter(0)).children.addAll(children);
				return null;
			}
			public void describeTo(Description description) {
				description.appendText("set mod builder children to ").appendValueList("[", ",", "]", children);
			}
		}
		
		@Test public void resolveChildren() throws TransformException {
			final Mod mod = new Mod(parentMod);
			final LinearBlock linearBlock = mockery.mock(LinearBlock.class);
			final Mod[] children = new Mod[] {new Mod(parentMod), new Mod(parentMod)};
			mod.self = mockery.mock(Mod.Shim.class);
			mockery.checking(new Expectations() {{
				one(linearBlock).resolve(with(new CheckModBuilderParams<Mod.Builder>(mod, linearBlock, false, modStore.query())));
				will(new SetModBuilderChildren(children));
				one(mod.self).prepScopeClone(with(same(workspace.query())));
				will(returnValue(modStore.query()));
			}});
			Collection<Mod> result = mod.resolveChildren(linearBlock, false, null);
			assertEquals(Arrays.asList(children), result);
		}

		@Test public void resolveChildrenLastBlock() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.restored = true;
			final LinearBlock linearBlock = mockery.mock(LinearBlock.class);
			final Mod[] children = new Mod[] {new Mod(parentMod), new Mod(parentMod)};
			mod.self = mockery.mock(Mod.Shim.class);
			mockery.checking(new Expectations() {{
				one(linearBlock).resolve(with(new CheckModBuilderParams<Mod.Builder>(mod, linearBlock, true, workspace.query())));
				will(new SetModBuilderChildren(children));
				one(mod.self).prepScopeClone(with(same(modStore.query())));
				will(returnValue(workspace.query()));
			}});
			Collection<Mod> result = mod.resolveChildren(linearBlock, true, modStore.query());
			assertEquals(Arrays.asList(children), result);
		}

		@Test public void resolveChildrenKeyBlock() throws TransformException {
			final Mod mod = new Mod(parentMod);
			final KeyBlock keyBlock = mockery.mock(KeyBlock.class);
			final Mod[] children = new Mod[] {new Mod(parentMod), new Mod(parentMod)};
			mod.self = mockery.mock(Mod.Shim.class);
			mockery.checking(new Expectations() {{
				one(keyBlock).resolve(with(new CheckModBuilderParams<KeyMod.Builder>(mod, keyBlock, false, workspace.query())));
				will(new SetModBuilderChildren(children));
				one(mod.self).prepScopeClone(with(same(workspace.query())));
				will(returnValue(workspace.query()));
			}});
			Collection<Mod> result = mod.resolveChildren(keyBlock, false, modStore.query());
			assertEquals(Arrays.asList(children), result);
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
			mod.node = modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					.end("mod").commit();
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).restore();  will(checkThatNamespaceBindingsAreEmpty(mod.node.namespaceBindings()));
			}});
			mod.restore();
			assertTrue(mod.restored);
			assertTrue(mod.references().isEmpty());
			assertEquals(Transformer.MOD_NS, mod.node.namespaceBindings().get(""));
		}

		@Test(expected = IllegalArgumentException.class)
		public void restoreWrongStage() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.node = modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 5)
					.end("mod").commit();
			mod.restore();
		}
		
		@Test public void restoreWithReferences() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.node = modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					  .elem("reference").attr("refid", "e1").attr("doc", doc1Name).end("reference")
					.end("mod").commit();
			mod.seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(mod.seg).restore();  will(checkThatNamespaceBindingsAreEmpty(mod.node.namespaceBindings()));
			}});
			mod.restore();
			assertTrue(mod.restored);
			assertEquals(Collections.singletonList(doc1.root().query().single("e1").node()), mod.references());
			assertEquals(Transformer.MOD_NS, mod.node.namespaceBindings().get(""));
		}

		@Test(expected = TransformException.class)
		public void restoreWithBadReferencePath() throws TransformException {
			final Mod mod = new Mod(parentMod);
			mod.node = modStore.append()
					.elem("mod").attr("xml:id", "_r1.e13.g23.").attr("stage", 4)
					  .elem("reference").attr("refid", "e1").attr("doc", "foo/doc").end("reference")
					.end("mod").commit();
			mod.restore();
		}
	}
}