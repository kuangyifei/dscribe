package com.ideanest.dscribe.java;

import java.text.MessageFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.job.TaskBase;

/**
 * Resolves type names found in Java source files, according to Java type resolution rules.
 * Normally this algorithm is implemented by a Java compiler, but sometimes we want to
 * know what fully qualified typename a typename mentioned locally corresponds to.
 * <p>
 * Instances of this class are not thread-safe. 
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.12 $ ($Date: 2006/03/14 22:49:47 $)
 */
public class TypeResolver extends TaskBase {
	
	private static final NamespaceMap NAMESPACE_MAPPINGS = new NamespaceMap(
			"", Namespace.JAVA
	);
	
	private String packageName;
	private final Set<String> classImports = new HashSet<String>();
	private final Set<String> wildcardImports = new HashSet<String>();
	private Folder resultStore;
	private int resolvedTypesCount, totalTypesCount;
	
	private static final Logger LOG = Logger.getLogger(TypeResolver.class);

	@Override
	protected void init(Node def) {
		resultStore = cycle().workspace(NAMESPACE_MAPPINGS);
	}
	
	/**
	 * Reset this type resolver for a new context.
	 */
	private void reset() {
		packageName = null;
		classImports.clear();
		wildcardImports.clear();
		wildcardImports.add("java.lang");
	}

	/**
	 * Resolve all types in the project's default store collection.
	 */
	@Phase
	public void elaborate() {
		LOG.debug("resolving types");
		ItemList docsWithUnresolvedTypes = resultStore.query().all("//unit[.//localType[not(sibling::type)]]");
		if (docsWithUnresolvedTypes.size() == 0) {
			LOG.info("all types already resolved");
			return;
		}
		for (Node unit : docsWithUnresolvedTypes.nodes()) {
			ItemList result = unit.query().all("//file[@type='source']");
			if (result.size() == 0) result = unit.query().all("//file");
			String sourceFileName = result.size() == 0 ? null : result.get(0).value();
			if (sourceFileName == null) {
				LOG.debug("resolving types in anonymous unit (document '" + unit.document().name() + "')");
			} else {
				LOG.debug("resolving types in unit '" + sourceFileName + "'");
			}
			try {
				resolveFile(unit);
			} catch (Exception e) {
				LOG.error("error while resolving types in file '" + unit.document().name() + "'" + (sourceFileName == null ? "" : ", unit '" + sourceFileName + "'"), e);
			}
		}
		LOG.info(new MessageFormat(
				"{1,choice," +
				"0#'{0,choice,1#resolved the only type|1<resolved all {0,number,integer} types}'" +
				"|0<resolved {0,number,integer} types, {1,number,integer} left unresolved}" +
				" (over {2,choice,1#1 unit|1<{2,number,integer} units})"
				).format(new Object[] {
						new Integer(resolvedTypesCount),
						new Integer(totalTypesCount-resolvedTypesCount),
						new Integer(docsWithUnresolvedTypes.size())}));
	}
	
	/**
	 * Resolve all types within a specific file in the project store.
	 * @param unit the unit root node
	 */
	private void resolveFile(Node unit) {
		reset();
		packageName = unit.query().optional("packageref").value();
		for (String importName : unit.query().all("child::import").values()) {
			if (importName.endsWith(".*")) {
				wildcardImports.add(importName.substring(0, importName.length()-2));
			} else {
				classImports.add(importName);
			}
		}
		
		ItemList elementsToResolve = unit.query().all("//*[localType][not(type)]");
		totalTypesCount += elementsToResolve.size();
		for (Node target : elementsToResolve.nodes()) {
			try {
				
				String resolvedType = resolve(target.query().single("localType").value(), target);
				
				String dim = target.query().optional("localType/@arrayDim").value();
				target.append()
					.elem("type")
						.attrIf(dim != null, "arrayDim", dim)
						.text(resolvedType)
					.end("type")
					.commit();
				
				resolvedTypesCount++;
				
			} catch (TypeResolutionException e) {
				target.append()
					.elem("typeNotResolved")
						.text(e.getMessage())
					.end("typeNotResolved")
					.commit();
			}
		}
	}

	/**
	 * Resolve a local type name to a type.  This takes into consideration the local
	 * context with appropriate scoping rules, the package of which the current source
	 * code is a member, and any import declarations that have been made.
	 * This method does not work for array types.
	 * 
	 * @param typeName a type name, qualified or not, primitive or not
	 * @param target the context in which the type was mentioned
	 * @return the fully qualified type to which the given type name resolves in this context
	 * @throws TypeResolutionException if the type cannot be resolved
	 */
	private String resolve(String typeName, Node target) throws TypeResolutionException {
		String result = resolveInternal(typeName, target);
		if (result == null) throw new TypeResolutionException(typeName, "not found");
		return result;
	}
	
	private String resolveInternal(String typeName, Node target) throws TypeResolutionException {
		// 1. if typeName is primitive, return the primitive type immediately
		if (Constants.PRIMITIVE_TYPES.contains(typeName)) return typeName;
		
		// 2. attempt to locate first part of name as simple type using all possible contexts
		String firstPart = typeName;
		final int firstDotIndex = typeName.indexOf('.');
		if (firstDotIndex != -1) firstPart = typeName.substring(0, firstDotIndex);
		{
			String firstPartQualified = findSimpleType(firstPart, target);
			if (firstPartQualified != null) {
				// found a match, everything following the first part must be a nested class
				if (firstDotIndex != -1) firstPartQualified += typeName.substring(firstDotIndex).replace('.', '$');
				return load(firstPartQualified);
			}
		}
		
		// 3. if first part is not a type, it must be a package; find first element that is a type, then convert rest to nested classes
		if (firstDotIndex == -1) return null;
		int dotIndex = firstDotIndex;
		while(dotIndex != -1) {
			dotIndex = typeName.indexOf('.', dotIndex+1);
			String fullName = dotIndex == -1 ? typeName : typeName.substring(0, dotIndex);
			if (load(fullName) != null) {
				if (dotIndex != -1) fullName += typeName.substring(dotIndex).replace('.', '$');
				return load(fullName);
			}
		}
		
		return null;
	}
	
	/**
	 * Find a simple type according to name scoping rules.
	 * @param simpleTypeName the name of the simple type to find; must not contain any delimiters ('.' or '$')
	 * @param target the node holding the reference we're trying to resolve (used for context)
	 * @return the fully qualified name of the type if found, <code>null</code> otherwise
	 * @throws TypeResolutionException if the type reference is ambiguous
	 */
	private String findSimpleType(String simpleTypeName, Node target) throws TypeResolutionException {
		// 1. is it a type name currently being defined, or a member of such?
		// (note that overlap is not possible, since hiding is forbidden)
		{
			String fullName = target.query().optional("(ancestor::class | ancestor::interface) [@name = $_1] / @implName", simpleTypeName).value();
			if (fullName != null) return fullName;
			for (String ancestorName : target.query().all("reverse((ancestor::class | ancestor::interface) / @implName)").values()) {
				fullName = load(ancestorName+ '$' + simpleTypeName);
				if (fullName != null) return fullName;
			}
		}
		
		// 2. is it explicitly imported?
		String suffix = "." + simpleTypeName;
		for (String name : classImports) {
			if (name.endsWith(suffix)) return name;
		}
		
		// 3. is it in the same package?
		{
			String fullName = packageName == null ? simpleTypeName : packageName + "." + simpleTypeName;
			String klass = load(fullName);
			if (klass != null) return fullName;
		}
		
		// 4. is it a wildcard import?
		{
			String klass = null;
			for (String name : wildcardImports) {
				String klass2 = load(name + suffix);
				if (klass2 != null) {
					if (klass != null) throw new TypeResolutionException(suffix, "ambiguous type reference");
					klass = klass2;
				}
			}
			if (klass != null) return klass;
		}
		
		return null;
	}

	private String load(String className) throws TypeResolutionException {
		ItemList qr = resultStore.query().all("/file//(class|interface)[@implName = $_1]/@fullName", className);
		if (qr.size() == 1) {
			return qr.get(0).value();
		} else if (qr.size() == 0) {
			try {
				Thread.currentThread().getContextClassLoader().loadClass(className);
				return className.replace('$', '.');
			} catch (ClassNotFoundException e) {
				return null;
			}
		} else {
			throw new TypeResolutionException(className, "duplicate type definition");
		}
	}

	/**
	 * Signals a failure to resolve a local type into a fully qualified one.
	 */
	static class TypeResolutionException extends Exception {

		private static final long serialVersionUID = 3257844376943276338L;
		final String typeName;
		
		String getTypeName() {return typeName;}

		TypeResolutionException(String typeName) {
			super();
			this.typeName = typeName;
		}

		TypeResolutionException(String typeName, String message) {
			super(message);
			this.typeName = typeName;
		}

		TypeResolutionException(String typeName, Throwable cause) {
			super(cause);
			this.typeName = typeName;
		}

		TypeResolutionException(String typeName, String message, Throwable cause) {
			super(message, cause);
			this.typeName = typeName;
		}
	}
}
