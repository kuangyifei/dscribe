package com.ideanest.dscribe.testutil;

import static com.ideanest.dscribe.testutil.Matchers.emptyCollectionOf;
import static org.junit.Assert.assertTrue;

import java.util.*;

import org.exist.fluent.*;
import org.hamcrest.*;
import org.jmock.*;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.runner.RunWith;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.Mod.Builder.DependencyModifier;

@RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml")
public abstract class BlockTestCase extends DatabaseTestCase {
	
	protected Folder content;
	protected final Mockery mockery = new JUnit4Mockery() {{
		setImposteriser(ClassImposteriser.INSTANCE);
	}};
	protected final KeyMod mod = mockery.mock(KeyMod.class);
	protected Mod.Builder modBuilder;
	protected KeyMod.Builder keyModBuilder;
	
	private List<Sequence> modBuilderPriors = new LinkedList<Sequence>();
	private ElementBuilder<XMLDocument> supplementBuilder;
	
	@Before
	public void prepareDatabase() {
		db.namespaceBindings().put("java", Namespace.JAVA);
		content = db.createFolder("/content");
		content.documents().build(Name.create("stuff"))
			.elem("java:class").attr("xml:id", "c1").attr("name", "Job")
				.elem("java:method").attr("xml:id", "m1").attr("name", "start").end("java:method")
				.elem("java:method").attr("xml:id", "m2").attr("name", "end").end("java:method")
			.end("java:class").commit();
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Block> T define(String xml) throws RuleBaseException {
		try {
			T block = (T) getClass().getEnclosingClass().asSubclass(BlockType.class).newInstance().define(
					db.getFolder("/").documents().load(Name.create("rule"), Source.xml(
							"<rule xmlns:java='" + Namespace.JAVA + "'>" + xml + "</rule>")).root().query().single("*").node());
			
			if (block instanceof LinearBlock) modBuilder = mockery.mock(Mod.Builder.class);
			else if (block instanceof KeyBlock) modBuilder = keyModBuilder = mockery.mock(KeyMod.Builder.class);
			else throw new RuntimeException("Block of unknown kind: " + block);
			
			mockery.checking(new Expectations() {{
				allowing(modBuilder).dependOn(with(emptyCollectionOf(String.class)));
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
		final Node data = db.createFolder("/supplement").documents().build(Name.generate()).node(
				db.query().single(xml).node()).commit().root();
		mockery.checking(new Expectations() {{
			allowing(mod).node(); will(returnValue(data));
		}});
	}
	
	public void setModScope(final QueryService qs) {
		mockery.checking(new Expectations() {{
			allowing(mod).scope(with(any(QueryService.class))); will(returnValue(qs));
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
	
	public <T> void setModNearestAncestorImplementing(final Class<? super T> clazz, final T implementor) throws TransformException {
		mockery.checking(new Expectations() {{
			allowing(mod).nearestAncestorImplementing(clazz); will(returnValue(implementor));
		}});
	}
	
	public <T> void setModBuilderNearestAncestorImplementing(final Class<? super T> clazz, final T implementor) throws TransformException {
		mockery.checking(new Expectations() {{
			allowing(modBuilder).nearestAncestorImplementing(clazz); will(returnValue(implementor));
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
	
	public void dependOnVariables(final String... varNames) throws TransformException {
		internalDependOnVariables(false, varNames);
	}
	
	public void dependOnUnverifiedVariables(final String... varNames) throws TransformException {
		internalDependOnVariables(true, varNames);
	}
	
	private void internalDependOnVariables(final boolean unverified, final String[] varNames) throws TransformException {
		if (varNames.length == 0) return;
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit dependOnVariables");
			DependencyModifier dependecyModifier = mockery.mock(Mod.Builder.DependencyModifier.class);
			one(modBuilder).dependOn(with(new Matchers.CollectionMatcher<String>(varNames)));
			will(returnValue(dependecyModifier)); inSequence(seq);
			if (unverified) {
				one(dependecyModifier).unverified();
				inSequence(seq);
			}
			modBuilderPriors.add(seq);
		}});
	}
	
	public void supplement() {
		supplementBuilder = db.createFolder("/supplement").documents().build(Name.generate());
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit supplement");
			atLeast(1).of(modBuilder).supplement(); will(returnValue(supplementBuilder)); inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void checkSupplement(String expected) {
		Node supplementNode = supplementBuilder.commit().root();
		assertTrue("supplement expected '" + expected + "', got '" + supplementNode + "'",
				db.query().presub().single("$_1 eq $2", supplementNode, expected).booleanValue());
	}
	
	public void generateIdsAndAffect(final String base, final int count) {
		mockery.checking(new Expectations() {{
			for (int i=1; i<=count; i++) {
				final String genid = "_" + base + (count == 1 ? "" : "-" + i) + ".";
				one(modBuilder).generateId(count == 1 ? -1 : i);
				will(returnValue(genid));
				one(modBuilder).affect(with(new NodeIdMatcher(genid)));
			}
		}});
	}
	
	public void thenCommit() throws TransformException {
		mockery.checking(new Expectations() {{
			one(modBuilder).commit();
			for (Sequence seq : modBuilderPriors) inSequence(seq);
		}});
	}
	
	public void bindVariable(final String name, final Object value) throws TransformException {
		mockery.checking(new Expectations() {{
			one(mod).bindVariable(name,value);
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
