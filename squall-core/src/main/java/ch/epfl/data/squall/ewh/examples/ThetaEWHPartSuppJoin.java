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


package ch.epfl.data.squall.ewh.examples;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ch.epfl.data.squall.components.Component;
import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.components.theta.ThetaJoinComponentFactory;
import ch.epfl.data.squall.ewh.components.DummyComponent;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.operators.PrintOperator;
import ch.epfl.data.squall.operators.ProjectOperator;
import ch.epfl.data.squall.predicates.ComparisonPredicate;
import ch.epfl.data.squall.query_plans.QueryBuilder;
import ch.epfl.data.squall.query_plans.QueryPlan;
import ch.epfl.data.squall.query_plans.ThetaQueryPlansParameters;
import ch.epfl.data.squall.types.IntegerType;
import ch.epfl.data.squall.types.NumericType;
import ch.epfl.data.squall.types.StringType;
import ch.epfl.data.squall.types.Type;
import ch.epfl.data.squall.utilities.MyUtilities;
import ch.epfl.data.squall.utilities.SystemParameters;
import ch.epfl.data.squall.utilities.SystemParameters.HistogramType;

// a candidate for new Eocd for the new Linux cluster
public class ThetaEWHPartSuppJoin extends QueryPlan{

	private QueryBuilder _queryBuilder = new QueryBuilder();
	private static final Type<String> _stringConv = new StringType();
	private static final IntegerType _ic = new IntegerType();

	// availqty
	public ThetaEWHPartSuppJoin(String dataPath, String extension, Map conf) {
		// creates materialized relations
		boolean printSelected = MyUtilities.isPrintFilteredLast(conf);
		String matName1 = "partsupp_qty_1";
		String matName2 = "partsupp_qty_2";
		PrintOperator print1 = printSelected ? new PrintOperator(matName1
				+ extension, conf) : null;
		PrintOperator print2 = printSelected ? new PrintOperator(matName2
				+ extension, conf) : null;
		// read from materialized relations
		boolean isMaterialized = SystemParameters.isExisting(conf,
				"DIP_MATERIALIZED")
				&& SystemParameters.getBoolean(conf, "DIP_MATERIALIZED");
		boolean isOkcanSampling = SystemParameters.isExisting(conf,
				"DIP_SAMPLING")
				&& SystemParameters.getBoolean(conf, "DIP_SAMPLING");
		boolean isEWHSampling = SystemParameters.isExisting(conf,
				"DIP_EWH_SAMPLING")
				&& SystemParameters.getBoolean(conf, "DIP_EWH_SAMPLING");
		boolean isEWHD2Histogram = SystemParameters.getBooleanIfExist(conf,
				HistogramType.D2_COMB_HIST.genConfEntryName());
		boolean isEWHS1Histogram = SystemParameters.getBooleanIfExist(conf,
				HistogramType.S1_RES_HIST.genConfEntryName());
		boolean isSrcHistogram = isEWHD2Histogram || isEWHS1Histogram;

		Component relationPartSupp1, relationPartSupp2;
		// Project on availqty(key), partkey and suppkey
		ProjectOperator projectionCustomer = new ProjectOperator(new int[] { 2,
				0, 1 });

		// with z2 too large output

		final List<Integer> hashCustomer = Arrays.asList(0);

		if (!isMaterialized) {
			relationPartSupp1 = new DataSourceComponent("PARTSUPP1", dataPath
					+ "partsupp" + extension).add(print1)
					.add(projectionCustomer).setOutputPartKey(hashCustomer);
			_queryBuilder.add(relationPartSupp1);

			relationPartSupp2 = new DataSourceComponent("PARTSUPP2", dataPath
					+ "partsupp" + extension).add(print2)
					.add(projectionCustomer).setOutputPartKey(hashCustomer);
			_queryBuilder.add(relationPartSupp2);
		} else {
			relationPartSupp1 = new DataSourceComponent("PARTSUPP1", dataPath
					+ matName1 + extension).add(projectionCustomer)
					.setOutputPartKey(hashCustomer);
			_queryBuilder.add(relationPartSupp1);

			relationPartSupp2 = new DataSourceComponent("PARTSUPP2", dataPath
					+ matName2 + extension).add(projectionCustomer)
					.setOutputPartKey(hashCustomer);
			_queryBuilder.add(relationPartSupp2);
		}

		NumericType keyType = _ic;
		ComparisonPredicate comparison = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP);
		int firstKeyProject = 0;
		int secondKeyProject = 0;

		if (printSelected) {
			relationPartSupp1.setPrintOut(false);
			relationPartSupp2.setPrintOut(false);
		} else if (isSrcHistogram) {
			_queryBuilder = MyUtilities.addSrcHistogram(relationPartSupp1,
					firstKeyProject, relationPartSupp2, secondKeyProject,
					keyType, comparison, isEWHD2Histogram, isEWHS1Histogram,
					conf);
		} else if (isOkcanSampling) {
			_queryBuilder = MyUtilities.addOkcanSampler(relationPartSupp1,
					relationPartSupp2, firstKeyProject, secondKeyProject,
					_queryBuilder, keyType, comparison, conf);
		} else if (isEWHSampling) {
			_queryBuilder = MyUtilities.addEWHSampler(relationPartSupp1,
					relationPartSupp2, firstKeyProject, secondKeyProject,
					_queryBuilder, keyType, comparison, conf);
		} else {
			final int Theta_JoinType = ThetaQueryPlansParameters
					.getThetaJoinType(conf);
			final ColumnReference colPS1 = new ColumnReference(keyType,
					firstKeyProject);
			final ColumnReference colPS2 = new ColumnReference(keyType,
					secondKeyProject);
			// Addition expr2 = new Addition(colO2, new ValueSpecification(_ic,
			// keyOffset));
			final ComparisonPredicate PS1_PS2_comp = new ComparisonPredicate(
					ComparisonPredicate.EQUAL_OP, colPS1, colPS2);

			// AggregateCountOperator agg = new AggregateCountOperator(conf);
			Component lastJoiner = ThetaJoinComponentFactory
					.createThetaJoinOperator(Theta_JoinType, relationPartSupp1,
							relationPartSupp2, _queryBuilder)
					.setJoinPredicate(PS1_PS2_comp)
					.setContentSensitiveThetaJoinWrapper(keyType);
			// .addOperator(agg)
			// lastJoiner.setPrintOut(false);

			DummyComponent dummy = new DummyComponent(lastJoiner, "DUMMY");
			_queryBuilder.add(dummy);
		}

	}

	public QueryBuilder getQueryPlan() {
		return _queryBuilder;
	}
}