package com.ideanest.dscribe.java;

import java.text.MessageFormat;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;

/**
 * Analyzes the origin of program elements and assigns unique identifiers
 * that may overlap the identifiers used by elements in previous versions
 * of the code.  Also produces a list of element modifications (add, edit,
 * delete).
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class SimpleOriginAnalyzer extends TaskBase {
	
	private static final Logger LOG = Logger.getLogger(SimpleOriginAnalyzer.class);
	
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Namespace.JAVA,
			"java", Namespace.JAVA,
			"vcm", Namespace.VCM,
			"notes", Namespace.NOTES
	);

	private Folder workspace, prevspace;
	private Node changes;

	@Override
	protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
		changes = workspace.documents().build(Name.adjust("origin-analysis-record"))
			.elem("record").end("record").commit().root();
	}

	@Phase
	public void elaborate() {
		LOG.debug("analyzing origins and assigning uids");
		if (prevspace.query().exists("//java:*[@xml:id]")) {
			matchUids(
					"//package[not(@xml:id)]",
					"//package[./text()=$it/text()]/@xml:id");
			matchUids(
					"//(class|interface)[not(@xml:id)]",
					"//(class|interface)[@implName=$it/@implName]/@xml:id");
			matchUids(
					"//field[not(@xml:id)]",
					"//java:*[@xml:id=$it/../@xml:id]/field[@name=$it/@name]/@xml:id");
			matchUids(
					"//constructor[not(@xml:id)]",
					"//java:*[@xml:id=$it/../@xml:id]/constructor[deep-equal(param/type, $it/param/type)]/@xml:id");
			matchUids(
					"//method[not(@xml:id)]",
					"//java:*[@xml:id=$it/../@xml:id]/method[@name=$it/@name][deep-equal(param/type, $it/param/type)]/@xml:id");
			for (String oldUid : prevspace.query().unordered("//java:*/@xml:id[not(.=$_1//java:*/@xml:id)]", workspace).values()) {
				changes.append().elem("change").attr("uidref", oldUid).attr("action", "delete").end("change").commit();
			}
		} else {
			for (Node node : workspace.query().unordered("//(package|class|interface|annotationinterface|field|constructor|method)").nodes()) {
				setUid(node, null);
			}
		}
		LOG.info(new MessageFormat("origin analysis complete" +
				"{0,choice,0#|1#, 1 element matched|1<, {0,number,integer} elements matched}" +
				"{1,choice,0#|1#, 1 new element added|1<, {1,number,integer} new elements added}" +
				"{2,choice,0#|1#, 1 element deleted|1<, {2,number,integer} elements deleted}")
				.format(new Object[] {
						changes.query().single("count(//change[@action='edit'])").intValue(),
						changes.query().single("count(//change[@action='add'])").intValue(),
						changes.query().single("count(//change[@action='delete'])").intValue(),
						}));
	}
	
	private void matchUids(String selectQuery, String matchQuery) {
		for (Node element : workspace.query().unordered(selectQuery).nodes()) {
			String uid = null;
			try {
				// TODO: expand search over all previously known elements, not just prevspace?
				uid = prevspace.query().let("it", element).optional(matchQuery).value();
			} catch (DatabaseException e) {
				LOG.warn("multiple prev uid matches for element " + element);
			}
			setUid(element, uid);
		}
	}
	
	private void setUid(Node element, String uid) {
		if (uid == null) {
			uid = cycle().generateUid("j");
			changes.append().elem("change").attr("idref", uid).attr("action", "add").end("change").commit();
		} else {
			changes.append().elem("change").attr("idref", uid).attr("action", "edit").end("change").commit();
		}
		element.update().attr("xml:id", uid).commit();
	}
	
}
