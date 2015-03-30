package ch.epfl.data.squall.query_plans;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.components.EquiJoinComponent;
import ch.epfl.data.squall.conversion.DoubleConversion;
import ch.epfl.data.squall.conversion.IntegerConversion;
import ch.epfl.data.squall.conversion.NumericConversion;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.expressions.Multiplication;
import ch.epfl.data.squall.expressions.ValueExpression;
import ch.epfl.data.squall.expressions.ValueSpecification;
import ch.epfl.data.squall.operators.AggregateSumOperator;
import ch.epfl.data.squall.operators.SelectOperator;
import ch.epfl.data.squall.predicates.ComparisonPredicate;

public class RSTPlan extends QueryPlan {
	private static Logger LOG = Logger.getLogger(RSTPlan.class);

	private static final NumericConversion<Double> _dc = new DoubleConversion();
	private static final NumericConversion<Integer> _ic = new IntegerConversion();

	private final QueryBuilder _queryBuilder = new QueryBuilder();

	public RSTPlan(String dataPath, String extension, Map conf) {
		// -------------------------------------------------------------------------------------
		// start of query plan filling
		final List<Integer> hashR = Arrays.asList(1);

		final DataSourceComponent relationR = new DataSourceComponent("R",
				dataPath + "r" + extension).setOutputPartKey(hashR);
		_queryBuilder.add(relationR);

		// -------------------------------------------------------------------------------------
		final List<Integer> hashS = Arrays.asList(0);

		final DataSourceComponent relationS = new DataSourceComponent("S",
				dataPath + "s" + extension).setOutputPartKey(hashS);
		_queryBuilder.add(relationS);

		// -------------------------------------------------------------------------------------
		final List<Integer> hashIndexes = Arrays.asList(2);

		final EquiJoinComponent R_Sjoin = new EquiJoinComponent(relationR,
				relationS).setOutputPartKey(hashIndexes);
		_queryBuilder.add(R_Sjoin);

		// -------------------------------------------------------------------------------------
		final List<Integer> hashT = Arrays.asList(0);

		final DataSourceComponent relationT = new DataSourceComponent("T",
				dataPath + "t" + extension).setOutputPartKey(hashT);
		_queryBuilder.add(relationT);

		// -------------------------------------------------------------------------------------
		final ValueExpression<Double> aggVe = new Multiplication(
				new ColumnReference(_dc, 0), new ColumnReference(_dc, 3));

		final AggregateSumOperator sp = new AggregateSumOperator(aggVe, conf);

		EquiJoinComponent rstJoin = new EquiJoinComponent(R_Sjoin, relationT)
				.add(new SelectOperator(new ComparisonPredicate(
						new ColumnReference(_ic, 1), new ValueSpecification(
								_ic, 10)))).add(sp);
		_queryBuilder.add(rstJoin);
	}

	public QueryBuilder getQueryPlan() {
		return _queryBuilder;
	}
}
