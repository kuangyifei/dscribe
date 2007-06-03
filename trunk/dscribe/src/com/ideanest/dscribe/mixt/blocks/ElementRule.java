package com.ideanest.dscribe.mixt.blocks;

import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.exist.fluent.QueryService.QueryAnalysis.Cardinality;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.Engine.RuleFactory;
import com.ideanest.dscribe.mixt.Query.Items;
import com.ideanest.dscribe.mixt.Rule.BindingNotSatisfiedException;

class ElementRule extends Rule {
	
	private static final Logger LOG = Logger.getLogger(ElementRule.class);
	
	static final Engine.RuleFactory FACTORY = new RuleFactory("//element") {
		@Override protected Rule create(Node def, Node prevDef, Engine engine) throws RuleBaseException {
			return new ElementRule(def, prevDef, engine);
		}
	};
	
	private final Query.Items target;
	private final boolean uniqueTarget;
	private final List<Insert> inserts;
	
	public ElementRule(Node def, Node prevDef, Engine engine) throws RuleBaseException {
		super(def, prevDef, engine);

		changeTracker.define("target",	"target | source | bind"						);
		changeTracker.define("result",		"insert | source | bind"						);
		changeTracker.define("override",	"override | target | source | bind"	);
		
		try {
			target = new Query.Items(def.query().single("target").node());
			// use any query service as basis for the analysis below
			Cardinality cardinality = target.analyze(def.query()).cardinality();
			uniqueTarget = cardinality == Cardinality.ZERO_OR_ONE || cardinality == Cardinality.ONE;
			inserts = defineMultiple(def.query().all("insert"), Insert.class);
			// TODO: deal with overrides
		} catch (DatabaseException e) {
			throw new RuleBaseException(this + " definition in error", e);
		}
	}

	@Override protected boolean singleModPerSource() {return uniqueTarget;}
	
	@Override protected boolean needGlobalModSearch() {
		return super.needGlobalModSearch() || changeTracker.hasChanged("target");
	}

	@Override protected Mod createMod(String caseId, QueryService modQueryService) {
		return new ElementMod(caseId, modQueryService);
	}

	@Override protected Collection<Mod> resolveMods(QueryService modQueryService) {
		// TODO: implement
	}
	
	
	class ElementMod extends Mod {
		final Node targetNode;
		
		ElementMod(String caseId, QueryService queryService) {
			super(caseId, queryService);
		}

		@Override void apply() {
			LOG.debug("applying " + this + " for " + this.engine);
			apply(createResults());
		}
		
		void apply(List<Node> results) {
			// TODO: process overrides
			targetSpec.insertResults(target, results);
			decorate(sourceNode, "source", false);
			decorate(target, "target", false);
			engine.numModsApplied.increment();
			// TODO: if target has children with order mods, queue target for order check
		}

		List<Node> createResults() {
			List<Node> results = new ArrayList<Node>();
			int resultIdCounter = 0;
			for (Insert insert : inserts) {
				ItemList constructedNodes = insert.construct(queryService);
				for (Node node : constructedNodes.nodes()) {
					decorateResult(node, ++resultIdCounter, insert);
					for (Node descendant : node.query().all("descendant::*").nodes()) {
						decorateResult(descendant, ++resultIdCounter, null);
					}
				}
				results.addAll(Arrays.asList(constructedNodes.nodes().toArray()));
			}
			return results;
		}
		
		@Override void verify() {
			if (!(engine.mayHaveBeenModified(sourceNode) || engine.mayHaveBeenModified(targetNode) || changeTracker.hasChanged("target"))) {
				Engine.LOG.debug("no need to verify " + this + " for " + ElementRule.this);
				return;
			}
			Engine.LOG.debug("verifying " + this + " for " + ElementRule.this);
			List<Node> newResults = createResults();
			boolean match = target.query().single("count(descendant::mod:app[@case=$_1])", caseId()).intValue() == newResults.size();
			if (match) {
				for (Node result : newResults) {
					if (!verify(result, target)) {
						match = false;
						break;
					}
				}
			}
			engine.numModsVerified.increment();
			if (!match) {
				Engine.LOG.debug("verification failed for " + this + "; refreshing");
				withdraw();
				apply(newResults);
			}
		}
		
		boolean verify(Node result, Node matchParent) {
			return matchParent.query().single(
					"function local:verify($a as element(), $parent as element()) as xs:boolean { \n" +
					"  let $b := $parent/*[@xml:id=$a/@xml:id] \n" +
					"  return \n" +
					"		if (empty($b)) then false() else \n" +
					"		deep-equal( \n" +
					"			element {node-name($a)} {$a/@*, $a/text()}, \n" +
					"  		element {node-name($b)} {$b/@*, $b/text()} \n" +
					"		) and every $child in $a/* satisfies local:verify($child, $b) \n" +
					"}; \n" +
					"local:verify($_1, $_2)", result, matchParent).booleanValue();
		}
	}

	static class Insert {
		private final boolean orderAsSource;
		private final int orderWeight;
		private final String query;
		
		Insert(Node def) {
			Item orderAttr = def.query().optional("@order-as-source-with-weight");
			if (orderAttr.extant()) {
				orderAsSource = true;
				orderWeight = orderAttr.intValue();
			} else {
				orderAsSource = false;
				orderWeight = 0;
			}
			query = def.value();
		}
		
		ItemList construct(QueryService qs) {
			return qs.all(query);
		}
		
		boolean isOrderAsSource() {
			return orderAsSource;
		}
	}
	
}
