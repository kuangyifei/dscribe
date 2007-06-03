package com.ideanest.dscribe;

import java.io.File;

import org.exist.fluent.*;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;


public class Configuration {
	
	private final Main main;
	private final Folder config;

	Configuration(Main main, Folder config) {
		this.main = main;
		this.config = config.clone();
		this.config.namespaceBindings().clear();
		this.config.namespaceBindings().put("", Namespace.CONFIG);
	}
	
	public Configuration newFromFolder(Folder folder) {
		return new Configuration(main, folder);
	}
	
	public File workdir() {
		return main.workdir();
	}
	
	public File tempdir() {
		return main.tempdir();
	}

	public Item findTagDef(QName qname) {
		return config.query().optional("//mapping[@namespace=$_1]/map[@tag=$_2]", qname.getNamespaceURI(), qname.getLocalPart());
	}
	
	public Class resolveTagClass(QName qname) throws ClassNotFoundException, MappingNotFoundException {
		String className = findTagDef(qname).query().optional("@class").value();
		if (className == null) throw new MappingNotFoundException(qname.toString());
		return Class.forName(className);
	}

	public Node findJob(String name) {
		return config.query().single("//job[@name=$_1]", name).node();
	}
	
	public static Configuration fromContext(JobExecutionContext context) throws SchedulerException {
		return ((Main) context.getScheduler().getContext().get(Main.MAIN_INSTANCE_KEY)).currentConfig();
	}

	public Schedule schedule() {
		return main.schedule();
	}

}
