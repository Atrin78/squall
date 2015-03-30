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
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ch.epfl.data.squall.conversion.DateConversion;
import ch.epfl.data.squall.conversion.TypeConversion;
import ch.epfl.data.squall.visitors.ValueExpressionVisitor;

public class DateSum implements ValueExpression<Date> {
	private static final long serialVersionUID = 1L;

	private final TypeConversion<Date> _dc = new DateConversion();

	private final ValueExpression<Date> _ve;
	private final int _interval, _unit;

	public DateSum(ValueExpression<Date> ve, int unit, int interval) {
		_ve = ve;
		_unit = unit;
		_interval = interval;
	}

	@Override
	public void accept(ValueExpressionVisitor vev) {
		vev.visit(this);
	}

	@Override
	public void changeValues(int i, ValueExpression<Date> newExpr) {
		// nothing
	}

	@Override
	public Date eval(List<String> tuple) {
		final Date base = _ve.eval(tuple);
		final Calendar c = Calendar.getInstance();
		c.setTime(base);
		c.add(_unit, _interval);
		return c.getTime();
	}

	@Override
	public String evalString(List<String> tuple) {
		return _dc.toString(eval(tuple));
	}

	@Override
	public List<ValueExpression> getInnerExpressions() {
		final List<ValueExpression> result = new ArrayList<ValueExpression>();
		result.add(_ve);
		return result;
	}

	@Override
	public TypeConversion getType() {
		return _dc;
	}

	@Override
	public void inverseNumber() {
		// nothing
	}

	@Override
	public boolean isNegative() {
		// nothing
		return false;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("DateSum ").append(_ve.toString());
		sb.append(" interval ").append(_interval);
		sb.append(" unit ").append(_unit);
		return sb.toString();
	}

}