package com.ideanest.dscribe.job;

import org.apache.log4j.Logger;
import org.quartz.*;

import com.ideanest.dscribe.Configuration;

public class QuartzCycle implements InterruptableJob, StatefulJob {
	
	private static final Logger LOG = Logger.getLogger(QuartzCycle.class);
	private Cycle cycle;

	public void execute(JobExecutionContext context) throws JobExecutionException {
		try {
			cycle = new Job(context.getJobDetail().getName(), Configuration.fromContext(context)).createCycle(context.getFireTime());
		} catch (Exception e) {
			String msg = "failed to instantiate job run for job '" + context.getJobDetail().getName() + "'";
			LOG.error(msg, e);
			throw new JobExecutionException(msg, e, false);
		}
		cycle.execute();
	}

	public void interrupt() throws UnableToInterruptJobException {
		cycle.interrupt();
	}

}
