package ch.epfl.data.squall.query_plans.theta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.epfl.data.squall.components.Component;
import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.components.theta.ThetaJoinComponentFactory;
import ch.epfl.data.squall.conversion.DoubleConversion;
import ch.epfl.data.squall.conversion.NumericConversion;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.operators.AggregateOperator;
import ch.epfl.data.squall.operators.AggregateSumOperator;
import ch.epfl.data.squall.operators.ProjectOperator;
import ch.epfl.data.squall.query_plans.QueryBuilder;
import ch.epfl.data.squall.query_plans.QueryPlan;

public class ThetaOutputDominatedPlan extends QueryPlan {

	private static Logger LOG = Logger
			.getLogger(ThetaOutputDominatedPlan.class);

	private final QueryBuilder _queryBuilder = new QueryBuilder();

	private static final NumericConversion<Double> _doubleConv = new DoubleConversion();

	/*
	 * SELECT SUM(SUPPLIER.SUPPKEY) FROM SUPPLIER, NATION
	 */
	public ThetaOutputDominatedPlan(String dataPath, String extension, Map conf) {
		final int Theta_JoinType = ThetaQueryPlansParameters
				.getThetaJoinType(conf);
		// -------------------------------------------------------------------------------------
		final List<Integer> hashSupplier = Arrays.asList(0);

		final ProjectOperator projectionSupplier = new ProjectOperator(
				new int[] { 0 });

		final DataSourceComponent relationSupplier = new DataSourceComponent(
				"SUPPLIER", dataPath + "supplier" + extension).add(
				projectionSupplier).setOutputPartKey(hashSupplier);
		_queryBuilder.add(relationSupplier);

		// -------------------------------------------------------------------------------------
		final List<Integer> hashNation = Arrays.asList(0);

		final ProjectOperator projectionNation = new ProjectOperator(
				new int[] { 1 });

		final DataSourceComponent relationNation = new DataSourceComponent(
				"NATION", dataPath + "nation" + extension)
				.add(projectionNation).setOutputPartKey(hashNation);
		_queryBuilder.add(relationNation);

		final AggregateOperator agg = new AggregateSumOperator(
				new ColumnReference(_doubleConv, 0), conf);
		// Empty parameters = Cartesian Product

		Component lastJoiner = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, relationSupplier,
						relationNation, _queryBuilder)
				.add(new ProjectOperator(new int[] { 0 })).add(agg);
		// lastJoiner.setPrintOut(false);

		// -------------------------------------------------------------------------------------

	}

	public QueryBuilder getQueryPlan() {
		return _queryBuilder;
	}
}
