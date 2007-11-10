package com.ideanest.dscribe.job;

import java.io.File;
import java.text.DateFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.quartz.*;
import org.quartz.Trigger;

import com.ideanest.dscribe.*;

/**
 * A run of a job that's executed in phases.  The phases are named, and can be either
 * listed explicitly for the job or implicitly for the general job type.  They are executed
 * sequentially.  Within each phase, tasks are executed in order of declaration within
 * the job.  Each task can vote to abandon or postpone the job.  At the end of each phase,
 * if all tasks have voted to abandon the job, it is stopped and its workspace deleted.
 * (A job should be abandoned when tasks figure out there's "nothing to do".)  If, on the
 * other hand, one or more tasks during a phase voted to postpone, the job is rescheduled
 * to run at the latest postponement date, unless this date is before the next time the
 * job would fire anyway, in which case it's just abandoned.  If the job is neither abandoned
 * nor postponed during a phase, it goes on to the next phase.  If all phases complete,
 * the job's workspace is committed to the store.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class Cycle {
	
	private final Job job;
	private Configuration config;
	private final boolean cleanRun;
	private Folder store, workspace, prevspace;
	private Node notes;
	private File workdir, tempdir;
	private List<String> phases;
	private List<Step> steps;
	private Date timestamp;
	private long serial, nextUid = 1;
	
	private boolean schedulePaused;
	private boolean running, interrupted, failed;
	private Thread myThread;
	private Step currentStep;
	private String currentPhase;
	private boolean taskVotedToAbandon, keepGoing;
	private Date postponedDate;
	private Collection<Task> tasksToDispose = new LinkedList<Task>();
	
	private Document.Listener inheritedDocumentListener = new Document.Listener() {
		public void handle(Document.Event ev) {
			final String relativePath = workspace.relativePath(ev.path);
			notes.query().optional("//file[@localName=$_1]", relativePath).node().delete();
			ev.document.listeners().remove(inheritedDocumentListener);
			LOG.debug("inherited file '" + relativePath + "' being modified, broke link to store");
		}
	};
	
	private interface Step {
		String name();
		void execute() throws Exception;
	}
	
	
	private static class AbandonJobException extends RuntimeException {
		// nothing special about it
	}

	private static final Logger LOG = Logger.getLogger(Cycle.class);


	class TaskWrapper {
		
		private final Task task;
		private final Node def;
		
		TaskWrapper(Node def, Task task) {
			this.def = def;
			this.task = task;
		}

		Set<String> resolvePhases() throws InvalidConfigurationException {
			Set<String> taskSupportedPhases = phasesToSet(task.getSupportedPhases());
			Set<String> taskPhases;
			
			String taskPhasesString = def.query().namespace("config", Namespace.CONFIG).optional("@config:phases").value();
			
			if (taskPhasesString != null) {
				taskPhases = phasesToSet(taskPhasesString);
				
				Set<String> badPhases = new TreeSet<String>(taskPhases);
				badPhases.removeAll(phases);
				if (!badPhases.isEmpty()) {
					taskPhases.removeAll(badPhases);
					LOG.warn("task " + def.name() + " specified phases " + badPhases + " not present in job phase plan " + phases);
				}
				
				if (taskSupportedPhases != null) {
					badPhases = new TreeSet<String>(taskPhases);
					badPhases.removeAll(taskSupportedPhases);
					if (!badPhases.isEmpty()) {
						taskPhases.removeAll(badPhases);
						LOG.warn("task " + def.name() + " specified phases " + badPhases + " not supported by task spec " + taskSupportedPhases);
					}
				}
				
			} else if (taskSupportedPhases != null) {
				taskPhases = new TreeSet<String>(taskSupportedPhases);
				taskPhases.retainAll(phases);
				if (taskPhases.isEmpty()) LOG.warn("task " + def.name() + ": default phases " + taskSupportedPhases + " do not intersect with job's phases " + phases);

			} else {
				taskPhases = new TreeSet<String>(phases);
			}

			if (taskPhases.isEmpty()) LOG.warn("task " + def.name() + " has no phases left to execute after pruning");

			return taskPhases;
		}

		Step createInitStep() {
			return new Step() {
				public String name() {return "init task '" + task.getName() + "'";}
				public void execute() throws Exception {
					task.init(Cycle.this, def);
					tasksToDispose.add(task);
				}
			};
		}
		
		Step createPhaseStep(final String phase) {
			return new Step() {
				public String name() {return "execute task '" + task.getName() + "', phase '" + phase + "'";}
				public void execute() throws Exception {
					taskVotedToAbandon = false;
					task.execute(phase);
					keepGoing |= !taskVotedToAbandon;
				}
			};
		}
		
		Step createDisposeStep() {
			return new Step() {
				public String name() {return "dispose task " + task.getName();}
				public void execute() throws Exception {
					try {
						task.dispose();
					} catch (Exception e) {
						LOG.warn("[task " + task.getName() + "] failed to dispose task", e);
					}
					tasksToDispose.remove(task);
				}
			};
		}
	}

	TaskWrapper createTaskWrapper(Node taskDef) throws ClassNotFoundException, InstantiationException, IllegalAccessException, MappingNotFoundException {
		Class<? extends Task> taskClass = config.resolveTagClass(taskDef.qname(), Task.class);
		return taskClass == null ? null : new TaskWrapper(taskDef, taskClass.newInstance());
	}
	
	
	public String name() {
		return job.name();
	}
	
	public File workdir() {
		return workdir;
	}
	
	public File resolveOptionalFile(String dir) {
		if (dir == null) return workdir();
		File f = new File(dir);
		if (!f.isAbsolute()) f = new File(workdir(), dir);
		return f;
	}
	
	public File tempdir() {
		return tempdir;
	}
	
	public Folder workspace(NamespaceMap namespaceMappings) {
		return dittoSpace(workspace, namespaceMappings);
	}
	
	public Folder prevspace(NamespaceMap namespaceMappings) {
		return dittoSpace(prevspace, namespaceMappings);
	}
	
	public Folder versionspace(NamespaceMap namespaceMappings) {
		return dittoSpace(job.versionSpace(), namespaceMappings);
	}
	
	private Folder dittoSpace(Folder space, NamespaceMap namespaceMappings) {
		if (space == null) throw new IllegalStateException("space not available at this time");
		space = space.clone();
		space.namespaceBindings().clear();
		if (namespaceMappings != null) space.namespaceBindings().putAll(namespaceMappings);
		return space;
	}

	/**
	 * Used only for mocking in unit tests.
	 */
	@Deprecated Cycle() {
		this(null, null, null, false);
	}
	
	Cycle(Job job, Configuration config, Date timestamp, boolean cleanRun) {
		this.job = job;
		this.config = config;
		this.timestamp = timestamp == null ? new Date() : (Date) timestamp.clone();
		this.cleanRun = cleanRun;
	}
	
	public void execute() throws JobExecutionException {
		synchronized(this) {
			if (running) throw new IllegalStateException("job already running");
			running = true;
			myThread = Thread.currentThread();
		}
		
		String oldThreadName = myThread.getName();

		try {
			
			job.setCurrentThreadName();
			// TODO: append log messages to notes
			
			LOG.info("started");

			try {
				// to abandon a job during prepartion, you must be careful since not all
				// structures are set up, and some of the task control mechanisms are
				// not in place
				prepare();
				
				perform();
				
			} catch (AbandonJobException e) {
				abortRun();
			}

		} catch (JobExecutionException e) {
			throw e;
			
		} catch (Exception e) {
			String msg = currentStep == null ? "job run error outside of any step" : "error performing step '" + currentStep.name() + "'";
			LOG.error(msg, e);
			failed = true;
			postponedDate = null;
			try {
				abortRun();
			} catch (Exception e2) {
				// don't propagate this exception, since the outer one is more important
				LOG.error("failed to abort run after exception", e2);
			}
			throw new JobExecutionException(msg, e, false);
			
		} finally {
			// clean up listener, though it's just weakly referenced so it would disappear by itself eventually
			Database.remove(inheritedDocumentListener);
			commitNotes();
			
			synchronized(this) {
				Thread.interrupted();	// clear interruption flag
				running = false;
				notifyAll();
			}
			Database.flush();
			myThread.setName(oldThreadName);
			
			if (schedulePaused) config.schedule().resume();
		}
	}

	public synchronized void interrupt() throws UnableToInterruptJobException {
		if (!running) return;
		interrupted = true;
		if (myThread == null) return;		// not even started yet!
		myThread.interrupt();
		if (myThread == Thread.currentThread()) throw new AbandonJobException(); 
		try {
			wait(5000L);
		} catch (InterruptedException e) {
			// redundant
		}
		if (running) throw new UnableToInterruptJobException("job not responding to interrupt request");
	}

	private void initializeWorkspace() {
		workdir = job.workdir();
		if (cleanRun) deleteContents(workdir);
		tempdir = config.tempdir();
		store = job.storeSpace();
		
		workspace = job.createWorkspace();
		
		prevspace = cleanRun ? job.emptySpace() : job.stableSpace();
		String prevSerial = prevspace.query().optional("/reef:notes/@serial").value();
		
		serial = job.versionSpace().query().optional("max(/reef:notes/@serial)").longValue() + 1;
		notes = workspace.documents().build(Name.create("notes"))
			.elem("reef:notes")
				.attr("job-name", job.name())
				.attr("serial", serial)
				.attrIf(prevSerial != null, "prev", prevSerial)
				.attr("started", timestamp)
			.end("reef:notes")
		 	.commit()
			.root();

		LOG.debug("initialized workspace for run #" + serial);
	}
	
	private void deleteContents(File dir) {
		if (!dir.isDirectory()) throw new IllegalArgumentException("not a directory: " + dir);
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) deleteContents(file);
			file.delete();
		}
	}

	private void commitStatus(String status) {
		assert status != null;
		if (notes == null) return;
		notes.update().attr("status", status).commit();
	}
	
	private void commitNotes() {
		if (notes == null) return;		// workspace didn't initialize, can't commit
		notes.update()
			.attr("ended", new Date())
			.attrIf(currentPhase != null, "phase", currentPhase)
			.attrIf(postponedDate != null, "until", postponedDate)
			.commit();
		notes.document().copy(
				job.versionSpace(),
				Name.create(Long.toString(serial))
		);
	}

	private void commitRun() {
		store(workspace, "");
		commitStatus("completed");
		job.commitWorkspace();
		workspace = null;
	}

	private void abortRun() throws SchedulerException {
		assert interrupted || failed || !keepGoing || postponedDate != null;
		
		disposeOpenTasks();
		
		String during = currentPhase == null ? "" : " during phase '" + currentPhase + "'";
		if (interrupted) {
			commitStatus("interrupted");
			LOG.info("interrupted" + during);
		} else if (failed) {
			commitStatus("failed");
			LOG.info("failed" + during);
		} else {
			String after = currentPhase == null ? "" : " after phase '" + currentPhase + "'";
			if (!keepGoing) {
				commitStatus("abandoned");
				LOG.info("abandoned" + after);
			} else {
				assert postponedDate != null;
				boolean needEarlyTrigger = checkNeedEarlyTrigger();
				if (needEarlyTrigger) job.schedule(new SimpleTrigger("n/a", "postponed", postponedDate));
				commitStatus("postponed");
				final String postponedDateString = DateFormat.getDateTimeInstance().format(postponedDate);
				if (needEarlyTrigger) {
					LOG.info("postponed" + after + " until " + postponedDateString);
				} else {
					LOG.info("postponed" + after + " until next regularly scheduled execution at " + postponedDateString);
				}
			}
		}
		
		if (workspace != null) job.abortWorkspace();
	}

	private boolean checkNeedEarlyTrigger() throws SchedulerException {
		boolean needTrigger = true;
		for (Trigger trigger : job.scheduledTriggers()) {
			Date nextFireTime = trigger.getNextFireTime();
			if (nextFireTime == null) continue;
			if (postponedDate.after(nextFireTime)) {
				postponedDate = (Date) nextFireTime.clone();
				needTrigger = false;
			}
		}
		return needTrigger;
	}

	private void disposeOpenTasks() {
		for (Task task : tasksToDispose) {
			try {
				task.dispose();
			} catch (Exception e) {
				LOG.warn("[task " + task.getName() + "] failed to dispose task", e);
			}
		}
		tasksToDispose.clear();
	}

	private void perform() throws Exception {
		LOG.debug("performing");
		
		for (Iterator<Step> it = steps.iterator(); it.hasNext(); ) {
			synchronized(this) {
				if (interrupted) throw new AbandonJobException();
				currentStep = it.next();
			}
			
			// if (notes != null) LOG.debug("notes at step " + currentStep.name() + "\n" + notes.toString());
			currentStep.execute();
			synchronized(this) {currentStep = null;}
			it.remove();		// remove the step to help free memory on long runs
		}
		
		assert tasksToDispose.isEmpty();
		LOG.info("completed");
	}

	private void store(Folder folder, String prefix) {
		for (Document localDoc : folder.documents()) {
			if (localDoc.equals(notes.document())) continue;		// don't store notes
			String localName = prefix + localDoc.name();
			if (notes.query().exists("//reef:file[@localName=$_1]", localName)) continue;
			Document storedDoc = localDoc.copy(store, Name.generate());
			notes.append()
				.elem("reef:file")
					.attr("localName", localName)
					.attr("storedName", storedDoc.name())
				.end("reef:file")
				.commit();
		}
		for (Folder child : folder.children()) {
			store(child, prefix + child.name() + "/");
		}
	}

	private void linearizeSteps(Map<String, List<Step>> phasedSteps) {		
		for (final String phase : phases) {
			List<Step> phaseSteps = phasedSteps.get(phase);
			if (phaseSteps.isEmpty()) continue;
			
			steps.add(new Step() {
				public String name() {return "start phase '" + phase + "'";}
				public void execute() throws Exception {
					LOG.debug("starting phase '" + phase + "'");
					keepGoing = false;
					currentPhase = phase;
					// notes().update().attr("currentPhase", phase).commit();
				}
			});
			
			steps.addAll(phaseSteps);
			
			steps.add(new Step() {
				public String name() {return "end phase '" + phase + "'";}
				public void execute() throws Exception {
					if (!keepGoing || postponedDate != null) throw new AbandonJobException();
					LOG.debug("completed phase '" + phase + "'");
					currentPhase = null;
					// notes().update().delAttr("currentPhase").commit();
				}
			});
		}
	}

	private void mapTaskSteps(final TaskWrapper taskWrapper, Map<String, List<Step>> phasedSteps) throws InvalidConfigurationException {
		Set<String> taskPhases = taskWrapper.resolvePhases();
		if (taskPhases.isEmpty()) return;

		boolean firstMatchingPhase = true;
		
		String lastPhase = null;
		for (final String phase : phases) {
			if (!taskPhases.contains(phase)) continue;
			
			lastPhase = phase;
			List<Step> stepList = phasedSteps.get(phase);
			
			if (firstMatchingPhase) {
				stepList.add(taskWrapper.createInitStep());
				firstMatchingPhase = false;
			}
			
			stepList.add(taskWrapper.createPhaseStep(phase));

		}
		
		assert lastPhase != null;
		phasedSteps.get(lastPhase).add(taskWrapper.createDisposeStep());
		
	}

	private void prepare() throws JobExecutionException {
		LOG.debug("preparing");
		
		try {
		
			// 1. check if job is running too often, if so postpone until later
			Date postponeUntil = job.checkMinRunInterval(timestamp);
			if (postponeUntil != null) {
				postpone(postponeUntil);
				throw new AbandonJobException();
			}
			
			// 2. prep phase step map
			phases = job.phases();
			Map<String, List<Step>> phasedSteps = new HashMap<String, List<Step>>();
			for (String phase : phases) phasedSteps.put(phase, new ArrayList<Step>());
			
			// 3. read tasks and fill phase step map
			for (TaskWrapper taskWrapper : job.createTaskWrappers(this)) {
				mapTaskSteps(taskWrapper, phasedSteps);
			}
			
			// 4. linearize phase step map and cap step list
			steps = new ArrayList<Step>();
			steps.add(new Step() {
				public String name() {return "initialize job run";}
				public void execute() throws Exception {
					initializeWorkspace();
				}
			});
			linearizeSteps(phasedSteps);
			steps.add(new Step() {
				public String name() {return "commit job run";}
				public void execute() throws Exception {
					commitRun();
				}
			});
			
		} catch (AbandonJobException e) {
			throw e;
		} catch (Exception e) {
			String msg = "job configuration error";
			LOG.error(msg, e);
			throw new JobExecutionException(msg, e, false);
		}
	}

	public void abandon() {
		taskVotedToAbandon = true;
	}

	/**
	 * Check if the work thread has been interrupted, and abandon the job if so.
	 * This method must only be called from the job thread, and only while the
	 * job is running.  It should be called regularly by long-running tasks, so that
	 * they can be interrupted gracefully.
	 *
	 * @throws AbandonJobException if the thread has been interrupted
	 */
	public synchronized void checkInterrupt() throws AbandonJobException {
		assert running;
		assert myThread == Thread.currentThread();
		if (myThread.isInterrupted()) {		// don't clear the flag until we're out
			interrupted = true;	// in case somebody interrupted the thread directly
			throw new AbandonJobException();
		}
	}

	public void postpone(Date futureDate) {
		if (futureDate.before(timestamp) || futureDate.equals(timestamp)) throw new IllegalArgumentException("postponed date " + futureDate + " precedes job run date " + timestamp);
		if (postponedDate == null || postponedDate.before(futureDate)) {
			postponedDate = (Date) futureDate.clone();
		}
	}
	
	public void makeNextCycleClean() {
		notes.append().elem("next-cycle-clean").end("next-cycle-clean").commit();
	}
	
	public Job referenceJob(String jobName) {
		return new Job(jobName, config);
	}
	
	void switchConfig(Folder newConfigFolder) throws SchedulerException {
		config.schedule().pause();
		schedulePaused = true;
		config = config.newFromFolder(newConfigFolder);
	}
	
	/**
	 * Inherit a document from the prevspace into the workspace.  Using this
	 * instead of a straight copy ensures only one copy is kept in the permanent
	 * store.  The copied document will be automatically forked if it's modified or
	 * removed.
	 * 
	 * @param <D> the type of document to inherit, inferred automatically
	 * @param doc the document to inherit, in prevspace
	 * @return the inherited document, in workspace
	 */
	public <D extends Document> D inherit(D doc) {
		ElementBuilder<Node> builder = notes.append();
		doc = inheritHelper(doc, builder);
		builder.commit();
		return doc;
	}
	
	/**
	 * Inherit all the documents in the given list just like {@link #inherit(XMLDocument)}, replacing
	 * each entry in the list with its inherited counterpart.
	 * 
	 * @param <D> the type of document to inherit, inferred automatically
	 * @param docs the documents to inherit; the list contents will be replaced with the inherited documents
	 */
	public <D extends Document> void inherit(List<D> docs) {
		if (docs.isEmpty()) return;
		ElementBuilder<Node> builder = notes.append();
		for (ListIterator<D> it = docs.listIterator(); it.hasNext(); ) {
			it.set(inheritHelper(it.next(), builder));
		}
		builder.commit();
	}
	
	public <D extends Document> void inherit(Collection<D> docs) {
		if (docs.isEmpty()) return;
		ElementBuilder<Node> builder = notes.append();
		for (D doc : docs) inheritHelper(doc, builder);
		builder.commit();
	}
	
	@SuppressWarnings("unchecked")
	private <D extends Document> D inheritHelper(D doc, ElementBuilder<Node> builder) {
		String prevRelativeName = prevspace.relativePath(doc.path());
		String prevRelativeCollectionName = prevspace.relativePath(doc.folder().path());
		Folder destination = workspace.children().create(prevRelativeCollectionName);
		D copy = (D) doc.copy(destination, Name.keepAdjust());
		copy.listeners().add(
				EnumSet.of(org.exist.fluent.Trigger.BEFORE_UPDATE, org.exist.fluent.Trigger.BEFORE_DELETE),
				inheritedDocumentListener);
		
		// try to link to existing stored file; be tolerant if missing, we'll just store it again with a new id
		String storedName = prevspace.query().optional("//reef:file[@localName=$_1]/@storedName", prevRelativeName).value();
		if (storedName != null) {
			builder
				.elem("reef:file")
					.attr("localName", prevRelativeCollectionName + "/" + copy.name())
					.attr("storedName", storedName)
				.end("reef:file");
		}
		
		return copy;
	}
	
	/**
	 * Return a collection of all the documents present in the workspace that were not
	 * inherited from the prevspace, and are therefore either new or modified.
	 *
	 * @return a collection of documents that were not inherited from prevspace
	 */
	public Collection<Document> uninheritedWorkspaceDocuments() {
		Collection<Document> modifiedDocs = new ArrayList<Document>();
		findUninheritedWorkspaceDocs(workspace, modifiedDocs);
		return modifiedDocs;
	}
	
	private void findUninheritedWorkspaceDocs(Folder folder, Collection<Document> modifiedDocs) {
		for (Document doc : folder.documents()) {
			// warning:
			// this relies on documents being marked as stored only at commit time (see #store),
			// and having the marker deleted when modified (see #inheritedDocumentListener)
			if (!notes.query().exists("//reef:file[@localName = $_1]", workspace.relativePath(doc.path()))) {
				modifiedDocs.add(doc);
			}
		}
		for (Folder child : folder.children()) findUninheritedWorkspaceDocs(child, modifiedDocs);
	}
	
	/**
	 * Return a collection of all the documents from prevspace that were not
	 * inherited by the workspace.
	 *
	 * @return a collection of documents that were not inherited from prevspace
	 */
	public Collection<Document> uninheritedPrevspaceDocuments() {
		Collection<Document> modifiedDocs = new ArrayList<Document>();
		findUninheritedPrevspaceDocs(prevspace, modifiedDocs);
		return modifiedDocs;
	}
	
	private void findUninheritedPrevspaceDocs(Folder folder, Collection<Document> modifiedDocs) {
		for (Document doc : folder.documents()) {
			if (!notes.query().let("$prevspace", prevspace).exists(
					"//reef:file[@storedName = $prevspace//reef:file[@localName = $_1]/@storedName]", prevspace.relativePath(doc.path()))) {
				modifiedDocs.add(doc);
			}
		}
		for (Folder child : folder.children()) findUninheritedPrevspaceDocs(child, modifiedDocs);
	}
	
	/**
	 * Generate a unique ID within the scope of the job.  The given series identifier
	 * will be used solely to help in figuring out what kind of element the UID identifies;
	 * it will not affect uniqueness and can be left blank.
	 *
	 * @param series an optional series for the UID (can be empty)
	 * @return a unique identifier that starts with the given prefix
	 */
	public String generateUid(String series) {
		// TODO: modify to make UID globally unique, need to assign unique IDs to jobs first
		if (series.length() == 0 || !Character.isLetter(series.charAt(0)) || series.charAt(0) == '_')
			throw new IllegalArgumentException("uid prefix '" + series + "' is not a valid way to start an xml:id");
		return series + serial + "-" + nextUid++;
	}

	protected static Set<String> phasesToSet(String phases) {
		return phases == null ? null : new TreeSet<String>(Job.phasesToList(phases));
	}


}
