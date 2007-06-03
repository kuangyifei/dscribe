package com.ideanest.dscribe.opti;

public interface AnnealingStrategy {

	/**
	 * Reset the strategy to initial conditions, ready for another run.
	 */
	void reset();

	/**
	 * Advance the strategy by one step forward, and return whether the simulation should keep
	 * running.
	 *
	 * @return <code>true</code> if this is a valid step, <code>false</code> if we're past the strategy end conditions
	 */
	boolean step();

	/**
	 * Decide whether to accept a new candidate solution with a positive cost delta to the previous one.
	 *
	 * @param costDelta the difference in cost between the new and old solutions; always positive
	 * @return <code>true</code> if the new candidate solution should be adopted, <code>false</code> if the previous solution should be retained
	 */
	boolean accept(double costDelta);

}
