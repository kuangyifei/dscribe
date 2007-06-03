package com.ideanest.dscribe.opti;



public class SimulatedAnnealingOptimizer<S extends Solution> {
	
	private final AnnealingStrategy strategy;

	public SimulatedAnnealingOptimizer(AnnealingStrategy strategy) {
		this.strategy = strategy;
	}
	
	@SuppressWarnings("unchecked")
	public S optimize(S initialCandidate) {
		strategy.reset();
		S solution = initialCandidate;
		while(solution.cost() > Double.NEGATIVE_INFINITY && strategy.step()) {
			S candidate = (S) solution.randomNeighbor();
			double delta = candidate.cost() - solution.cost();
			if (delta == 0) continue;
			if (delta < 0 || strategy.accept(delta)) solution = candidate;
		}
		return solution;
	}

}
