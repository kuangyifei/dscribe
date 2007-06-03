package com.ideanest.dscribe.java;

import java.io.*;

import javax.xml.namespace.QName;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;
import com.ideanest.dscribe.opti.AnnealingDiagramAssigner;

public class DiagramExtractor extends TaskBase {

	private static final Logger LOG = Logger.getLogger(DiagramExtractor.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"java", Namespace.JAVA,
			"uml", Namespace.UML,
			"rules", Namespace.RULES,
			"vcm", Namespace.VCM,
			"reef", Namespace.NOTES
	);
	
	static final QName
		JAVA_PACKAGE = new QName(Namespace.JAVA, "package"),
		JAVA_CLASS = new QName(Namespace.JAVA, "class"),
		JAVA_INTERFACE = new QName(Namespace.JAVA, "interface");
	
	private Folder workspace, prevspace;
	private boolean cleanRun;

	@Override
	protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
		cleanRun = workspace.query().flag("/reef:notes/@cleanrun", false);
	}
	
	@Phase
	public void preprules() {
		LOG.debug("copying rules and diagrams from prevspace");
		copyRules();
		if (!cleanRun) cleanRun = !copyAndPruneDiagrams();
	}
	
	private void copyRules() {
		// TODO: copy over default rulesets, but remember to support evolution!
		ItemList rulesets = prevspace.query().unordered("/rules:ruleset");
		cycle().inherit(rulesets.nodes().documents());
		// fragment below is temporary until default ruleset handling is in place
		if (rulesets.size() == 0) {
			workspace.documents().build(Name.adjust("global-rules"))
				.elem("rules:ruleset").attr("stage", "global").end("rules:ruleset").commit();
			workspace.documents().build(Name.adjust("local-rules"))
				.elem("rules:ruleset").attr("stage", "local").end("rules:ruleset").commit();
		}
	}
	
	private void snapshotDiagrams() {
		workspace.query().unordered("/uml:diagram//rules:mod").deleteAllNodes();
		for (Node diagram : workspace.query().unordered("/uml:diagram").nodes()) {
			diagram.query().optional("rules:snapshot").node().delete();
			diagram.append().elem("rules:snapshot").node(diagram).end("rules:snapshot").commit();
		}
	}

	private boolean copyAndPruneDiagrams() {
		cycle().inherit(prevspace.query().unordered("/uml:diagram").nodes().documents());
		for (String idToPrune : workspace.query().unordered("//java:change[@action='edit' or @action='delete']/@idref").values()) {
			deleteDerivations(idToPrune);
		}
		return true;
	}

	private void deleteDerivations(String idToPrune) {
		for (Node umlToPrune : workspace.query().unordered("//rules:mod[(@action='create' or @action='modify') and rules:derived/@from=$_1]/ancestor::uml:*[@xml:id]", idToPrune).nodes()) {
			deleteDerivations(umlToPrune.query().single("@xml:id").value());
			umlToPrune.query().unordered("*[not(@xml:id)]").deleteAllNodes();
		}
	}

	
}
