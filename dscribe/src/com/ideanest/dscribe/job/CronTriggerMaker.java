package com.ideanest.dscribe.job;

import java.text.ParseException;

import org.exist.fluent.Item;
import org.quartz.CronTrigger;
import org.quartz.Trigger;

import com.ideanest.dscribe.InvalidConfigurationException;
import com.ideanest.dscribe.TriggerMaker;

/**
 * Schedules jobs using cron-like triggers.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class CronTriggerMaker implements TriggerMaker {
	
	public Trigger create(Item def) throws InvalidConfigurationException {
		String triggerExpression = def.query().single("@at").value();
		
		try {
			// TODO: set misfire instruction
			CronTrigger trigger = new CronTrigger();
			trigger.setCronExpression(triggerExpression);
			return trigger;
		} catch (ParseException e) {
			throw new InvalidConfigurationException("invalid trigger expression '" + triggerExpression, e);
		} catch (UnsupportedOperationException e) {
			throw new InvalidConfigurationException("unsupported trigger expression '" + triggerExpression, e);						
		}
	}

}
