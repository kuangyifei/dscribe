package com.ideanest.dscribe.job;

import java.io.File;
import java.text.MessageFormat;

import org.apache.log4j.Logger;
import org.exist.fluent.NamespaceMap;
import org.exist.fluent.Node;

import com.ideanest.dscribe.Namespace;

/**
 * Wipe the contents of a directory prior to a build.  Target the work directory by default.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class WipeDir extends TaskBase {

	private static final Logger LOG = Logger.getLogger(WipeDir.class);
	
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"notes", Namespace.NOTES,
			"vcm", Namespace.VCM
	);
	
	private String condition;
	private File targetDir;
	private int numFailed;

	@Override
	protected void init(Node taskDef) throws Exception {
		condition = taskDef.query().single("@if").value();
		targetDir = cycle().workdir();
		String subDir = taskDef.query().optional("@dir").value();
		if (subDir != null) {
			if (subDir.equals("$temp")) targetDir = cycle().tempdir();
			else targetDir = new File(targetDir, subDir);
		}
	}

	@Phase
	public void update() {
		Node notes = cycle().workspace(NAMESPACE_MAPPINGS).query().single("/notes:notes").node();
		if (notes.query()
				.let("notes", notes)
				.let("versions", cycle().versionspace(NAMESPACE_MAPPINGS))
				.single(condition).booleanValue()) {
			deleteContents(targetDir);
			LOG.info(new MessageFormat(
					"wiped ''{0}''{1,choice,0#|1#, failed to delete 1 file|2#, failed to delete {1,number,integer} files}")
					.format(new Object[] {targetDir.toString(), new Integer(numFailed)}));
		}
	}
	
	private void deleteContents(File dir) {
		File[] files = dir.listFiles();
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			if (file.isDirectory()) deleteContents(file);
			if (!file.delete()) {
				LOG.debug("failed to wipe file '" + file + "'");
				numFailed++;
			}
		}
	}
}
