package com.ideanest.dscribe.java;

import java.util.*;

/**
 * Some useful constants for Java-related functions. 
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.2 $ ($Date: 2004/11/08 21:51:42 $)
 */
public interface Constants {
	
	/**
	 * An unmodifiable set of all primitive types in Java.
	 */
	public static final Set<String> PRIMITIVE_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(new String[] {
		"void", "boolean", "byte", "char", "short", "int", "long", "float", "double"
	})));
	
}
