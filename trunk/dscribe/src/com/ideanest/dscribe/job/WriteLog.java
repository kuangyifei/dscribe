package com.ideanest.dscribe.job;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.exist.fluent.Node;


/**
 * Write a log entry.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class WriteLog implements Task {
	
	private String name;
	private String message;
	private Level level;

	public String getSupportedPhases() {
		return null;
	}

	public void init(Cycle job, Node taskDefinition) throws Exception {
		name = taskDefinition.name();
		message = taskDefinition.value();
		level = Level.toLevel(taskDefinition.query().optional("@level").value(), Level.INFO);
	}
	
	public String getName() {return name;}

	public void execute(String phase) throws Exception {
		Logger.getLogger(WriteLog.class).log(level, message);
	}

	public void dispose() throws Exception {
		// nothing to do
	}

}
