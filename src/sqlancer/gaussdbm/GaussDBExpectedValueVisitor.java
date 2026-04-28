package sqlancer.gaussdbm;

import sqlancer.IgnoreMeException;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBBetweenOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBIfFunction;
import sqlancer.gaussdbm.ast.GaussDBInOperation;
import sqlancer.gaussdbm.ast.GaussDBJsonFunction;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBWindowFunction;

public final class GaussDBExpectedValueVisitor {

    private final StringBuilder sb = new StringBuilder();
    private int nrTabs;

    private GaussDBExpectedValueVisitor() {
    }

    private void print(GaussDBExpression expr) {
        GaussDBToStringVisitor v = new GaussDBToStringVisitor();
        v.visit(expr);
        for (int i = 0; i < nrTabs; i++) {
            sb.append("\t");
        }
        sb.append(v.get());
        sb.append(" -- ");
        sb.append(expr.getExpectedValue());
        sb.append("\n");
    }

    public void visit(GaussDBExpression expr) {
        nrTabs++;
        try {
            visitInner(expr);
        } catch (IgnoreMeException e) {
            // skip subtree
        }
        nrTabs--;
    }

    private void visitInner(GaussDBExpression expr) {
        if (expr instanceof GaussDBConstant) {
            print(expr);
        } else if (expr instanceof GaussDBColumnReference) {
            print(expr);
        } else if (expr instanceof GaussDBUnaryPostfixOperation) {
            GaussDBUnaryPostfixOperation op = (GaussDBUnaryPostfixOperation) expr;
            print(op);
            visit(op.getExpr());
        } else if (expr instanceof GaussDBUnaryPrefixOperation) {
            GaussDBUnaryPrefixOperation op = (GaussDBUnaryPrefixOperation) expr;
            print(op);
            visit(op.getExpr());
        } else if (expr instanceof GaussDBBinaryLogicalOperation) {
            GaussDBBinaryLogicalOperation op = (GaussDBBinaryLogicalOperation) expr;
            print(op);
            visit(op.getLeft());
            visit(op.getRight());
        } else if (expr instanceof GaussDBBinaryComparisonOperation) {
            GaussDBBinaryComparisonOperation op = (GaussDBBinaryComparisonOperation) expr;
            print(op);
            visit(op.getLeft());
            visit(op.getRight());
        } else if (expr instanceof GaussDBBinaryArithmeticOperation) {
            GaussDBBinaryArithmeticOperation op = (GaussDBBinaryArithmeticOperation) expr;
            print(op);
            visit(op.getLeft());
            visit(op.getRight());
        } else if (expr instanceof GaussDBBetweenOperation) {
            GaussDBBetweenOperation op = (GaussDBBetweenOperation) expr;
            print(op);
            visit(op.getExpr());
            visit(op.getLeft());
            visit(op.getRight());
        } else if (expr instanceof GaussDBInOperation) {
            GaussDBInOperation op = (GaussDBInOperation) expr;
            print(op);
            visit(op.getExpr());
            for (GaussDBExpression listElement : op.getListElements()) {
                visit(listElement);
            }
        } else if (expr instanceof GaussDBSelect) {
            GaussDBSelect s = (GaussDBSelect) expr;
            for (GaussDBExpression j : s.getJoinList()) {
                visit(j);
            }
            if (s.getWhereClause() != null) {
                visit(s.getWhereClause());
            }
        } else if (expr instanceof GaussDBJoin) {
            GaussDBJoin j = (GaussDBJoin) expr;
            print(j);
            visit(j.getOnCondition());
        } else if (expr instanceof GaussDBTableReference) {
            // leaf for PQS containment
        } else if (expr instanceof GaussDBAggregate) {
            throw new IgnoreMeException();
        } else if (expr instanceof GaussDBIfFunction) {
            GaussDBIfFunction f = (GaussDBIfFunction) expr;
            print(f);
            visit(f.getCondition());
            visit(f.getThenExpr());
            visit(f.getElseExpr());
        } else if (expr instanceof GaussDBCaseWhen) {
            GaussDBCaseWhen c = (GaussDBCaseWhen) expr;
            print(c);
            visit(c.getWhenExpr());
            visit(c.getThenExpr());
            visit(c.getElseExpr());
        } else if (expr instanceof GaussDBWindowFunction) {
            // PQS is currently unsupported for window functions.
            throw new IgnoreMeException();
        } else if (expr instanceof GaussDBComputableFunction) {
            GaussDBComputableFunction func = (GaussDBComputableFunction) expr;
            print(func);
            for (GaussDBExpression arg : func.getArguments()) {
                visit(arg);
            }
        } else if (expr instanceof GaussDBJsonFunction) {
            GaussDBJsonFunction func = (GaussDBJsonFunction) expr;
            print(func);
            for (GaussDBExpression arg : func.getArguments()) {
                visit(arg);
            }
        } else {
            throw new AssertionError(expr);
        }
    }

    public String get() {
        return sb.toString();
    }

    public static String asExpectedValues(GaussDBExpression expr) {
        GaussDBExpectedValueVisitor v = new GaussDBExpectedValueVisitor();
        v.visit(expr);
        return v.get();
    }
}
