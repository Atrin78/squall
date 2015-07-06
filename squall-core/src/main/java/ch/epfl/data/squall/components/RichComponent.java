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

package ch.epfl.data.squall.components;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;

import ch.epfl.data.squall.expressions.ValueExpression;
import ch.epfl.data.squall.operators.ChainOperator;
import ch.epfl.data.squall.operators.Operator;
import ch.epfl.data.squall.types.Type;

public abstract class RichComponent<C extends Component> implements Component {

    protected abstract C getThis();

    private long _batchOutputMillis;
    private final ChainOperator _chain = new ChainOperator();
    private Component _child;

    private List<ValueExpression> _hashExpressions;
    private List<Integer> _hashIndexes;

    private boolean _printOut;
    private boolean _printOutSet; // whether printOut condition is already set


    @Override
    public boolean equals(Object obj) {
	if (obj instanceof Component)
            return getName().equals(((Component) obj).getName());
	else
	    return false;
    }

    @Override
    public long getBatchOutputMillis() {
	return _batchOutputMillis;
    }

    // Operations with chaining
    @Override
    public C setBatchOutputMillis(long millis) {
	_batchOutputMillis = millis;
	return getThis();
    }

    @Override
    public C add(Operator operator) {
	_chain.addOperator(operator);
	return getThis();
    }

    @Override
    public ChainOperator getChainOperator() {
	return _chain;
    }

    @Override
    public Component getChild() {
	return _child;
    }

    @Override
    public void setChild(Component child) {
	_child = child;
    }

    @Override
    public List<ValueExpression> getHashExpressions() {
	return _hashExpressions;
    }

    @Override
    public C setHashExpressions(
	    List<ValueExpression> hashExpressions) {
	_hashExpressions = hashExpressions;
	return getThis();
    }

    @Override
    public List<Integer> getHashIndexes() {
	return _hashIndexes;
    }

    @Override
    public C setOutputPartKey(List<Integer> hashIndexes) {
	_hashIndexes = hashIndexes;
	return getThis();
    }

    @Override
    public boolean getPrintOut() {
	return _printOut;
    }

    protected boolean getPrintOutSet() {
	return _printOutSet;
    }

    @Override
    public C setPrintOut(boolean printOut) {
	_printOutSet = true;
	_printOut = printOut;
	return getThis();
    }

    @Override
    public int hashCode() {
	int hash = 7;
	hash = 37 * hash
		+ (getName() != null ? getName().hashCode() : 0);
	return hash;
    }

    @Override
    public C setContentSensitiveThetaJoinWrapper(Type wrapper) {
        return getThis();
    }

    @Override
    public C setOutputPartKey(int... hashIndexes) {
	return setOutputPartKey(Arrays.asList(ArrayUtils.toObject(hashIndexes)));
    }
}
