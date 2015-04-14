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

package ch.epfl.data.squall.storm_components;

import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import backtype.storm.Config;
import backtype.storm.topology.TopologyBuilder;
import ch.epfl.data.squall.components.ComponentProperties;
import ch.epfl.data.squall.storm_components.synchronization.TopologyKiller;

/*
 * This class works only for Hyracks query.
 * A more generic approach is necessary to support randomization for other queries
 *    (an inline version of DBGEN).
 */
public class StormRandomDataSource extends StormDataSource {
    private static final long serialVersionUID = 1L;
    private static Logger LOG = Logger.getLogger(StormRandomDataSource.class);

    private final int _generatedMax = 1500000;
    private final int _customerTotal = 1500000;
    private final int _ordersTotal = 15000000;

    private final Random _randomGenerator = new Random();
    private int _tuplesProduced = -1;
    private final int _customerProduced;
    private final int _ordersProduced;

    public StormRandomDataSource(ComponentProperties cp,
	    List<String> allCompNames, String inputPath, int hierarchyPosition,
	    int parallelism, TopologyBuilder builder, TopologyKiller killer,
	    Config conf) {
	super(cp, allCompNames, inputPath, hierarchyPosition, parallelism,
		false, builder, killer, conf);

	_customerProduced = _customerTotal / parallelism;
	_ordersProduced = _ordersTotal / parallelism;
    }

    @Override
    protected String readLine() {
	_tuplesProduced++;
	String res = null;
	if (getID().equalsIgnoreCase("Customer")) {
	    res = (_randomGenerator.nextInt(_generatedMax))
		    + "|Pera|palace|1|011|sdfa sdwe|FURNITURE|bla";
	    if (_tuplesProduced == _customerProduced)
		return null;
	    else
		return res;
	} else {
	    res = (_randomGenerator.nextInt(_generatedMax))
		    + "|1111|F|1022.34|1995-11-11|1-URGENT|mika|1-URGENT|no comment";
	    if (_tuplesProduced == _ordersProduced)
		return null;
	    else
		return res;
	}
    }
}
