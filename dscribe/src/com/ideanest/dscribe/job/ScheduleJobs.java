package com.ideanest.dscribe.job;

import java.util.*;

import org.exist.fluent.*;
import org.quartz.SchedulerException;

import com.ideanest.dscribe.NameDispenser;
import com.ideanest.dscribe.Namespace;

public class ScheduleJobs extends TaskBase {
	
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Namespace.CONFIG
	);
	
	private Folder workspace, prevspace;

	@Override
	protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
	}

	@Phase
	public void schedule() {
		NameDispenser.resolveDuplicateNames(workspace, "//job", "name");
		NameDispenser.resolveMissingNames(workspace, "//job", "name");
		
		Set<String>
			currentJobNames = setOfValues(workspace.query().unordered("//job/@name")),
			previousJobNames = setOfValues(prevspace.query().unordered("//job/@name"));
		
		reconcileMatchingJobSchedules(currentJobNames, previousJobNames);
		unscheduleDeletedJobs(currentJobNames, previousJobNames);
		scheduleNewJobs(currentJobNames, previousJobNames);
		
	}

	private void scheduleNewJobs(Set<String> currentJobNames, Set<String> previousJobNames) {
		Set<String> addedJobNames = new TreeSet<String>(currentJobNames);
		addedJobNames.removeAll(previousJobNames);
		// don't pause the scheduler unnecessarily if no new jobs added
		if (addedJobNames.isEmpty()) return;
		try {
			cycle().switchConfig(workspace);
		} catch (SchedulerException e) {
			throw new RuntimeException("failed to pause scheduler, unable to schedule new jobs");
		}
		for (String name : addedJobNames) cycle().referenceJob(name).schedule();
	}

	private void unscheduleDeletedJobs(Set<String> currentJobNames, Set<String> previousJobNames) {
		Set<String> deletedJobNames = new TreeSet<String>(previousJobNames);
		deletedJobNames.removeAll(currentJobNames);
		for (String name : deletedJobNames) cycle().referenceJob(name).unschedule();
	}

	private void reconcileMatchingJobSchedules(Set<String> currentJobNames, Set<String> previousJobNames) {
		Set<String> matchedJobNames = new TreeSet<String>(currentJobNames);
		matchedJobNames.retainAll(previousJobNames);
		for (String name : matchedJobNames) cycle().referenceJob(name).schedule();
	}
	
	private Set<String> setOfValues(ItemList itemList) {
		return Collections.unmodifiableSet(new TreeSet<String>(Arrays.asList(itemList.values().toArray())));
	}
}
