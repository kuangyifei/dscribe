package com.ideanest.dscribe.java;

import javax.xml.namespace.QName;

import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;
import com.ideanest.dscribe.mixt.Transformer;

public class DiagramExtractor extends TaskBase {

	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"java", Namespace.JAVA,
			"uml", Namespace.UML,
			"rules", Transformer.RULES_NS,
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
		// TODO: implement
		if (!cleanRun) copyRules();
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
	
}
