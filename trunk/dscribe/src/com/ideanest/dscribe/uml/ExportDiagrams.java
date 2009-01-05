package com.ideanest.dscribe.uml;

import static org.junit.Assert.assertTrue;

import java.io.*;
import java.text.MessageFormat;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;
import com.ideanest.dscribe.mixt.Engine;

public class ExportDiagrams extends TaskBase {
	
	private static final Logger LOG = Logger.getLogger(ExportDiagrams.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"uml", Namespace.UML,
			"mixt", Engine.MIXT_NS,
			"mod", Engine.MOD_NS,
			"a", "http://ideanest.com/dscribe/ns/actions"
	);
	
	private Folder workspace;
	private File dstDir;
	private String template, rulesDocName;

	@Override protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		dstDir = cycle().resolveOptionalFile(taskDef.query().optional("@dst").value());
		template = readFile(cycle().resolveOptionalFile(taskDef.query().single("@template").value()));
		rulesDocName = taskDef.query().optional("@rules-doc").value();
	}
	
	@Phase public void export() throws IOException, ExportException {
		int exportCount = 0;
		for (Node diagram : workspace.query().unordered("//uml:diagram").nodes()) {
			File diagramFile = new File(dstDir, diagram.query().single("@diagram").value() + ".lzx");
			diagramFile.getParentFile().mkdirs();
			Writer out = new FileWriter(diagramFile);
			out.write(extractDiagram(diagram));
			out.close();
			exportCount++;
		}
		LOG.info(
				new MessageFormat("exported {0,choice,0#0 diagrams|1#1 diagram|1<{0,number,integer} diagrams}")
				.format(new Object[] {exportCount}));
	}
	
	private String extractDiagram(Node diagram) throws IOException, ExportException {
		Node rules = findRules();
		return template
			.replace("$(highSerial)", workspace.query().single("max(/a:triggers[@kind='stored']/*/@serial)").value())
			.replace("$(actionIdBase)", cycle().generateUid("a") + "-")
			.replace("$(diagram)", collapseNamespaces(diagram).toString())
			.replace("$(rules)", collapseNamespaces(rules).toString())
			.replace("$(modstore)", collapseNamespaces(filterMods(diagram, rules)).toString())
			.replace("$(actions)",
					collapseNamespaces(filterActions(diagram, workspace.query().single("/a:candidates[@kind='derived'").node())).toString()
					+ "\n"
					+ collapseNamespaces(filterActions(diagram, workspace.query().single("/a:triggers[@kind='derived']").node())).toString()
					+ "\n"
					+ collapseNamespaces(filterActions(diagram, workspace.query().single("/a:triggers[@kind='stored']").node())).toString());
	}
	
	private Node filterActions(Node diagram, Node actions) {
		return workspace.query().let("$actions", actions).let("$diagram", diagram).single(
				"let $diagramId := $diagram/@xml:id " +
				"return element {node-name($actions)} {" +
				"	$actions/@*," +
				"	$actions/*[@diagram = $diagramId or @scope = 'all' or @target = $diagramId]" +
				"}"
		).node();
	}
	
	private Node filterMods(Node diagram, Node rules) {
		return workspace.query().let("$rules", rules).let("$diagram", diagram).single(
				"declare function local:filter($node, $ancestorMods, $affectingMods) {" +
				"	if (not($node/self::mod:mod) or exists($node intersect $affectingMods)) then $node" +
				"	else if (exists($node intersect $ancestorMods)) then" +
				"		element {node-name($node)} {" +
				"			$node/@*," +
				"			for $child in mod:mod return local:filter($child, $ancestorMods, $affectingMods)" +
				"		}" +
				"	else ()" +
				"};" +
				"element mod:modstore {" +
				"	for $mods in //mod:mods[@rule=$rules/mixt:rule/@xml:id]" +
				"	let $affectingMods := $mods//mod:mod[exists($diagram/id(mod:affected/@refid))]," +
				"			$ancestorMods := $affectingMods/ancestor::*" +
				"	return element mod:mods {" +
				"		$mods/@*," +
				"		for $mod in $mods/* return local:filter($mod, $ancestorMods, $affectingMods)" +
				"	}" +
				"}"
		).node();
	}
	
	private Node findRules() throws ExportException {
		Node matchingRules = null;
		for (Node rules : workspace.query().unordered("//mixt:rules").nodes()) {
			if (rules.document().name().contains(rulesDocName)) {
				if (matchingRules != null) {
					throw new ExportException(
							"multiple documents found matching rules doc name '" + rulesDocName + "': "
							+ matchingRules.document().name() + " and " + rules.document().name());
				}
				matchingRules = rules;
			}
		}
		if (matchingRules == null) throw new ExportException("no document found matching rules doc name '" + rulesDocName + "'");
		return matchingRules;
	}
	
	private Node collapseNamespaces(Node node) {
		return node.query().single(
				"declare default element namespace '';" +
				"declare function local:collapse($item) {" +
				"	typeswitch ($item)" +
				"		case element() return local:collapse-element($item)" +
				"		case attribute() return local:collapse-attribute($item)" +
				"		default return $item" +
				"};" +
				"declare function local:collapse-element($e) {" +
				"	element {translate(name($e), ':-.', '___')} {" +
				"		(for $a in $e/attribute::* return local:collapse-attribute($a)," +
				"		 for $x in $e/node() return local:collapse($x))" +
				"	}" +
				"};" +
				"declare function local:collapse-attribute($a) {" +
				"	attribute {translate(name($a), ':-.', '___')} {string($a)}" +
				"};" +
				"local:collapse(.)"
		).node();
	}
	
	private static String readFile(File file) throws IOException {
		// Warning:  only works for files <2Gb in length.
		byte[] bytes = new byte[(int) file.length()];
		InputStream stream = new FileInputStream(file);
		int numRead = stream.read(bytes);
		if (numRead != bytes.length) throw new IOError(new RuntimeException("couldn't read whole file: " + file));
		stream.close();
		return new String(bytes, "UTF-8");
	}
	
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static class _Test extends DatabaseTestCase {
		private final ExportDiagrams export = new ExportDiagrams();
		
		@Test public void collapseNamespaces() {
			String xmlWithNamespaces =
				"<uml:diagram xmlns:uml='http://example.com/diagram' diagram='foo:bar' xml:id='xxx'>" +
				"	<mixt:rule xmlns:mixt='http://example.com/mixt' xmlns:unused='http://example.com'>" +
				"		<mixt:for each='$a'>foo bar</mixt:for>" +
				"	</mixt:rule>" +
				"</uml:diagram>";
			String xmlWithoutNamespaces =
				"<uml_diagram diagram='foo:bar' xml_id='xxx'>" +
				"	<mixt_rule>" +
				"		<mixt_for each='$a'>foo bar</mixt_for>" +
				"	</mixt_rule>" +
				"</uml_diagram>";
			Node nodeWithNamespaces = db.query().single(xmlWithNamespaces).node();
			Node nodeWithoutNamespaces = db.query().single(xmlWithoutNamespaces).node();
			Node nodeCollapsed = export.collapseNamespaces(nodeWithNamespaces);
			assertTrue(
					"collapsed result:\n" + nodeCollapsed,
					db.query().single("deep-equal($_1, $_2)", nodeWithoutNamespaces, nodeCollapsed).booleanValue());
		}
	}

}
