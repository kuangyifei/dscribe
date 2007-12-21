package com.ideanest.dscribe.mixt;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.hamcrest.*;
import org.jmock.*;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.*;
import org.junit.runner.RunWith;

import com.ideanest.dscribe.Namespace;

public class Mod {
	
	static final Logger LOG = Logger.getLogger(Mod.class);
	static final NamespaceMap MOD_NAMESPACE = new NamespaceMap("", Namespace.MOD);
	static final NamespaceMap EMPTY_NAMESPACES = new NamespaceMap();
	
	final Rule rule;
	final int stage;
	final Mod parent;
	
	private boolean restored;
	private Set<String> boundVariables;
	private List<Node> references;
	private Seg seg;
	private Node data;

	Mod(Rule rule) {
		this.rule = rule;
		this.stage = -1;
		this.parent = null;
		this.restored = true;
	}
	
	Mod(Mod parent) {
		this.rule = parent.rule;
		this.stage = parent.stage+1;
		this.parent = parent;
		this.restored = false;
	}
	
	Mod binder(String varName) {
		if (boundVariables != null && boundVariables.contains(varName)) return this;
		return parent.binder(varName);
	}
	
	Map<String,Object> variableBindings() {
		return parent.variableBindings();
	}

	public QueryService globalScope() {
		return scope(rule.engine.globalScope());
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
	
	public Node data() {
		return data;
	}
	
	public Folder workspace() {
		return rule.engine.workspace().cloneWithoutNamespaceBindings();
	}
	
	public List<Node> references() {
		if (references == null) return Collections.emptyList();
		return Collections.unmodifiableList(references);
	}
	
	public <T> T nearestAncestorImplementing(Class<T> clazz) throws TransformException {
		if (clazz.isInstance(seg)) return clazz.cast(seg);
		return parent.nearestAncestorImplementing(clazz);
	}
	
	Collection<Mod> resolveChildren(Block block, boolean lastBlock, QueryService touchedScope) throws TransformException {
		QueryService scope = prepScopeClone((touchedScope == null || !restored) ? rule.engine.globalScope() : touchedScope);
		
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
		Mod mod = block instanceof KeyBlock ? new KeyMod(this, key) : new Mod(this);
		mod.seg = block.createSeg(mod);
		return mod;
	}
	
	Mod restoreChild(Block block, Node blockData) throws TransformException {
		Mod mod = deriveChild(block, blockData.query().single("../@xml:id").value());
		mod.data = blockData;
		mod.restore();
		return mod;
	}
	
	void restore() throws TransformException {
		restored = true;
		final int storedStage = data.query().single("@stage").intValue();
		if (this.stage != storedStage)
			throw new IllegalArgumentException("stage mismatch on node restore, given " + stage + ", stored " + storedStage);
		
		ItemList refNodes = data.query().unordered("references");
		if (refNodes.size() > 0) {
			references = new ArrayList<Node>(refNodes.size());
			for (Node refNode : refNodes.nodes()) {
				Node node = rule.engine.globalScope().single("/id($_1)", refNode.query().single("@refid").value()).node();
				final String actualPath = rule.engine.relativePath(node.document());
				final String storedPath = refNode.query().single("@doc").value();
				if (!actualPath.equals(storedPath))
					throw new TransformException("doc mismatch on reference, resolves to '" + actualPath + "' but stored as '" + storedPath + "'");
				references.add(node);
			}
		}
		
		data.namespaceBindings().replaceWith(EMPTY_NAMESPACES);
		seg.restore();
	}
	
	void verify() throws TransformException {
		LOG.debug("verifying " + this);
		seg.verify();
	}
	
	void analyze() throws TransformException {
		LOG.debug("analyzing " + this);
		seg.analyze();
	}
	
	void writeAncestors(ElementBuilder<?> builder, boolean immediate) {
		parent.writeAncestors(builder, immediate);
	}
	
	@Override public String toString() {
		return rule + ".mod[" + stage + ":" + key() + "]";
	}
	
	
	static KeyMod bootstrap(Rule rule) {
		return new KeyMod(rule) {
			@Override void writeAncestors(ElementBuilder<?> builder, boolean immediate) {}
			@Override public String toString() {return "rootmod[" +rule + "]";} 
			@Override Mod restoreChild(Block block, Node data) throws TransformException {
				throw new UnsupportedOperationException();
			}
			@Override public <T> T nearestAncestorImplementing(Class<T> clazz) throws TransformException {
				throw new TransformException("no ancestor found that implements " + clazz);
			}
		};
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
				
				Node modNode = mod.rule.engine.modStore().query().optional("/id($_1)[self::mod]", mod.key()).node();
				if (modNode.extant()) {
					final int oldStage = modNode.query().single("@stage").intValue();
					if (oldStage > mod.stage) return;
					if (oldStage == mod.stage) {
						mod.restored = true;
						mod.data = modNode.query().single("block[xs:integer(@stage)=$_1]", mod.stage).node();
					}
				} else {
					ElementBuilder<Node> modDataBuilder = mod.rule.engine.modStore().append()
						.elem("mod").attr("xml:id", mod.key()).attr("rule", mod.rule.id).end("mod");
					parent.writeAncestors(modDataBuilder, true);
					modNode = modDataBuilder.commit();
				}
				if (!mod.restored) mod.data = writeData(mod, modNode);
				
				mod.seg = block.createSeg(mod);
				mod.seg.restore();
				children.add(mod);
			} finally {
				reset();
			}
		}

		private Node writeData(Mod mod, Node modNode) {
			ElementBuilder<Node> dataBuilder = modNode.append();
			dataBuilder.elem("block").attr("stage", mod.stage);
			for (String docName : dependentDocNames) {
				dataBuilder.elem("dependency")
					.attr("kind", unverifiedDocNames.contains(docName) ? "unverified" : "verified")
					.attr("doc", docName)
				.end("dependency");
			}
			for (String nodeId : affectedNodeIds) dataBuilder.elem("affected").attr("refid", nodeId).end("affected");
			for (Node node : references)
				dataBuilder.elem("reference")
					.attr("refid", node.query().single("@xml:id").value())
					.attr("doc", parent.rule.engine.relativePath(node.document()))
				.end("reference");
			if (supplement != null) dataBuilder.node(supplement.commit());
			Node data = dataBuilder.end("block").commit();
			modNode.update().attr("stage", mod.stage).commit();
			return data;
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
			Map<String,Object> varMap = Collections.emptyMap();
			depMod.addAll(ancestor.data().query().clone(MOD_NAMESPACE, varMap)
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
			if (!node.query().exists("@xml:id")) throw new IllegalArgumentException("referenced node doesn't have an xml:id: " + node);
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
			return parent.key() + (parent.stage+1) + (serial >= 0 ? "-" + serial : "") + ".";
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
			doc1 = db.getFolder("/").documents().load(Name.generate(), Source.xml("<foo><e1 xml:id='e1'/></foo>"));
			doc1Name = db.getFolder("/").relativePath(doc1.path());
			workspace = db.createFolder("/workspace");
			workspace.namespaceBindings().put("", "http://example.com");
			engine = mockery.mock(Engine.class);
			
			modStore = db.getFolder("/").documents().load(
					Name.generate(), Source.xml("<mods xmlns='" + Namespace.MOD + "'/>")).root();
			modStore.namespaceBindings().put("", Namespace.MOD);
			
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
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod>" + 
					"    <dependency kind='verified' doc='d" + n + "v.xml'/>" + 
					"    <dependency kind='unverified' doc='d" + n + "u.xml'/>" + 
					"  </mod>" +
					"</mods>"
			)).query().namespace("", Namespace.MOD).single("//mod").node();
			mockery.checking(new Expectations() {{
				allowing(ancestor).data();  will(returnValue(ancestorData));
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
			final Map<String, Object> variableBindings = new HashMap<String, Object>();
			variableBindings.put("a", "not a node");
			variableBindings.put("b", doc1.root());
			final Mod ancestor1 = createAncestor(1), ancestor2 = createAncestor(2);
			mockery.checking(new Expectations() {{
				allowing(parentMod).variableBindings();  will(returnValue(variableBindings));
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

		@Test(expected = IllegalArgumentException.class)
		public void referenceNoId() {
			builder.reference(doc1.root());
		}
		
		@Test public void generateId() {
			assertEquals(builder.generateId(-1), builder.generateId(-2));
			assertThat(builder.generateId(0), not(equalTo(builder.generateId(1))));			
		}
		
		@Test public void writeData() throws Exception {
			Mod mod = mockery.mock(Mod.class, "childMod");
			mod_stage.set(mod, 3);
			builder.dependentDocNames.add("d1v.xml");
			builder.dependentDocNames.add("d1u.xml");
			builder.unverifiedDocNames.add("d1u.xml");
			builder.affectedNodeIds.add("e2");
			builder.references.add(doc1.query().single("//e1").node());
			builder.supplement().elem("checksum").end("checksum").elem("foobar").end("foobar");
			
			Node targetNode = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<mod xmlns='" + Namespace.MOD + "' stage='3'>" +
					"<block stage='3'>" +
					"<dependency kind='unverified' doc='d1u.xml'/>" +
					"<dependency kind='verified' doc='d1v.xml'/>" +
					"<affected refid='e2'/>" +
					"<reference refid='e1' doc='" + doc1Name + "'/>" +
					"<checksum xmlns=''/><foobar xmlns=''/>" +
					"</block></mod>"
					)).root();
			
			Node modNode = db.getFolder("/").documents().load(
					Name.generate(), Source.xml("<mod xmlns='" + Namespace.MOD + "'/>")).root();
			modNode.namespaceBindings().put("", Namespace.MOD);
			Node blockNode = builder.writeData(mod, modNode);
			
			assertEquals(modNode.query().single("block").node(), blockNode);
			if (!db.query().single("deep-equal($_1, $_2)", targetNode, modNode).booleanValue()) {
				fail("mismatch\n\nExpected:\n" + targetNode + "\n\nActual:\n" + modNode + "\n");
			}
		}
		
		@Test(expected = TransformException.class)
		public void twoCommitsFail() throws TransformException {
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(parentMod).writeAncestors(with(any(ElementBuilder.class)), with(equal(true)));
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
				one(seg).restore();
			}});
			builder.commit();
			builder.commit();
		}
		
		@Test public void commitNewNode() throws TransformException {
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(parentMod).writeAncestors(with(any(ElementBuilder.class)), with(equal(true)));
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
			Node blockNode = modStore.query().optional("mod[@xml:id='_r1.e13.'][@rule='r1']/block[xs:integer(@stage)=4]").node();
			assertTrue(blockNode.extant());
			assertEquals(blockNode, childMod.data);
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}

		@Test public void commitExtantNodeEarlierStage() throws TransformException {
			builder.commit();
			assertEquals(0, builder.children.size());
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}

		@Test public void commitExtantNodeSameStage() throws TransformException {
			Node oldBlock = modStore.query().single("//block[@stage='4']").node();
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
				one(block).createSeg(with(any(Mod.class)));  will(returnValue(seg));
				one(seg).restore();
			}});
			builder.references.add(doc1.query().single("//e1").node());
			builder.commit();
			assertEquals(1, builder.children.size());
			Mod childMod = builder.children.get(0);
			assertSame(seg, childMod.seg);
			assertEquals(Collections.singletonList(doc1.query().single("//e1").node()), childMod.references);
			assertTrue(childMod.restored);
			assertEquals(oldBlock, childMod.data);
			assertEquals(0, builder.dependentDocNames.size());
			assertEquals(0, builder.unverifiedDocNames.size());
			assertEquals(0, builder.affectedNodeIds.size());
			assertEquals(0, builder.references.size());
			assertNull(builder.supplement);
		}

		@Test public void commitExtantNodeLaterStage() throws TransformException {
			Node oldModNode = modStore.query().single("//mod").node();
			final Seg seg = mockery.mock(Seg.class);
			mockery.checking(new Expectations() {{
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
			Node blockNode = oldModNode.query().optional("block[xs:integer(@stage)=4]").node();
			assertTrue(blockNode.extant());
			assertEquals(blockNode, childMod.data);
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
		
		@Test public void bootstrapWriteAncestors() {
			Mod.bootstrap(rule).writeAncestors(null, true);
		}
		
		@Test(expected = UnsupportedOperationException.class)
		public void bootstrapRestoreChild() throws TransformException {
			Mod.bootstrap(rule).restoreChild(null, null);
		}
		
		@Test(expected = TransformException.class)
		public void bootstrapNearestAncestorImplementing() throws TransformException {
			Mod.bootstrap(rule).nearestAncestorImplementing(null);
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
		
		private class CleanWorkspaceQueryMatcher extends TypeSafeMatcher<QueryService> {
			@Override public boolean matchesSafely(QueryService item) {
				return new NamespaceMap().equals(item.namespaceBindings()) && item.all("//*").isEmpty();
			}
			public void describeTo(Description description) {
				description.appendText("matches a clean workspace");
			}
			
		}
		
		@Test public void workspace() {
			Mod mod = new Mod(parentMod);
			Folder cleanWorkspace = mod.workspace();
			assertEquals(workspace, cleanWorkspace);
			assertEquals(new NamespaceMap(), cleanWorkspace.namespaceBindings());
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
				one(parentMod).prepScopeClone(with(new CleanWorkspaceQueryMatcher()));
					will(returnValue(result));
			}});
			Mod mod = new Mod(parentMod);
			assertSame(result, mod.scope(null));
		}

	}
}