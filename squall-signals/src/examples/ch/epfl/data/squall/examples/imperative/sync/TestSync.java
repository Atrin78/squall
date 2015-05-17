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

package ch.epfl.data.squall.examples.imperative.sync;

import java.util.ArrayList;
import java.util.Map;

import ch.epfl.data.squall.components.Component;
import ch.epfl.data.squall.components.EquiJoinComponent;
import ch.epfl.data.squall.components.OperatorComponent;
import ch.epfl.data.squall.components.signal_components.SignaledDataSourceComponent;
import ch.epfl.data.squall.operators.AggregateCountOperator;
import ch.epfl.data.squall.operators.Operator;
import ch.epfl.data.squall.query_plans.QueryPlan;
import ch.epfl.data.squall.types.DateType;
import ch.epfl.data.squall.types.DoubleType;
import ch.epfl.data.squall.types.IntegerType;
import ch.epfl.data.squall.types.StringType;
import ch.epfl.data.squall.types.Type;
import ch.epfl.data.squall.utilities.SystemParameters;

public class TestSync extends QueryPlan {

    public TestSync(String dataPath, String extension, Map conf) {
	super(dataPath, extension, conf);
    }

    @Override
    public Component createQueryPlan(String dataPath, String extension, Map conf) {
	// -------------------------------------------------------------------------------------

	ArrayList<Type> customerSchema = new ArrayList<Type>();
	customerSchema.add(new IntegerType());
	customerSchema.add(new StringType());
	customerSchema.add(new StringType());
	customerSchema.add(new IntegerType());
	customerSchema.add(new StringType());
	customerSchema.add(new DoubleType());
	customerSchema.add(new StringType());
	customerSchema.add(new StringType());

	
	int distributionSecs  = SystemParameters.getInt(conf,"DISTRIBUTION_SECS");
	int tuplesThresh  = SystemParameters.getInt(conf,"TUPLES_THRES");
	String zookeeperHost  = SystemParameters.getString(conf,"ZOOKEEPER_HOST");
	int windowSize  = SystemParameters.getInt(conf,"WINOW_SIZE");
	
	Component customer;
	if(windowSize<0)
		customer = new SignaledDataSourceComponent("CUSTOMER",
				zookeeperHost, customerSchema, 0, distributionSecs, tuplesThresh); //secs, windowsize, frquentthres, update rate harmnizer
	else{
		int frequencyThres  = SystemParameters.getInt(conf,"FREQUENCY_THRESH");
		int updateRate  = SystemParameters.getInt(conf,"UPDATE_RATE");
		customer = new SignaledDataSourceComponent("CUSTOMER",
				zookeeperHost, customerSchema, 0, distributionSecs,tuplesThresh, windowSize, frequencyThres, updateRate); //secs, number of tuples threshold, windowsize, frquentthres, update rate harmnizer
	}
	
	ArrayList<Type> ordersSchema = new ArrayList<Type>();
	ordersSchema.add(new IntegerType());
	ordersSchema.add(new IntegerType());
	ordersSchema.add(new StringType());
	ordersSchema.add(new DoubleType());
	ordersSchema.add(new DateType());
	ordersSchema.add(new StringType());
	ordersSchema.add(new StringType());
	ordersSchema.add(new IntegerType());
	ordersSchema.add(new StringType());

	// -------------------------------------------------------------------------------------
//	Component orders = new SignaledDataSourceComponent("ORDERS",
//		"localhost:2000", ordersSchema, 0, 10, 10000, 5000, 1000);
	
//	ArrayList<Component> merge = new ArrayList<Component>();
//	merge.add(customer);merge.add(orders);
	
//	Component aggOpt = new OperatorComponent(merge, "CUSTOMER_ORDERS");
	
	Component aggOpt = new OperatorComponent(customer, "CUSTOMER_ORDERS");
	
	AggregateCountOperator count= new AggregateCountOperator(conf);
	
	aggOpt.add(count);

	// -------------------------------------------------------------------------------------
//	Component custOrders = new EquiJoinComponent(customer, 0, orders, 0)
//		.add(new AggregateCountOperator(conf).setGroupByColumns(1));
	//return custOrders;
	return aggOpt;
	// -------------------------------------------------------------------------------------
    }
}
