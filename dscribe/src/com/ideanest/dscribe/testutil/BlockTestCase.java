package com.ideanest.dscribe.testutil;

import static com.ideanest.dscribe.testutil.Matchers.emptyCollectionOf;

import java.util.*;

import org.exist.fluent.*;
import org.jmock.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;

public abstract class BlockTestCase extends DatabaseTestCase {
	
	protected Folder content;
	protected final Mockery mockery = new JUnit4Mockery() {{
		setImposteriser(ClassImposteriser.INSTANCE);
	}};
	protected final Mod mod = mockery.mock(Mod.class);
	protected Mod.Builder modBuilder;
	protected KeyMod.Builder keyModBuilder;
	
	private List<Sequence> modBuilderPriors = new LinkedList<Sequence>();
	
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
					db.query().namespace("", Namespace.RULES)
					.single("<rule xmlns:java='" + Namespace.JAVA + "'>" + xml + "</rule>")
					.query().single("*").node());
			
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
	
	public void dependOnVariables(final String... varNames) {
		mockery.checking(new Expectations() {{
			Sequence seq = mockery.sequence("modBuilder pre-commit dependOnVariables");
			one(modBuilder).dependOn(with(new Matchers.CollectionMatcher<String>(varNames)));
			will(returnValue(mockery.mock(Mod.Builder.DependencyModifier.class)));
			inSequence(seq);
			modBuilderPriors.add(seq);
		}});
	}
	
	public void dontCommit() throws TransformException {
		mockery.checking(new Expectations() {{
			never(modBuilder).commit();
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
			one(mod).bindVariable(with(equal(name)), with(same(value)));
		}});
	}
}
