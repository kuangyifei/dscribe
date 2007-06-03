package com.ideanest.dscribe.opti;

/**
 * A solution to an optimization problem.  Implementations of this interface must be immutable.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public interface Solution {
	
	/**
	 * Return the calculated cost of this solution.  The lower the cost, the better the solution.
	 * A cost of +inf indicates an unacceptable solution, and a cost of -inf indicates a perfect
	 * solution.
	 *
	 * @return the cost of the solution, can be cached
	 */
	double cost();
	
	/**
	 * Return another solution in the neighbourhood of this one.
	 *
	 * @return a solution derived (randomly) from this one
	 */
	Solution randomNeighbor();

}
