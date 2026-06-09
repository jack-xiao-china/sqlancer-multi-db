package sqlancer.gaussdba;

import sqlancer.common.visitor.ToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAAggregate;
import sqlancer.gaussdba.ast.GaussDBABetweenOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryArithmeticOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation;
import sqlancer.gaussdba.ast.GaussDBACaseWhen;
import sqlancer.gaussdba.ast.GaussDBACastOperation;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAColumnValue;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBADataType;
import sqlancer.gaussdba.ast.GaussDBAAlias;
import sqlancer.gaussdba.ast.GaussDBAExists;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBADerivedTable;
import sqlancer.gaussdba.ast.GaussDBAInOperation;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBALikeOperation;
import sqlancer.gaussdba.ast.GaussDBAMinusSelect;
import sqlancer.gaussdba.ast.GaussDBAPrintedExpression;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBAText;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnionSelect;
import sqlancer.gaussdba.ast.GaussDBAWithSelect;
import sqlancer.gaussdba.ast.GaussDBACteDefinition;
import sqlancer.gaussdba.ast.GaussDBACteTableReference;

public class GaussDBAToStringVisitor extends ToStringVisitor<GaussDBAExpression> {

    @Override
    public void visitSpecific(GaussDBAExpression expr) {
        if (expr instanceof GaussDBASelect) {
            visit((GaussDBASelect) expr);
        } else if (expr instanceof GaussDBAConstant) {
            visit((GaussDBAConstant) expr);
        } else if (expr instanceof GaussDBAColumnReference) {
            visit((GaussDBAColumnReference) expr);
        } else if (expr instanceof GaussDBAColumnValue) {
            visit((GaussDBAColumnValue) expr);
        } else if (expr instanceof GaussDBABinaryLogicalOperation) {
            visit((GaussDBABinaryLogicalOperation) expr);
        } else if (expr instanceof GaussDBABinaryComparisonOperation) {
            visit((GaussDBABinaryComparisonOperation) expr);
        } else if (expr instanceof GaussDBABinaryArithmeticOperation) {
            visit((GaussDBABinaryArithmeticOperation) expr);
        } else if (expr instanceof GaussDBABetweenOperation) {
            visit((GaussDBABetweenOperation) expr);
        } else if (expr instanceof GaussDBATableReference) {
            visit((GaussDBATableReference) expr);
        } else if (expr instanceof GaussDBAJoin) {
            visit((GaussDBAJoin) expr);
        } else if (expr instanceof GaussDBAUnaryPrefixOperation) {
            visit((GaussDBAUnaryPrefixOperation) expr);
        } else if (expr instanceof GaussDBAUnaryPostfixOperation) {
            visit((GaussDBAUnaryPostfixOperation) expr);
        } else if (expr instanceof GaussDBACaseWhen) {
            visit((GaussDBACaseWhen) expr);
        } else if (expr instanceof GaussDBACastOperation) {
            visit((GaussDBACastOperation) expr);
        } else if (expr instanceof GaussDBAAggregate) {
            visit((GaussDBAAggregate) expr);
        } else if (expr instanceof GaussDBAInOperation) {
            visit((GaussDBAInOperation) expr);
        } else if (expr instanceof GaussDBALikeOperation) {
            visit((GaussDBALikeOperation) expr);
        } else if (expr instanceof GaussDBAExists) {
            visit((GaussDBAExists) expr);
        } else if (expr instanceof GaussDBAPrintedExpression) {
            visit((GaussDBAPrintedExpression) expr);
        } else if (expr instanceof GaussDBAUnionSelect) {
            visit((GaussDBAUnionSelect) expr);
        } else if (expr instanceof GaussDBAMinusSelect) {
            visit((GaussDBAMinusSelect) expr);
        } else if (expr instanceof GaussDBAWithSelect) {
            visit((GaussDBAWithSelect) expr);
        } else if (expr instanceof GaussDBACteTableReference) {
            visit((GaussDBACteTableReference) expr);
        } else if (expr instanceof GaussDBADerivedTable) {
            visit((GaussDBADerivedTable) expr);
        } else if (expr instanceof GaussDBAAlias) {
            visit((GaussDBAAlias) expr);
        } else if (expr instanceof GaussDBAText) {
            visit((GaussDBAText) expr);
        } else {
            throw new AssertionError(expr);
        }
    }

    public void visit(GaussDBASelect s) {
        sb.append("SELECT ");
        if (s.getSelectType() == GaussDBASelect.GaussDBASelectType.DISTINCT) {
            sb.append("DISTINCT ");
        }
        if (s.getFetchColumns() == null || s.getFetchColumns().isEmpty()) {
            sb.append("*");
        } else {
            super.visit(s.getFetchColumns());
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

    public void visit(GaussDBAConstant constant) {
        sb.append(constant.getTextRepresentation());
    }

    public void visit(GaussDBAColumnReference column) {
        if (column.getColumn().getTable() != null) {
            sb.append(column.getColumn().getTable().getName());
            sb.append(".");
        }
        sb.append(column.getColumn().getName());
    }

    public void visit(GaussDBAColumnValue columnValue) {
        GaussDBASchema.GaussDBAColumn c = columnValue.getColumn();
        if (c.getTable() != null) {
            sb.append(c.getTable().getName());
            sb.append(".");
        }
        sb.append(c.getName());
    }

    public void visit(GaussDBABinaryLogicalOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(" ");
        sb.append(op.getOp().getTextRepresentation());
        sb.append(" ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBABinaryComparisonOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(" ");
        sb.append(op.getOp().getTextRepr());
        sb.append(" ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBABinaryArithmeticOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(" ");
        sb.append(op.getOp().toString());
        sb.append(" ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBABetweenOperation op) {
        sb.append("(");
        visit(op.getExpr());
        sb.append(op.isNegated() ? " NOT BETWEEN " : " BETWEEN ");
        visit(op.getLeft());
        sb.append(" AND ");
        visit(op.getRight());
        sb.append(")");
    }

    public void visit(GaussDBATableReference ref) {
        sb.append(ref.getTable().getName());
    }

    public void visit(GaussDBAJoin join) {
        switch (join.getJoinType()) {
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
        case FULL:
            sb.append("FULL JOIN ");
            break;
        case NATURAL:
            sb.append("NATURAL JOIN ");
            break;
        default:
            throw new AssertionError(join.getJoinType());
        }
        visit(join.getTableReference());
        if (join.getJoinType() != GaussDBAJoin.GaussDBAJoinType.CROSS
                && join.getJoinType() != GaussDBAJoin.GaussDBAJoinType.NATURAL) {
            sb.append(" ON ");
            visit(join.getOnCondition());
        }
    }

    public void visit(GaussDBAUnaryPrefixOperation op) {
        sb.append("(");
        sb.append(op.getOp().getText());
        sb.append(" ");
        visit(op.getExpr());
        sb.append(")");
    }

    public void visit(GaussDBAUnaryPostfixOperation op) {
        sb.append("(");
        visit(op.getExpr());
        sb.append(" ");
        sb.append(op.getOp().getText());
        sb.append(")");
    }

    public void visit(GaussDBACaseWhen caseWhen) {
        sb.append("CASE");
        for (int i = 0; i < caseWhen.getConditions().size(); i++) {
            sb.append(" WHEN ");
            visit(caseWhen.getConditions().get(i));
            sb.append(" THEN ");
            visit(caseWhen.getThenExpressions().get(i));
        }
        if (caseWhen.getElseExpression() != null) {
            sb.append(" ELSE ");
            visit(caseWhen.getElseExpression());
        }
        sb.append(" END");
    }

    public void visit(GaussDBACastOperation cast) {
        sb.append("CAST(");
        visit(cast.getExpr());
        sb.append(" AS ");
        sb.append(getTypeString(cast.getType()));
        sb.append(")");
    }

    private String getTypeString(GaussDBADataType type) {
        switch (type) {
        case NUMBER:
            return "NUMBER";
        case VARCHAR2:
            return "VARCHAR2";
        case DATE:
            return "DATE";
        case TIMESTAMP:
            return "TIMESTAMP";
        case CLOB:
            return "CLOB";
        case BLOB:
            return "BLOB";
        default:
            throw new AssertionError(type);
        }
    }

    public void visit(GaussDBAAggregate aggregate) {
        sb.append(aggregate.getFunc().toString());
        sb.append("(");
        if (!aggregate.getArgs().isEmpty()) {
            super.visit(aggregate.getArgs());
        } else {
            sb.append("*");
        }
        sb.append(")");
    }

    public void visit(GaussDBAInOperation inOp) {
        sb.append("(");
        visit(inOp.getExpr());
        sb.append(inOp.isNegated() ? " NOT IN (" : " IN (");
        super.visit(inOp.getListElements());
        sb.append("))");
    }

    public void visit(GaussDBALikeOperation likeOp) {
        sb.append("(");
        visit(likeOp.getLeft());
        sb.append(likeOp.isNegated() ? " NOT LIKE " : " LIKE ");
        visit(likeOp.getRight());
        sb.append(")");
    }

    public void visit(GaussDBAExists exists) {
        sb.append("EXISTS (");
        visit(exists.getSubquery());
        sb.append(")");
    }

    public void visit(GaussDBAPrintedExpression printed) {
        sb.append(printed.getPrintedSql());
    }

    public void visit(GaussDBAUnionSelect union) {
        sb.append("(");
        for (int i = 0; i < union.getSelects().size(); i++) {
            visit(union.getSelects().get(i));
            if (i < union.getSelects().size() - 1) {
                sb.append(union.isUnionAll() ? " UNION ALL " : " UNION ");
            }
        }
        sb.append(")");
    }

    public void visit(GaussDBAMinusSelect minus) {
        sb.append("(");
        for (int i = 0; i < minus.getSelects().size(); i++) {
            visit(minus.getSelects().get(i));
            if (i < minus.getSelects().size() - 1) {
                sb.append(minus.isMinusAll() ? " MINUS ALL " : " MINUS ");
            }
        }
        sb.append(")");
    }

    public void visit(GaussDBAWithSelect withSelect) {
        sb.append("WITH ");
        for (int i = 0; i < withSelect.getCtes().size(); i++) {
            GaussDBACteDefinition cte = withSelect.getCtes().get(i);
            sb.append(cte.getName());
            sb.append(" AS (");
            visit(cte.getSelect());
            sb.append(")");
            if (i < withSelect.getCtes().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(" ");
        visit(withSelect.getMainSelect());
    }

    public void visit(GaussDBACteTableReference cteRef) {
        sb.append(cteRef.getName());
    }

    public void visit(GaussDBADerivedTable derived) {
        sb.append("(");
        visit(derived.getSelect());
        sb.append(") AS ");
        sb.append(derived.getAlias());
    }

    public void visit(GaussDBAAlias alias) {
        visit(alias.getExpression());
        sb.append(alias.getOperatorRepresentation());
    }

    public void visit(GaussDBAText text) {
        sb.append(text.getText());
    }

    public static String asString(GaussDBAExpression expr) {
        GaussDBAToStringVisitor v = new GaussDBAToStringVisitor();
        v.visit(expr);
        return v.get();
    }
}