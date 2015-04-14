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

package ch.epfl.data.squall.expressions;

import java.util.ArrayList;
import java.util.List;

import ch.epfl.data.squall.types.Type;
import ch.epfl.data.squall.visitors.ValueExpressionVisitor;

public class ColumnReference<T extends Comparable<T>> implements
	ValueExpression<T> {
    private static final long serialVersionUID = 1L;

    private int _columnIndex;
    private String _columnStr;
    private Type<T> _wrapper;

    public ColumnReference(Type<T> wrapper, int columnIndex) {
	_columnIndex = columnIndex;
	_wrapper = wrapper;
    }

    /*
     * columnStr is optional, used only in toString method
     */
    public ColumnReference(Type<T> wrapper, int columnIndex, String columnStr) {
	this(wrapper, columnIndex);
	_columnStr = columnStr;
    }

    @Override
    public void accept(ValueExpressionVisitor vev) {
	vev.visit(this);
    }

    @Override
    public void changeValues(int i, ValueExpression<T> newExpr) {
	// nothing
    }

    @Override
    public T eval(List<String> tuple) {
	final String value = tuple.get(_columnIndex);
	return _wrapper.fromString(value);
    }

    @Override
    public String evalString(List<String> tuple) {
	return tuple.get(_columnIndex);
    }

    public int getColumnIndex() {
	return _columnIndex;
    }

    @Override
    public List<ValueExpression> getInnerExpressions() {
	return new ArrayList<ValueExpression>();
    }

    @Override
    public Type getType() {
	return _wrapper;
    }

    @Override
    public void inverseNumber() {
	// nothing

    }

    @Override
    public boolean isNegative() {
	return false;
    }

    public void setColumnIndex(int column) {
	_columnIndex = column;
    }

    @Override
    public String toString() {
	final StringBuilder sb = new StringBuilder();
	if (_columnStr != null)
	    sb.append("\"").append(_columnStr).append("\" - ");
	sb.append("Col ").append(_columnIndex);
	return sb.toString();
    }

}
