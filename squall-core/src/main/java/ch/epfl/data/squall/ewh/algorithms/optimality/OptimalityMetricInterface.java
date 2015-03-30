package ch.epfl.data.squall.ewh.algorithms.optimality;

import ch.epfl.data.squall.ewh.data_structures.Region;

public interface OptimalityMetricInterface {

	// actual maxRegionWeight from existing regions
	public double getActualMaxRegionWeight();

	/*
	 * 
	 * return value is better if it is lower
	 */
	public double getOptimalityDistance();

	// according to wf; regions are final(non-coarsened) regions
	public double getWeight(Region region);

}
