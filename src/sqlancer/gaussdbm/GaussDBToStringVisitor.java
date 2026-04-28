package sqlancer.gaussdbm;

import sqlancer.Randomly;
import sqlancer.common.visitor.ToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;
import sqlancer.gaussdbm.ast.GaussDBBetweenOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBCastOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBCteDefinition;
import sqlancer.gaussdbm.ast.GaussDBCteTableReference;
import sqlancer.gaussdbm.ast.GaussDBDerivedTable;
import sqlancer.gaussdbm.ast.GaussDBExists;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBIfFunction;
import sqlancer.gaussdbm.ast.GaussDBInOperation;
import sqlancer.gaussdbm.ast.GaussDBJsonFunction;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBManuelPredicate;
import sqlancer.gaussdbm.ast.GaussDBOracleAlias;
import sqlancer.gaussdbm.ast.GaussDBPostfixText;
import sqlancer.gaussdbm.ast.GaussDBPrintedExpression;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction;
import sqlancer.gaussdbm.ast.GaussDBText;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnionSelect;
import sqlancer.gaussdbm.ast.GaussDBWindowFunction;
import sqlancer.gaussdbm.ast.GaussDBWithSelect;

import java.util.List;

public class GaussDBToStringVisitor extends ToStringVisitor<GaussDBExpression> {

    private int ref;

    @Override
    public void visitSpecific(GaussDBExpression expr) {
        if (expr instanceof GaussDBSelect) {
            visit((GaussDBSelect) expr);
        } else if (expr instanceof GaussDBConstant) {
            visit((GaussDBConstant) expr);
        } else if (expr instanceof GaussDBColumnReference) {
            visit((GaussDBColumnReference) expr);
        } else if (expr instanceof GaussDBBinaryLogicalOperation) {
            visit((GaussDBBinaryLogicalOperation) expr);
        } else if (expr instanceof GaussDBBinaryComparisonOperation) {
            visit((GaussDBBinaryComparisonOperation) expr);
        } else if (expr instanceof GaussDBBinaryArithmeticOperation) {
            visit((GaussDBBinaryArithmeticOperation) expr);
        } else if (expr instanceof GaussDBBetweenOperation) {
            visit((GaussDBBetweenOperation) expr);
        } else if (expr instanceof GaussDBTableReference) {
            visit((GaussDBTableReference) expr);
        } else if (expr instanceof GaussDBJoin) {
            visit((GaussDBJoin) expr);
        } else if (expr instanceof GaussDBUnaryPrefixOperation) {
            visit((GaussDBUnaryPrefixOperation) expr);
        } else if (expr instanceof GaussDBUnaryPostfixOperation) {
            visit((GaussDBUnaryPostfixOperation) expr);
        } else if (expr instanceof GaussDBAggregate) {
            visit((GaussDBAggregate) expr);
        } else if (expr instanceof GaussDBIfFunction) {
            visit((GaussDBIfFunction) expr);
        } else if (expr instanceof GaussDBCaseWhen) {
            visit((GaussDBCaseWhen) expr);
        } else if (expr instanceof GaussDBText) {
            visit((GaussDBText) expr);
        } else if (expr instanceof GaussDBPrintedExpression) {
            visit((GaussDBPrintedExpression) expr);
        } else if (expr instanceof GaussDBUnionSelect) {
            visit((GaussDBUnionSelect) expr);
        } else if (expr instanceof GaussDBWithSelect) {
            visit((GaussDBWithSelect) expr);
        } else if (expr instanceof GaussDBDerivedTable) {
            visit((GaussDBDerivedTable) expr);
        } else if (expr instanceof GaussDBCteTableReference) {
            visit((GaussDBCteTableReference) expr);
        } else if (expr instanceof GaussDBOracleAlias) {
            visit((GaussDBOracleAlias) expr);
        } else if (expr instanceof GaussDBManuelPredicate) {
            visit((GaussDBManuelPredicate) expr);
        } else if (expr instanceof GaussDBPostfixText) {
            visit((GaussDBPostfixText) expr);
        } else if (expr instanceof GaussDBInOperation) {
            visit((GaussDBInOperation) expr);
        } else if (expr instanceof GaussDBExists) {
            visit((GaussDBExists) expr);
        } else if (expr instanceof GaussDBWindowFunction) {
            visit((GaussDBWindowFunction) expr);
        } else if (expr instanceof GaussDBCastOperation) {
            visit((GaussDBCastOperation) expr);
        } else if (expr instanceof GaussDBTemporalFunction) {
            visit((GaussDBTemporalFunction) expr);
        } else if (expr instanceof GaussDBComputableFunction) {
            visit((GaussDBComputableFunction) expr);
        } else if (expr instanceof GaussDBJsonFunction) {
            visit((GaussDBJsonFunction) expr);
        } else {
            throw new AssertionError(expr);
        }
    }

    public void visit(GaussDBText text) {
        sb.append(text.getText());
    }

    public void visit(GaussDBPrintedExpression printed) {
        sb.append("(");
        sb.append(printed.getPrintedSql());
        sb.append(")");
    }

    public void visit(GaussDBUnionSelect u) {
        List<GaussDBSelect> branches = u.getBranches();
        for (int i = 0; i < branches.size(); i++) {
            if (i > 0) {
                sb.append(u.isUnionAll() ? " UNION ALL " : " UNION ");
            }
            visit(branches.get(i));
        }
    }

    public void visit(GaussDBWithSelect w) {
        sb.append("WITH ");
        for (int i = 0; i < w.getCtes().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            GaussDBCteDefinition d = w.getCtes().get(i);
            sb.append(d.getName());
            sb.append(" AS (");
            visit(d.getSubquery());
            sb.append(") ");
        }
        visit(w.getMainQuery());
    }

    public void visit(GaussDBDerivedTable d) {
        sb.append("(");
        visit(d.getSubquery());
        sb.append(") AS ");
        sb.append(d.getAlias());
    }

    public void visit(GaussDBCteTableReference r) {
        sb.append(r.getCteName());
    }

    public void visit(GaussDBOracleAlias alias) {
        if (alias.getOriginalExpression() != null) {
            GaussDBExpression expr = alias.getOriginalExpression();
            // Wrap subqueries in parentheses for M-compatibility
            if (expr instanceof GaussDBSelect) {
                sb.append("(");
                visit(expr);
                sb.append(")");
            } else {
                visit(expr);
            }
        } else {
            sb.append("NULL");
        }
    }

    public void visit(GaussDBSelect s) {
        sb.append("SELECT ");
        switch (s.getSelectType()) {
        case DISTINCT:
            sb.append("DISTINCT ");
            break;
        case ALL:
            if (Randomly.getBoolean()) {
                sb.append("ALL ");
            }
            break;
        default:
            throw new AssertionError(s.getSelectType());
        }
        if (s.getFetchColumns() == null) {
            sb.append("*");
        } else {
            List<GaussDBExpression> fetchColumns = s.getFetchColumns();
            for (int i = 0; i < fetchColumns.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                visit(fetchColumns.get(i));
                sb.append(" AS ref");
                sb.append(ref++);
            }
        }
        if (!s.getFromList().isEmpty()) {
            sb.append(" FROM ");
            super.visit(s.getFromList());
        }
        if (!s.getJoinList().isEmpty()) {
            sb.append(" ");
            super.visit(s.getJoinList());
        }
        if (s.getWhereClause() != null) {
            sb.append(" WHERE ");
            visit(s.getWhereClause());
        }
        if (!s.getGroupByExpressions().isEmpty()) {
            sb.append(" GROUP BY ");
            super.visit(s.getGroupByExpressions());
        }
        if (s.getHavingClause() != null) {
            sb.append(" HAVING ");
            visit(s.getHavingClause());
        }
        if (!s.getOrderByClauses().isEmpty()) {
            sb.append(" ORDER BY ");
            super.visit(s.getOrderByClauses());
        }
        if (s.getLimitClause() != null) {
            sb.append(" LIMIT ");
            visit(s.getLimitClause());
        }
        if (s.getOffsetClause() != null) {
            sb.append(" OFFSET ");
            visit(s.getOffsetClause());
        }
    }

    public void visit(GaussDBAggregate aggr) {
        GaussDBAggregateFunction func = aggr.getFunc();
        String option = func.getOption();
        List<GaussDBExpression> exprs = aggr.getExprs();
        sb.append(func.getName());
        sb.append("(");
        if (option != null) {
            sb.append(option);
            sb.append(" ");
        }
        for (int i = 0; i < exprs.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            visit(exprs.get(i));
        }
        sb.append(")");
    }

    public void visit(GaussDBIfFunction f) {
        sb.append("IF(");
        visit(f.getCondition());
        sb.append(", ");
        visit(f.getThenExpr());
        sb.append(", ");
        visit(f.getElseExpr());
        sb.append(")");
    }

    public void visit(GaussDBCaseWhen c) {
        sb.append("(CASE WHEN ");
        visit(c.getWhenExpr());
        sb.append(" THEN ");
        visit(c.getThenExpr());
        sb.append(" ELSE ");
        visit(c.getElseExpr());
        sb.append(" END)");
    }

    public void visit(GaussDBConstant constant) {
        sb.append(constant.getTextRepresentation());
    }

    public void visit(GaussDBColumnReference column) {
        sb.append(column.getColumn().getTable().getName());
        sb.append(".");
        sb.append(column.getColumn().getName());
    }

    public void visit(GaussDBBinaryLogicalOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(" ");
        sb.append(op.getOp().getTextRepresentation());
        sb.append(" ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBBinaryComparisonOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(" ");
        sb.append(op.getOp().getTextRepr());
        sb.append(" ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBBinaryArithmeticOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(" ");
        sb.append(op.getOp().getTextRepresentation());
        sb.append(" ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBBetweenOperation op) {
        sb.append("(");
        visit(op.getExpr());
        sb.append(op.isNegated() ? " NOT BETWEEN " : " BETWEEN ");
        visit(op.getLeft());
        sb.append(" AND ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBInOperation op) {
        sb.append("(");
        visit(op.getExpr());
        if (!op.isTrue()) {
            sb.append(" NOT");
        }
        sb.append(" IN (");
        for (int i = 0; i < op.getListElements().size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            visit(op.getListElements().get(i));
        }
        sb.append("))");
    }

    public void visit(GaussDBTableReference ref) {
        sb.append(ref.getTable().getName());
    }

    public void visit(GaussDBJoin join) {
        switch (join.getJoinType()) {
        case NATURAL:
            sb.append("NATURAL JOIN ");
            break;
        case INNER:
            sb.append("JOIN ");
            break;
        case LEFT:
            sb.append("LEFT JOIN ");
            break;
        case RIGHT:
            sb.append("RIGHT JOIN ");
            break;
        case CROSS:
            sb.append("CROSS JOIN ");
            break;
        default:
            throw new AssertionError(join.getJoinType());
        }
        visit(join.getTableReference());
        if (join.getJoinType() != GaussDBJoin.JoinType.CROSS && join.getJoinType() != GaussDBJoin.JoinType.NATURAL) {
            sb.append(" ON ");
            visit(join.getOnCondition());
        }
    }

    public void visit(GaussDBUnaryPrefixOperation op) {
        sb.append("(");
        sb.append(op.getOp().getText());
        sb.append(" ");
        visit(op.getExpr());
        sb.append(")");
    }

    public void visit(GaussDBUnaryPostfixOperation op) {
        sb.append("(");
        visit(op.getExpr());
        sb.append(" ");
        sb.append(op.getOp().getText());
        sb.append(")");
    }

    public static String asString(GaussDBExpression expr) {
        GaussDBToStringVisitor v = new GaussDBToStringVisitor();
        v.visit(expr);
        return v.get();
    }

    public static String asExpectedValues(GaussDBExpression expr) {
        return GaussDBExpectedValueVisitor.asExpectedValues(expr);
    }

    public void visit(GaussDBManuelPredicate pred) {
        sb.append(pred.getString());
    }

    public void visit(GaussDBPostfixText postfixText) {
        visit(postfixText.getExpr());
        sb.append(postfixText.getText());
    }

    public void visit(GaussDBExists exists) {
        sb.append("EXISTS (");
        visit(exists.getExpr());
        sb.append(")");
    }

    public void visit(GaussDBWindowFunction window) {
        sb.append(window.getFunction().getName());
        sb.append("(");
        if (window.getExpr() != null) {
            visit(window.getExpr());
        }
        sb.append(") OVER (");
        if (!window.getPartitionBy().isEmpty()) {
            sb.append("PARTITION BY ");
            for (int i = 0; i < window.getPartitionBy().size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                visit(window.getPartitionBy().get(i));
            }
            if (!window.getOrderBy().isEmpty()) {
                sb.append(" ");
            }
        }
        if (!window.getOrderBy().isEmpty()) {
            sb.append("ORDER BY ");
            for (int i = 0; i < window.getOrderBy().size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                visit(window.getOrderBy().get(i));
            }
        }
        sb.append(")");
    }

    public void visit(GaussDBCastOperation cast) {
        sb.append("CAST(");
        visit(cast.getExpr());
        sb.append(" AS ");
        sb.append(cast.getType().getTextRepresentation());
        sb.append(")");
    }

    public void visit(GaussDBTemporalFunction func) {
        sb.append(func.getFunc().getFunctionName());
        sb.append("(");
        List<GaussDBExpression> args = func.getArgs();
        for (int i = 0; i < args.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            visit(args.get(i));
        }
        sb.append(")");
    }

    public void visit(GaussDBComputableFunction func) {
        sb.append(func.getFunction().getName());
        sb.append("(");
        List<GaussDBExpression> args = func.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            visit(args.get(i));
        }
        sb.append(")");
    }

    public void visit(GaussDBJsonFunction func) {
        sb.append(func.getFunction().getName());
        sb.append("(");
        List<GaussDBExpression> args = func.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            visit(args.get(i));
        }
        sb.append(")");
    }
}
