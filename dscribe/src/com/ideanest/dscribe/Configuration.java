package com.ideanest.dscribe;

import java.io.File;

import org.exist.fluent.*;
import org.quartz.*;


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
	
	/**
	 * Return the class mapped to the given tag name if it's a subtype of the given class.  If the mapped
	 * class is not a subtype of <code>clazz</code>, return <code>null</code>.
	 *
	 * @param <TagType> the type of mapped class you're looking for
	 * @param qname the qualified name of the tag to resolve
	 * @param clazz the reflected class of the type you're looking for
	 * @return the class mapped to the tag if it's a subtype of <code>TagType</code> or <code>null</code> otherwise
	 * @throws MappingNotFoundException if there is no mapping for the given tag
	 * @throws ClassNotFoundException if the mapped class cannot be loaded
	 */
	public <TagType> Class<? extends TagType> resolveTagClass(QName qname, Class<TagType> clazz)
			throws ClassNotFoundException, MappingNotFoundException {
		String className = findTagDef(qname).query().optional("@class").value();
		if (className == null) throw new MappingNotFoundException(qname.toString());
		Class<?> foundClass = Class.forName(className);
		return clazz.isAssignableFrom(foundClass) ? foundClass.asSubclass(clazz) : null;
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
