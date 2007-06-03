package com.ideanest.dscribe.job;

import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.exist.fluent.Node;


/**
 * A base class for Task implementations that takes care of housekeeping details.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public abstract class TaskBase implements Task {
	
	@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
	protected @interface Phase {
		String value() default "";
	}
	
	public static class IllegalTaskPhaseConfigurationException extends RuntimeException {
		public IllegalTaskPhaseConfigurationException() {super();}
		public IllegalTaskPhaseConfigurationException(String message) {super(message);}
		public IllegalTaskPhaseConfigurationException(Throwable cause) {super(cause);}
		public IllegalTaskPhaseConfigurationException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private final String supportedPhases;
	private String name;
	private Cycle cycle;

	public TaskBase() {
		StringBuilder buf = new StringBuilder();
		for (Method m : this.getClass().getMethods()) {
			if (m.isAnnotationPresent(Phase.class)) {
				if (m.getParameterTypes().length > 0) throw new IllegalTaskPhaseConfigurationException("method with arguments cannot be tagged with @Phase");
				String phase = m.getAnnotation(Phase.class).value();
				if (phase.length() == 0) phase = m.getName();
				if (buf.length() > 0) buf.append(' ');
				buf.append(phase);
			}
		}
		supportedPhases = buf.toString();
		if (supportedPhases.length() == 0) throw new IllegalTaskPhaseConfigurationException("TaskBase subclass has no @Phase methods");
	}

	public String getSupportedPhases() {
		return supportedPhases;
	}

	public final void init(Cycle _cycle, Node taskDefinition) throws Exception {
		this.cycle = _cycle;
		name = taskDefinition.name();
		init(taskDefinition);
	}
	
	protected abstract void init(Node taskDef) throws Exception;

	public String getName() {
		return name;
	}
	
	protected Cycle cycle() {
		return cycle;
	}

	public void execute(String phase) throws Exception {
		try {
			getClass().getMethod(phase).invoke(this);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
			throw e;
		}
	}

	public void dispose() throws Exception {
		// nothing to do by default
	}

}
