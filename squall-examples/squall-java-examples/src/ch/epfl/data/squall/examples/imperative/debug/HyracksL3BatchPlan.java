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

package ch.epfl.data.squall.examples.imperative.debug;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.components.EquiJoinComponent;
import ch.epfl.data.squall.components.OperatorComponent;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.operators.AggregateCountOperator;
import ch.epfl.data.squall.operators.AggregateSumOperator;
import ch.epfl.data.squall.operators.ProjectOperator;
import ch.epfl.data.squall.query_plans.QueryBuilder;
import ch.epfl.data.squall.query_plans.QueryPlan;
import ch.epfl.data.squall.types.IntegerType;

public class HyracksL3BatchPlan extends QueryPlan {
    private static Logger LOG = Logger.getLogger(HyracksL3BatchPlan.class);

    private final QueryBuilder _queryBuilder = new QueryBuilder();

    private static final IntegerType _ic = new IntegerType();

    public HyracksL3BatchPlan(String dataPath, String extension, Map conf) {
	// -------------------------------------------------------------------------------------
	// start of query plan filling
	final ProjectOperator projectionCustomer = new ProjectOperator(
		new int[] { 0, 6 });
	final List<Integer> hashCustomer = Arrays.asList(0);
	final DataSourceComponent relationCustomer = new DataSourceComponent(
		"CUSTOMER", dataPath + "customer" + extension).add(
		projectionCustomer).setOutputPartKey(hashCustomer);
	_queryBuilder.add(relationCustomer);

	// -------------------------------------------------------------------------------------
	final ProjectOperator projectionOrders = new ProjectOperator(
		new int[] { 1 });
	final List<Integer> hashOrders = Arrays.asList(0);
	final DataSourceComponent relationOrders = new DataSourceComponent(
		"ORDERS", dataPath + "orders" + extension)
		.add(projectionOrders).setOutputPartKey(hashOrders);
	_queryBuilder.add(relationOrders);

	// -------------------------------------------------------------------------------------

	final AggregateCountOperator postAgg = new AggregateCountOperator(conf)
		.setGroupByColumns(Arrays.asList(1));
	final List<Integer> hashIndexes = Arrays.asList(0);
	final EquiJoinComponent CUSTOMER_ORDERSjoin = new EquiJoinComponent(
		relationCustomer, relationOrders).add(postAgg)
		.setOutputPartKey(hashIndexes).setBatchOutputMillis(1000);
	_queryBuilder.add(CUSTOMER_ORDERSjoin);

	// -------------------------------------------------------------------------------------
	final AggregateSumOperator agg = new AggregateSumOperator(
		new ColumnReference(_ic, 1), conf).setGroupByColumns(Arrays
		.asList(0));

	OperatorComponent oc = new OperatorComponent(CUSTOMER_ORDERSjoin,
		"COUNTAGG").add(agg).setFullHashList(
		Arrays.asList("FURNITURE", "BUILDING", "MACHINERY",
			"HOUSEHOLD", "AUTOMOBILE"));
	_queryBuilder.add(oc);

	// -------------------------------------------------------------------------------------

    }

    @Override
    public QueryBuilder getQueryPlan() {
	return _queryBuilder;
    }

}
