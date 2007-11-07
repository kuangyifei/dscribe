package com.ideanest.dscribe.job;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.xml.datatype.Duration;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.quartz.*;
import org.quartz.Trigger;

import com.ideanest.dscribe.*;

public class Job {
	
	private static final String CLEAN_CYCLE_MARKER_DOC_NAME = "clean-cycle-marker";
	
	private static final String VERSIONSPACE_FOLDER_NAME = "versions";
	private static final String ABORTSPACE_FOLDER_NAME = "abortspace";
	private static final String PREVSPACE_FOLDER_NAME = "prevspace";
	private static final String WORKSPACE_FOLDER_NAME = "workspace";

	private static final Logger LOG = Logger.getLogger(Job.class);
	
	private final Configuration config;
	private final String name, type;
	private final Node def;
	private final Folder root;
	private final boolean enabled, debugDump;
	private List<String> phases;
	private File workdir;
	
	public Job(String name, Configuration config) {
		this.config = config;
		this.name = name;
		def = config.findJob(name);
		
		enabled = !def.query().exists("(. | ancestor::group)[@enabled='false']");
		debugDump = def.query().flag("@debug-dump", false);
		
		String temp_type = def.query().optional("@type").value();
		if (temp_type == null) temp_type = def.name();
		type = temp_type;
		
		root = Database.login("admin", null).createFolder("/reef/" + type + "/" + name);
		root.namespaceBindings().put("reef", Namespace.NOTES);
		root.namespaceBindings().put("", Namespace.NOTES);
	}
	
	@Override public String toString() {
		return "job '" + name + "'";
	}
	
	public void runIfEnabled() throws JobExecutionException {
		if (!enabled) return;
		createCycle(null).execute();
	}
	
	public Cycle createCycle(Date fireTime) {
		return createCycle(fireTime,
				root.documents().contains(CLEAN_CYCLE_MARKER_DOC_NAME) ||
				latestSpace().query().flag("exists(//notes/next-cycle-clean)", false));
	}
	
	public Cycle createCycle(Date fireTime, boolean cleanRun) {
		root.documents().build(Name.overwrite(CLEAN_CYCLE_MARKER_DOC_NAME)).elem("empty").end("empty").commit();
		return new Cycle(this, config, fireTime, cleanRun);
	}
	
	/**
	 * Set the name of the current thread to the job's name and type, so that it can
	 * be identified easily in the debugger or log file.
	 */
	public void setCurrentThreadName() {
		Thread.currentThread().setName(type + " " + name);
	}

	public String name() {
		return name;
	}
	
	public Folder versionSpace() {
		return root.children().create(VERSIONSPACE_FOLDER_NAME);
	}
	
	public Folder emptySpace() {
		Folder emptySpace = root.children().create("emptyspace");
		emptySpace.clear();
		return emptySpace;
	}
	
	public Folder stableSpace() {
		// TODO: add stable space locking, unique naming
		return root.children().create(PREVSPACE_FOLDER_NAME);
	}
	
	public Folder latestSpace() {
		return root.children().contains(ABORTSPACE_FOLDER_NAME) ?
				root.children().get(ABORTSPACE_FOLDER_NAME)	: stableSpace();
	}
	
	Folder storeSpace() {
		return root.children().create("store");
	}
	
	Folder createWorkspace() {
		Folder workspace = root.children().create(WORKSPACE_FOLDER_NAME);
		workspace.clear();
		return workspace;
	}
	
	void commitWorkspace() {
		debugDump();
		if (root.documents().contains(CLEAN_CYCLE_MARKER_DOC_NAME)) root.documents().get(CLEAN_CYCLE_MARKER_DOC_NAME).delete();
		root.children().get(WORKSPACE_FOLDER_NAME).move(root, Name.overwrite(PREVSPACE_FOLDER_NAME));
		if (root.children().contains(ABORTSPACE_FOLDER_NAME)) root.children().get(ABORTSPACE_FOLDER_NAME).delete();
	}
	
	void abortWorkspace() {
		debugDump();
		root.children().get(WORKSPACE_FOLDER_NAME).move(root, Name.overwrite(ABORTSPACE_FOLDER_NAME));
	}
	
	private void debugDump() {
		if (!debugDump) return;
		try {
			File debugDest = new File(config.tempdir(), "debug-dump-" + name());
			root.children().get(WORKSPACE_FOLDER_NAME).export(debugDest);
			LOG.debug("dumped final workspace into '" + debugDest + "'");
		} catch (IOException e) {
			LOG.error("failed to dump workspace for debugging", e);
		}
	}
	
	public File workdir() {
		if (workdir == null) {
			workdir = new File(config.workdir(), "work/" + type + "/" + name);
			workdir.mkdirs();
		}
		return workdir;
	}
	
	public String fullName() {
		return def.query().single("string-join(ancestor-or-self::job/@name, '.')").value();
	}
	
	List<Trigger> scheduledTriggers() throws SchedulerException {
		return Collections.unmodifiableList(Arrays.asList(config.schedule().triggers(this)));
	}
	
	/**
	 * Reconcile this job's configured triggers with the scheduled ones, scheduling and
	 * unscheduling triggers as required.  If the job is disabled, unschedule it completely.
	 * Handle scheduling errors at a per-trigger granularity, with a best-effort approach.
	 * Log any failures.
	 */
	void schedule() {
		if (!enabled) {
			unschedule();
			return;
		}
		LOG.debug("reconciling schedule of " + this);
		try {
			Set<String> triggerKeys = new HashSet<String>(), matchedTriggerKeys = new HashSet<String>();
			for (Trigger trigger : scheduledTriggers()) {
				triggerKeys.add(trigger.getDescription());
			}
			for (Trigger trigger : configuredTriggers()) {
				if (triggerKeys.contains(trigger.getDescription())) {
					LOG.debug(" - matched existing trigger " + trigger.getDescription());
					matchedTriggerKeys.add(trigger.getDescription());
				} else {
					LOG.debug(" - new trigger " + trigger.getDescription());
					try {
						schedule(trigger);
					} catch (SchedulerException e) {
						LOG.error("failed to schedule new trigger " + trigger.getDescription() + " for " + this, e);
					}
				}
			}
			triggerKeys.removeAll(matchedTriggerKeys);
			for (String triggerName : triggerKeys) {
				LOG.debug(" - deleting trigger " + triggerName);
				try {
					unschedule(triggerName);
				} catch (SchedulerException e) {
					LOG.error("failed to unschedule trigger for " + this, e);
				}
			}
		} catch (SchedulerException e) {
			LOG.error("failed to update schedule for " + this, e);
		}
	}
	
	private List<Trigger> configuredTriggers() {
		List<Trigger> triggers = new ArrayList<Trigger>();
		for (Node child : def.query().unordered("*").nodes()) {
			try {
				Class<? extends TriggerMaker> childClass = config.resolveTagClass(child.qname(), TriggerMaker.class);
				Trigger trigger = childClass.newInstance().create(child);
				trigger.setDescription(child.toString());
				triggers.add(trigger);
			} catch (Exception e) {
				LOG.error("unable to instantiate potential trigger for job '" + name() + "'", e);
			}
		}
		return Collections.unmodifiableList(triggers);
	}
	
	void schedule(Trigger trigger) throws SchedulerException {
		trigger.setJobName(name());
		trigger.setJobGroup(null);
		config.schedule().ensureJob(this);
		config.schedule().addTrigger(trigger);
	}
	
	private void unschedule(String triggerName) throws SchedulerException {
		config.schedule().removeTrigger(triggerName);
	}
	
	void unschedule() {
		LOG.debug("unscheduling " + this);
		try {
			config.schedule().removeJob(this);
		} catch (SchedulerException e) {
			LOG.error("failed to unschedule " + this, e);
		}
	}
	
	Date checkMinRunInterval(Date runTimestamp) {
		Duration minInterval = def.query().optional("@min-run-interval").durationValue();
		if (minInterval == null) return null;
		
		// if previous run aborted due to min-interval violation, there will be no committed
		// notes in abortspace, so we'll use the prevspace end run date and avoid an
		// infinite loop
		Date prevRunEnd = root.children().create(ABORTSPACE_FOLDER_NAME).query().optional("/notes/@ended").instantValue();
		if (prevRunEnd == null) prevRunEnd = root.children().create(PREVSPACE_FOLDER_NAME).query().optional("/notes/@ended").instantValue();
		if (prevRunEnd == null) return null;
		
		Duration sinceLastRun = DataUtils.datatypeFactory().newDuration(runTimestamp.getTime() - prevRunEnd.getTime());
		if (sinceLastRun.isShorterThan(minInterval)) {
			// postpone until min interval would be satisfied
			minInterval.addTo(prevRunEnd);
			LOG.info("postponing job due to min run interval violation (" + sinceLastRun + " < " + minInterval + ")");
			return prevRunEnd;
		}
		return null;
	}

	public List<String> phases() throws InvalidConfigurationException {
		if (phases == null) {
			String phasesString = def.query().optional("@phases").value();
			if (phasesString == null) phasesString = config.findTagDef(def.qname()).query().optional("default[@type=$_1]/@phases", type).value();
			if (phasesString == null) throw new InvalidConfigurationException("job phases not specified explicitly or implicitly");
			phases = Collections.unmodifiableList(phasesToList(phasesString));
		}
		return phases;
	}
	
	List<Cycle.TaskWrapper> createTaskWrappers(Cycle jobRun) throws InvalidConfigurationException {
		boolean failed = false;
		List<Cycle.TaskWrapper> tasks = new ArrayList<Cycle.TaskWrapper>();
		for (Node taskDef : def.query().all("child::*").nodes()) {
			try {
				tasks.add(jobRun.createTaskWrapper(taskDef));
			} catch (Exception e) {
				LOG.error("[task " + taskDef.name() + "] task configuration error", e);
				failed = true;
			}
		}
		if (failed) throw new InvalidConfigurationException("failed to instantiate all job tasks");
		tasks = Collections.unmodifiableList(tasks);
		return tasks;
	}

	protected static List<String> phasesToList(String phasesString) {
		return phasesString == null ? null : Arrays.asList(phasesString.split(" +"));
	}


}
