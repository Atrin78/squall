package ch.epfl.data.squall.api.sql.visitors.squall;

import java.util.ArrayList;
import java.util.List;

import ch.epfl.data.squall.expressions.Addition;
import ch.epfl.data.squall.expressions.ColumnReference;
import ch.epfl.data.squall.expressions.DateDiff;
import ch.epfl.data.squall.expressions.DateSum;
import ch.epfl.data.squall.expressions.Division;
import ch.epfl.data.squall.expressions.IntegerYearFromDate;
import ch.epfl.data.squall.expressions.Multiplication;
import ch.epfl.data.squall.expressions.StringConcatenate;
import ch.epfl.data.squall.expressions.Subtraction;
import ch.epfl.data.squall.expressions.ValueExpression;
import ch.epfl.data.squall.expressions.ValueSpecification;
import ch.epfl.data.squall.visitors.ValueExpressionVisitor;

public class ColumnRefCollectVisitor implements ValueExpressionVisitor {
	private final List<ColumnReference> _crList = new ArrayList<ColumnReference>();

	public List<ColumnReference> getColumnRefs() {
		return _crList;
	}

	@Override
	public void visit(Addition add) {
		visit(add.getInnerExpressions());
	}

	@Override
	public void visit(ColumnReference cr) {
		_crList.add(cr);
	}

	@Override
	public void visit(DateDiff dd) {
		visit(dd.getInnerExpressions());
	}

	@Override
	public void visit(DateSum ds) {
		visit(ds.getInnerExpressions());
	}

	@Override
	public void visit(Division dvsn) {
		visit(dvsn.getInnerExpressions());
	}

	@Override
	public void visit(IntegerYearFromDate iyfd) {
		visit(iyfd.getInnerExpressions());
	}

	private void visit(List<ValueExpression> veList) {
		for (final ValueExpression ve : veList)
			ve.accept(this);
	}

	@Override
	public void visit(Multiplication mult) {
		visit(mult.getInnerExpressions());
	}

	@Override
	public void visit(StringConcatenate sc) {
		visit(sc.getInnerExpressions());
	}

	@Override
	public void visit(Subtraction sub) {
		visit(sub.getInnerExpressions());
	}

	@Override
	public void visit(ValueSpecification vs) {
		// constant
	}

}
