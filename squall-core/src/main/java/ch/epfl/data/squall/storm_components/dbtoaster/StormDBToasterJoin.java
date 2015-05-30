/*
 *
 *  * Copyright (c) 2011-2015 EPFL DATA Laboratory
 *  * Copyright (c) 2014-2015 The Squall Collaboration (see NOTICE)
 *  *
 *  * All rights reserved.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package ch.epfl.data.squall.storm_components.dbtoaster;

import backtype.storm.Config;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.InputDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Tuple;
import ch.epfl.data.squall.components.ComponentProperties;
import ch.epfl.data.squall.dbtoaster.DBToasterEngine;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.operators.AggregateOperator;
import ch.epfl.data.squall.operators.AggregateSumOperator;
import ch.epfl.data.squall.operators.ChainOperator;
import ch.epfl.data.squall.operators.Operator;
import ch.epfl.data.squall.storm_components.InterchangingComponent;
import ch.epfl.data.squall.storm_components.StormBoltComponent;
import ch.epfl.data.squall.storm_components.StormComponent;
import ch.epfl.data.squall.storm_components.StormEmitter;
import ch.epfl.data.squall.storm_components.synchronization.TopologyKiller;
import ch.epfl.data.squall.thetajoin.matrix_assignment.HyperCubeAssignerFactory;
import ch.epfl.data.squall.thetajoin.matrix_assignment.HyperCubeAssignment;
import ch.epfl.data.squall.types.MultiplicityType;
import ch.epfl.data.squall.types.Type;
import ch.epfl.data.squall.utilities.MyUtilities;
import ch.epfl.data.squall.utilities.PartitioningScheme;
import ch.epfl.data.squall.utilities.PeriodicAggBatchSend;
import ch.epfl.data.squall.utilities.SystemParameters;
import ch.epfl.data.squall.utilities.statistics.StatisticsUtilities;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class StormDBToasterJoin extends StormBoltComponent {

    private static Logger LOG = Logger.getLogger(StormDBToasterJoin.class);

    private final ChainOperator _operatorChain;

    private long _numSentTuples = 0;

    // for load-balancing
    private final List<String> _fullHashList;

    // for batch sending
    private final Semaphore _semAgg = new Semaphore(1, true);
    private boolean _firstTime = true;
    private PeriodicAggBatchSend _periodicAggBatch;
    private final long _aggBatchOutputMillis;

    protected StatisticsUtilities _statsUtils;

    private DBToasterEngine dbtoasterEngine;
    private static final String DBT_GEN_PKG = "ddbt.gen.";
    private static final String QUERY_CLASS_SUFFIX = "Impl";
    private String _dbToasterQueryName;
    private boolean _outputWithMultiplicity;

    private StormEmitter[] _emitters;
    private Map<String, Type[]> _emitterNamesColTypes; // map between emitter names and types of tuple from the emitter
    private Set<String> _emittersWithMultiplicity;
    private Map<String, AggregateSumOperator> _emitterOperators;

    public StormDBToasterJoin(StormEmitter[] emitters,
                              ComponentProperties cp, List<String> allCompNames,
                              Map<String, Type[]> emitterNameColTypes,
                              Set<String> emittersWithMultiplicity,
                              int hierarchyPosition, TopologyBuilder builder,
                              TopologyKiller killer, Config conf, boolean outputMultiplicity) {
        super(cp, allCompNames, hierarchyPosition, conf);


        _emitters = emitters;
        _emitterNamesColTypes = emitterNameColTypes;
        _emittersWithMultiplicity = emittersWithMultiplicity;
        if (_emittersWithMultiplicity.size() > 0) {
            _emitterOperators = new HashMap<String, AggregateSumOperator>();
            for (String name : _emittersWithMultiplicity) {
                Type[] colTypes = _emitterNamesColTypes.get(name);
                int valueCol = colTypes.length - 1;
                int[] groupByCols = new int[colTypes.length - 1];
                for (int i = 0; i < groupByCols.length; i++)
                    groupByCols[i] = i;

                LOG.info("table: " + name + " aggregate with group by: " + Arrays.toString(groupByCols));

                _emitterOperators.put(name, new AggregateSumOperator(new ColumnReference(colTypes[valueCol], valueCol), getConf()).setGroupByColumns(groupByCols));
            }
        }

        _operatorChain = cp.getChainOperator();
        _fullHashList = cp.getFullHashList();

        _dbToasterQueryName = cp.getName() + QUERY_CLASS_SUFFIX;
        _outputWithMultiplicity = outputMultiplicity;

        _aggBatchOutputMillis = cp.getBatchOutputMillis();

        _statsUtils = new StatisticsUtilities(getConf(), LOG);

        final int parallelism = SystemParameters.getInt(getConf(), getID()
                + "_PAR");

        // connecting with previous level using Hypercube Assignment
        InputDeclarer currentBolt = builder.setBolt(getID(), this, parallelism);
        currentBolt = attachEmitters(conf, currentBolt, allCompNames, parallelism);
        // connecting with Killer
        if (getHierarchyPosition() == FINAL_COMPONENT
                && (!MyUtilities.isAckEveryTuple(conf)))
            killer.registerComponent(this, parallelism);
        if (cp.getPrintOut() && _operatorChain.isBlocking())
            currentBolt.allGrouping(killer.getID(),
                    SystemParameters.DUMP_RESULTS_STREAM);
    }

    private InputDeclarer attachEmitters(Config conf, InputDeclarer currentBolt,
                 List<String> allCompNames, int parallelism) {

        // split between nested and non-nested emitters
        List<StormEmitter> nestedEmitters = new ArrayList<StormEmitter>();
        List<StormEmitter> nonNestedEmitters = new ArrayList<StormEmitter>();
        for (StormEmitter e : _emitters) {
            if (_emittersWithMultiplicity.contains(e.getName())) {
                nestedEmitters.add(e);
            } else {
                nonNestedEmitters.add(e);
            }
        }

        // all nested emitters should broadcast to this
        MyUtilities.attachEmitterBroadcast(currentBolt, nestedEmitters);

        // other non-nested emitters follow the specified partitioning scheme
        switch (getPartitioningScheme(conf)) {
            case HYPERCUBE:
                long[] cardinality = getEmittersCardinality(nonNestedEmitters, conf);
                LOG.info("cardinalities: " + Arrays.toString(cardinality));
                final HyperCubeAssignment _currentHyperCubeMappingAssignment =
                        new HyperCubeAssignerFactory().getAssigner(parallelism, cardinality);

                LOG.info("assignment: " + _currentHyperCubeMappingAssignment.getMappingDimensions());
                currentBolt = MyUtilities.hyperCubeAttachEmitterComponents(currentBolt,
                        nonNestedEmitters, allCompNames,
                        _currentHyperCubeMappingAssignment, conf);
                break;
            case STARSCHEMA:
                currentBolt = MyUtilities.attachEmitterStarSchema(conf,
                        currentBolt, nonNestedEmitters, getEmittersCardinality(nonNestedEmitters, conf));
                break;
            case HASH:
                currentBolt = MyUtilities.attachEmitterHash(conf, _fullHashList,
                        currentBolt, nonNestedEmitters.get(0), nonNestedEmitters.subList(1, nonNestedEmitters.size()).toArray(new StormEmitter[nonNestedEmitters.size() - 1]));
                break;
        }
        return currentBolt;
    }

    private PartitioningScheme getPartitioningScheme(Config conf) {
        String schemeName = SystemParameters.getString(conf, getName() + "_PART_SCHEME");
        if (schemeName == null || schemeName.equals("")) {
            LOG.info("use default Hypercube partitioning scheme");
            return PartitioningScheme.HYPERCUBE;
        } else {
            LOG.info("use partitioning scheme : " + schemeName);
            return PartitioningScheme.valueOf(schemeName);
        }
    }

    private long[] getEmittersCardinality(List<StormEmitter> emitters, Config conf) {
        long[] cardinality = new long[emitters.size()];
        for (int i = 0; i < emitters.size(); i++) {
            cardinality[i] = SystemParameters.getInt(conf, emitters.get(i).getName() + "_CARD");
        }
        return cardinality;
    }

    @Override
    public void prepare(Map map, TopologyContext tc, OutputCollector collector) {
        super.prepare(map, tc, collector);

        dbtoasterEngine = new DBToasterEngine(DBT_GEN_PKG + _dbToasterQueryName);
    }

    @Override
    public void execute(Tuple stormTupleRcv) {
        if (_firstTime
                && MyUtilities.isAggBatchOutputMode(_aggBatchOutputMillis)) {
            _periodicAggBatch = new PeriodicAggBatchSend(_aggBatchOutputMillis,
                    this);
            _firstTime = false;
        }

        if (receivedDumpSignal(stormTupleRcv)) {
            MyUtilities.dumpSignal(this, stormTupleRcv, getCollector());
            return;
        }

        if (!MyUtilities.isManualBatchingMode(getConf())) {
            final List<String> tuple = (List<String>) stormTupleRcv
                    .getValueByField(StormComponent.TUPLE); // getValue(1);
            if (processFinalAck(tuple, stormTupleRcv)) {
                // need to close db toaster app here
                dbtoasterEngine.endStream();
                return;
            }

            processNonLastTuple(tuple,
                    stormTupleRcv, true);
        } else {
            final String inputBatch = stormTupleRcv
                    .getStringByField(StormComponent.TUPLE);// getString(1);
            final String[] wholeTuples = inputBatch
                    .split(SystemParameters.MANUAL_BATCH_TUPLE_DELIMITER);
            final int batchSize = wholeTuples.length;
            for (int i = 0; i < batchSize; i++) {
                // parsing
                final String currentTuple = new String(wholeTuples[i]);
                final String[] parts = currentTuple
                        .split(SystemParameters.MANUAL_BATCH_HASH_DELIMITER);
                String inputTupleString = null;
                if (parts.length == 1)
                    // lastAck
                    inputTupleString = new String(parts[0]);
                else {
                    inputTupleString = new String(parts[1]);
                }
                final List<String> tuple = MyUtilities.stringToTuple(
                        inputTupleString, getConf());
                // final Ack check
                if (processFinalAck(tuple, stormTupleRcv)) {
                    if (i != batchSize - 1)
                        throw new RuntimeException(
                                "Should not be here. LAST_ACK is not the last tuple!");
                    return;
                }
                // processing a tuple
                processNonLastTuple(tuple,
                        stormTupleRcv, (i == batchSize - 1));

            }
        }

        getCollector().ack(stormTupleRcv);
    }

    private void processNonLastTuple(List<String> tuple, Tuple stormTupleRcv,
                                     boolean isLastInBatch) {

        String sourceComponentName = stormTupleRcv.getSourceComponent();
        Type[] colTypes = _emitterNamesColTypes.get(sourceComponentName);

        boolean tupleWithMultiplicity = _emittersWithMultiplicity.contains(sourceComponentName);

        byte multiplicity = 1;
        if (!tupleWithMultiplicity) {
            if (sourceComponentName.equals("LINEITEM") && tuple.get(0).equals("840"))
                LOG.info("receive tuple from :  " + sourceComponentName + " " + Arrays.toString(tuple.toArray()));
            List<Object> typedTuple = createTypedTuple(tuple, colTypes);
            performJoin(sourceComponentName, stormTupleRcv, typedTuple, multiplicity, isLastInBatch);

        } else {
            //LOG.info("Processing tuple from " + sourceComponentName + " : " + Arrays.toString(tuple.toArray()));

            if (sourceComponentName.equals("L_AVG") && tuple.get(0).equals("840"))
                LOG.info("receive tuple from :  " + sourceComponentName + " " + Arrays.toString(tuple.toArray()));

            AggregateSumOperator emitOp = _emitterOperators.get(sourceComponentName);
            List<String>[] updatePair = emitOp.processUpdate(tuple, 0);
            if (updatePair[0] != null) {
                if (sourceComponentName.equals("L_AVG") && tuple.get(0).equals("840"))
                    LOG.info("output tuple delete : " + Arrays.toString(updatePair[0].toArray()));

                List<Object> typedTuple = createTypedTuple(tuple, colTypes);
                performJoin(sourceComponentName, stormTupleRcv, typedTuple, DBToasterEngine.TUPLE_DELETE, isLastInBatch);
            }

            if (updatePair[1] != null) {
                if (sourceComponentName.equals("L_AVG") && tuple.get(0).equals("840"))
                    LOG.info("output tuple insert : " + Arrays.toString(updatePair[1].toArray()));

                List<Object> typedTuple = createTypedTuple(tuple, colTypes);
                performJoin(sourceComponentName, stormTupleRcv, typedTuple, DBToasterEngine.TUPLE_INSERT, isLastInBatch);
            }
        }

    }

    /**
     * PerformJoin method insert tuple / delete tuple to the DBToasterInstance and get the output stream
     * @param sourceComponentName
     * @param stormTupleRcv
     * @param typedTuple
     * @param multiplicity
     * @param isLastInBatch
     */
    protected void performJoin(String sourceComponentName, Tuple stormTupleRcv,
                               List<Object> typedTuple,
                               byte multiplicity,
                               boolean isLastInBatch) {



        dbtoasterEngine.receiveTuple(sourceComponentName, multiplicity, typedTuple);

        List<Object[]> stream = dbtoasterEngine.getStreamOfUpdateTuples(_outputWithMultiplicity);

        long lineageTimestamp = 0L;
        if (MyUtilities.isCustomTimestampMode(getConf()))
            lineageTimestamp = stormTupleRcv
                    .getLongByField(StormComponent.TIMESTAMP);

        for (Object[] u : stream) {
            List<String> outputTuple = createStringTuple(u);
            //LOG.info("output tuple: " + Arrays.toString(outputTuple.toArray()));
            applyOperatorsAndSend(stormTupleRcv, outputTuple, lineageTimestamp, isLastInBatch);
        }

    }

    private List<Object> createTypedTuple(List<String> tuple, Type[] columnTypes) {
        List<Object> typedTuple = new LinkedList<Object>();

//        int offset = multiplicityIncluded ? 1 : 0;
        for (int i = 0; i < columnTypes.length; i++) {
            Object value = columnTypes[i].fromString(tuple.get(i));//ignore the first multiplicity field by offset by 1
            typedTuple.add(value);
        }

        return typedTuple;
    }

    private List<String> createStringTuple(Object[] typedTuple) {
        List<String> tuple = new LinkedList<String>();
        for (Object o : typedTuple) tuple.add("" + o);
        return tuple;
    }

    protected void applyOperatorsAndSend(Tuple stormTupleRcv,
                                         List<String> tuple, long lineageTimestamp, boolean isLastInBatch) {
        if (MyUtilities.isAggBatchOutputMode(_aggBatchOutputMillis))
            try {
                _semAgg.acquire();
            } catch (final InterruptedException ex) {
            }

        tuple = _operatorChain.process(tuple, lineageTimestamp);

        if (MyUtilities.isAggBatchOutputMode(_aggBatchOutputMillis))
            _semAgg.release();
        if (tuple == null)
            return;
        _numSentTuples++;
        printTuple(tuple);

        if (_numSentTuples % _statsUtils.getDipOutputFreqPrint() == 0)
            printStatistics(SystemParameters.OUTPUT_PRINT);

        if (MyUtilities
                .isSending(getHierarchyPosition(), _aggBatchOutputMillis)) {
            tupleSend(tuple, stormTupleRcv, lineageTimestamp);
        }

        if (MyUtilities.isPrintLatency(getHierarchyPosition(), getConf())) {
            if (!MyUtilities.isManualBatchingMode(getConf()) || isLastInBatch) {
                printTupleLatency(_numSentTuples - 1, lineageTimestamp);
            }
        }
    }

    @Override
    public ChainOperator getChainOperator() {
        return _operatorChain;
    }

    @Override
    protected InterchangingComponent getInterComp() {
        return null;
    }

    @Override
    public long getNumSentTuples() {
        return _numSentTuples;
    }

    @Override
    public PeriodicAggBatchSend getPeriodicAggBatch() {
        return _periodicAggBatch;
    }

    @Override
    protected void printStatistics(int type) {

    }

    @Override
    public void purgeStaleStateFromWindow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void aggBatchSend() {
        if (MyUtilities.isAggBatchOutputMode(_aggBatchOutputMillis))
            if (_operatorChain != null) {
                final Operator lastOperator = _operatorChain.getLastOperator();
                if (lastOperator instanceof AggregateOperator) {
                    try {
                        _semAgg.acquire();
                    } catch (final InterruptedException ex) {
                    }
                    // sending
                    final AggregateOperator agg = (AggregateOperator) lastOperator;
                    final List<String> tuples = agg.getContent();
                    for (final String tuple : tuples)
                        tupleSend(MyUtilities.stringToTuple(tuple, getConf()),
                                null, 0);
                    // clearing
                    agg.clearStorage();
                    _semAgg.release();
                }
            }
    }

    @Override
    public String getInfoID() {
        return getID();
    }
}
