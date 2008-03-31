package com.ideanest.dscribe.mixt.test;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.Assert;
import org.junit.internal.runners.*;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;

public class ParameterizedShowingArgs extends CompositeRunner {
	static class TestClassRunnerForParameters extends JUnit4ClassRunner {
		private final Object[] parameters;
		private final Constructor<?> constructor;
		private final Method runMethod;

		TestClassRunnerForParameters(TestClass testClass, Method runMethod, Object[] parameters) throws InitializationError {
			super(testClass.getJavaClass());
			this.runMethod = runMethod;
			this.parameters = parameters;
			this.constructor = getOnlyConstructor();
		}

		@Override protected Object createTest() throws Exception {
			return constructor.newInstance(parameters);
		}
		
		@Override protected String getName() {
			return (String) parameters[0];
		}
		
		@Override protected String testName(final Method method) {
			return getName();
		}

		@Override public Description getDescription() {
			return Description.createTestDescription(getTestClass().getJavaClass(), testName(runMethod), testAnnotations(runMethod));
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
			invokeTestMethod(runMethod, notifier);
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
		
		Method runMethod = klass.getMethod("run");
		
		MethodValidator methodValidator = new MethodValidator(testClass);
		methodValidator.validateStaticMethods();
		methodValidator.validateInstanceMethods();
		methodValidator.assertValid();
		
		for (final Object each : getParametersList()) {
			if (each instanceof Object[]) {
				add(new TestClassRunnerForParameters(testClass, runMethod, (Object[])each));
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

