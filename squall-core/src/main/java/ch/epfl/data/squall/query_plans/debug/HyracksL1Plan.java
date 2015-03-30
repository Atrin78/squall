package ch.epfl.data.squall.query_plans.debug;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.conversion.IntegerConversion;
import ch.epfl.data.squall.operators.ProjectOperator;
import ch.epfl.data.squall.query_plans.QueryBuilder;
import ch.epfl.data.squall.query_plans.QueryPlan;

public class HyracksL1Plan extends QueryPlan {
	private static Logger LOG = Logger.getLogger(HyracksL1Plan.class);

	private final QueryBuilder _queryBuilder = new QueryBuilder();

	private static final IntegerConversion _ic = new IntegerConversion();

	public HyracksL1Plan(String dataPath, String extension, Map conf) {
		// -------------------------------------------------------------------------------------
		// start of query plan filling
		final ProjectOperator projectionCustomer = new ProjectOperator(
				new int[] { 0, 6 });
		final List<Integer> hashCustomer = Arrays.asList(0);
		DataSourceComponent relationCustomer = new DataSourceComponent(
				"CUSTOMER", dataPath + "customer" + extension)
				.add(projectionCustomer).setOutputPartKey(hashCustomer)
				.setPrintOut(false);
		_queryBuilder.add(relationCustomer);

		// -------------------------------------------------------------------------------------
		final ProjectOperator projectionOrders = new ProjectOperator(
				new int[] { 1 });
		final List<Integer> hashOrders = Arrays.asList(0);
		DataSourceComponent relationOrders = new DataSourceComponent("ORDERS",
				dataPath + "orders" + extension).add(projectionOrders)
				.setOutputPartKey(hashOrders).setPrintOut(false);
		_queryBuilder.add(relationOrders);

	}

	public QueryBuilder getQueryPlan() {
		return _queryBuilder;
	}

}
