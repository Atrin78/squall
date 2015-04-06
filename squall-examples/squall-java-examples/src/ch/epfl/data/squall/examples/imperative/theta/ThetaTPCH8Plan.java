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


package ch.epfl.data.squall.examples.imperative.theta;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.epfl.data.squall.components.Component;
import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.components.theta.ThetaJoinComponentFactory;
import ch.epfl.data.squall.conversion.DateConversion;
import ch.epfl.data.squall.conversion.DoubleConversion;
import ch.epfl.data.squall.conversion.IntegerConversion;
import ch.epfl.data.squall.conversion.NumericConversion;
import ch.epfl.data.squall.conversion.StringConversion;
import ch.epfl.data.squall.conversion.TypeConversion;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.expressions.IntegerYearFromDate;
import ch.epfl.data.squall.expressions.Multiplication;
import ch.epfl.data.squall.expressions.Subtraction;
import ch.epfl.data.squall.expressions.ValueExpression;
import ch.epfl.data.squall.expressions.ValueSpecification;
import ch.epfl.data.squall.operators.AggregateOperator;
import ch.epfl.data.squall.operators.AggregateSumOperator;
import ch.epfl.data.squall.operators.ProjectOperator;
import ch.epfl.data.squall.operators.SelectOperator;
import ch.epfl.data.squall.predicates.BetweenPredicate;
import ch.epfl.data.squall.predicates.ComparisonPredicate;
import ch.epfl.data.squall.query_plans.QueryBuilder;
import ch.epfl.data.squall.query_plans.QueryPlan;

public class ThetaTPCH8Plan extends QueryPlan {
	private static Logger LOG = Logger.getLogger(ThetaTPCH8Plan.class);

	private QueryBuilder _queryBuilder = new QueryBuilder();

	// the field nation is not used, since we cannot provide final result if
	// having more final components
	private static final String _nation = "BRAZIL";
	private static final String _region = "AMERICA";
	private static final String _type = "ECONOMY ANODIZED STEEL";
	private static final String _date1Str = "1995-01-01";
	private static final String _date2Str = "1996-12-31";

	private static final TypeConversion<Date> _dateConv = new DateConversion();
	private static final NumericConversion<Double> _doubleConv = new DoubleConversion();
	private static final TypeConversion<String> _sc = new StringConversion();

	private static final Date _date1 = _dateConv.fromString(_date1Str);
	private static final Date _date2 = _dateConv.fromString(_date2Str);

	private static final IntegerConversion _ic = new IntegerConversion();

	public ThetaTPCH8Plan(String dataPath, String extension, Map conf) {

		int Theta_JoinType = ThetaQueryPlansParameters.getThetaJoinType(conf);

		// -------------------------------------------------------------------------------------
		List<Integer> hashRegion = Arrays.asList(0);

		SelectOperator selectionRegion = new SelectOperator(
				new ComparisonPredicate(new ColumnReference(_sc, 1),
						new ValueSpecification(_sc, _region)));

		ProjectOperator projectionRegion = new ProjectOperator(new int[] { 0 });

		DataSourceComponent relationRegion = new DataSourceComponent("REGION",
				dataPath + "region" + extension).setOutputPartKey(hashRegion)
				.add(selectionRegion).add(projectionRegion);
		_queryBuilder.add(relationRegion);

		// -------------------------------------------------------------------------------------
		List<Integer> hashNation1 = Arrays.asList(1);

		ProjectOperator projectionNation1 = new ProjectOperator(new int[] { 0,
				2 });

		DataSourceComponent relationNation1 = new DataSourceComponent(
				"NATION1", dataPath + "nation" + extension).setOutputPartKey(
				hashNation1).add(projectionNation1);
		_queryBuilder.add(relationNation1);

		// -------------------------------------------------------------------------------------
		ColumnReference colR = new ColumnReference(_ic, 0);
		ColumnReference colN = new ColumnReference(_ic, 1);
		ComparisonPredicate R_N_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colR, colN);

		Component R_Njoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, relationRegion,
						relationNation1, _queryBuilder)
				.add(new ProjectOperator(new int[] { 1 }))
				.setOutputPartKey(Arrays.asList(0)).setJoinPredicate(R_N_comp);

		// -------------------------------------------------------------------------------------
		List<Integer> hashCustomer = Arrays.asList(0);

		ProjectOperator projectionCustomer = new ProjectOperator(new int[] { 3,
				0 });

		DataSourceComponent relationCustomer = new DataSourceComponent(
				"CUSTOMER", dataPath + "customer" + extension)
				.setOutputPartKey(hashCustomer).add(projectionCustomer);
		_queryBuilder.add(relationCustomer);

		// -------------------------------------------------------------------------------------

		ColumnReference colR_N = new ColumnReference(_ic, 0);
		ColumnReference colC = new ColumnReference(_ic, 0);
		ComparisonPredicate R_N_C_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colR_N, colC);
		Component R_N_Cjoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, R_Njoin,
						relationCustomer, _queryBuilder)
				.add(new ProjectOperator(new int[] { 2 }))
				.setOutputPartKey(Arrays.asList(0))
				.setJoinPredicate(R_N_C_comp);

		// -------------------------------------------------------------------------------------
		List<Integer> hashSupplier = Arrays.asList(1);

		ProjectOperator projectionSupplier = new ProjectOperator(new int[] { 0,
				3 });

		DataSourceComponent relationSupplier = new DataSourceComponent(
				"SUPPLIER", dataPath + "supplier" + extension)
				.setOutputPartKey(hashSupplier).add(projectionSupplier);
		_queryBuilder.add(relationSupplier);

		// -------------------------------------------------------------------------------------
		List<Integer> hashNation2 = Arrays.asList(0);

		ProjectOperator projectionNation2 = new ProjectOperator(new int[] { 0,
				1 });

		DataSourceComponent relationNation2 = new DataSourceComponent(
				"NATION2", dataPath + "nation" + extension).setOutputPartKey(
				hashNation2).add(projectionNation2);
		_queryBuilder.add(relationNation2);

		// -------------------------------------------------------------------------------------
		ColumnReference colS = new ColumnReference(_ic, 1);
		ColumnReference colN2 = new ColumnReference(_ic, 0);
		ComparisonPredicate S_N2_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colS, colN2);

		Component S_Njoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, relationSupplier,
						relationNation2, _queryBuilder)
				.add(new ProjectOperator(new int[] { 0, 3 }))
				.setOutputPartKey(Arrays.asList(0)).setJoinPredicate(S_N2_comp);

		// -------------------------------------------------------------------------------------
		List<Integer> hashPart = Arrays.asList(0);

		SelectOperator selectionPart = new SelectOperator(
				new ComparisonPredicate(new ColumnReference(_sc, 4),
						new ValueSpecification(_sc, _type)));

		ProjectOperator projectionPart = new ProjectOperator(new int[] { 0 });

		DataSourceComponent relationPart = new DataSourceComponent("PART",
				dataPath + "part" + extension).setOutputPartKey(hashPart)
				.add(selectionPart).add(projectionPart);
		_queryBuilder.add(relationPart);

		// -------------------------------------------------------------------------------------
		List<Integer> hashLineitem = Arrays.asList(1);

		// first field in projection
		ColumnReference orderKey = new ColumnReference(_sc, 0);
		// second field in projection
		ColumnReference partKey = new ColumnReference(_sc, 1);
		// third field in projection
		ColumnReference suppKey = new ColumnReference(_sc, 2);
		// forth field in projection
		ValueExpression<Double> substract = new Subtraction(
				new ValueSpecification(_doubleConv, 1.0), new ColumnReference(
						_doubleConv, 6));
		// extendedPrice*(1-discount)
		ValueExpression<Double> product = new Multiplication(
				new ColumnReference(_doubleConv, 5), substract);
		ProjectOperator projectionLineitem = new ProjectOperator(orderKey,
				partKey, suppKey, product);

		DataSourceComponent relationLineitem = new DataSourceComponent(
				"LINEITEM", dataPath + "lineitem" + extension)
				.setOutputPartKey(hashLineitem).add(projectionLineitem);
		_queryBuilder.add(relationLineitem);

		// -------------------------------------------------------------------------------------
		ColumnReference colP = new ColumnReference(_ic, 0);
		ColumnReference colL = new ColumnReference(_ic, 1);
		ComparisonPredicate P_L_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colP, colL);

		Component P_Ljoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, relationPart,
						relationLineitem, _queryBuilder)
				.add(new ProjectOperator(new int[] { 1, 3, 4 }))
				.setOutputPartKey(Arrays.asList(0)).setJoinPredicate(P_L_comp)
		// .addOperator(agg)
		;

		// -------------------------------------------------------------------------------------
		List<Integer> hashOrders = Arrays.asList(0);

		SelectOperator selectionOrders = new SelectOperator(
				new BetweenPredicate(new ColumnReference(_dateConv, 4), true,
						new ValueSpecification(_dateConv, _date1), true,
						new ValueSpecification(_dateConv, _date2)));

		// first field in projection
		ValueExpression OrdersOrderKey = new ColumnReference(_sc, 0);
		// second field in projection
		ValueExpression OrdersCustKey = new ColumnReference(_sc, 1);
		// third field in projection
		ValueExpression OrdersExtractYear = new IntegerYearFromDate(
				new ColumnReference<Date>(_dateConv, 4));
		ProjectOperator projectionOrders = new ProjectOperator(OrdersOrderKey,
				OrdersCustKey, OrdersExtractYear);

		DataSourceComponent relationOrders = new DataSourceComponent("ORDERS",
				dataPath + "orders" + extension).setOutputPartKey(hashOrders)
				.add(selectionOrders).add(projectionOrders);
		_queryBuilder.add(relationOrders);

		// -------------------------------------------------------------------------------------
		ColumnReference colP_L = new ColumnReference(_ic, 0);
		ColumnReference colO = new ColumnReference(_ic, 0);
		ComparisonPredicate P_L_O_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colP_L, colO);

		Component P_L_Ojoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, P_Ljoin,
						relationOrders, _queryBuilder)
				.add(new ProjectOperator(new int[] { 1, 2, 4, 5 }))
				.setOutputPartKey(Arrays.asList(0))
				.setJoinPredicate(P_L_O_comp);

		// -------------------------------------------------------------------------------------
		ColumnReference colS_N = new ColumnReference(_ic, 0);
		ColumnReference colP_L_O = new ColumnReference(_ic, 0);
		ComparisonPredicate S_N_P_L_O_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colS_N, colP_L_O);

		Component S_N_P_L_Ojoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, S_Njoin, P_L_Ojoin,
						_queryBuilder)
				.add(new ProjectOperator(new int[] { 1, 3, 4, 5 }))
				.setOutputPartKey(Arrays.asList(2))
				.setJoinPredicate(S_N_P_L_O_comp);

		// -------------------------------------------------------------------------------------
		AggregateOperator agg = new AggregateSumOperator(new ColumnReference(
				_doubleConv, 2), conf).setGroupByColumns(Arrays.asList(1, 4));

		ColumnReference colR_N_C = new ColumnReference(_ic, 0);
		ColumnReference colS_N_P_L_O = new ColumnReference(_ic, 2);
		ComparisonPredicate R_N_C_S_N_P_L_O_comp = new ComparisonPredicate(
				ComparisonPredicate.EQUAL_OP, colR_N_C, colS_N_P_L_O);

		Component R_N_C_S_N_P_L_Ojoin = ThetaJoinComponentFactory
				.createThetaJoinOperator(Theta_JoinType, R_N_Cjoin,
						S_N_P_L_Ojoin, _queryBuilder).add(agg)
				.setJoinPredicate(R_N_C_S_N_P_L_O_comp);

		// -------------------------------------------------------------------------------------

	}

	public QueryBuilder getQueryPlan() {
		return _queryBuilder;
	}
}
