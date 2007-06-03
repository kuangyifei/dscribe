package com.ideanest.dscribe.java;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.apache.tools.ant.DirectoryScanner;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;
import com.thoughtworks.qdox.parser.Builder;
import com.thoughtworks.qdox.parser.ParseException;
import com.thoughtworks.qdox.parser.impl.JFlexLexer;
import com.thoughtworks.qdox.parser.impl.Parser;
import com.thoughtworks.qdox.parser.structs.*;

/**
 * Extracts high-level information from Java source code.  The information is
 * limited to a surface parse, and does not take into consideration method bodies,
 * initializers, etc.  Any types encountered are reported literally, to be resolved
 * later.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.16 $ ($Date: 2006/04/07 01:39:35 $)
 */
public class SourceExtractor extends TaskBase implements Builder {
	
	private static final Logger LOG = Logger.getLogger(SourceExtractor.class);
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Namespace.JAVA,
			"vcm", Namespace.VCM,
			"reef", Namespace.NOTES
	);
	
	private Folder workspace, prevspace, codespace;
	private int numExtracted, numFailed, numInherited;
	

	@Override
	protected void init(Node def) {
		workspace = cycle().workspace(NAMESPACE_MAPPINGS);
		prevspace = cycle().prevspace(NAMESPACE_MAPPINGS);
		codespace = workspace.children().create("code");
	}
	
	@Phase
	public void extract() {
		LOG.debug("extracting from source");
		
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(cycle().workdir());
		scanner.setIncludes(new String[] {"**/*.java"});
		scanner.addDefaultExcludes();
		scanner.scan();
		
		for (final String relativeName : scanner.getIncludedFiles()) {
			ItemList prevRips = null;
			if (			workspace.query().exists("//vcm:file[(@action='add' or @action='edit') and vcm:filename=$_1]", relativeName)
					||	(prevRips = prevspace.query().unordered("/unit[file=$_1]", relativeName)).size() == 0
			) {
				if (prevRips != null) LOG.debug("java source analysis of unchanged file '" + relativeName + "' not found; forcing re-analysis");
				try {
					LOG.debug("analyzing java source file '" + relativeName + "'");
					extract(relativeName);
					numExtracted++;
				} catch (Exception e) {
					LOG.error("java extractor failed on '" + relativeName + "'", e);
					numFailed++;
				} finally {
					reset();
				}
			} else {
				assert prevRips != null;
				for (Node res : prevRips.nodes()) {
					if (workspace.query().exists(
							"//reef:file[@storedName=exactly-one($_1//reef:file[@localName=$_2]/@storedName)]",
							prevspace, prevspace.relativePath(res.document().path()))) {
						LOG.debug("source unit already inherited for file '" + relativeName + "'");
					} else {
						LOG.debug("inheriting source extraction of '" + relativeName + "'");
						cycle().inherit(res.document());
						numInherited++;
					}
				}
			}
		}
		
		codespace.documents().build(Name.adjust("packages")).node(
				workspace.query().single(
						"element packages {" +
						"	for $name in distinct-values(//packageref/text())" +
						"	return element package {$name}" +
						"}"
				).node()
		).commit();
		
		LOG.info(new MessageFormat(
				"source extraction complete" +
				"{0,choice,0#, no files analyzed|1#, 1 file analyzed|1<, {0,number,integer} files analyzed}" +
				"{1,choice,0#|1# (1 file failed)|1< ({1,number,integer} files failed)}" +
				"{2,choice,0#|1#, 1 file inherited|1<, {2,number,integer} files inherited}")
				.format(new Object[]{
						new Integer(numExtracted+numFailed),
						new Integer(numFailed),
						new Integer(numInherited)}));
	}
	
	private ElementBuilder<?> builder;
	private ElementBuilder<org.w3c.dom.Node> javadocBuilder;
	private String namePrefix = "";
	
	private void reset() {
		javadocBuilder = null;
		namePrefix = "";
		builder = null;
	}

	public synchronized void extract(String relativeFilename) throws FileNotFoundException, ParseException {
		reset();
		
		File file = new File(cycle().workdir(), relativeFilename);
		builder = codespace.documents().build(Name.adjust(file.getName()));
		builder.elem("unit").elem("file").attr("type", "source").text(relativeFilename).end("file");
		
		InputStream input = null;
		try {
			input = new BufferedInputStream(new FileInputStream(file));
			new Parser(new JFlexLexer(input), this).parse();
		} finally {
			if (input != null) try {
				input.close();
			} catch (IOException e) {
				// ignore
			}
		}
		builder.end("unit").commit();
	}
	
	private void closeJavadoc() {
		if (javadocBuilder != null) {
			builder.node(javadocBuilder.commit());
			javadocBuilder = null;
		}
	}
	
	private ElementBuilder<org.w3c.dom.Node> javadoc() {
		if (javadocBuilder == null) javadocBuilder = ElementBuilder.createScratch(NAMESPACE_MAPPINGS);
		return javadocBuilder;
	}
	
	public void addPackage(String packageName) {
		builder.elem("packageref").text(packageName);
		closeJavadoc();
		builder.end("packageref");
		this.namePrefix = packageName + '.';
	}

	public void addImport(String importName) {
		builder.elem("import").text(importName);
		closeJavadoc();
		builder.end("import");
	}

	public void addJavaDoc(String text) {
		javadoc().elem("comment").text(text).end("comment");
	}

	public void addJavaDocTag(TagDef def) {
		javadoc().elem("tag").attr("name", def.name).text(def.text).end("tag");
	}

	@SuppressWarnings("unchecked")
	public void beginClass(ClassDef def) {
		builder.elem(def.type)
			.attr("name", def.name)
			.attr("implName", namePrefix + def.name)
			.attr("fullName", (namePrefix + def.name).replace('$', '.'))
			.attr("line", Integer.toString(def.lineNumber))
			.attr("modifiers", flatten(def.modifiers));
		closeJavadoc();
		namePrefix += def.name + '$';
		if (def.type.equals(ClassDef.INTERFACE) || def.type.equals(ClassDef.ANNOTATION_TYPE)) {
			for (String type : (Set<String>) def.extendz) addType("extends", type, 0);
		} else {
			if (def.extendz.size() > 0) {
				assert def.extendz.size() == 1;
				addType("extends", (String) def.extendz.iterator().next(), 0);
			}
			for (String type : (Set<String>) def.implementz) addType("implements", type, 0);
		}
	}
	
	public void endClass() {
		javadocBuilder = null;
		builder.end("interface", "class");
		int lastSeparatorIndex = namePrefix.lastIndexOf('$', namePrefix.length()-2);
		if (lastSeparatorIndex == -1) lastSeparatorIndex = namePrefix.lastIndexOf('.', namePrefix.length()-2);
		if (lastSeparatorIndex == -1) namePrefix = ""; else namePrefix = namePrefix.substring(0, lastSeparatorIndex+1);
	}
	
	public void addField(FieldDef def) {
		builder.elem("field")
			.attr("name", def.name)
			.attr("line", Integer.toString(def.lineNumber))
			.attr("modifiers", flatten(def.modifiers));
		addType(def.type, def.dimensions);
		closeJavadoc();
		builder.end("field");
	}

	@SuppressWarnings("unchecked")
	public void addMethod(MethodDef def) {
		String elemName = def.constructor ? "constructor" : "method";
		builder.elem(elemName);
		if (!def.constructor) {
			builder.attr("name", def.name);
			addType("returns", def.returns, def.dimensions);
		}
		builder
			.attr("line", Integer.toString(def.lineNumber))
			.attr("modifiers", flatten(def.modifiers));
		closeJavadoc();
		for (FieldDef param : (List<FieldDef>) def.params) {
			builder.elem("param").attr("name", param.name);
			addType(param.type, param.dimensions);
			builder.end("param");
		}
		for (String exType : (Set<String>) def.exceptions) addType("throws", exType, 0);
		builder.end(elemName);
	}

	private static String flatten(Collection<?> a) {
		StringBuilder buf = new StringBuilder();
		for (Iterator<?> it = a.iterator(); it.hasNext();) {
			buf.append(it.next());
			if (it.hasNext()) buf.append(' ');
		}
		return buf.toString();
	}
	
	private void addType(String relation, String typeName, int dimensions) {
		builder.elem(relation);
		addType(typeName, dimensions);
		builder.end(relation);
	}
	
	private void addType(String typeName, int dimensions) {
		String elemName = Constants.PRIMITIVE_TYPES.contains(typeName) ? "type" : "localType";
		builder.elem(elemName)
			.attrIf(dimensions >= 1, "arrayDim", Integer.toString(dimensions))
			.text(typeName)
			.end(elemName);
	}

	public void addAnnotation(AnnoDef annotation) {
		// TODO: handle annotations
	}
	
}