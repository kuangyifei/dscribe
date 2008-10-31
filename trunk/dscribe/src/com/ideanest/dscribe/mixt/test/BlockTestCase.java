package com.ideanest.dscribe.mixt.test;

import static com.ideanest.dscribe.mixt.test.Matchers.emptyCollectionOf;
import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.hamcrest.*;
import org.jmock.*;
import org.jmock.api.Action;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.runner.RunWith;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.Mod.Builder.*;

@RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml")
public abstract class BlockTestCase extends DatabaseTestCase {
	
	protected Folder content;
	protected final Mockery mockery = new JUnit4Mockery() {{
		setImposteriser(ClassImposteriser.INSTANCE);
	}};
	protected final KeyMod mod = mockery.mock(KeyMod.class);
	protected Mod.Builder modBuilder;
	protected KeyMod.Builder keyModBuilder;
	
	protected List<Sequence> modBuilderPriors = new LinkedList<Sequence>();
	private ElementBuilder<XMLDocument> supplementBuilder;
	private int counter;
	
	@Before
	public void prepareDatabase() {
		db.namespaceBindings().put("java", "http://example.com/java");
		db.namespaceBindings().put("uml", "http://example.com/uml");
		content = db.createFolder("/content");
		content.documents().load(Name.create("stuff"), Source.xml(
				"<java:class xmlns:java='http://example.com/java' xml:id='c1' name='Job'>" +
				"	<java:method xml:id='m1' name='start'/>" +
				"	<java:method xml:id='m3' name='other'/>" +
				"	<java:method xml:id='m2' name='end'/>" +
				"</java:class>"));
		content.documents().load(Name.create("stuff2"), Source.xml(
				"<java:interface xmlns:java='http://example.com/java' xml:id='c1b' name='Job'>" +
				"	<java:field xml:id='f1' name='foo'/>" +
				"	<java:field xml:id='f2' name='bar'/>" +
				"</java:interface>"));
		content.documents().load(Name.create("uml-stuff"), Source.xml(
				"<uml:class xmlns:uml='http://example.com/uml' xml:id='uc1' depict='c1'>" +
				"	<uml:name xml:id='cname'>Job</uml:name>" +
				"	<uml:stereotype>interface</uml:stereotype>" +
				"	<uml:compartment xml:id='comp1' kind='attribute'>" +
				"		<uml:attribute xml:id='uf1' depict='f1'/>" +
				"		<uml:attribute xml:id='uf2' depict='f2'/>" +
				"	</uml:compartment>" +
				"	<uml:compartment xml:id='comp2' kind='operation'>" +
				"		<uml:operation xml:id='um1' depict='m1'/>" +
				"		<uml:operation xml:id='um2' depict='m2'/>" +
				"	</uml:compartment>" +
				"</uml:class>"));
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Block> T define(String xml) throws RuleBaseException {
		try {
			T block = (T) getClass().getEnclosingClass().asSubclass(BlockType.class).newInstance().define(
					db.getFolder("/").documents().load(Name.create("rule"), Source.xml(
							"<rule xmlns:java='http://example.com/java' xmlns:uml='http://example.com/uml'>"
							+ xml + "</rule>")).root().query().single("*").node());
			
			if (block instanceof LinearBlock) modBuilder = mockery.mock(Mod.Builder.class);
			else if (block instanceof KeyBlock) modBuilder = keyModBuilder = mockery.mock(KeyMod.Builder.class);
			else throw new RuntimeException("Block of unknown kind: " + block);
			
			mockery.checking(new Expectations() {{
				allowing(modBuilder).dependOn(with(emptyCollectionOf(QName.class)));
			}});
			
			return block;
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void setModBuilderScope(final QueryService qs) {
		mockery.checking(new Expectations() {{
			allowing(modBuilder).scope(); will(returnValue(qs));
		}});
	}
	
	public void setModBuilderScopeWithVariablesBound(final QueryService qs) {
		mockery.checking(new Expectations() {{
			allowing(modBuilder).scopeWithVariablesBound(with(any(QueryService.class)));
			will(returnValue(qs));
		}});
	}
	
	public void setModBuilderParent(final Mod mod) {
		mockery.checking(new Expectations() {{
			allowing(modBuilder).parent(); will(returnValue(mod));
		}});
	}
	
	public void setModKey(final String key) {
		mockery.checking(new Expectations() {{
			allowing(mod).key(); will(returnValue(key));
		}});
	}
	
	public void setModData(String xml) {
		final Node data = db.createFolder("/supplement").documents().build(Name.generate()).elem("root").nodes(
				db.query().all(xml).nodes()).end("root").commit().root();
		mockery.checking(new Expectations() {{
			allowing(mod).supplementQuery(); will(returnValue(data.query()));
		}});
	}
	
	public void setModScope(final QueryService... qs) {
		mockery.checking(new Expectations() {{
			allowing(mod).scope(with(any(QueryService.class)));
			if (qs.length == 1) {
				will(returnValue(qs[0]));
			} else {
				Action[] actions = new Action[qs.length];
				for (int i=0; i<qs.length; i++) actions[i] = returnValue(qs[i]);
				will(onConsecutiveCalls(actions));
			}
		}});
	}
	
	public void setModGlobalScope(final QueryService qs) {
		mockery.checking(new Expectations() {{
			allowing(mod).globalScope(); will(returnValue(qs));
		}});
	}
	
	public void setModWorkspace(final Folder workspace) {
		mockery.checking(new Expectations() {{
			allowing(mod).workspace(); will(returnValue(workspace));
		}});
	}
	
	public void setModReferences(final Node... nodes) {
		mockery.checking(new Expectations() {{
			allowing(mod).references(); will(returnValue(Arrays.asList(nodes)));
		}});
	}
	
	public void setModAffectedIds(final String... ids) {
		mockery.checking(new Expectations() {{
			allowing(mod).affectedIds(); will(returnValue(Arrays.asList(ids)));
		}});
	}
	
	public <T> void setModNearestAncestorImplementing(final Class<? super T> clazz, final T implementor) throws TransformException {
		mockery.checking(new Expectations() {{
			allowing(mod).nearest(clazz); will(returnValue(implementor));
		}});
	}
	
	public <T> void dependOnNearest(final Class<? super T> clazz, final boolean verified, final T implementor) throws TransformException {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit dependOnNearest");
			AncestorDependencyModifier<?> modifier = mockery.mock(AncestorDependencyModifier.class);
			one(modBuilder).dependOnNearest(clazz); will(returnValue(modifier)); inSequence(seq);
			if (!verified) {
				one(modifier).unverified(); will(returnValue(modifier)); inSequence(seq);
			}
			allowing(modifier).get(); will(returnValue(implementor));
			modBuilderPriors.add(seq);
		}});
	}
	
	public void reference(final Node node) {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit reference");
			one(modBuilder).reference(node); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void referenceKey(final Node node) {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit referenceKey");
			one(keyModBuilder).referenceKey(node); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void setKey(final String key) {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit setKey");
			one(keyModBuilder).setKey(key); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void dependOnDocument(final XMLDocument doc) {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit dependOn document");
			DependencyModifier dependecyModifier = mockery.mock(Mod.Builder.DependencyModifier.class);
			one(modBuilder).dependOn(doc); will(returnValue(dependecyModifier)); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void dependOnVariables(final QName... varNames) throws TransformException {
		internalDependOnVariables(false, varNames);
	}
	
	public void dependOnUnverifiedVariables(final QName... varNames) throws TransformException {
		internalDependOnVariables(true, varNames);
	}
	
	private void internalDependOnVariables(final boolean unverified, final QName[] varNames) throws TransformException {
		if (varNames.length == 0) return;
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit dependOnVariables");
			DependencyModifier dependencyModifier = mockery.mock(Mod.Builder.DependencyModifier.class, "dependencyModifier_" + ++counter);
			one(modBuilder).dependOn(with(new Matchers.CollectionMatcher<QName>(varNames)));
			will(returnValue(dependencyModifier)); inSequence(seq);
			if (unverified) {
				one(dependencyModifier).unverified();
				inSequence(seq);
			}
			modBuilderPriors.add(seq);
		}});
	}
	
	public void supplement() {
		if (supplementBuilder == null) {
			supplementBuilder = db.createFolder("/supplement").documents().build(Name.generate()).elem("root");
		}
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit supplement");
			allowing(modBuilder).supplement(); will(returnValue(supplementBuilder)); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void checkSupplement(String expected) {
		XMLDocument supplementDoc = supplementBuilder.end("root").commit();
		if (supplementDoc == null) {
			fail("no supplement recorded, expected '" + expected + "'");
		} else {
			ItemList supplement = supplementDoc.root().query().all("*");
			assertTrue("supplement expected '" + expected + "', got '" + supplement + "'",
					db.query().presub().single("deep-equal($_1, $2)", supplement, expected).booleanValue());
		}
	}
	
	public void generateIdsAndAffect(final String base, final int count, final boolean inOrder) {
		mockery.checking(new Expectations() {{
			for (int i=1; i<=count; i++) {
				Sequence seq1 = mockery.sequence("modBuilder pre-commit affect");
				Sequence seq2 = mockery.sequence("modBuilder pre-commit order");
				final String genid = "_" + base + (count == 1 ? "" : "-" + i) + ".";
				one(modBuilder).generateId(count == 1 ? -1 : i);
				will(returnValue(genid)); inSequences(seq1, seq2);
				one(modBuilder).affect(with(new NodeIdMatcher(genid))); inSequence(seq1);
				modBuilderPriors.add(seq1);
				if (inOrder) {
					one(modBuilder).order(with(new NodeIdMatcher(genid))); inSequence(seq2);
					modBuilderPriors.add(seq2);
				}
			}
		}});
	}
	
	public void order(final String nodeId) {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit order");
			one(modBuilder).order(with(new NodeIdMatcher(nodeId))); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void thenCommit() throws TransformException {
		mockery.checking(new Expectations() {{
			one(modBuilder).commit();
			for (Sequence seq : modBuilderPriors) inSequence(seq);
		}});
	}
	
	public void bindVariable(final QName name, final Resource value) throws TransformException {
		mockery.checking(new Expectations() {{
			one(mod).bindVariable(name, value);
		}});
	}
	
	private static class NodeIdMatcher extends BaseMatcher<Node> {
		private final String id;
		public NodeIdMatcher(String id) {this.id = id;}
		public boolean matches(Object item) {
			return id.equals(((Node) item).query().single("@xml:id").value());
		}
		public void describeTo(Description description) {
			description.appendText("@xml:id='" + id + "'");
		}
	}
}
