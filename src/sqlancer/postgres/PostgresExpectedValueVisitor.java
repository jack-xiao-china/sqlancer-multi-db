package sqlancer.postgres;

import sqlancer.IgnoreMeException;
import sqlancer.postgres.ast.PostgresAggregate;
import sqlancer.postgres.ast.PostgresBetweenOperation;
import sqlancer.postgres.ast.PostgresBinaryJsonOperation;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation;
import sqlancer.postgres.ast.PostgresCaseWhen;
import sqlancer.postgres.ast.PostgresCastOperation;
import sqlancer.postgres.ast.PostgresCollate;
import sqlancer.postgres.ast.PostgresColumnReference;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresFunction;
import sqlancer.postgres.ast.PostgresInOperation;
import sqlancer.postgres.ast.PostgresJsonContainOperation;
import sqlancer.postgres.ast.PostgresLikeOperation;
import sqlancer.postgres.ast.PostgresOrderByTerm;
import sqlancer.postgres.ast.PostgresPOSIXRegularExpression;
import sqlancer.postgres.ast.PostgresPostfixOperation;
import sqlancer.postgres.ast.PostgresPostfixText;
import sqlancer.postgres.ast.PostgresPrintedExpression;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresText;
import sqlancer.postgres.ast.PostgresUnionSelect;
import sqlancer.postgres.ast.PostgresWithSelect;
import sqlancer.postgres.ast.PostgresDerivedTable;
import sqlancer.postgres.ast.PostgresCteTableReference;
import sqlancer.postgres.ast.PostgresOracleExpressionBag;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresSelect.PostgresSubquery;
import sqlancer.postgres.ast.PostgresSimilarTo;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.ast.PostgresTemporalBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresTemporalFunction;
import sqlancer.postgres.ast.PostgresWindowFunction;

public final class PostgresExpectedValueVisitor implements PostgresVisitor {

    private final StringBuilder sb = new StringBuilder();
    private static final int NR_TABS = 0;

    private void print(PostgresExpression expr) {
        PostgresToStringVisitor v = new PostgresToStringVisitor();
        v.visit(expr);
        for (int i = 0; i < NR_TABS; i++) {
            sb.append("\t");
        }
        sb.append(v.get());
        sb.append(" -- ");
        sb.append(expr.getExpectedValue());
        sb.append("\n");
    }

    // @Override
    // public void visit(PostgresExpression expr) {
    // nrTabs++;
    // try {
    // super.visit(expr);
    // } catch (IgnoreMeException e) {

    // }
    // nrTabs--;
    // }

    @Override
    public void visit(PostgresConstant constant) {
        print(constant);
    }

    @Override
    public void visit(PostgresPostfixOperation op) {
        print(op);
        visit(op.getExpression());
    }

    public String get() {
        return sb.toString();
    }

    @Override
    public void visit(PostgresColumnValue c) {
        print(c);
    }

    @Override
    public void visit(PostgresPrefixOperation op) {
        print(op);
        visit(op.getExpression());
    }

    @Override
    public void visit(PostgresColumnReference column) {
        print(column);
    }

    @Override
    public void visit(PostgresTableReference tb) {
    }

    @Override
    public void visit(PostgresText text) {
        // raw SQL node: cannot compute expected value
    }

    @Override
    public void visit(PostgresUnionSelect unionSelect) {
        // query node: ignore for expected-value tracing
    }

    @Override
    public void visit(PostgresWithSelect withSelect) {
        // query node: ignore for expected-value tracing
    }

    @Override
    public void visit(PostgresDerivedTable derivedTable) {
        // query node: ignore
    }

    @Override
    public void visit(PostgresCteTableReference cteTableReference) {
        // table ref node: ignore
    }

    @Override
    public void visit(PostgresOracleExpressionBag bag) {
        visit(bag.getExpr());
    }

    @Override
    public void visit(PostgresPrintedExpression printedExpression) {
        visit(printedExpression.getOriginal());
    }

    @Override
    public void visit(PostgresCaseWhen caseWhen) {
        throw new IgnoreMeException();
    }

    @Override
    public void visit(PostgresSelect op) {
        visit(op.getWhereClause());
    }

    @Override
    public void visit(PostgresOrderByTerm op) {

    }

    @Override
    public void visit(PostgresWindowFunction windowFunction) {
        throw new IgnoreMeException();
    }

    @Override
    public void visit(PostgresFunction f) {
        print(f);
        for (int i = 0; i < f.getArguments().length; i++) {
            visit(f.getArguments()[i]);
        }
    }

    @Override
    public void visit(PostgresTemporalFunction function) {
        print(function);
        for (PostgresExpression arg : function.getArguments()) {
            visit(arg);
        }
    }

    @Override
    public void visit(PostgresTemporalBinaryArithmeticOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(PostgresCastOperation cast) {
        print(cast);
        visit(cast.getExpression());
    }

    @Override
    public void visit(PostgresBetweenOperation op) {
        print(op);
        visit(op.getExpr());
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(PostgresInOperation op) {
        print(op);
        visit(op.getExpr());
        for (PostgresExpression right : op.getListElements()) {
            visit(right);
        }
    }

    @Override
    public void visit(PostgresPostfixText op) {
        print(op);
        visit(op.getExpr());
    }

    @Override
    public void visit(PostgresAggregate op) {
        print(op);
        for (PostgresExpression expr : op.getArgs()) {
            visit(expr);
        }
    }

    @Override
    public void visit(PostgresSimilarTo op) {
        print(op);
        visit(op.getString());
        visit(op.getSimilarTo());
        if (op.getEscapeCharacter() != null) {
            visit(op.getEscapeCharacter());
        }
    }

    @Override
    public void visit(PostgresPOSIXRegularExpression op) {
        print(op);
        visit(op.getString());
        visit(op.getRegex());
    }

    @Override
    public void visit(PostgresCollate op) {
        print(op);
        visit(op.getExpr());
    }

    @Override
    public void visit(PostgresFromTable from) {
        print(from);
    }

    @Override
    public void visit(PostgresSubquery subquery) {
        print(subquery);
    }

    @Override
    public void visit(PostgresBinaryLogicalOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(PostgresLikeOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(PostgresBinaryJsonOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(PostgresJsonContainOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

}
