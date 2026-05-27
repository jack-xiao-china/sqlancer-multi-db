package sqlancer.mysql.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
import sqlancer.mysql.ast.MySQLCteTableReference;
import sqlancer.mysql.ast.MySQLExists;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLInOperation;
import sqlancer.mysql.ast.MySQLScalarSubquery;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLOrderByTerm;
import sqlancer.mysql.ast.MySQLPostfixText;
import sqlancer.mysql.ast.MySQLPrintedExpression;
import sqlancer.mysql.ast.MySQLStringExpression;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLTemporalFunction;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;
import sqlancer.mysql.ast.MySQLUnionSelect;
import sqlancer.mysql.ast.MySQLWithSelect;
import sqlancer.mysql.ast.MySQLCteDefinition;
import sqlancer.mysql.ast.MySQLWindowFunction;

/**
 * Structural map/copy/replace on MySQL AST (EET §3.2 recursive traversal support).
 */
public final class MySQLEETExpressionTree {

    private MySQLEETExpressionTree() {
    }

    /**
     * Deep copy of AST (immutable nodes shared where safe).
     */
    public static MySQLExpression copyAst(MySQLExpression e) {
        return mapChildren(MySQLEETExpressionTree::copyAst, e);
    }

    /**
     * Replace one occurrence (by identity) with a new subtree.
     */
    public static MySQLExpression replaceNode(MySQLExpression root, MySQLExpression target, MySQLExpression replacement) {
        if (root == target) {
            return replacement;
        }
        return mapChildren(c -> replaceNode(c, target, replacement), root);
    }

    /**
     * DFS order list of nodes (for component reduction / component_id iteration).
     */
    public static List<MySQLExpression> collectDfs(MySQLExpression root) {
        List<MySQLExpression> out = new ArrayList<>();
        collectDfs(root, out);
        return out;
    }

    private static void collectDfs(MySQLExpression e, List<MySQLExpression> out) {
        if (e == null) {
            return;
        }
        out.add(e);
        forEachChild(e, c -> collectDfs(c, out));
    }

    /**
     * Map each child expression recursively.
     */
    public static MySQLExpression mapChildren(Function<MySQLExpression, MySQLExpression> mapChild, MySQLExpression e) {
        if (e == null) {
            return null;
        }
        if (e instanceof MySQLText || e instanceof MySQLTableReference || e instanceof MySQLCteTableReference) {
            return e;
        }
        if (e instanceof MySQLColumnReference || e instanceof MySQLConstant || e instanceof MySQLStringExpression) {
            return e;
        }
        if (e instanceof MySQLBinaryLogicalOperation) {
            MySQLBinaryLogicalOperation b = (MySQLBinaryLogicalOperation) e;
            return new MySQLBinaryLogicalOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()), b.getOp());
        }
        if (e instanceof MySQLBinaryComparisonOperation) {
            MySQLBinaryComparisonOperation b = (MySQLBinaryComparisonOperation) e;
            return new MySQLBinaryComparisonOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()), b.getOp());
        }
        if (e instanceof MySQLBinaryOperation) {
            MySQLBinaryOperation b = (MySQLBinaryOperation) e;
            return new MySQLBinaryOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()), b.getOp());
        }
        if (e instanceof MySQLUnaryPrefixOperation) {
            MySQLUnaryPrefixOperation u = (MySQLUnaryPrefixOperation) e;
            return new MySQLUnaryPrefixOperation(mapChild.apply(u.getExpression()), u.getOp());
        }
        if (e instanceof MySQLUnaryPostfixOperation) {
            MySQLUnaryPostfixOperation u = (MySQLUnaryPostfixOperation) e;
            return new MySQLUnaryPostfixOperation(mapChild.apply(u.getExpression()), u.getOperator(), u.isNegated());
        }
        if (e instanceof MySQLCaseOperator) {
            MySQLCaseOperator c = (MySQLCaseOperator) e;
            MySQLExpression sw = c.getSwitchCondition() == null ? null : mapChild.apply(c.getSwitchCondition());
            List<MySQLExpression> whens = new ArrayList<>();
            List<MySQLExpression> thens = new ArrayList<>();
            for (int i = 0; i < c.getConditions().size(); i++) {
                whens.add(mapChild.apply(c.getConditions().get(i)));
                thens.add(mapChild.apply(c.getExpressions().get(i)));
            }
            MySQLExpression elseE = c.getElseExpr() == null ? null : mapChild.apply(c.getElseExpr());
            return new MySQLCaseOperator(sw, whens, thens, elseE);
        }
        if (e instanceof MySQLInOperation) {
            MySQLInOperation i = (MySQLInOperation) e;
            List<MySQLExpression> list = new ArrayList<>();
            for (MySQLExpression x : i.getListElements()) {
                list.add(mapChild.apply(x));
            }
            return new MySQLInOperation(mapChild.apply(i.getExpr()), list, i.isTrue());
        }
        if (e instanceof MySQLExists) {
            MySQLExists ex = (MySQLExists) e;
            MySQLExpression inner = mapChild.apply(ex.getExpr());
            // Keep original PQS expected; do not re-run inner.getExpectedValue() (can NPE on partial subtrees during EET).
            return new MySQLExists(inner, ex.getExpectedValue());
        }
        if (e instanceof MySQLAnyAllSubquery) {
            MySQLAnyAllSubquery aas = (MySQLAnyAllSubquery) e;
            MySQLExpression mappedLhs = mapChild.apply(aas.getLhs());
            MySQLExpression mappedSubquery = mapChild.apply(aas.getSubquery());
            return new MySQLAnyAllSubquery(mappedLhs, mappedSubquery, aas.getComparisonOp(), aas.getQuantifier(),
                    aas.getExpectedValue());
        }
        if (e instanceof MySQLScalarSubquery) {
            MySQLScalarSubquery ss = (MySQLScalarSubquery) e;
            MySQLExpression mappedSubquery = mapChild.apply(ss.getSubquery());
            return new MySQLScalarSubquery((MySQLSelect) mappedSubquery);
        }
        if (e instanceof MySQLCastOperation) {
            MySQLCastOperation c = (MySQLCastOperation) e;
            return new MySQLCastOperation(mapChild.apply(c.getExpr()), c.getType());
        }
        if (e instanceof MySQLBetweenOperation) {
            MySQLBetweenOperation b = (MySQLBetweenOperation) e;
            return new MySQLBetweenOperation(mapChild.apply(b.getExpr()), mapChild.apply(b.getLeft()),
                    mapChild.apply(b.getRight()));
        }
        if (e instanceof MySQLPrintedExpression) {
            MySQLPrintedExpression p = (MySQLPrintedExpression) e;
            return new MySQLPrintedExpression(mapChild.apply(p.getOriginal()));
        }
        if (e instanceof MySQLCollate) {
            MySQLCollate c = (MySQLCollate) e;
            return new MySQLCollate(mapChild.apply(c.getExpression()), c.getCollate());
        }
        if (e instanceof MySQLComputableFunction) {
            MySQLComputableFunction f = (MySQLComputableFunction) e;
            MySQLExpression[] args = f.getArguments();
            MySQLExpression[] mapped = new MySQLExpression[args.length];
            for (int i = 0; i < args.length; i++) {
                mapped[i] = mapChild.apply(args[i]);
            }
            return new MySQLComputableFunction(f.getFunction(), mapped);
        }
        if (e instanceof MySQLAggregate) {
            MySQLAggregate a = (MySQLAggregate) e;
            List<MySQLExpression> list = new ArrayList<>();
            for (MySQLExpression x : a.getExprs()) {
                list.add(mapChild.apply(x));
            }
            return new MySQLAggregate(list, a.getFunc());
        }
        if (e instanceof MySQLBinaryArithmeticOperation) {
            MySQLBinaryArithmeticOperation b = (MySQLBinaryArithmeticOperation) e;
            return new MySQLBinaryArithmeticOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof MySQLTemporalFunction) {
            MySQLTemporalFunction t = (MySQLTemporalFunction) e;
            MySQLExpression mappedTemporal = mapChild.apply(t.getTemporalExpr());
            MySQLExpression mappedInterval = mapChild.apply(t.getIntervalExpr());
            return new MySQLTemporalFunction(t.getKind(), mappedTemporal, mappedInterval,
                    t.getIntervalUnit(), t.getReturnType());
        }
        if (e instanceof MySQLWindowFunction) {
            MySQLWindowFunction w = (MySQLWindowFunction) e;
            MySQLExpression mappedExpr = mapChild.apply(w.getExpr());
            MySQLExpression mappedPartition = mapChild.apply(w.getPartitionBy());
            return new MySQLWindowFunction(w.getFunction(), mappedExpr, mappedPartition);
        }
        if (e instanceof MySQLPostfixText) {
            MySQLPostfixText p = (MySQLPostfixText) e;
            return new MySQLPostfixText(mapChild.apply(p.getExpr()), p.getText());
        }
        if (e instanceof MySQLOrderByTerm) {
            MySQLOrderByTerm o = (MySQLOrderByTerm) e;
            return new MySQLOrderByTerm(mapChild.apply(o.getExpr()), o.getOrder());
        }
        // Query-level nodes: recurse into branches/fields, but node itself is not wrapped
        if (e instanceof MySQLUnionSelect) {
            MySQLUnionSelect u = (MySQLUnionSelect) e;
            List<MySQLSelect> newBranches = new ArrayList<>();
            for (MySQLSelect b : u.getBranches()) {
                newBranches.add((MySQLSelect) mapChild.apply(b));
            }
            return new MySQLUnionSelect(newBranches, u.isUnionAll());
        }
        if (e instanceof MySQLSelect) {
            MySQLSelect s = (MySQLSelect) e;
            List<MySQLExpression> newFetch = new ArrayList<>();
            for (MySQLExpression col : s.getFetchColumns()) {
                newFetch.add(mapChild.apply(col));
            }
            MySQLExpression newWhere = s.getWhereClause() == null ? null : mapChild.apply(s.getWhereClause());
            MySQLExpression newHaving = s.getHavingClause() == null ? null : mapChild.apply(s.getHavingClause());
            List<MySQLExpression> newGroupBy = new ArrayList<>();
            if (s.getGroupByExpressions() != null) {
                for (MySQLExpression gb : s.getGroupByExpressions()) {
                    newGroupBy.add(mapChild.apply(gb));
                }
            }
            MySQLSelect copy = new MySQLSelect();
            copy.setFromOptions(s.getFromOptions());
            copy.setModifiers(s.getModifiers());
            copy.setHint(s.getHint());
            copy.setFetchColumns(newFetch);
            copy.setFromList(new ArrayList<>(s.getFromList()));
            copy.setJoinList(new ArrayList<>(s.getJoinList()));
            copy.setWhereClause(newWhere);
            copy.setGroupByExpressions(newGroupBy);
            copy.setHavingClause(newHaving);
            copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
            copy.setLimitClause(s.getLimitClause());
            copy.setOffsetClause(s.getOffsetClause());
            return copy;
        }
        if (e instanceof MySQLWithSelect) {
            MySQLWithSelect w = (MySQLWithSelect) e;
            List<MySQLCteDefinition> newCtes = new ArrayList<>();
            for (MySQLCteDefinition c : w.getCtes()) {
                newCtes.add(new MySQLCteDefinition(c.getName(), (MySQLSelect) mapChild.apply(c.getSubquery())));
            }
            return new MySQLWithSelect(newCtes, (MySQLSelect) mapChild.apply(w.getMainQuery()));
        }
        return e;
    }

    private static void forEachChild(MySQLExpression e, java.util.function.Consumer<MySQLExpression> sink) {
        if (e == null) {
            return;
        }
        if (e instanceof MySQLBinaryLogicalOperation) {
            MySQLBinaryLogicalOperation b = (MySQLBinaryLogicalOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof MySQLBinaryComparisonOperation) {
            MySQLBinaryComparisonOperation b = (MySQLBinaryComparisonOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof MySQLBinaryOperation) {
            MySQLBinaryOperation b = (MySQLBinaryOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof MySQLUnaryPrefixOperation) {
            sink.accept(((MySQLUnaryPrefixOperation) e).getExpression());
        } else if (e instanceof MySQLUnaryPostfixOperation) {
            sink.accept(((MySQLUnaryPostfixOperation) e).getExpression());
        } else if (e instanceof MySQLCaseOperator) {
            MySQLCaseOperator c = (MySQLCaseOperator) e;
            if (c.getSwitchCondition() != null) {
                sink.accept(c.getSwitchCondition());
            }
            for (int i = 0; i < c.getConditions().size(); i++) {
                sink.accept(c.getConditions().get(i));
                sink.accept(c.getExpressions().get(i));
            }
            if (c.getElseExpr() != null) {
                sink.accept(c.getElseExpr());
            }
        } else if (e instanceof MySQLInOperation) {
            MySQLInOperation i = (MySQLInOperation) e;
            sink.accept(i.getExpr());
            for (MySQLExpression x : i.getListElements()) {
                sink.accept(x);
            }
        } else if (e instanceof MySQLExists) {
            sink.accept(((MySQLExists) e).getExpr());
        } else if (e instanceof MySQLScalarSubquery) {
            sink.accept(((MySQLScalarSubquery) e).getSubquery());
        } else if (e instanceof MySQLCastOperation) {
            sink.accept(((MySQLCastOperation) e).getExpr());
        } else if (e instanceof MySQLBetweenOperation) {
            MySQLBetweenOperation b = (MySQLBetweenOperation) e;
            sink.accept(b.getExpr());
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof MySQLPrintedExpression) {
            sink.accept(((MySQLPrintedExpression) e).getOriginal());
        } else if (e instanceof MySQLCollate) {
            sink.accept(((MySQLCollate) e).getExpression());
        } else if (e instanceof MySQLComputableFunction) {
            for (MySQLExpression x : ((MySQLComputableFunction) e).getArguments()) {
                sink.accept(x);
            }
        } else if (e instanceof MySQLAggregate) {
            for (MySQLExpression x : ((MySQLAggregate) e).getExprs()) {
                sink.accept(x);
            }
        } else if (e instanceof MySQLBinaryArithmeticOperation) {
            MySQLBinaryArithmeticOperation b = (MySQLBinaryArithmeticOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof MySQLTemporalFunction) {
            MySQLTemporalFunction t = (MySQLTemporalFunction) e;
            sink.accept(t.getTemporalExpr());
            sink.accept(t.getIntervalExpr());
        } else if (e instanceof MySQLWindowFunction) {
            MySQLWindowFunction w = (MySQLWindowFunction) e;
            sink.accept(w.getExpr());
            sink.accept(w.getPartitionBy());
        } else if (e instanceof MySQLPostfixText) {
            sink.accept(((MySQLPostfixText) e).getExpr());
        } else if (e instanceof MySQLOrderByTerm) {
            sink.accept(((MySQLOrderByTerm) e).getExpr());
        } else if (e instanceof MySQLUnionSelect) {
            for (MySQLSelect b : ((MySQLUnionSelect) e).getBranches()) {
                sink.accept(b);
            }
        } else if (e instanceof MySQLSelect) {
            MySQLSelect s = (MySQLSelect) e;
            for (MySQLExpression col : s.getFetchColumns()) {
                sink.accept(col);
            }
            if (s.getWhereClause() != null) {
                sink.accept(s.getWhereClause());
            }
            if (s.getHavingClause() != null) {
                sink.accept(s.getHavingClause());
            }
            if (s.getGroupByExpressions() != null) {
                for (MySQLExpression gb : s.getGroupByExpressions()) {
                    sink.accept(gb);
                }
            }
        } else if (e instanceof MySQLWithSelect) {
            MySQLWithSelect w = (MySQLWithSelect) e;
            for (MySQLCteDefinition c : w.getCtes()) {
                sink.accept(c.getSubquery());
            }
            sink.accept(w.getMainQuery());
        }
    }

    public static boolean isEetReductionLeaf(MySQLExpression e) {
        return e instanceof MySQLText || e instanceof MySQLTableReference || e instanceof MySQLCteTableReference
                || e instanceof MySQLColumnReference || e instanceof MySQLConstant || e instanceof MySQLStringExpression;
    }
}
