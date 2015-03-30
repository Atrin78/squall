package ch.epfl.data.squall.connectors.hdfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.storm.hdfs.bolt.HdfsBolt;
import org.apache.storm.hdfs.bolt.format.DefaultFileNameFormat;
import org.apache.storm.hdfs.bolt.format.DelimitedRecordFormat;
import org.apache.storm.hdfs.bolt.format.FileNameFormat;
import org.apache.storm.hdfs.bolt.format.RecordFormat;
import org.apache.storm.hdfs.bolt.rotation.FileRotationPolicy;
import org.apache.storm.hdfs.bolt.rotation.FileSizeRotationPolicy;
import org.apache.storm.hdfs.bolt.rotation.FileSizeRotationPolicy.Units;
import org.apache.storm.hdfs.bolt.sync.CountSyncPolicy;
import org.apache.storm.hdfs.bolt.sync.SyncPolicy;

import ch.epfl.data.squall.components.Component;
import ch.epfl.data.squall.components.DataSourceComponent;
import ch.epfl.data.squall.conversion.TypeConversion;
import ch.epfl.data.squall.expressions.ValueExpression;
import ch.epfl.data.squall.operators.ChainOperator;
import ch.epfl.data.squall.operators.Operator;
import ch.epfl.data.squall.predicates.Predicate;
import ch.epfl.data.squall.storm_components.InterchangingComponent;
import ch.epfl.data.squall.storm_components.StormComponent;
import ch.epfl.data.squall.storm_components.StormEmitter;
import ch.epfl.data.squall.storm_components.synchronization.TopologyKiller;
import ch.epfl.data.squall.utilities.MyUtilities;
import backtype.storm.Config;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichBolt;


//import org.apache.storm.h*;

public class HDFSmaterializer implements Component{
	
	private final String _componentName;

	private long _batchOutputMillis;

	private List<Integer> _hashIndexes;
	private List<ValueExpression> _hashExpressions;

	private final ChainOperator _chain = new ChainOperator();

	private boolean _printOut;
	private boolean _printOutSet;

	private final Component _parent;
	private Component _child;
	//private StormOperator _stormOperator;

	private List<String> _fullHashList;
	
	private String _hdfsPath;
	
	public HDFSmaterializer(Component parent, String componentName, String hdfsPath) {

		_parent = parent;
		_parent.setChild(this);
		_componentName = componentName;
		_hdfsPath = hdfsPath;
	}


	public BaseRichBolt createHDFSmaterializer(String hdfsPath) {
		RecordFormat format = new DelimitedRecordFormat()
		.withFieldDelimiter("|");

		// sync the filesystem after every 1k tuples
		SyncPolicy syncPolicy = new CountSyncPolicy(1000);

		// rotate files when they reach 5MB
		FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, Units.MB);

		FileNameFormat fileNameFormat = new DefaultFileNameFormat()
		.withPath("/foo/");

		HdfsBolt bolt = new HdfsBolt()
		.withFsUrl(hdfsPath)
		.withFileNameFormat(fileNameFormat)
		.withRecordFormat(format)
		.withRotationPolicy(rotationPolicy)
		.withSyncPolicy(syncPolicy);
		return bolt;
	}

	@Override
	public HDFSmaterializer add(Operator operator) {
		_chain.addOperator(operator);
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Component)
			return _componentName.equals(((Component) obj).getName());
		else
			return false;
	}

	@Override
	public List<DataSourceComponent> getAncestorDataSources() {
		final List<DataSourceComponent> list = new ArrayList<DataSourceComponent>();
		list.addAll(_parent.getAncestorDataSources());
		return list;
	}

	@Override
	public long getBatchOutputMillis() {
		return _batchOutputMillis;
	}

	@Override
	public ChainOperator getChainOperator() {
		return _chain;
	}

	@Override
	public Component getChild() {
		return _child;
	}

	// from StormComponent
	@Override
	public String[] getEmitterIDs() {
		throw new RuntimeException("Not implemented yet");
	}

	@Override
	public List<String> getFullHashList() {
		return _fullHashList;
	}

	@Override
	public List<ValueExpression> getHashExpressions() {
		return _hashExpressions;
	}

	@Override
	public List<Integer> getHashIndexes() {
		return _hashIndexes;
	}

	@Override
	public String getInfoID() {
		throw new RuntimeException("Not implemented yet");
	}

	@Override
	public String getName() {
		return _componentName;
	}

	@Override
	public Component[] getParents() {
		return new Component[] { _parent };
	}

	@Override
	public boolean getPrintOut() {
		return _printOut;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 47 * hash
				+ (_componentName != null ? _componentName.hashCode() : 0);
		return hash;
	}

	@Override
	public void makeBolts(TopologyBuilder builder, TopologyKiller killer,
			List<String> allCompNames, Config conf, int hierarchyPosition) {

		// by default print out for the last component
		// for other conditions, can be set via setPrintOut
		if (hierarchyPosition == StormComponent.FINAL_COMPONENT
				&& !_printOutSet)
			setPrintOut(true);

		MyUtilities.checkBatchOutput(_batchOutputMillis,
				_chain.getAggregation(), conf);

		BaseRichBolt hdfsBolt= createHDFSmaterializer(_hdfsPath);

		
		builder.setBolt("hdfs", hdfsBolt, 4).shuffleGrouping(((StormEmitter)_parent).getEmitterIDs()[0]);
		
	}

	@Override
	public HDFSmaterializer setBatchOutputMillis(long millis) {
		_batchOutputMillis = millis;
		return this;
	}

	@Override
	public void setChild(Component child) {
		_child = child;
	}

	@Override
	public Component setContentSensitiveThetaJoinWrapper(TypeConversion wrapper) {
		return this;
	}

	@Override
	public HDFSmaterializer setFullHashList(List<String> fullHashList) {
		_fullHashList = fullHashList;
		return this;
	}

	@Override
	public HDFSmaterializer setHashExpressions(
			List<ValueExpression> hashExpressions) {
		_hashExpressions = hashExpressions;
		return this;
	}

	@Override
	public Component setInterComp(InterchangingComponent inter) {
		throw new RuntimeException(
				"Operator component does not support setInterComp");
	}

	@Override
	public Component setJoinPredicate(Predicate joinPredicate) {
		throw new RuntimeException(
				"Operator component does not support Join Predicates");
	}

	@Override
	public HDFSmaterializer setOutputPartKey(int... hashIndexes) {
		return setOutputPartKey(Arrays.asList(ArrayUtils.toObject(hashIndexes)));
	}

	@Override
	public HDFSmaterializer setOutputPartKey(List<Integer> hashIndexes) {
		_hashIndexes = hashIndexes;
		return this;
	}

	@Override
	public HDFSmaterializer setPrintOut(boolean printOut) {
		_printOutSet = true;
		_printOut = printOut;
		return this;
	}

}
