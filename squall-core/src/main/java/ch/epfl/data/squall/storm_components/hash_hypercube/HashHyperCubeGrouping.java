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

package ch.epfl.data.squall.storm_components.hash_hypercube;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import backtype.storm.generated.GlobalStreamId;
import backtype.storm.grouping.CustomStreamGrouping;
import backtype.storm.task.WorkerTopologyContext;
import ch.epfl.data.squall.thetajoin.matrix_assignment.HashHyperCubeAssignment;
import ch.epfl.data.squall.utilities.MyUtilities;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class HashHyperCubeGrouping implements CustomStreamGrouping {
    private static final long serialVersionUID = 1L;
    private static Logger LOG = Logger
	    .getLogger(HashHyperCubeGrouping.class);

    private final HashHyperCubeAssignment _assignment;
    private final List<EmitterDesc> _emitters;
    private List<Integer> _targetTasks;
    private final Map _map;

    public HashHyperCubeGrouping(List<EmitterDesc> emitters, HashHyperCubeAssignment assignment, Map map) {
	_emitters = emitters;
	_assignment = assignment;
	_map = map;
    }

    // @Override
    @Override
    public List<Integer> chooseTasks(int taskId, List<Object> stormTuple) {
	// the following is checking for FinalAck
	if (!MyUtilities.isManualBatchingMode(_map)) {
	    final List<String> tuple = (List<String>) stormTuple.get(1); // TUPLE
	    if (MyUtilities.isFinalAck(tuple, _map))
		return _targetTasks;
	} else {
	    final String tupleBatch = (String) stormTuple.get(1); // TUPLE
	    if (MyUtilities.isFinalAckManualBatching(tupleBatch, _map))
		// send to everyone
		return _targetTasks;
	}

	// the following picks tasks for Non-FinalAck
	return chooseTasksNonFinalAck(stormTuple);
    }

    private List<Integer> chooseTasksNonFinalAck(List<Object> stormTuple) {
    List<Integer> tasks = null;
    final String tableName = (String) stormTuple.get(0);
    final List<String> tuple = (List<String>) stormTuple.get(1); // TUPLE
    for (EmitterDesc emitter : _emitters) {
    	if (emitter.index.equals(tableName)) {
    		Map<String, Object> colums = new HashMap<String, Object>();
            List<Integer> regionsID = _assignment.getRegionIDs(createColumns(emitter, tuple));
            tasks = translateIdsToTasks(regionsID);
            break;
    	}
    }

    if (tasks == null) {
        LOG.info(_emitters);
        LOG.info("Table Name: " + tableName);
        LOG.info("WRONG ASSIGNMENT");
    }    

    return tasks;
    }

    // Creates <ColumnName, Value> 
    public Map<String, String> createColumns(EmitterDesc emitter, List<String> stormTuple) {
    	HashMap<String, String> columns = new HashMap<String, String>();
    	int i = 0;
    	for (String columnName : emitter.columnNames) {
    		columns.put(columnName, stormTuple.get(i++));
    	}

    	return columns;
    }

    @Override
    public void prepare(WorkerTopologyContext wtc, GlobalStreamId gsi,
	    List<Integer> targetTasks) {
	_targetTasks = targetTasks;
    }

    private List<Integer> translateIdsToTasks(List<Integer> ids) {
	final List<Integer> converted = new ArrayList<Integer>();
	for (final int id : ids)
	    converted.add(_targetTasks.get(id));
	return converted;
    }

    public static class EmitterDesc implements Serializable {
    	public String name;
        public String index;
        public long cardinality;
    	public String[] columnNames;
        public boolean random;

    	public EmitterDesc(String name, String index, long cardinality, String[] columnNames) {
    		this.name = name;
            this.index = index;
            this.cardinality = cardinality;
    		this.columnNames = columnNames;
    	}

        public EmitterDesc(String name, String index, long cardinality, String[] columnsNames, boolean random) {
            this(name, index, cardinality, columnsNames);
            this.random = random;
        }

        public String toString() {
            String tmp = "";
            for (String s : columnNames) tmp += " " + s;
            return name + " " + index + " " + cardinality + "\n" + tmp;
        }
    }
}
