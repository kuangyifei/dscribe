package com.ideanest.dscribe;

import java.util.Random;

import org.apache.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;

import com.ideanest.dscribe.job.Job;
import com.ideanest.dscribe.job.QuartzCycle;

public class Schedule {
	
	private static final Logger LOG = Logger.getLogger(Schedule.class);
	private final Scheduler scheduler;
	private final Main main;
	private int pauseCount;

	Schedule(Main main) throws SchedulerException {
		this.main = main;
		DirectSchedulerFactory.getInstance().createVolatileScheduler(1);
		scheduler = DirectSchedulerFactory.getInstance().getScheduler();
		scheduler.getContext().put(Main.MAIN_INSTANCE_KEY, main);
		scheduler.start();
	}

	/**
	 * Pause the schedule.  Must be followed by a call to {@link #resume()}.
	 *
	 * @throws SchedulerException if pausing failed
	 */
	synchronized public void pause() throws SchedulerException {
		if (pauseCount == Integer.MAX_VALUE) throw new SchedulerException("maximum simultaneous schedule pauses reached");
		if (++pauseCount == 1) scheduler.standby();
	}
	
	/**
	 * Resume the schedule.  This method will not return until the schedule has been
	 * successfully resumed.
	 */
	synchronized public void resume() {
		if (pauseCount <= 0) throw new IllegalStateException("schedule not paused");
		if (--pauseCount > 0) return;
		try {
			scheduler.start();
		} catch (SchedulerException e) {
			LOG.error("failed to resume schedule, will reset scheduler and bootstrap config", e);
			try {
				scheduler.shutdown();
			} catch (SchedulerException e1) {
				LOG.error("failed to shut down old scheduler, continuing anyway", e);
				// TODO:  instead, force exit and restart app
			}
			main.resetSchedule();
		}
		if (LOG.isDebugEnabled()) logSchedulerConfig();
	}

	public void ensureJob(Job job) throws SchedulerException {
		scheduler.addJob(new JobDetail(job.name(), null, QuartzCycle.class), true);
	}
	
	public void removeJob(Job job) throws SchedulerException {
		removeJob(job.name());
	}
	
	public void removeJob(String name) throws SchedulerException {
		scheduler.deleteJob(name, null);
	}
	
	private static final Random random = new Random();
	
	public void addTrigger(Trigger trigger) throws SchedulerException {
		synchronized(random) {
			while(true) {
				try {
					trigger.setName(Integer.toString(Math.abs(random.nextInt()), Character.MAX_RADIX));
					scheduler.scheduleJob(trigger);
					break;
				} catch (ObjectAlreadyExistsException e) {
					// that's OK, we'll just try another name
				}
			}
		}
	}
	
	public void removeTrigger(String name) throws SchedulerException {
		scheduler.unscheduleJob(name, null);
	}

	public Trigger[] triggers(Job job) throws SchedulerException {
		return scheduler.getTriggersOfJob(job.name(), null);
	}

	private void logSchedulerConfig() {
		try {
			StringBuilder buf = new StringBuilder("\n");
			for (String groupName : scheduler.getJobGroupNames()) {
				buf.append("-- Group: ").append(groupName).append("\n");
				for (String jobName : scheduler.getJobNames(groupName)) {
					JobDetail jobDetail = scheduler.getJobDetail(jobName, groupName);
					buf.append("   -- Job: ").append(jobName).append(", class ").append(jobDetail.getJobClass().getName()).append("\n");
					for (Trigger trigger : scheduler.getTriggersOfJob(jobName, groupName)) {
						buf.append("      -- ").append(trigger).append("\n");
					}
				}
			}
			LOG.debug(buf.toString());
		} catch (SchedulerException e) {
			LOG.debug("failed to log scheduler config", e);
		}
	}

	
	
}
