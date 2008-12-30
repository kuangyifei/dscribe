package com.ideanest.dscribe.vcm;

import java.io.*;
import java.nio.channels.FileChannel;
import java.text.*;
import java.util.Date;

import org.apache.log4j.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;

/**
 * Synchronizes modifications in a filesystem source directory with the job's
 * working directory.  Keeps track of files between job runs so it doesn't rely
 * solely on the last modification timestamp, and can detect deleted files.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class Filesystem extends TaskBase {
	
	private static final Logger LOG = Logger.getLogger(Filesystem.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"notes", Namespace.NOTES,
			"vcm", Namespace.VCM,
			"fs", Namespace.VCM_FILESYSTEM
	);
		
	private File srcDir, dstDir;
	private Resource prevFiles;
	private Node workFiles;
	private DirectoryScanner scanner;
	private QuietPeriod quietPeriod;
	private Date latestFileModification;
	private boolean hasModifications;

	@Override
	protected void init(Node taskDef) throws Exception {
		srcDir = cycle().resolveOptionalFile(taskDef.query().single("@src").value()).getCanonicalFile();
		dstDir = cycle().resolveOptionalFile(taskDef.query().optional("@dst").value()).getCanonicalFile();
		
		quietPeriod = new QuietPeriod(cycle(), taskDef, null);	// default start scan time of taskStart-quietPeriod, so that the only possible agreement point is "now"
		latestFileModification = new Date(0);	// arbitrary past dateTime in case there are no files
		initScanner(taskDef);
		initSpaces();
	}

	private void initScanner(Item taskDef) {
		scanner = new DirectoryScanner();
		scanner.setBasedir(srcDir);
		ItemList includes = taskDef.query().all("include/@name");
		if (includes.size() > 0) scanner.setIncludes(includes.values().toArray());
		ItemList excludes = taskDef.query().all("exclude/@name");
		if (excludes.size() > 0) scanner.setExcludes(excludes.values().toArray());
		if (taskDef.query().flag("@defaultexcludes", true)) scanner.addDefaultExcludes();
	}

	private void initSpaces() {
		prevFiles = cycle().prevspace(NAMESPACE_MAPPINGS).query().optional("/fs:record[@srcDir=$_1]", srcDir.toString());
		
		workFiles = cycle().workspace(NAMESPACE_MAPPINGS).documents()
			.build(Name.adjust("filesystem-record"))
			.elem("fs:record")
				.attr("srcDir", srcDir.toString())
			.end("fs:record")
			.commit()
			.root();
	}

	@Phase
	public void check() throws Exception {
		// TODO: check for clock drift in source filesystem and compensate
		try {
			// 1. check for new and changed files
			scanner.scan();
			checkModified(scanner.getIncludedDirectories());
			checkModified(scanner.getIncludedFiles());
			
			// 2. check for deleted files
			checkDeleted();
			
		} catch (IllegalStateException e) {
			LOG.error("problems with source directory '" + srcDir + "'", e);
		}
		
		// modifications before the quiet period window will not be recorded by the quiet period handler, so flag them explicitly here otherwise job will be abandoned
		if (hasModifications) quietPeriod.addChangePointBeforeScanStart();
		quietPeriod.recordProposal(workFiles.append());
		
		if (hasModifications) {
			LOG.info(new MessageFormat(
					"file system changes detected in ''{0}''; " +
					"{1,choice,0#|1#1 file/dir changed, |1<{1,number,integer} files/dirs changed, }" +
					"{2,choice,0#|1#1 file/dir added, |1<{2,number,integer} files/dirs added, }" +
					"{3,choice,0#|1#1 file/dir deleted, |1<{3,number,integer} files/dirs deleted, }" +
					"quiet period {4,choice,0#not violated|1#violated once|2#violated twice|2<violated {4,number,integer} times}"
					).format(new Object[] {
							srcDir,
							new Integer(workFiles.query().single("count(//vcm:file[@action='edit'])").intValue()),
							new Integer(workFiles.query().single("count(//vcm:file[@action='add'])").intValue()),
							new Integer(workFiles.query().single("count(//vcm:file[@action='delete'])").intValue()),
							new Integer(quietPeriod.numViolations())
					}));
		} else {
			LOG.info("no file system changes in '" + srcDir + "'");
		}
		
	}
		
	private void checkDeleted() {
		for (String filename : prevFiles.query().all(
				"for $prevName in //vcm:file[not(@action='delete')]/vcm:filename/text() " +
				"where not(exists($_1//vcm:file[vcm:filename=$prevName])) " +
				"return $prevName", workFiles
				).values()) {
			workFiles.append()
				.elem("vcm:modification")
					.attr("demarcation", "bogus")
					.attr("type", Namespace.VCM_FILESYSTEM)
					.elem("vcm:file")
						.attr("action", "delete")
						.elem("vcm:filename").text(filename).end("vcm:filename")
					.end("vcm:file")
				.end("vcm:modification")
				.commit();
			hasModifications = true;
		}
	}

	private void checkModified(String[] filenames) throws ParseException {
		for (String relativeName : filenames) {
			cycle().checkInterrupt();
			
			File srcFile = new File(srcDir, relativeName);

			Date srcLastModified = new Date(srcFile.lastModified());
			quietPeriod.addChangePoint(srcLastModified);
			// TODO: check date for sanity
			
			if (srcLastModified.after(latestFileModification)) latestFileModification = srcLastModified;
			Date prevSrcLastModified =
				prevFiles.query().optional("//vcm:file[vcm:filename = $_1]/@fs:srcStamp", relativeName).instantValue();
			String vcmAction = null;
			if (prevSrcLastModified == null) {
				vcmAction = "add";
			} else if (!srcFile.isDirectory() && !srcLastModified.equals(prevSrcLastModified)) {
				vcmAction = "edit";
			}
			
			if (vcmAction != null) {
				workFiles.append()
					.elem("vcm:modification")
						.attr("demarcation", "bogus")
						.attr("type", "fs")
						.attr("date", srcLastModified)
						.elem("vcm:file")
							.attr("action", vcmAction)
							.attr("fs:srcStamp", srcLastModified)
							.elem("vcm:filename").text(relativeName).end("vcm:filename")
						.end("vcm:file")
					.end("vcm:modification")
					.commit();
				hasModifications = true;
			} else {
				workFiles.append()
					.elem("vcm:file")
						.attr("fs:srcStamp", srcLastModified)
						.elem("vcm:filename").text(relativeName).end("vcm:filename")
					.end("vcm:file")
					.commit();
			}
		}
	}
	
	@Phase
	public void update() throws IOException {
		// no need to look at agreed-upon checkpoint; we proposed at most one range,
		// so any selected checkpoint must be in it, and the range's modifications were
		// pre-computed in the check() step
		
		// 1. delete removed files
		ItemList removedFiles = workFiles.query().unordered("//vcm:file[@action='delete']/vcm:filename");
		for (String filename : removedFiles.values()) {
			delete(new File(dstDir, filename));
		}
		
		// 2. copy new and modified files
		ItemList changedFiles = workFiles.query().unordered("//vcm:file[@action='add' or @action='edit']");
		for (Node node : changedFiles.nodes()) {
			copy(node);
		}
		
		// 3. check that other files weren't modified, warn and re-copy if necessary
		int numCopiedFiles = changedFiles.size();
		for (Node node : workFiles.query().unordered("//vcm:file[not(@action)]").nodes()) {
			if (verifyUnmodified(node)) numCopiedFiles++;
		}
		
		LOG.info(new MessageFormat(
				"{0,choice,0#|1#1 file/dir copied, |1<{0,number,integer} files/dirs copied, }" +
				"{1,choice,0#|1#1 file/dir deleted, |1<{1,number,integer} files/dirs deleted, }" +
				"file system working copy updated"
				).format(new Object[]{new Integer(numCopiedFiles), new Integer(removedFiles.size())}));
		
	}
	
	private boolean verifyUnmodified(Node fileNode) throws IOException {
		String relativeName = fileNode.query().single("vcm:filename").value();
		File dstFile = new File(dstDir, relativeName);
		if (!dstFile.exists()) {
			LOG.warn("file '" + relativeName + "' missing from working directory; re-copying");
			copy(fileNode, relativeName);
			return true;
		} else if (dstFile.isDirectory()) {
			// don't compare timestamps on directories
			return false;
		} else {
			Date oldStamp = prevFiles.query().single("//vcm:file[vcm:filename=$_1]/@fs:dstStamp", relativeName).instantValue();
			Date newStamp = new Date(dstFile.lastModified());
			if (!oldStamp.equals(newStamp)) {
				LOG.warn("file '" + relativeName + "' modified unexpectedly in working directory; re-copying");
				copy(fileNode, relativeName);
				return true;
			}
			fileNode.update().attr("fs:dstStamp", oldStamp).commit();
			return false;
		}
	}

	private void delete(File file) throws IOException {
		if (!file.exists()) return;
		if (file.isDirectory()) {
			for (File child : file.listFiles()) delete(child);
		}
		file.delete();
	}
	
	private void copy(Node fileNode) throws IOException {
		copy(fileNode, fileNode.query().single("vcm:filename").value());
	}
	
	private void copy(Node fileNode, String relativeName) throws IOException {
		File src = new File(srcDir, relativeName), dst = new File(dstDir, relativeName);
		
		LOG.debug("copying " + src + " to " + dst + (src.isDirectory() ? " (directory)" : ""));
		
		Date srcStamp = fileNode.query().single("@fs:srcStamp").instantValue();
		
		FileChannel srcChannel = null, dstChannel = null;
		Date srcLastModified;
		try {
			
			if (!src.isDirectory()) {
				srcChannel = new FileInputStream(src).getChannel();
				srcChannel.lock(0, srcChannel.size(), true);
			}
			srcLastModified = new Date(src.lastModified());
			if (!srcStamp.equals(srcLastModified)) {
				LOG.warn("file '" + relativeName + "' modified since check phase, unable to update");
				throw new IOException("file changed");
			}
			
			if (src.isDirectory()) {
				dst.mkdirs();
			} else {
				dst.getAbsoluteFile().getParentFile().mkdirs();
				dstChannel = new FileOutputStream(dst).getChannel();
				dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
			}
		} finally {
			closeSafely(srcChannel);
			closeSafely(dstChannel);
		}
		
		if (!src.isDirectory()) {
			dst.setLastModified(srcLastModified.getTime());
			fileNode.update()
				.attr("fs:dstStamp", DataUtils.toDateTime(dst.lastModified()))
				.commit();
		}
			
	}
	
	private void closeSafely(FileChannel channel) {
		if (channel == null) return;
		try {
			channel.close();
		} catch (IOException e) {
			LOG.warn("failed to close file channel", e);
		}
	}
}
