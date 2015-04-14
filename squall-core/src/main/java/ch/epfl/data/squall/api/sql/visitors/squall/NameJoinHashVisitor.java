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

package ch.epfl.data.squall.api.sql.visitors.squall;

import java.util.List;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.schema.Column;
import ch.epfl.data.squall.api.sql.optimizers.name.NameTranslator;
import ch.epfl.data.squall.api.sql.util.NotFromMyBranchException;
import ch.epfl.data.squall.api.sql.util.ParserUtil;
import ch.epfl.data.squall.api.sql.util.TupleSchema;
import ch.epfl.data.squall.components.Component;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.expressions.ValueExpression;
import ch.epfl.data.squall.types.Type;

public class NameJoinHashVisitor extends IndexJoinHashVisitor {
    private final NameTranslator _nt;
    private final Component _affectedComponent;

    private final TupleSchema _tupleSchema;

    public NameJoinHashVisitor(TupleSchema tupleSchema,
	    Component affectedComponent) {
	_tupleSchema = tupleSchema;
	_affectedComponent = affectedComponent;

	_nt = new NameTranslator(affectedComponent.getName());
    }

    /*
     * returns true if an expression was found in tupleSchema true means no need
     * to call parent It has side effects - putting on exprStack
     */
    private <T extends Expression> boolean isRecognized(T expr) {
	// expr is changed in place, so that it does not contain synonims
	final int position = _nt.indexOf(_tupleSchema, expr);
	if (position != ParserUtil.NOT_FOUND) {
	    // we found an expression already in the tuple schema
	    final Type tc = _nt.getType(_tupleSchema, expr);
	    final ValueExpression ve = new ColumnReference(tc, position,
		    ParserUtil.getStringExpr(expr));
	    pushToExprStack(ve);
	    return true;
	} else
	    return false;
    }

    @Override
    public void visit(Addition adtn) {
	if (!isRecognized(adtn))
	    // normal call to parent
	    super.visit(adtn);
    }

    /*
     * only getColumnIndex method invocation is different than in parent
     */
    @Override
    public void visit(Column column) {
	final String tableCompName = ParserUtil.getComponentName(column);
	final List<String> ancestorNames = ParserUtil
		.getSourceNameList(_affectedComponent);

	if (ancestorNames.contains(tableCompName)) {
	    // extract the position (index) of the required column
	    // column might be changed, due to the synonim effect
	    final int position = _nt.getColumnIndex(_tupleSchema, column);

	    // extract type for the column
	    final Type tc = _nt.getType(_tupleSchema, column);

	    final ValueExpression ve = new ColumnReference(tc, position,
		    ParserUtil.getStringExpr(column));
	    pushToExprStack(ve);
	} else
	    throw new NotFromMyBranchException();
    }

    @Override
    public void visit(Division dvsn) {
	if (!isRecognized(dvsn))
	    // normal call to parent
	    super.visit(dvsn);
    }

    @Override
    public void visit(Function function) {
	if (!isRecognized(function))
	    // normal call to parent
	    super.visit(function);
    }

    @Override
    public void visit(Multiplication m) {
	if (!isRecognized(m))
	    // normal call to parent
	    super.visit(m);
    }

    @Override
    public void visit(Parenthesis prnths) {
	if (!isRecognized(prnths))
	    // normal call to parent
	    super.visit(prnths);
    }

    @Override
    public void visit(Subtraction s) {
	if (!isRecognized(s))
	    // normal call to parent
	    super.visit(s);
    }

}