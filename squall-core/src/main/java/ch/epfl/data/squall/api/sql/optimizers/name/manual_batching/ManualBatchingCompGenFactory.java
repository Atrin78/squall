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

package ch.epfl.data.squall.api.sql.optimizers.name.manual_batching;

import java.util.Map;

import ch.epfl.data.squall.api.sql.optimizers.name.NameCompGen;
import ch.epfl.data.squall.api.sql.schema.Schema;
import ch.epfl.data.squall.api.sql.util.TableAliasName;

/*
 * It generates different NameCompGen for each partial query plan
 *   NameCompGen is responsible for attaching operators to components
 * Aggregation only on the last level.
 */
public class ManualBatchingCompGenFactory {
    private final Schema _schema;
    private final Map _map; // map is updates in place
    private final TableAliasName _tan;

    private ManualBatchingParallelismAssigner _parAssigner;

    /*
     * only plan, no parallelism
     */
    public ManualBatchingCompGenFactory(Map map, TableAliasName tan) {
	_map = map;
	_tan = tan;

	_schema = new Schema(map);
    }

    /*
     * generating plan + parallelism
     */
    public ManualBatchingCompGenFactory(Map map, TableAliasName tan,
	    int totalSourcePar) {
	this(map, tan);
	setParAssignerMode(totalSourcePar);
    }

    public NameCompGen create() {
	return new NameCompGen(_schema, _map, _parAssigner);
    }

    public ManualBatchingParallelismAssigner getParAssigner() {
	return _parAssigner;
    }

    public final void setParAssignerMode(int totalSourcePar) {
	// in general there might be many NameComponentGenerators,
	// that's why CPA is computed before of NCG
	_parAssigner = new ManualBatchingParallelismAssigner(_schema, _tan,
		_map);

	// for the same _parAssigner, we might try with different totalSourcePar
	_parAssigner.computeSourcePar(totalSourcePar);
    }

}