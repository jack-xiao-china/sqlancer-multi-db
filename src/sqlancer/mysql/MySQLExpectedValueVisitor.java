package sqlancer.mysql;

import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.mysql.ast.MySQLAggregate;
import sqlancer.mysql.ast.MySQLAnyAllSubquery;
import sqlancer.mysql.ast.MySQLBetweenOperation;
import sqlancer.mysql.ast.MySQLBinaryArithmeticOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryOperation;
import sqlancer.mysql.ast.MySQLCaseOperator;
import sqlancer.mysql.ast.MySQLCastOperation;
import sqlancer.mysql.ast.MySQLCollate;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLComputableFunction;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExists;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLInOperation;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLOrderByTerm;
import sqlancer.mysql.ast.MySQLScalarSubquery;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLStringExpression;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.ast.MySQLPrintedExpression;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation;
import sqlancer.mysql.ast.MySQLUnionSelect;
import sqlancer.mysql.ast.MySQLWithSelect;
import sqlancer.mysql.ast.MySQLDerivedTable;
import sqlancer.mysql.ast.MySQLCteTableReference;
import sqlancer.mysql.ast.MySQLOracleExpressionBag;
import sqlancer.mysql.ast.MySQLValues;
import sqlancer.mysql.ast.MySQLResultMap;
import sqlancer.mysql.ast.MySQLOracleAlias;
import sqlancer.mysql.ast.MySQLTypeof;
import sqlancer.mysql.ast.MySQLTableAndColumnRef;
import sqlancer.mysql.ast.MySQLTemporalFunction;

public class MySQLExpectedValueVisitor implements MySQLVisitor {

    private final StringBuilder sb = new StringBuilder();
    private int nrTabs;

    private void print(MySQLExpression expr) {
        MySQLToStringVisitor v = new MySQLToStringVisitor();
        v.visit(expr);
        for (int i = 0; i < nrTabs; i++) {
            sb.append("\t");
        }
        sb.append(v.get());
        sb.append(" -- ");
        sb.append(expr.getExpectedValue());
        sb.append("\n");
    }

    @Override
    public void visit(MySQLExpression expr) {
        nrTabs++;
        try {
            MySQLVisitor.super.visit(expr);
        } catch (IgnoreMeException e) {

        }
        nrTabs--;
    }

    @Override
    public void visit(MySQLConstant constant) {
        print(constant);
    }

    @Override
    public void visit(MySQLColumnReference column) {
        print(column);
    }

    @Override
    public void visit(MySQLUnaryPostfixOperation op) {
        print(op);
        visit(op.getExpression());
    }

    @Override
    public void visit(MySQLComputableFunction f) {
        print(f);
        for (MySQLExpression expr : f.getArguments()) {
            visit(expr);
        }
    }

    @Override
    public void visit(MySQLBinaryLogicalOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    public String get() {
        return sb.toString();
    }

    @Override
    public void visit(MySQLSelect select) {
        for (MySQLExpression j : select.getJoinList()) {
            visit(j);
        }
        if (select.getWhereClause() != null) {
            visit(select.getWhereClause());
        }
    }

    @Override
    public void visit(MySQLBinaryComparisonOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(MySQLCastOperation op) {
        print(op);
        visit(op.getExpr());
    }

    @Override
    public void visit(MySQLInOperation op) {
        print(op);
        for (MySQLExpression right : op.getListElements()) {
            visit(right);
        }
    }

    @Override
    public void visit(MySQLBinaryOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(MySQLBinaryArithmeticOperation op) {
        print(op);
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(MySQLOrderByTerm op) {
    }

    @Override
    public void visit(MySQLExists op) {
        print(op);
        visit(op.getExpr());
    }

    @Override
    public void visit(MySQLAnyAllSubquery op) {
        print(op);
        visit(op.getLhs());
        visit(op.getSubquery());
    }

    @Override
    public void visit(MySQLScalarSubquery op) {
        print(op);
        visit(op.getSubquery());
    }

    @Override
    public void visit(MySQLStringExpression op) {
        print(op);
    }

    @Override
    public void visit(MySQLBetweenOperation op) {
        print(op);
        visit(op.getExpr());
        visit(op.getLeft());
        visit(op.getRight());
    }

    @Override
    public void visit(MySQLTableReference ref) {
    }

    @Override
    public void visit(MySQLCollate collate) {
        print(collate);
        visit(collate.getExpectedValue());
    }

    @Override
    public void visit(MySQLJoin join) {
        print(join);
        visit(join.getOnClause());
    }

    @Override
    public void visit(MySQLText text) {
        print(text);
    }

    @Override
    public void visit(MySQLAggregate aggr) {
        // PQS is currently unsupported for aggregates.
        throw new IgnoreMeException();
    }

    @Override
    public void visit(MySQLCaseOperator caseOp) {
        print(caseOp);

        MySQLExpression switchCondition = caseOp.getSwitchCondition();
        if (switchCondition != null) {
            print(switchCondition);
            visit(switchCondition);
        }

        List<MySQLExpression> whenConditions = caseOp.getConditions();
        List<MySQLExpression> thenExpressions = caseOp.getExpressions();

        for (int i = 0; i < whenConditions.size(); i++) {
            print(whenConditions.get(i));
            visit(whenConditions.get(i));
            print(thenExpressions.get(i));
            visit(thenExpressions.get(i));
        }

        MySQLExpression elseExpr = caseOp.getElseExpr();
        if (elseExpr != null) {
            print(elseExpr);
            visit(elseExpr);
        }
    }

    @Override
    public void visit(MySQLPrintedExpression printed) {
        print(printed);
    }

    @Override
    public void visit(MySQLUnionSelect unionSelect) {
        throw new IgnoreMeException();
    }

    @Override
    public void visit(MySQLWithSelect withSelect) {
        throw new IgnoreMeException();
    }

    @Override
    public void visit(MySQLDerivedTable derivedTable) {
        throw new IgnoreMeException();
    }

    @Override
    public void visit(MySQLCteTableReference cteTableRef) {
        throw new IgnoreMeException();
    }

    @Override
    public void visit(MySQLOracleExpressionBag bag) {
        print(bag);
    }

    @Override
    public void visit(MySQLValues values) {
        print(values);
    }

    @Override
    public void visit(MySQLResultMap resultMap) {
        print(resultMap);
    }

    @Override
    public void visit(MySQLOracleAlias alias) {
        print(alias);
    }

    @Override
    public void visit(MySQLTypeof typeOf) {
        print(typeOf);
    }

    @Override
    public void visit(MySQLTableAndColumnRef ref) {
        print(ref);
    }

    @Override
    public void visit(MySQLTemporalFunction func) {
        print(func);
        visit(func.getTemporalExpr());
        visit(func.getIntervalExpr());
    }
}
