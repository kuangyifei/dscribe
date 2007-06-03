package com.ideanest.dscribe;

import org.exist.fluent.Item;
import org.quartz.Trigger;


/**
 * Something that can create and set triggers for a job.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public interface TriggerMaker {
	
	Trigger create(Item def) throws InvalidConfigurationException;

}
