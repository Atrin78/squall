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

package ch.epfl.data.squall.components.signal_components;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import backtype.storm.Config;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;
import ch.epfl.data.squall.components.ComponentProperties;
import ch.epfl.data.squall.components.signal_components.storm.SignalClient;
import ch.epfl.data.squall.operators.ChainOperator;
import ch.epfl.data.squall.storm_components.StormComponent;
import ch.epfl.data.squall.storm_components.synchronization.TopologyKiller;
import ch.epfl.data.squall.types.Type;
import ch.epfl.data.squall.utilities.MyUtilities;
import ch.epfl.data.squall.utilities.SystemParameters;

public class SynchronizedStormDataSource extends
	StormSynchronizedSpoutComponent {
    private static final long serialVersionUID = 1L;
    private static Logger LOG = Logger
	    .getLogger(SynchronizedStormDataSource.class);

    private boolean _hasReachedEOF = false;
    private boolean _hasSentEOF = false; // have sent EOF to TopologyKiller
    // (AckEachTuple mode)
    private boolean _hasSentLastAck = false; // AckLastTuple mode

    private long _pendingTuples = 0;
    private int _numSentTuples = 0;

    private final ChainOperator _operatorChain;

    private final int _keyIndex;
    private int _keyValue = 0;
    private String _name;
    private ArrayList<Type> _schema;
    
    
    //Harmonizer parameters
    private String _zookeeperhost, _harmonizerSyncedSpoutName;
    private int _harmonizerUpdateThreshold;
    private int _currentHarmonizerUpdateFreq=0;
    private transient SignalClient _scHarmonizer;
    private HashMap<Integer, Integer> _keyFrequencies;
    private boolean _isHarmonized;

    public SynchronizedStormDataSource(ComponentProperties cp,
	    List<String> allCompNames, ArrayList<Type> tupleTypes,
	    int hierarchyPosition, int parallelism, int keyIndex,
	    boolean isPartitioner, TopologyBuilder builder,
	    TopologyKiller killer, Config conf) {
	super(cp, allCompNames, hierarchyPosition, isPartitioner, conf);
	_keyIndex = keyIndex;
	_name = cp.getName();
	_operatorChain = cp.getChainOperator();
	_schema = tupleTypes;
	if (getHierarchyPosition() == FINAL_COMPONENT
		&& (!MyUtilities.isAckEveryTuple(conf)))
	    killer.registerComponent(this, parallelism);
	builder.setSpout(getID(), this, parallelism);
	if (MyUtilities.isAckEveryTuple(conf))
	    killer.registerComponent(this, parallelism);
    }
    
    public SynchronizedStormDataSource(ComponentProperties cp,
    	    List<String> allCompNames, ArrayList<Type> tupleTypes,
    	    int hierarchyPosition, int parallelism, int keyIndex,
    	    boolean isPartitioner, TopologyBuilder builder,
    	    TopologyKiller killer, Config conf, String zookeeperhost, String harmonizerName, int harmonizerUpdateThreshold) {
    	this(cp,allCompNames, tupleTypes,hierarchyPosition, parallelism, keyIndex, isPartitioner, builder, killer, conf);
    	_harmonizerSyncedSpoutName= harmonizerName;
    	_zookeeperhost=zookeeperhost;
    	_harmonizerUpdateThreshold=harmonizerUpdateThreshold;
    	_keyFrequencies= new HashMap<Integer, Integer>();
    	_isHarmonized=true;
        }

    // ack method on spout is called only if in AckEveryTuple mode (ACKERS > 0)
    @Override
    public void ack(Object msgId) {
	_pendingTuples--;
    }

    @Override
    public void aggBatchSend() {
    	throw new RuntimeException("Batching is disabled in this operator!");
    }

    protected void applyOperatorsAndSend(List<String> tuple) {
	long timestamp = 0;
	if ((MyUtilities.isCustomTimestampMode(getConf()) && getHierarchyPosition() == StormComponent.NEXT_TO_LAST_COMPONENT)
		|| MyUtilities.isWindowTimestampMode(getConf()))
	    timestamp = System.currentTimeMillis();
	
	tuple = _operatorChain.process(tuple, timestamp);

	if (tuple == null)
	    return;

	_numSentTuples++;
	_pendingTuples++;
	printTuple(tuple);
	
	if (MyUtilities.isPrintLatency(getHierarchyPosition(), getConf())) {
	    printTupleLatency(_numSentTuples - 1, timestamp);
	}
    }

    @Override
    public void close() {
	super.close();
	_scHarmonizer.close();
    }

    /*
     * whatever is inside this method is done only once
     */
    private void eofFinalization() {
	printContent();

	if (!MyUtilities.isAckEveryTuple(getConf()))
	    if (getHierarchyPosition() == FINAL_COMPONENT) {
		if (!_hasSentEOF) {
		    _hasSentEOF = true; // to ensure we will not send multiple
		    // EOF per single spout
		    getCollector().emit(SystemParameters.EOF_STREAM,
			    new Values(SystemParameters.EOF));
		}
	    } else if (!_hasSentLastAck) {
		LOG.info(getID() + ":Has sent last_ack, tuples sent:"
			+ _numSentTuples);
		_hasSentLastAck = true;
		final List<String> lastTuple = new ArrayList<String>(
			Arrays.asList(SystemParameters.LAST_ACK));
		tupleSend(lastTuple, null, 0);
	    }
	if (_operatorChain != null) {
	    _operatorChain.finalizeProcessing();
	}
    }

    @Override
    public void fail(Object msgId) {
	throw new RuntimeException("Failing tuple in " + getID());
    }

    @Override
    public ChainOperator getChainOperator() {
	return _operatorChain;
    }

    // StormComponent
    @Override
    public String getInfoID() {
	final StringBuilder sb = new StringBuilder();
	sb.append("Table ").append(getID()).append(" has ID: ").append(getID());
	return sb.toString();
    }

    @Override
    public long getNumSentTuples() {
	return _numSentTuples;
    }

    public long getPendingTuples() {
	return _pendingTuples;
    }

    // from IRichSpout interface
    @Override
    public void nextTuple() {    	
	final String line = generateLine();
	
	
	//Send frequency statistics & housekeeping
	if(_isHarmonized && _currentHarmonizerUpdateFreq>_harmonizerUpdateThreshold){
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = null;
			  out = new ObjectOutputStream(bos);   
			  out.writeObject(_keyFrequencies);
			  byte[] objectBytes = bos.toByteArray();
			  _scHarmonizer.send(objectBytes);
			   //LOG.info("Spout sending histogram.....");
		      out.close();
		      bos.close();
		} catch (Exception e) {
		    e.printStackTrace();
		}
		_keyFrequencies.clear();
		_currentHarmonizerUpdateFreq=0;
	}

	if (line == null) {
	    if (!_hasReachedEOF) {
		_hasReachedEOF = true;
		// we reached EOF, first time this happens we invoke the method:
		eofFinalization();
	    }
	    sendEOF();
	    // sleep since we are not going to do useful work,
	    // but still are looping in nextTuple method
	    Utils.sleep(SystemParameters.EOF_TIMEOUT_MILLIS);
	    return;
	}

	final List<String> tuple = MyUtilities.fileLineToTuple(line, getConf());
	applyOperatorsAndSend(tuple);
	
	//Update frequency statistics
	if(_isHarmonized){
		updateHistogram(Integer.parseInt(tuple.get(_keyIndex)));
		_currentHarmonizerUpdateFreq++;
	}
	
    }
    
    private void updateHistogram(int key){
    	Integer value= _keyFrequencies.get(key);
    	if(value!=null)
    		_keyFrequencies.put(key, value+1);
    	else
    		_keyFrequencies.put(key, 1);
    }

    // BaseRichSpout
    @Override
    public void open(Map map, TopologyContext tc, SpoutOutputCollector collector) {
	super.open(map, tc, collector);
	if(_harmonizerSyncedSpoutName!=null && _zookeeperhost!=null){
		_scHarmonizer = new SignalClient(_zookeeperhost, _harmonizerSyncedSpoutName);
		_scHarmonizer.start();
	}
    }

    // HELPER methods
    protected String generateLine() {
	String text = "";
	for (int i = 0; i < _schema.size(); i++) {
	    if (i == _keyIndex)
		text += String.valueOf(_keyValue);
	    else {
		Type attribute = _schema.get(i);
		text += attribute.toString(attribute.generateRandomInstance());
	    }
	    text += "|";
	}
	return text;
    }

    /*
     * sending EOF in AckEveryTuple mode when we send at least one tuple to the
     * next component
     */
    private void sendEOF() {
	if (MyUtilities.isAckEveryTuple(getConf()))
	    if (_pendingTuples == 0)
		if (!_hasSentEOF) {
		    _hasSentEOF = true;
		    getCollector().emit(SystemParameters.EOF_STREAM,
			    new Values(SystemParameters.EOF));
		}
    }

    /**
     * Signal from the Distribution Spout Signaler or it can be from the Harmonizer Signal
     * @param payload
     */
    @Override
    public void onSignal(byte[] payload) {
	int x = byteArrayToInt(payload);
	LOG.info("Changed the KeyValue from " + _keyValue + " to " + x);
	_keyValue = x;
    }

    private int byteArrayToInt(byte[] b) {
	int value = 0;
	for (int i = 0; i < 4; i++) {
	    int shift = (4 - 1 - i) * 8;
	    value += (b[i] & 0x000000FF) << shift;
	}
	return value;
    }
}