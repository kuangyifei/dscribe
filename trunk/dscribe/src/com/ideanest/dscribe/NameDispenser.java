package com.ideanest.dscribe;

import org.apache.log4j.Logger;
import org.exist.fluent.Node;
import org.exist.fluent.Resource;


/**
 * Dispenses unique names with the context of some query.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class NameDispenser {
	
	private int counter = 1;
	private final Resource context;
	private final String elementQuery, nameAttr;

	public NameDispenser(Resource context, String elementQuery, String nameAttr) {
		this.context = context;
		this.elementQuery = elementQuery;
		this.nameAttr = nameAttr;
	}
	
	public String generate(String baseName) {
		String newName;
		do {
			newName = baseName + "-" + counter++;
		} while (context.query().presub().exists("$1[@$2 = $_3]", elementQuery, nameAttr, newName));
		return newName;
	}

	public static void resolveDuplicateNames(Resource context, String elementQuery, String nameAttr) {
		NameDispenser dispenser = new NameDispenser(context, elementQuery, nameAttr);
		for(Node elem : context.query().presub().all(
				"let $e := $1[@$2] " + 
				"return " + 
				"  for $a in $e " + 
				"  where exists($e[not(. is $a) and @$2 = $a/@$2]) " + 
				"  return $a", elementQuery, nameAttr
				).nodes()) {
			String oldName = elem.query().presub().single("@$1", nameAttr).value();
			String newName = dispenser.generate(oldName);
			elem.update().attr(nameAttr, newName).commit();
			LOG.warn("renamed " + elem.name() + " '" + oldName + "' to '" + newName + "' due to conflict");
		}
	}

	public static void resolveMissingNames(Resource context, String elementQuery, String nameAttr) {
		NameDispenser dispenser = new NameDispenser(context, elementQuery, nameAttr);
		for (Node elem : context.query().presub().all("$1[not(@$2)]", elementQuery, nameAttr).nodes()) {
			String newName = dispenser.generate(elem.name());
			elem.update().attr(nameAttr, newName).commit();
			LOG.warn("assigned name '" + newName + "' to a nameless " + elem.name());
		}
	}

	private static final Logger LOG = Logger.getLogger(NameDispenser.class);
}
