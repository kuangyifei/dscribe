package com.ideanest.dscribe.opti;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.*;
import com.ideanest.dscribe.job.*;

/**
 * Encapsulates the assignment of orphan Java elements to diagrams.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class AnnealingDiagramAssigner extends TaskBase {
	
	/**
	 * A plugin that specializes the diagram assigner for a certain type of element.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public interface Calculator {
		
		/**
		 * Return the desired namespace mappings that will be put into effect on all queries related
		 * to this calculator.  The prefix <code>rules</code> is reserved for the framework.
		 * 
		 * @return the desired mappings from prefix to namespace URI 
		 */
		NamespaceMap getNamespaceBindings();
		
		/**
		 * Return a query fragment that will select all elements in the context
		 * that this plugin can calculate costs for.  The fragment will be evaluated with the
		 * namespace mappings provided by this calculator.
		 *
		 * @return a query fragment that finds all nested target elements and is suitable for suffixing with predicates
		 */
		String getElementQueryFragment();
		
		/**
		 * Calculate the cost of having the two elements in the same diagram.
		 * The cost relation must be symmetric.  The two elements will apply the namespace
		 * mappings provided by this calculator to any further queries.
		 * 
		 * @param e1 the first element, always of the type serviced by this plugin
		 * @param e2 the other element, may be of any type
		 * @return the cost, in the range of -1000 to +1000; lower values indicate affinity, higher values a clash
		 */
		double calculateCost(Node e1, Node e2);
	}
	

	private static final Logger LOG = Logger.getLogger(AnnealingDiagramAssigner.class);
	private static final Random random = new Random();
	
	private List<Calculator> calculators = new ArrayList<Calculator>();
	
	@Override
	protected void init(Node taskDef) {
		for (String calculatorClassName : taskDef.query().all("calculator/@class").values()) {
			try {
				Calculator calc = (Calculator) Class.forName(calculatorClassName).newInstance();
				if (calc.getNamespaceBindings().get("rules") != null) throw new InvalidConfigurationException("calculator maps reserved namespace prefix 'rules'");
				calculators.add(calc);
			} catch (Exception e) {
				LOG.warn("failed to instantiate calculator class " + calculatorClassName, e);
			}
		}
	}
	
	@Phase
	public void preprules() {
		for (Calculator calc : calculators) new AssignmentRun(calc, cycle()).run();
	}
	
	/**
	 * Given two dot-separated strings, returns the number of initial tokens they have in
	 * common divided by the maximum number they could possible have in common given
	 * their lengths.
	 *
	 * @param dottedName1 one dot-separated name
	 * @param dottedName2 the other dot-separated name
	 * @return the fraction of initial (prefix) tokens the two names have in common, between <code>0.0</code> and <code>1.0</code> inclusive
	 */
	public static double commonPrefixFraction(String dottedName1, String dottedName2) {
		StringTokenizer
		st1 = new StringTokenizer(dottedName1, "."),
		st2 = new StringTokenizer(dottedName2, ".");
		final int baseCount = Math.min(st1.countTokens(), st2.countTokens());
		int matchCount = 0;
		do {
			if (!st1.nextToken().equals(st2.nextToken())) break;
			matchCount++;
		} while (st1.hasMoreTokens() && st2.hasMoreTokens());
		double r = ((double) matchCount) / baseCount;
		assert r >= 0.0 && r <= 1.0;
		return r;
	}

	/**
	 * Computes Levenshtein distance between the two strings, and returns it as the complement
	 * of the fraction of the maximum possible distance.  So if the two strings are the same, the
	 * commonality fraction is <code>1.0</code> and if they have nothing in common, the fraction
	 * is <code>0.0</code>.
	 *
	 * @param s1 one string to analyze
	 * @param s2 the other string to analyze
	 * @return the fraction of commonality between the strings, between <code>0.0</code> and <code>1.0</code> inclusive
	 */
	public static double stringCommonalityFraction(String s1, String s2) {
		final int l1 = s1.length(), l2 = s2.length();
		if (l1 == 0 && l2 == 0) return 1.0;
		if (l1 == 0 || l2 == 0) return 0.0;
		int[] m1 = new int[l2+1], m2 = new int[l2+1], m3;
		for (int j=0; j<=l2; j++) m1[j] = j;
		for (int i=1; i<=l1; i++) {
			m2[0] = m1[0] + 1;
			for (int j=1; j<=l2; j++) {
				m2[j] = Math.min(m1[j-1] + (s1.charAt(i-1) == s2.charAt(j-1) ? 0 : 1),
						Math.min(m1[j] + 1, m2[j-1] + 1));
			}
			m3 = m1; m1 = m2; m2 = m3;
		}
		double r = 1.0 - ((double) m1[l2]) / Math.max(l1, l2);
		assert r >= 0.0 && r <= 1.0;
		return r;
	}

	private static class AssignmentRun {
		
		private final Calculator plugin;
		private final Folder workspace;
		private final Cycle job;
		private final List<Diagram> diagrams = new ArrayList<Diagram>();
		private final List<Orphan> orphans = new ArrayList<Orphan>();
		private SymmetricTable orphanClashes;
		
		AssignmentRun(Calculator plugin, Cycle job) {
			this.plugin = plugin;
			this.job = job;
			this.workspace = job.workspace(plugin.getNamespaceBindings());
			this.workspace.namespaceBindings().put("mapping", Namespace.MAPPING);
		}
		
		public void run() {
			initOrphans();
			if (orphans.size() == 0) return;
			initDiagrams();
			// clear out nodes, no need to cache them any more
			for (Orphan orphan : orphans) orphan.node = null;
			
			Assignment ass = new Assignment();
			SimulatedAnnealingOptimizer<Assignment> sao = new SimulatedAnnealingOptimizer<Assignment>(
					new GeometricAnnealingStrategy(1000, 10, 0.9, 500));		// TODO: tweak parameters based on dataset size?
			ass = sao.optimize(ass);
			
			emitRules(ass);
		}
		
		private void initOrphans() {
			ItemList elements = workspace.query().presub().unordered(
					"let $targets := //mapping:java-element-to-diagram/@java-element " +
					"return $1[not(@xml:id = $targets)]", plugin.getElementQueryFragment());
			orphanClashes = new SymmetricTable(elements.size());
			int i=-1;
			for (Node node : elements.nodes()) {
				orphans.add(new Orphan(node, ++i));
				diagrams.add(new Diagram());
				for (int j=0; j<i; j++) orphanClashes.set(i, j, plugin.calculateCost(node, orphans.get(j).node));
			}
		}
		
		private void initDiagrams() {
			// find all diagrams that have at least one element of the desired kind already assigned
			ItemList elements = workspace.query().let("workspace", workspace).presub().unordered(
					"let " +
					"	$mappings := //mapping:java-element-to-diagram " +
					"return " +
					"	for $diagramId in distinct-values($mappings/@diagram) " +
					"	where exists($1[@xml:id = $mappings[@diagram = $diagramId]/@java-element]) " +
					"	return $diagramId", plugin.getElementQueryFragment());
			for (String diagramId : elements.values()) {
				double[] orphanCosts = new double[orphans.size()];
				ItemList elementsInDiagram = workspace.query().unordered(
						"//*[@xml:id = //mapping:java-element-to-diagram[@diagram=$_1]/@java-element]", diagramId);
				// a diagram is useless if no orphan could possibly be placed in it
				boolean uselessDiagram = true;
				for (Orphan orphan : orphans) {
					double cost = 0.0;
					for (Node otherNode : elementsInDiagram.nodes()) cost += plugin.calculateCost(orphan.node, otherNode);
					cost /= elementsInDiagram.size();
					orphanCosts[orphan.ordinal] = cost;
					uselessDiagram &= cost == Double.POSITIVE_INFINITY;
				}
				if (!uselessDiagram) diagrams.add(new Diagram(diagramId, orphanCosts));
			}
		}
		
		private void emitRules(Assignment ass) {
			ElementBuilder<Node> builder = workspace.query().unordered("//mapping:mappings").get(0).node().append();
			for (Orphan orphan : orphans) {
				Diagram diagram = ass.get(orphan);
				if (diagram.id == null) {
					diagram.id = job.generateUid("d");
					builder.elem("mapping:create-diagram")
						.attr("xml:id", job.generateUid("m"))
						.attr("diagram", diagram.id)
						.attr("kind", "class")
						.end("mapping:create-diagram");
				}
				builder.elem("mapping:java-element-to-diagram")
					.attr("xml:id", job.generateUid("m"))
					.attr("diagram", diagram.id)
					.attr("java-element", orphan.id)
					.end("mapping:java-element-to-diagram");
			}
			builder.commit();
		}
		
		
		private class Assignment implements Solution {
			
			private final int[] orphansToDiagrams;
			private double cost = Double.NaN;
			
			Assignment() {
				if (orphans.size() == 1) {
					// If only one orphan, check if it must be assigned to a new diagram -- if so, we're done.
					// If more than one orphan, even if none of the existing diagrams fit, we must still go
					// through the optimization to see if they can be combined in a new diagram. 
					int numAcceptableDiagrams = 0;
					for (Diagram diagram : diagrams)
						if (diagram.cost(orphans) < Double.POSITIVE_INFINITY) numAcceptableDiagrams++;
					assert numAcceptableDiagrams > 0;
					if (numAcceptableDiagrams == 1) cost = Double.NEGATIVE_INFINITY;
				}
				orphansToDiagrams = new int[orphans.size()];
				for (int i=0; i<orphansToDiagrams.length; i++) orphansToDiagrams[i] = i;
			}
			
			private Assignment(int[] orphansToDiagrams) {
				this.orphansToDiagrams = orphansToDiagrams;
			}
			
			@SuppressWarnings("unchecked")
			public double cost() {
				if (Double.isNaN(cost)) {
					List<Orphan>[] perDiagramAssignments = new List[diagrams.size()];
					for (int i=0; i<orphansToDiagrams.length; i++) {
						int dindex = orphansToDiagrams[i];
						List<Orphan> list = perDiagramAssignments[dindex];
						if (list == null) {
							list = new LinkedList<Orphan>();
							perDiagramAssignments[dindex] = list;
						}
						list.add(orphans.get(i));
					}
					cost = 0.0;
					int i=0;
					for (Diagram diagram : diagrams) cost += diagram.cost(perDiagramAssignments[i++]); 
					cost /= diagrams.size();
				}
				return cost;
			}
			
			public Solution randomNeighbor() {
				int orphanIndex = random.nextInt(orphans.size());
				Orphan orphan = orphans.get(orphanIndex);
				int currentTargetIndex = orphansToDiagrams[orphanIndex]; 
				int newTargetIndex;
				do {
					newTargetIndex = random.nextInt(diagrams.size());
				} while (
						currentTargetIndex == newTargetIndex
						|| !diagrams.get(newTargetIndex).acceptable(orphan)
				);
				int[] newOrphansToDiagrams = orphansToDiagrams.clone();
				newOrphansToDiagrams[orphanIndex] = newTargetIndex;
				return new Assignment(newOrphansToDiagrams);
			}
			
			Diagram get(Orphan orphan) {
				return diagrams.get(orphansToDiagrams[orphan.ordinal]);
			}
		}
		
		private class Diagram {
			
			private String id;
			private final int numFixedElements;
			private final double[] orphanCosts;
			
			/**
			 * Create an empty proposed diagram handle.
			 */
			Diagram() {
				this(null, null);
			}
			
			Diagram(String id, double[] orphanCosts) {
				this.id = id;
				this.numFixedElements = id == null ? 0
						: workspace.query().single(
								"count(//mapping:java-element-to-diagram[@diagram=$_1])", id).intValue();
				this.orphanCosts = orphanCosts;
			}
			
			boolean acceptable(Orphan orphan) {
				return orphanCosts == null || orphanCosts[orphan.ordinal] < Double.POSITIVE_INFINITY;
			}
			
			double cost(List<Orphan> assignedOrphans) {
				if (assignedOrphans == null || assignedOrphans.isEmpty()) return 0.0;
				
				final int numAssignedOrphans = assignedOrphans.size();
				double cost = 0.0;
				
				for (Orphan orphan : assignedOrphans) {
					if (orphanCosts != null) cost += orphanCosts[orphan.ordinal] / numAssignedOrphans;
					if (numAssignedOrphans > 1) {
						double clashCost = 0.0;
						for (Orphan otherOrphan : assignedOrphans) {
							if (orphan != otherOrphan) clashCost += orphanClashes.get(orphan.ordinal, otherOrphan.ordinal);
						}
						cost += clashCost / (numAssignedOrphans * (numAssignedOrphans-1));
					}
				}
				cost += crowdingCost(numAssignedOrphans);
				
				return cost;
			}
			
			private double crowdingCost(int numExtraElements) {
				assert numExtraElements > 0;
				// TODO: take into consideration amount of space actually left on diagram, if available
				return Math.expm1(Math.abs(numFixedElements + numExtraElements - 5))*100;
			}
		}
		
		private class Orphan {
			final String id;
			private int ordinal;
			Node node;
			Orphan(Node node, int ordinal) {
				this.node = node;
				this.id = node.query().single("@xml:id").value();
				this.ordinal = ordinal;
			}
			double cost(Orphan orphan) {
				return orphanClashes.get(ordinal, orphan.ordinal);
			}
		}
		
	}
	
	private static class SymmetricTable {
		private final double[] values;
		SymmetricTable(int size) {
			values = new double[Math.max(0, size*(size-1)/2)];
		}
		double get(int a, int b) {
			return values[index(a,b)];
		}
		void set(int a, int b, double value) {
			values[index(a,b)] = value;
		}
		private int index(int a, int b) {
			if (a == b) throw new IndexOutOfBoundsException("attempt to access entry from diagonal at [" + a + "," + b + "]");
			if (a < 0 || b < 0) throw new IndexOutOfBoundsException("negative index [" + a +"," + b + "]");
			if (a < b) {int c=a; a=b; b=c;}
			return a*(a-1)/2+b;
		}
	}
	
	/**
	 * @deprecated for testing only
	 */
	@Deprecated
	public static class _Test {
		@Test public void commonPrefixFraction1() {
			assertEquals(0.0, AnnealingDiagramAssigner.commonPrefixFraction("com.ideanest.reef", "org.foo.blah.fuz"), 0);
		}
		@Test public void commonPrefixFraction2() {
			assertEquals(1.0, AnnealingDiagramAssigner.commonPrefixFraction("com.ideanest.reef", "com.ideanest.reef"), 0);
		}
		@Test public void commonPrefixFraction3() {
			assertEquals(1.0, AnnealingDiagramAssigner.commonPrefixFraction("com.ideanest.reef.db", "com.ideanest"), 0);
		}
		@Test public void commonPrefixFraction4() {
			assertEquals(1.0, AnnealingDiagramAssigner.commonPrefixFraction("com.ideanest", "com.ideanest.reef.db"), 0);
		}
		@Test public void commonPrefixFraction5() {
			assertEquals(0.5, AnnealingDiagramAssigner.commonPrefixFraction("com.ideanest.vb.blah", "com.ideanest.reef.db"), 0);
		}
		@Test public void stringCommonalityFraction1() {
			assertEquals(0.0, AnnealingDiagramAssigner.stringCommonalityFraction("abcd", "defg"), 0);
		}
		@Test public void stringCommonalityFraction2() {
			assertEquals(1.0, AnnealingDiagramAssigner.stringCommonalityFraction("", ""), 0);
		}
		@Test public void stringCommonalityFraction3() {
			assertEquals(0.0, AnnealingDiagramAssigner.stringCommonalityFraction("abcd", ""), 0);
		}
		@Test public void stringCommonalityFraction4() {
			assertEquals(0.0, AnnealingDiagramAssigner.stringCommonalityFraction("", "defg"), 0);
		}
		@Test public void stringCommonalityFraction5() {
			assertEquals(1.0, AnnealingDiagramAssigner.stringCommonalityFraction("abc", "abc"), 0);
		}
		@Test public void stringCommonalityFraction6() {
			assertEquals(0.75, AnnealingDiagramAssigner.stringCommonalityFraction("abcd", "abc"), 0);
		}
		@Test public void stringCommonalityFraction7() {
			assertEquals(0.75, AnnealingDiagramAssigner.stringCommonalityFraction("abc", "abcd"), 0);
		}
		@Test public void stringCommonalityFraction8() {
			assertEquals(1-(7.0/24), AnnealingDiagramAssigner.stringCommonalityFraction("IllegalArgumentException", "IllegalOptionException"), 0.01);
		}
	}

	
}
