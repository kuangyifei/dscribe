package com.ideanest.dscribe.test;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Date;

import org.exist.fluent.*;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import com.ideanest.dscribe.TriggerMaker;
import com.ideanest.dscribe.job.TaskBase;

public class TestManager extends TaskBase implements TriggerMaker {
	
	private File baseDir;

	@Override
	protected void init(Node taskDef) throws Exception {
		baseDir = new File(cycle().workdir(), "projects/" + cycle().name());
		delete(new File(baseDir, "final-workspace"));
	}
	
	@Phase
	public void prep() throws IOException {
		// don't clear workspace, notes are already in there and it should be cleaned by JobRun anyway
		cycle().prevspace(null).clear();
		cycle().workspace(null).parent().children().get("versions").clear();
		cycle().workspace(null).parent().children().get("store").clear();
		File initialDir = new File(baseDir, "initial");
		if (!initialDir.exists()) return;
		copy(new File(initialDir, "workdir"), "");
		slurp(new File(initialDir, "workspace"), cycle().workspace(null));
		slurp(new File(initialDir, "prevspace"), cycle().prevspace(null));
	}
	
	@Phase
	public void verify() throws IOException {
		dump(new File(baseDir, "final-workspace"), cycle().workspace(null));
	}

	public Trigger create(Item def) {
		SimpleTrigger trigger = new SimpleTrigger();
		trigger.setStartTime(new Date());
		return trigger;
	}
	
	private void dump(File dst, Folder src) throws IOException {
		dst.mkdirs();
		for (Document doc : src.documents()) doc.export(new File(dst, doc.name()));
		for (Folder child : src.children()) {
			dump(new File(dst, child.name()), child);
		}
	}
	
	private void slurp(File srcDir, Folder dst) {
		if (!srcDir.exists() || srcDir.getName().equals("CVS")) return;
		for (String filename : srcDir.list()) {
			File src = new File(srcDir, filename);
			if (src.isDirectory()) {
				slurp(src, dst.children().create(filename));
			} else {
				// TODO: allow load non-XML documents too, detect type automatically?
				dst.documents().load(Name.create(src.getName()), Source.xml(src));
			}
		}
	}

	private void copy(File srcDir, String relativeName) throws IOException {
		if (!srcDir.exists()) return;
		File src = new File(srcDir, relativeName), dst = new File(cycle().workdir(), relativeName);
		
		if (src.isDirectory()) {
			dst.mkdirs();
			for (String filename : src.list()) copy(srcDir, relativeName + "/" + filename);
		} else {
			long srcLastModified = src.lastModified();
			FileChannel srcChannel = null, dstChannel = null;
			try {
				srcChannel = new FileInputStream(src).getChannel();
				dst.getAbsoluteFile().getParentFile().mkdirs();
				dstChannel = new FileOutputStream(dst).getChannel();
				dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
			} finally {
				closeSafely(srcChannel);
				closeSafely(dstChannel);
			}
			dst.setLastModified(srcLastModified);
		}
		
	}
	
	private void closeSafely(FileChannel channel) {
		if (channel == null) return;
		try {
			channel.close();
		} catch (IOException e) {
			// oh well
		}
	}
	
	private void delete(File file) {
		if (file.isDirectory()) {
			for (String child : file.list()) delete(new File(file, child));
		}
		file.delete();
	}
}
