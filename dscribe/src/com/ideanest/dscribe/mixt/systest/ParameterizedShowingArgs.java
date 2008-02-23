package com.ideanest.dscribe.mixt.systest;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.Assert;
import org.junit.internal.runners.*;
import org.junit.runner.notification.RunNotifier;

public class ParameterizedShowingArgs extends CompositeRunner {
	static class TestClassRunnerForParameters extends JUnit4ClassRunner {
		private final Object[] parameters;
		private final Constructor<?> constructor;
		private final String paramString;

		TestClassRunnerForParameters(TestClass testClass, Object[] parameters) throws InitializationError {
			super(testClass.getJavaClass());
			this.parameters = parameters;
			this.constructor = getOnlyConstructor();
			this.paramString = Arrays.asList(parameters).toString();
		}

		@Override protected Object createTest() throws Exception {
			return constructor.newInstance(parameters);
		}
		
		@Override protected String getName() {
			return paramString;
		}
		
		@Override protected String testName(final Method method) {
			return method.getName() + paramString;
		}

		private Constructor<?> getOnlyConstructor() {
			Constructor<?>[] constructors= getTestClass().getJavaClass().getConstructors();
			Assert.assertEquals(1, constructors.length);
			return constructors[0];
		}
		
		@Override protected void validate() throws InitializationError {
			// do nothing: validated before.
		}
		
		@Override public void run(RunNotifier notifier) {
			runMethods(notifier);
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface Parameters {
	}
	
	private final TestClass testClass;

	public ParameterizedShowingArgs(Class<?> klass) throws Exception {
		super(klass.getName());
		this.testClass = new TestClass(klass);
		
		MethodValidator methodValidator = new MethodValidator(testClass);
		methodValidator.validateStaticMethods();
		methodValidator.validateInstanceMethods();
		methodValidator.assertValid();
		
		for (final Object each : getParametersList()) {
			if (each instanceof Object[]) {
				add(new TestClassRunnerForParameters(testClass, (Object[])each));
			} else {
				throw new Exception(String.format("%s.%s() must return a Collection of arrays.", testClass.getName(), getParametersMethod().getName()));
			}
		}
	}
	
	@Override public void run(final RunNotifier notifier) {
		new ClassRoadie(notifier, testClass, getDescription(), new Runnable() {
			public void run() {
				runChildren(notifier);
			}
		}).runProtected();
	}
	
	private Collection<?> getParametersList() throws IllegalAccessException, InvocationTargetException, Exception {
		return (Collection<?>) getParametersMethod().invoke(null);
	}
	
	private Method getParametersMethod() throws Exception {
		List<Method> methods= testClass.getAnnotatedMethods(Parameters.class);
		for (Method each : methods) {
			int modifiers = each.getModifiers();
			if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) return each;
		}
		throw new Exception("No public static parameters method on class " + getName());
	}
}

