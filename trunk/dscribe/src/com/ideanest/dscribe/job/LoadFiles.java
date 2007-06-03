package com.ideanest.dscribe.job;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.apache.log4j.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;

/**
 * Load files from a directory into a folder in the database, optionally wiping the folder first.
 * The source can also be a single file, in which case it'll be loaded directly into the destination
 * folder.  If the destination folder is not empty, files will overwrite documents with the same
 * path and name.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class LoadFiles extends TaskBase {
	
	private static final Logger LOG = Logger.getLogger(LoadFiles.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"notes", Namespace.NOTES,
			"vcm", Namespace.VCM,
			"load", "http://ideanest.com/reef/ns/LoadFiles"
	);
	
	private File src;
	private Folder dst, workspace, prevspace;
	private Node notes;
	private boolean singleFile;
	private DirectoryScanner scanner;
	private int numFilesLoaded, numFilesInherited, numFilesFailed;
	private String workdirPrefix;

	@Override
	protected void init(Node taskDef) throws Exception {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		notes = workspace.query().single("//notes:notes").node();
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
		workdirPrefix = cycle().workdir().getCanonicalPath() + File.separatorChar;

		src = cycle().resolveOptionalFile(taskDef.query().optional("@source").value());
		singleFile = !src.isDirectory();

		String dstPath = taskDef.query().optional("@folder").value();
		dst = dstPath == null || dstPath.length() == 0 ? workspace : workspace.children().create(dstPath);

		if (!singleFile) {
			scanner = new DirectoryScanner();
			scanner.setBasedir(src);
		}
	}
	
	@Phase
	public void load() {
		if (singleFile) {
			loadFile(src, dst);
		} else {
			scanner.scan();
			for (String relativeName : scanner.getIncludedFiles()) {
				Folder target = dst;
				String path = new File(relativeName).getParent();
				if (path != null) target = dst.children().create(path.replace(File.separatorChar, '/'));
				loadFile(new File(src, relativeName), target);
			}
		}
		LOG.info(MessageFormat.format("{0,choice,0#no files|1#1 file|1<{0,number,integer} files} loaded{4,choice,0#|1#, 1 file inherited|1<, {4,number,integer} files inherited} from ''{2}'' into {3}{1,choice,0#|1#; 1 file failed to load|1<; {1,number,integer} files failed to load}",
				new Object[] {numFilesLoaded, numFilesFailed, src, dst, numFilesInherited}));
	}
	
	private void loadFile(File xmlFile, Folder target) {
		try {
			Document newDoc = null;
			String canonicalFilePath = xmlFile.getCanonicalPath();
			assert canonicalFilePath.startsWith(workdirPrefix);
			String relativeFilePath = canonicalFilePath.substring(workdirPrefix.length());
			if (!workspace.query().exists("//vcm:modification/vcm:file[filename=$_1]", relativeFilePath)) {
				String relativeDocPath = prevspace.query().optional("//load:loaded[load:from=$_1]/load:to", relativeFilePath).value();
				if (relativeDocPath != null) {
					try {
						Document oldDoc = prevspace.documents().get(relativeDocPath);
						if (prevspace.relativePath(oldDoc.folder().path()).equals(workspace.relativePath(target.path()))) {
							LOG.debug("inheriting '" + relativeFilePath + "'");
							newDoc = cycle().inherit(oldDoc);
							numFilesInherited++;
						}
					} catch (DatabaseException e) {
						LOG.debug("didn't find previously loaded document", e);
					}
				}
			}
			if (newDoc == null) {
				if (LOG.isDebugEnabled()) LOG.debug(MessageFormat.format("loading ''{0}'' into {1}", new Object[] {xmlFile, target}));
				newDoc = target.documents().load(Name.keepAdjust(), Source.xml(xmlFile));
				numFilesLoaded++;
			}
			notes.append().elem("load:loaded")
				.elem("load:from").text(relativeFilePath).end("load:from")
				.elem("load:to").text(workspace.relativePath(newDoc.path())).end("load:to")
			.end("load:loaded").commit();
		} catch (IOException e) {
			// TODO: huh?  is this right?
			LOG.warn("couldn't inherit loaded file, forcing load", e);
		} catch (DatabaseException e) {
			LOG.error("error loading file '" + xmlFile + "'");
			numFilesFailed++;
		}
	}

}
