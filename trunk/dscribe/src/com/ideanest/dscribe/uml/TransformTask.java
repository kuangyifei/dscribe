package com.ideanest.dscribe.uml;

import java.io.*;
import java.text.*;

import org.apache.log4j.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;
import com.ideanest.dscribe.mixt.*;

public class TransformTask extends TaskBase {
	
	private static final Logger LOG = Logger.getLogger(TransformTask.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Engine.MIXT_NS,
			"mod", Engine.MOD_NS,
			"notes", Namespace.NOTES
	);
	
	private Folder workspace, prevspace, rulespace;
	private File rules;
	private boolean singleFile;
	private DirectoryScanner scanner;

	@Override
	protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
		
		String dstPath = taskDef.query().optional("@folder").value();
		rulespace = dstPath == null || dstPath.length() == 0 ? workspace : workspace.children().create(dstPath);
		
		rules = cycle().resolveOptionalFile(taskDef.query().optional("@rules").value());
		singleFile = !rules.isDirectory();
		if (!singleFile) {
			scanner = new DirectoryScanner();
			scanner.setBasedir(rules);
			scanner.setIncludes(new String[] {"*.mxc", "*.mxt"});
		}
	}
	
	@Phase
	public void transform() throws RuleBaseException, TransformException {
		Engine engine = initEngine(initModStore());
		try {
			engine.executeTransform(cycle().uninheritedWorkspaceDocuments());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			cycle().checkInterrupt();
		}

		// TODO: merge precedence data back into global configuration
		
		LOG.info(new MessageFormat(
				"completed transformation after {0,choice,1#one iteration|1<{0,number,integer} iterations}")
				.format(new Object[] {engine.stats().numCycles.value()}));
	}
	
	private void loadRules() throws FileNotFoundException, IOException, ParseException {
		if (singleFile) {
			loadRulesFile(rules, rulespace);
		} else {
			scanner.scan();
			for (String relativeName : scanner.getIncludedFiles()) {
				Folder target = rulespace;
				String path = new File(relativeName).getParent();
				if (path != null) target = rulespace.children().create(path.replace(File.separatorChar, '/'));
				loadRulesFile(new File(rules, relativeName), target);
			}
		}
	}
	
	private void loadRulesFile(File rulesFile, Folder target) throws FileNotFoundException, IOException, ParseException {
		Source.XML source = rulesFile.getName().endsWith(".mxc") ?
			CompactFormTranslator.compactToXml(new FileReader(rulesFile)) :
			Source.xml(rulesFile);
		target.documents().load(Name.adjust(rulesFile.getName()), source);
	}
	
	/**
	 * Copy the modstore over from prevspace if available, otherwise create a new one.
	 * @return the modstore root node
	 */
	private Node initModStore() {
		// There should be no modstores in the workspace, but to avoid confusion delete
		// any that are found.
		for (Document doc : workspace.query().unordered("/mod:modstore").nodes().documents()) {
			LOG.error("unexpected modstore found in workspace: " + doc);
			doc.delete();
		}
		
		// Copy or create.
		Node modStore = prevspace.query().optional("/mod:modstore").node();
		if (modStore.extant()) {
			cycle().inherit(modStore.document());
			// Inherit any documents that were affected by the previous run of the transform.
			// TODO: if any of these were previously inherited, this will create a duplicate copy -- fix or ignore?
			cycle().inherit(prevspace.query().unordered("/id($_1//mod:affected/@refid)", modStore).nodes().documents());
		} else {
			modStore = workspace.documents().load(
					Name.adjust("mods"), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
		}
		
		// Final namespace binding adjustments.
		modStore.namespaceBindings().sever();
		modStore.namespaceBindings().clear();
		modStore.namespaceBindings().put("", Engine.MOD_NS);
		return modStore;
	}

	private Engine initEngine(Node modStore) throws RuleBaseException {
		try {
			loadRules();
			return new Engine(rulespace, prevspace, workspace, modStore);
		} catch (FileNotFoundException e) {
			LOG.error("error loading ruleset, reverting to last known good set from previous cycle", e);
		} catch (IOException e) {
			LOG.error("error loading ruleset, reverting to last known good set from previous cycle", e);
		} catch (ParseException e) {
			LOG.error("error parsing compact ruleset, reverting to last known good set from previous cycle", e);
		} catch (RuleBaseException e) {
			LOG.error("error in the ruleset, reverting to last known good set from previous cycle", e);
		}
		
		revertRules();
		try {
			// Use workspace as rulespace here, since can't be sure where the reverted rules ended up.
			return new Engine(workspace, prevspace, workspace, modStore);
		} catch (RuleBaseException e) {
			// running a clean cycle wouldn't help, since an error in the current ruleset is what got us here in the first place
			throw new RuleBaseException("error in ruleset from previous cycle, no usable ruleset available, aborting", e);
		}
	}

	private void revertRules() throws RuleBaseException {
		for (XMLDocument doc : workspace.query().unordered("//rules").nodes().documents()) {
			doc.delete();
		}
		ItemList prevRules = prevspace.query().unordered("//rules");
		if (prevRules.isEmpty()) throw new RuleBaseException("no rules found in previous cycle, no usable ruleset available, aborting");
		for (XMLDocument doc : prevRules.nodes().documents()) {
			doc.copy(workspace.children().create(prevspace.relativePath(doc.folder().path())), Name.keepAdjust());
		}
	}
}
