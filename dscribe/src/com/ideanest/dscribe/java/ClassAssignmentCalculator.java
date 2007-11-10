package com.ideanest.dscribe.java;


import java.util.*;

import javax.xml.namespace.QName;

import org.exist.fluent.*;
import org.junit.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.opti.AnnealingDiagramAssigner;
import static org.junit.Assert.*;

public class ClassAssignmentCalculator implements AnnealingDiagramAssigner.Calculator {
	
	private static final NamespaceMap NAMESPACE_BINDINGS = new NamespaceMap(
			"", Namespace.JAVA
	);
	
	public NamespaceMap getNamespaceBindings() {
		return NAMESPACE_BINDINGS;
	}
	
	public String getElementQueryFragment() {return "(//class | //interface)";}
	
	public double calculateCost(Node e1, Node e2) {
		QName e1qname = e1.qname(), e2qname = e2.qname();
		assert e1qname.equals(DiagramExtractor.JAVA_CLASS) || e1qname.equals(DiagramExtractor.JAVA_INTERFACE);
		double cost = 0.0;
		
		if (e2qname.equals(DiagramExtractor.JAVA_CLASS) || e2qname.equals(DiagramExtractor.JAVA_INTERFACE)) {
			// shared package and/or nesting
			cost -= 300 * AnnealingDiagramAssigner.commonPrefixFraction(
					e1.query().single("@fullName").value(),
					e2.query().single("@fullName").value());
			
			// similar simple name (with minimum floor to cut off noise)
			cost -= 100 * Math.max(0.3, AnnealingDiagramAssigner.stringCommonalityFraction(
					e1.query().single("@name").value(),
					e2.query().single("@name").value()));
			
			// magnitude of references between the two types
			cost -= 300 * typeCrossRefFraction(e1, e2);
			
			// common parent type
			cost -= 300 * commonParentTypesFraction(e1, e2);
			
		} else {
			// TODO: distinguish between neutral and incompatible foreign elements?
			cost += 100;
		}
		
		return cost;
	}

	private double commonParentTypesFraction(Node e1, Node e2) {
		double r = e1.query().let("e1", e1).let("e2", e2).single(
				"declare function local:common-count($seq1, $seq2) as xs:integer {" +
				"	count(for $x in $seq1 where $x=$seq2 return $x)" +
				"};" +
				"declare function local:max-count($seq1, $seq2) as xs:integer {" +
				"	max((count($seq1), count($seq2)))" +
				"};" +
				"let" +
				"	$ext1 := $e1/extends/type," +
				"	$ext2 := $e2/extends/type," +
				"	$impl1 := $e1/implements/type," +
				"	$impl2 := $e2/implements/type," +
				"	$max := local:max-count($ext1, $ext2) + local:max-count($impl1, $impl2) " +
				"return" +
				"	if ($max eq 0)" +
				"		then 0" +
				"		else (local:common-count($ext1, $ext2) + local:common-count($impl1, $impl2)) div $max"
		).doubleValue();
		assert r >= 0 && r <= 1;
		return r;
	}
	
	/**
	 * Calculates a fraction between 0 and 1 that expresses the magnitude of references between
	 * the two types.  For example, having one type extend another makes them very close, but
	 * having one type declare another as a thrown exception doesn't do much.
	 *
	 * @param e1 a class or interface node
	 * @param e2 a class or interface node
	 * @return a type cross-ref fraction between 0 and 1
	 */
	private double typeCrossRefFraction(Node e1, Node e2) {
		double typeRefCost = 0.0;
		for (Node typeParent : e1.query().let("e1", e1).let("e2", e2).unordered(
				"($e1//type[text() eq $e2/@implName] | $e2//type[text() eq $e1/@implName])/..").nodes()) {
			QName qname = typeParent.qname();
			if (!Namespace.JAVA.equals(qname.getNamespaceURI())) continue;
			typeRefCost = TYPE_REF_COST_PER_CONTEXT.get(qname.getLocalPart());
			String modifiers = typeParent.query().presub()
					.optional("$1/@modifiers", MODIFIER_LOCATION_PER_CONTEXT.get(qname.getLocalPart())).value();
			if (modifiers != null) {
				StringTokenizer st = new StringTokenizer(modifiers);
				while(st.hasMoreTokens()) {
					Double multiplier = COST_MULTIPLIER_PER_MODIFIER.get(st.nextToken());
					if (multiplier != null) typeRefCost *= multiplier;
				}
			}
		}
		double r = 1 - 1 / Math.pow(2, typeRefCost / TYPE_REF_HALFWAY_POINT);
		assert r >= 0 && r <= 1;
		return r;
	}

	/**
	 * The type ref halfway point is the cost value that will give a crossref fraction of 0.5.
	 * It's used to scale the asymptotic function that converts a potentially unbounded cost
	 * sum into a 0 to 1 fraction.
	 */
	private static final double TYPE_REF_HALFWAY_POINT = 250;
	
	private static final Map<String, Double>
		TYPE_REF_COST_PER_CONTEXT = new HashMap<String, Double>(),
		COST_MULTIPLIER_PER_MODIFIER = new HashMap<String, Double>();
	private static final Map<String, String>
		MODIFIER_LOCATION_PER_CONTEXT = new HashMap<String, String>();

	private static void initContext(String element, double typeRefCost, String location) {
		TYPE_REF_COST_PER_CONTEXT.put(element, typeRefCost);
		MODIFIER_LOCATION_PER_CONTEXT.put(element, location);
	}
	
	static {
		initContext("extends",			250.0, "..");
		initContext("implements",	150.0, "..");
		initContext("field",				50.0, ".");
		initContext("method",			35.0, ".");
		initContext("param",			25.0, "..");
		initContext("throws",			2.0, "..");
		// TODO: add type parameter dependencies?

		// all relative to the default (package-scope) visibility modifier
		COST_MULTIPLIER_PER_MODIFIER.put("public", 2.0);
		COST_MULTIPLIER_PER_MODIFIER.put("protected", 1.2);
		COST_MULTIPLIER_PER_MODIFIER.put("private", 0.2);
		
	}
	
	@Deprecated @SuppressWarnings("deprecation") @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class _Test extends DatabaseTestCase {
		private ClassAssignmentCalculator calc;
		@Before public void setUp() {calc = new ClassAssignmentCalculator();}
		@After public void tearDown() {calc = null;}
		
		@Test public void commonParentTypes1() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class").end("class").commit();
			Node e2 = root.append().elem("class").end("class").commit();
			assertEquals(0.0, calc.commonParentTypesFraction(e1, e2), 0.0);
		}
		
		@Test public void commonParentTypes2() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class").elem("extends").elem("type").text("Noodle").end("type").end("extends").end("class").commit();
			Node e2 = root.append().elem("class").elem("extends").elem("type").text("Noodle").end("type").end("extends").end("class").commit();
			assertEquals(1.0, calc.commonParentTypesFraction(e1, e2), 0.0);
		}
		
		@Test public void commonParentTypes3() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class").elem("implements").elem("type").text("Noodle").end("type").end("implements").end("class").commit();
			Node e2 = root.append().elem("class").elem("implements").elem("type").text("Noodle").end("type").end("implements").end("class").commit();
			assertEquals(1.0, calc.commonParentTypesFraction(e1, e2), 0.0);
		}
		
		@Test public void commonParentTypes4() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class")
				.elem("extends").elem("type").text("Noodle").end("type").end("extends")
			.end("class").commit();
			Node e2 = root.append().elem("class")
				.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.elem("implements").elem("type").text("Spaghetti").end("type").end("implements")
			.end("class").commit();
			assertEquals(0.5, calc.commonParentTypesFraction(e1, e2), 0.0);
		}
		
		@Test public void commonParentTypes5() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class").elem("extends").elem("type").text("Noodle").end("type").end("extends").end("class").commit();
			Node e2 = root.append().elem("class").elem("implements").elem("type").text("Noodle").end("type").end("implements").end("class").commit();
			assertEquals(0.0, calc.commonParentTypesFraction(e1, e2), 0.0);
		}

		@Test public void commonParentTypes6() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class")
				.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.elem("extends").elem("type").text("Spaghetti").end("type").end("extends")
			.end("class").commit();
			Node e2 = root.append().elem("class")
				.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.elem("extends").elem("type").text("Spaghetti").end("type").end("extends")
			.end("class").commit();
			assertEquals(1.0, calc.commonParentTypesFraction(e1, e2), 0.0);
		}

		@Test public void commonParentTypes7() {
			Node root = makeRoot();
			Node e1 = root.append().elem("class")
				.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.elem("extends").elem("type").text("Spaghetti").end("type").end("extends")
			.end("class").commit();
			Node e2 = root.append().elem("class")
				.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.elem("extends").elem("type").text("Lasagna").end("type").end("extends")
			.end("class").commit();
			assertEquals(0.5, calc.commonParentTypesFraction(e1, e2), 0.0);
		}

		@Test public void typeCrossRef1() {
			Node root = makeRoot();
			Node e1 = root.append()
				.elem("class").attr("implName", "Spaghetti").attr("modifiers", "")
					.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.end("class").commit();
			Node e2 = root.append()
				.elem("class").attr("implName", "Noodle").attr("modifiers", "")
				.end("class").commit();
			assertEquals(0.5, calc.typeCrossRefFraction(e1, e2), 0.0);
		}
		
		@Test public void typeCrossRef2() {
			Node root = makeRoot();
			Node e1 = root.append()
				.elem("class").attr("implName", "Spaghetti").attr("modifiers", "public")
					.elem("extends").elem("type").text("Noodle").end("type").end("extends")
				.end("class").commit();
			Node e2 = root.append()
				.elem("class").attr("implName", "Noodle").attr("modifiers", "")
				.end("class").commit();
			assertEquals(0.75, calc.typeCrossRefFraction(e1, e2), 0.0);
		}
		
		@Test public void typeCrossRef3() {
			Node root = makeRoot();
			Node e1 = root.append()
				.elem("class").attr("implName", "Spaghetti").attr("modifiers", "")
					.elem("field").attr("modifiers", "").elem("type").text("Noodle").end("type").end("field")
				.end("class").commit();
			Node e2 = root.append()
				.elem("class").attr("implName", "Noodle").attr("modifiers", "")
				.end("class").commit();
			assertEquals(0.13, calc.typeCrossRefFraction(e1, e2), 0.01);
		}
		
		@Test public void typeCrossRef4() {
			Node root = makeRoot();
			Node e1 = root.append()
				.elem("class").attr("implName", "Spaghetti").attr("modifiers", "public")
					.elem("field").attr("modifiers", "").elem("type").text("Noodle").end("type").end("field")
				.end("class").commit();
			Node e2 = root.append()
				.elem("class").attr("implName", "Noodle").attr("modifiers", "")
				.end("class").commit();
			assertEquals(0.13, calc.typeCrossRefFraction(e1, e2), 0.01);
		}
		
		@Test public void typeCrossRef5() {
			Node root = makeRoot();
			Node e1 = root.append()
				.elem("class").attr("implName", "Spaghetti").attr("modifiers", "")
					.elem("field").attr("modifiers", "public").elem("type").text("Noodle").end("type").end("field")
				.end("class").commit();
			Node e2 = root.append()
				.elem("class").attr("implName", "Noodle").attr("modifiers", "")
				.end("class").commit();
			assertEquals(0.24, calc.typeCrossRefFraction(e1, e2), 0.01);
		}
		
		private Node makeRoot() {
			Folder folder = db.createFolder("/test");
			folder.namespaceBindings().putAll(NAMESPACE_BINDINGS);
			Node root = folder.documents().build(Name.create("code")).elem("unit").end("unit").commit().root();
			return root;
		}
	}
	
}