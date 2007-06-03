package com.ideanest.dscribe.java;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.*;

import org.apache.log4j.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;

/**
 * Builds the target system's code, compiling it into bytecode and running any
 * unit tests it can find.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class CodeBuilder extends TaskBase {
	private static final Logger LOG = Logger.getLogger(CodeBuilder.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Namespace.JAVA,
			"java", Namespace.JAVA
	);
	
	private Node prevRecord, workRecord;

	@Override
	protected void init(Node taskDef) throws Exception {
		prevRecord = cycle().prevspace(NAMESPACE_MAPPINGS).query().optional("code-builder-record").node();
		workRecord = cycle().workspace(NAMESPACE_MAPPINGS).documents().build(Name.adjust("code-builder-record"))
			.elem("code-builder-record").end("code-builder-record").commit().root();
	}
	
	@Phase
	public void build() {
		// if we detect a fancy build config and it fails, it'll throw an exception and fail the run
		if (buildMaven()) return;
		if (buildAnt()) return;
		if (buildEclipse()) return;
		// but if we don't, and the ad-hoc attempt fails, push on -- maybe it's not meant to be buildable
		try {
			buildAdHoc();
		} catch (Exception e) {
			LOG.error("failed to build system, proceeding without bytecode", e);
		}
		// TODO: abandon if unit tests failed?
	}
	
	/**
	 * Try to find a Maven config file and run the Maven build.
	 *
	 * @return <code>true</code> if Maven config found and build successful, <code>false</code> if no Maven config found
	 * @throws Exception if Maven config found and build not successful
	 */
	private boolean buildMaven() {
		// TODO: look for Maven config file
		return false;
	}
	
	/**
	 * Try to find an Ant config file and run the Ant build.
	 *
	 * @return <code>true</code> if Ant config found and build successful, <code>false</code> if no Ant config found
	 * @throws Exception if Ant config found and build not successful
	 */
	private boolean buildAnt() {
		// TODO: look for Ant config file
		return false;		
	}
	
	/**
	 * Try to find an Eclipse project definition file and simulate an Eclipse build.
	 *
	 * @return <code>true</code> if Eclipse config found and build successful, <code>false</code> if no Eclipse config found
	 * @throws Exception if Eclipse config found and build not successful
	 */
	private boolean buildEclipse() {
		// TODO: look for Eclipse config file
		return false;		
	}
	
	/**
	 * Try to compile any sources found and run recognized unit tests.
	 *
	 * @throws Exception if we failed to build
	 */
	private void buildAdHoc() {
		// 1. locate compiler and JDK libraries
		Compiler compiler = new Compiler();
		// 2. locate custom libraries (JARs and classes)
		// 3. locate package roots
		// 4. try compiling, adjusting for errors
		//	- add missing classes to classpath
		//	- change source version number and matching JDK library
		//	- wipe all classes and recompile from scratch
		// 5. find and run unit tests
		//	- in case of errors, try all the fixes listed above
	}
	
	// TODO: remove or rewrite this class when JSR 199 becomes available (standardized access to compiler)
	// http://www.jcp.org/en/jsr/detail?id=199
	private class Compiler {
		private final Method compileMethod;
		private String version = "1.5";
		private Map<String,File> rtLibMap = new TreeMap<String,File>();
		
		Compiler() {
			compileMethod = findCompiler();
		}
		
		private File findRTLibrary(String rtVersion, File[] roots) throws IOException {
			File rtLib = rtLibMap.get(rtVersion);
			if (rtLib == null) {
				Map<String,File> candidates = findJars("**/rt.jar", rtVersion, "Java Platform API Specification", roots);
				if (candidates.isEmpty()) throw new IOException("no runtime library found for version " + rtVersion);
				rtLib = candidates.values().iterator().next();
				rtLibMap.put(rtVersion, rtLib);
			}
			return rtLib;
		}
		
		private Method getCompilerMethod(ClassLoader loader) throws Exception {
			Method compile = loader.loadClass("sun.tools.javac.Main").getMethod("compile", new Class[] {String[].class, PrintWriter.class});
			StringWriter capture = new StringWriter();
			compile.invoke(null, new Object[] {new String[] {"-source", version}, new PrintWriter(capture)});
			try {
				String firstLine = new StringTokenizer(capture.toString(), "\n").nextToken();
				if (firstLine.equals("javac: invalid source release: " + version)) throw new RuntimeException("compiler version too old, does not accept " + version + " sources");
				if (firstLine.equals("javac: no source files")) return compile;
				throw new RuntimeException("unexpected compiler error message: " + firstLine);
			} catch (NoSuchElementException e) {
				throw new RuntimeException("compiler invocation produced no output");
			}
		}
		
		private Method loadCompiler(File file) throws Exception {
			if (!file.exists()) throw new IOException("file " + file + " not found");
			Method method = getCompilerMethod(new URLClassLoader(new URL[]{file.toURL()}, Thread.currentThread().getContextClassLoader()));
			workRecord.append().elem("tools-jar-location").text(file.getCanonicalPath()).end("tools-jar-location").commit();
			LOG.debug("using javac from tools.jar at " + file);
			file = file.getParentFile();
			if (file != null) file = file.getParentFile();
			if (file != null) findRTLibrary(version, new File[] {file});
			return method;
		}
		
		private String readVersion(File jarLocation, String desiredVersionPrefix, String desiredSpecification) throws IOException {
			Manifest manifest = new JarFile(jarLocation, false).getManifest();
			if (desiredSpecification != null) {
				String spec = manifest.getMainAttributes().getValue(Attributes.Name.SPECIFICATION_TITLE);
				if (!desiredSpecification.equals(spec)) throw new IOException("wrong specification title: " + spec);
			}
			String jarVersion = new StringTokenizer(manifest.getMainAttributes().getValue("Created-By")).nextToken();
			if (!jarVersion.startsWith(desiredVersionPrefix)) throw new IOException("wrong jar version: " + jarVersion);
			return jarVersion;
		}
		
		private Method findCompiler() {
			// 1. check if there's a compiler already on the classpath, in which case we have no control over it
			try {
				Method m = getCompilerMethod(Thread.currentThread().getContextClassLoader());
				LOG.warn("compiler present on default classpath, unable to control version used");
				return m;
			} catch (ClassNotFoundException e) {
				// no compiler class on default classpath, this is good
			} catch (Exception e) {
				throw new RuntimeException("compiler present on default classpath not working, unable to look for another one", e);
			}

			// 2. try the last working tools.jar to avoid full search; this means we won't find a newer
			// version until the old one is deleted, but that's all right
			String lastWorkingLocation = prevRecord.query().optional("//tools-jar-location").value();
			if (lastWorkingLocation != null) {
				File file = new File(lastWorkingLocation);
				try {
					readVersion(file, version + ".", null);	// discard actual version as long as it's 1.5.*
					return loadCompiler(file);
				} catch (Exception e) {
				}
				LOG.debug("appropriate tools.jar not found in previous location, will search for it");
			}
			
			// 3. find all other reachable tools.jar files and order them by version, from most recent to oldest
			for (File location : findJars("**/tools.jar", "1.5", null, File.listRoots()).values()) {
				try {
					return loadCompiler(location);
				} catch (Exception e) {
					LOG.debug("failed to load compiler at " + location, e);
				}
			}
			
			throw new RuntimeException("no working java compiler found; please install the full JDK 1.5 on this system");
		}
		
		private SortedMap<String,File> findJars(String filePattern, String desiredVersion, String desiredSpecification, File[] roots) {
			final String desiredVersionPrefix = desiredVersion + ".";
			// the way Sun assigns version numbers, normal string comparison should do
			SortedMap<String,File> candidates = new TreeMap<String,File>(Collections.reverseOrder());
			for (File root : roots) {
				DirectoryScanner scanner = new DirectoryScanner();
				scanner.setBasedir(root);
				scanner.setIncludes(new String[] {filePattern});
				scanner.addDefaultExcludes();
				scanner.scan();
				for (String location : scanner.getIncludedFiles()) {
					File file = new File(location);
					try {
						candidates.put(readVersion(file, desiredVersionPrefix, desiredSpecification), file);
					} catch (IOException e) {
						LOG.debug("wrong version of " + filePattern+ " at " + location);
					}
				}
			}
			return candidates;
		}
		
	}

}
