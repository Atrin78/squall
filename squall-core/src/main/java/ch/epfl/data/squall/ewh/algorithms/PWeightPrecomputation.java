/*
 * Copyright (c) 2011-2015 EPFL DATA Laboratory
 * Copyright (c) 2014-2015 The Squall Collaboration (see NOTICE)
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package ch.epfl.data.squall.ewh.algorithms;

import ch.epfl.data.squall.ewh.algorithms.optimality.WeightFunction;
import ch.epfl.data.squall.ewh.data_structures.Region;
import ch.epfl.data.squall.ewh.data_structures.SimpleMatrix;

// p^2 points of rounded matrix precomputed
// coordinates of a region are coarsened
// kind of densePrecomputation
public class PWeightPrecomputation implements WeightPrecomputation {
	private WeightFunction _wf;
	private ShallowCoarsener _coarsener; // p^2 is for last point in each 2D
	// bucket from _sc (pxp matrix)

	private int[][] _prefixSum;
	private int _xSize, _ySize; // dimensions of the prefixSum int[][]

	public PWeightPrecomputation(WeightFunction wf, ShallowCoarsener sc,
			WeightPrecomputation samplePrecomputation) {
		_wf = wf;
		_coarsener = sc;
		_xSize = _coarsener.getNumXCoarsenedPoints();
		_ySize = _coarsener.getNumYCoarsenedPoints();
		_prefixSum = new int[_xSize][_ySize];

		precomputePSquare(samplePrecomputation);
	}

	public void addDeltaMatrix(SimpleMatrix deltaMatrix) {
		// compute prefix sum of the delta matrix
		DenseWeightPrecomputation deltaWP = new DenseWeightPrecomputation(_wf,
				deltaMatrix);

		// add the delta prefix sum to our prefix sum
		for (int i = 0; i < _xSize; i++) {
			for (int j = 0; j < _ySize; j++) {
				_prefixSum[i][j] += deltaWP.getPrefixSum(i, j);
			}
		}
	}

	@Override
	public int getFrequency(Region region) {
		int corner0x = region.getCorner(0).get_x() - 1;
		int corner0y = region.getCorner(0).get_y() - 1;
		int corner1x = region.getCorner(1).get_x() - 1;
		int corner1y = region.getCorner(1).get_y();
		int corner2x = region.getCorner(2).get_x();
		int corner2y = region.getCorner(2).get_y() - 1;
		int corner3x = region.getCorner(3).get_x(); // this point is inclusive
		int corner3y = region.getCorner(3).get_y();

		return getPrefixSum(corner3x, corner3y)
				- getPrefixSum(corner2x, corner2y)
				- getPrefixSum(corner1x, corner1y)
				+ getPrefixSum(corner0x, corner0y);
	}

	@Override
	public int getMinHalfPerimeterForWeight(double maxWeight) {
		throw new RuntimeException("Not implemented for now!");
	}

	@Override
	public int getPrefixSum(int coarsenedX, int coarsenedY) {
		if (coarsenedX < 0 || coarsenedY < 0) {
			return 0;
		} else {
			// both joinMatrix and prefixSum with originalToCoarsened
			// translation proved time-inefficient
			return _prefixSum[coarsenedX][coarsenedY];
		}
	}

	@Override
	public int getTotalFrequency() {
		return getPrefixSum(_xSize - 1, _ySize - 1);
	}

	// this is an exception: Region is an originalRegion!!!!!!!!!!!!
	// this is not a bottleneck (isEmpty is), and if we did not want to put many
	// "if (SystemParameters.COARSE_PRECOMPUTATION)"s all around the code
	@Override
	public double getWeight(Region originalRegion) {
		Region coarsenedRegion = _coarsener
				.translateOriginalToCoarsenedRegion(originalRegion);
		return _wf.getWeight(originalRegion.getHalfPerimeter(),
				getFrequency(coarsenedRegion));
	}

	@Override
	public WeightFunction getWeightFunction() {
		return _wf;
	}

	@Override
	public int getXSize() {
		return _xSize;
	}

	@Override
	public int getYSize() {
		return _ySize;
	}

	// this was the motivation for using coarsened representation
	// this dominates the whole execution as it is invoked from
	// Region.minimizeToNotEmptyCoarsened
	// which is invoked for each splitter within a rectangle in BSP
	@Override
	public boolean isEmpty(Region region) {
		return getFrequency(region) == 0;
	}

	// no need for further optimizations, because p is rather small
	// samplePrecomputation: n_s x n_s precomputation (possibly monotonic)
	private void precomputePSquare(WeightPrecomputation samplePrecomputation) {
		for (int ci = 0; ci < _xSize; ci++) {
			int i = _coarsener.getOriginalXCoordinate(ci, true);
			for (int cj = 0; cj < _ySize; cj++) {
				int j = _coarsener.getOriginalYCoordinate(cj, true);
				int prefix = samplePrecomputation.getPrefixSum(i, j);
				_prefixSum[ci][cj] = prefix;
			}
		}
	}

	@Override
	public String toString() {
		return _wf.toString();
	}

}
