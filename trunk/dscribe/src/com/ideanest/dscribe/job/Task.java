package com.ideanest.dscribe.job;

import org.exist.fluent.Node;

/**
 * A single task to be executed as part of a chore or project.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public interface Task {
	
	/**
	 * Return a space-separated list of supported phases for this task.  If the list is
	 * not <code>null</code>, the <code>execute</code> method will only be called
	 * with one of these phases as argument.  If the list is <code>null</code>, then
	 * the task performs the same action independent of the phase where it's used.
	 * 
	 * @see Cycle
	 * @return the list of phases supported by this task 
	 */
	String getSupportedPhases();
	
	/**
	 * Initialize the task, validating its definition and allocating any resources it needs.
	 * 
	 * @param cycle the cycle this task belongs to
	 * @param taskDefinition reference to the task definition fragment
	 * @throws Exception if the definition is not valid or a resource cannot be allocated
	 */
	void init(Cycle cycle, Node taskDefinition) throws Exception;
	
	/**
	 * Return a user-recognizable name for this task; this is most likely to be the tag from the
	 * task definition, perhaps augmented with some additional identifying characteristics.
	 *
	 * @return a user-recognizable name for this task
	 */
	String getName();
	
	/**
	 * Execute the task for a given phase.
	 *
	 * @param phase the current phase of execution, may affect the actions undertaken by the task
	 * @throws Exception if something went wrong; the job will be aborted
	 */
	void execute(String phase) throws Exception;
	
	/**
	 * Dispose all resources allocated in the <code>init</code> method.  No further methods
	 * will be invoked on this task.
	 * 
	 * @throws Exception if resources could not be disposed; the job will continue nonetheless
	 */
	void dispose() throws Exception;
}
