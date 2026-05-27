package sqlancer.gaussdba.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
import sqlancer.gaussdba.ast.GaussDBACteDefinition;
import sqlancer.gaussdba.ast.GaussDBACteTableReference;
import sqlancer.gaussdba.ast.GaussDBADerivedTable;
import sqlancer.gaussdba.ast.GaussDBAExists;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAInOperation;
import sqlancer.gaussdba.ast.GaussDBALikeOperation;
import sqlancer.gaussdba.ast.GaussDBAManuelPredicate;
import sqlancer.gaussdba.ast.GaussDBAMinusSelect;
import sqlancer.gaussdba.ast.GaussDBAPostfixText;
import sqlancer.gaussdba.ast.GaussDBAPrintedExpression;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBAScalarSubquery;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAText;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnionSelect;
import sqlancer.gaussdba.ast.GaussDBAWithSelect;

/**
 * Structural map/copy/replace on GaussDB-A AST (EET recursive traversal support).
 * Covers all expression node types to match MySQL/PG ExpressionTree coverage.
 */
public final class GaussDBAEETExpressionTree {

    private GaussDBAEETExpressionTree() {
    }

    public static GaussDBAExpression copyAst(GaussDBAExpression e) {
        return mapChildren(GaussDBAEETExpressionTree::copyAst, e);
    }

    public static GaussDBAExpression replaceNode(GaussDBAExpression root, GaussDBAExpression target,
            GaussDBAExpression replacement) {
        if (root == target) {
            return replacement;
        }
        return mapChildren(c -> replaceNode(c, target, replacement), root);
    }

    public static List<GaussDBAExpression> collectDfs(GaussDBAExpression root) {
        List<GaussDBAExpression> out = new ArrayList<>();
        collectDfs(root, out);
        return out;
    }

    private static void collectDfs(GaussDBAExpression e, List<GaussDBAExpression> out) {
        if (e == null) {
            return;
        }
        out.add(e);
        forEachChild(e, c -> collectDfs(c, out));
    }

    public static GaussDBAExpression mapChildren(Function<GaussDBAExpression, GaussDBAExpression> mapChild,
            GaussDBAExpression e) {
        if (e == null) {
            return null;
        }
        // Leaf nodes
        if (e instanceof GaussDBATableReference || e instanceof GaussDBAText
                || e instanceof GaussDBACteTableReference || e instanceof GaussDBAManuelPredicate) {
            return e;
        }
        if (e instanceof GaussDBAColumnReference || e instanceof GaussDBAConstant
                || e instanceof GaussDBAColumnValue) {
            return e;
        }
        // Expression nodes with children
        if (e instanceof GaussDBABinaryLogicalOperation) {
            GaussDBABinaryLogicalOperation b = (GaussDBABinaryLogicalOperation) e;
            return new GaussDBABinaryLogicalOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof GaussDBABinaryComparisonOperation) {
            GaussDBABinaryComparisonOperation b = (GaussDBABinaryComparisonOperation) e;
            return new GaussDBABinaryComparisonOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof GaussDBAUnaryPrefixOperation) {
            GaussDBAUnaryPrefixOperation u = (GaussDBAUnaryPrefixOperation) e;
            return new GaussDBAUnaryPrefixOperation(mapChild.apply(u.getExpr()), u.getOp());
        }
        if (e instanceof GaussDBAUnaryPostfixOperation) {
            GaussDBAUnaryPostfixOperation u = (GaussDBAUnaryPostfixOperation) e;
            return new GaussDBAUnaryPostfixOperation(mapChild.apply(u.getExpr()), u.getOp());
        }
        if (e instanceof GaussDBACaseWhen) {
            GaussDBACaseWhen c = (GaussDBACaseWhen) e;
            List<GaussDBAExpression> newConditions = new ArrayList<>();
            List<GaussDBAExpression> newThen = new ArrayList<>();
            for (int i = 0; i < c.getConditions().size(); i++) {
                newConditions.add(mapChild.apply(c.getConditions().get(i)));
                newThen.add(mapChild.apply(c.getThenExpressions().get(i)));
            }
            GaussDBAExpression newElse = mapChild.apply(c.getElseExpression());
            return new GaussDBACaseWhen(newConditions, newThen, newElse);
        }
        if (e instanceof GaussDBABetweenOperation) {
            GaussDBABetweenOperation b = (GaussDBABetweenOperation) e;
            return new GaussDBABetweenOperation(mapChild.apply(b.getExpr()), mapChild.apply(b.getLeft()),
                    mapChild.apply(b.getRight()), b.isNegated());
        }
        if (e instanceof GaussDBAInOperation) {
            GaussDBAInOperation i = (GaussDBAInOperation) e;
            List<GaussDBAExpression> newList = new ArrayList<>();
            for (GaussDBAExpression x : i.getListElements()) {
                newList.add(mapChild.apply(x));
            }
            return new GaussDBAInOperation(mapChild.apply(i.getExpr()), newList, i.isNegated());
        }
        if (e instanceof GaussDBAExists) {
            GaussDBAExists ex = (GaussDBAExists) e;
            return new GaussDBAExists(mapChild.apply(ex.getSubquery()));
        }
        if (e instanceof GaussDBAPrintedExpression) {
            GaussDBAPrintedExpression p = (GaussDBAPrintedExpression) e;
            return new GaussDBAPrintedExpression(mapChild.apply(p.getOriginal()));
        }
        if (e instanceof GaussDBAAggregate) {
            GaussDBAAggregate a = (GaussDBAAggregate) e;
            List<GaussDBAExpression> newArgs = new ArrayList<>();
            for (GaussDBAExpression x : a.getArgs()) {
                newArgs.add(mapChild.apply(x));
            }
            return new GaussDBAAggregate(newArgs, a.getFunc());
        }
        if (e instanceof GaussDBABinaryArithmeticOperation) {
            GaussDBABinaryArithmeticOperation b = (GaussDBABinaryArithmeticOperation) e;
            return new GaussDBABinaryArithmeticOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof GaussDBACastOperation) {
            GaussDBACastOperation c = (GaussDBACastOperation) e;
            return new GaussDBACastOperation(mapChild.apply(c.getExpr()), c.getType());
        }
        if (e instanceof GaussDBALikeOperation) {
            GaussDBALikeOperation l = (GaussDBALikeOperation) e;
            return new GaussDBALikeOperation(mapChild.apply(l.getLeft()), mapChild.apply(l.getRight()),
                    l.isNegated());
        }
        if (e instanceof GaussDBAScalarSubquery) {
            GaussDBAScalarSubquery ss = (GaussDBAScalarSubquery) e;
            return new GaussDBAScalarSubquery((GaussDBASelect) mapChild.apply(ss.getSubquery()));
        }
        if (e instanceof GaussDBAPostfixText) {
            GaussDBAPostfixText p = (GaussDBAPostfixText) e;
            return new GaussDBAPostfixText(mapChild.apply(p.getExpr()), p.getText());
        }
        if (e instanceof GaussDBADerivedTable) {
            GaussDBADerivedTable d = (GaussDBADerivedTable) e;
            return new GaussDBADerivedTable((GaussDBASelect) mapChild.apply(d.getSelect()), d.getAlias());
        }
        // Query-level nodes: recurse into branches/fields, node itself not wrapped
        if (e instanceof GaussDBAUnionSelect) {
            GaussDBAUnionSelect u = (GaussDBAUnionSelect) e;
            List<GaussDBASelect> newSelects = new ArrayList<>();
            for (GaussDBASelect s : u.getSelects()) {
                newSelects.add((GaussDBASelect) mapChild.apply(s));
            }
            return new GaussDBAUnionSelect(newSelects, u.isUnionAll());
        }
        if (e instanceof GaussDBAMinusSelect) {
            GaussDBAMinusSelect m = (GaussDBAMinusSelect) e;
            List<GaussDBASelect> newSelects = new ArrayList<>();
            for (GaussDBASelect s : m.getSelects()) {
                newSelects.add((GaussDBASelect) mapChild.apply(s));
            }
            return new GaussDBAMinusSelect(newSelects, m.isMinusAll());
        }
        if (e instanceof GaussDBASelect) {
            GaussDBASelect s = (GaussDBASelect) e;
            List<GaussDBAExpression> newFetch = new ArrayList<>();
            for (GaussDBAExpression col : s.getFetchColumns()) {
                newFetch.add(mapChild.apply(col));
            }
            GaussDBAExpression newWhere = s.getWhereClause() == null ? null : mapChild.apply(s.getWhereClause());
            GaussDBAExpression newHaving = s.getHavingClause() == null ? null : mapChild.apply(s.getHavingClause());
            List<GaussDBAExpression> newGroupBy = new ArrayList<>();
            if (s.getGroupByExpressions() != null) {
                for (GaussDBAExpression gb : s.getGroupByExpressions()) {
                    newGroupBy.add(mapChild.apply(gb));
                }
            }
            GaussDBASelect copy = new GaussDBASelect();
            copy.setSelectType(s.getSelectType());
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
        if (e instanceof GaussDBAWithSelect) {
            GaussDBAWithSelect w = (GaussDBAWithSelect) e;
            List<GaussDBACteDefinition> newCtes = new ArrayList<>();
            for (GaussDBACteDefinition c : w.getCtes()) {
                newCtes.add(new GaussDBACteDefinition(c.getName(), (GaussDBASelect) mapChild.apply(c.getSelect())));
            }
            return new GaussDBAWithSelect(newCtes, (GaussDBASelect) mapChild.apply(w.getMainSelect()));
        }
        return e;
    }

    private static void forEachChild(GaussDBAExpression e, java.util.function.Consumer<GaussDBAExpression> sink) {
        if (e == null) {
            return;
        }
        if (e instanceof GaussDBABinaryLogicalOperation) {
            GaussDBABinaryLogicalOperation b = (GaussDBABinaryLogicalOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBABinaryComparisonOperation) {
            GaussDBABinaryComparisonOperation b = (GaussDBABinaryComparisonOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBABinaryArithmeticOperation) {
            GaussDBABinaryArithmeticOperation b = (GaussDBABinaryArithmeticOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBAUnaryPrefixOperation) {
            sink.accept(((GaussDBAUnaryPrefixOperation) e).getExpr());
        } else if (e instanceof GaussDBAUnaryPostfixOperation) {
            sink.accept(((GaussDBAUnaryPostfixOperation) e).getExpr());
        } else if (e instanceof GaussDBACaseWhen) {
            GaussDBACaseWhen c = (GaussDBACaseWhen) e;
            for (GaussDBAExpression cond : c.getConditions()) {
                sink.accept(cond);
            }
            for (GaussDBAExpression then : c.getThenExpressions()) {
                sink.accept(then);
            }
            sink.accept(c.getElseExpression());
        } else if (e instanceof GaussDBABetweenOperation) {
            GaussDBABetweenOperation b = (GaussDBABetweenOperation) e;
            sink.accept(b.getExpr());
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBAInOperation) {
            GaussDBAInOperation i = (GaussDBAInOperation) e;
            sink.accept(i.getExpr());
            for (GaussDBAExpression x : i.getListElements()) {
                sink.accept(x);
            }
        } else if (e instanceof GaussDBAExists) {
            sink.accept(((GaussDBAExists) e).getSubquery());
        } else if (e instanceof GaussDBAPrintedExpression) {
            sink.accept(((GaussDBAPrintedExpression) e).getOriginal());
        } else if (e instanceof GaussDBAAggregate) {
            for (GaussDBAExpression x : ((GaussDBAAggregate) e).getArgs()) {
                sink.accept(x);
            }
        } else if (e instanceof GaussDBACastOperation) {
            sink.accept(((GaussDBACastOperation) e).getExpr());
        } else if (e instanceof GaussDBALikeOperation) {
            GaussDBALikeOperation l = (GaussDBALikeOperation) e;
            sink.accept(l.getLeft());
            sink.accept(l.getRight());
        } else if (e instanceof GaussDBAScalarSubquery) {
            sink.accept(((GaussDBAScalarSubquery) e).getSubquery());
        } else if (e instanceof GaussDBAPostfixText) {
            sink.accept(((GaussDBAPostfixText) e).getExpr());
        } else if (e instanceof GaussDBADerivedTable) {
            sink.accept(((GaussDBADerivedTable) e).getSelect());
        } else if (e instanceof GaussDBAUnionSelect) {
            for (GaussDBASelect s : ((GaussDBAUnionSelect) e).getSelects()) {
                sink.accept(s);
            }
        } else if (e instanceof GaussDBAMinusSelect) {
            for (GaussDBASelect s : ((GaussDBAMinusSelect) e).getSelects()) {
                sink.accept(s);
            }
        } else if (e instanceof GaussDBASelect) {
            GaussDBASelect s = (GaussDBASelect) e;
            for (GaussDBAExpression col : s.getFetchColumns()) {
                sink.accept(col);
            }
            if (s.getWhereClause() != null) {
                sink.accept(s.getWhereClause());
            }
            if (s.getHavingClause() != null) {
                sink.accept(s.getHavingClause());
            }
            if (s.getGroupByExpressions() != null) {
                for (GaussDBAExpression gb : s.getGroupByExpressions()) {
                    sink.accept(gb);
                }
            }
        } else if (e instanceof GaussDBAWithSelect) {
            GaussDBAWithSelect w = (GaussDBAWithSelect) e;
            for (GaussDBACteDefinition c : w.getCtes()) {
                sink.accept(c.getSelect());
            }
            sink.accept(w.getMainSelect());
        }
    }

    public static boolean isEetReductionLeaf(GaussDBAExpression e) {
        return e instanceof GaussDBATableReference || e instanceof GaussDBAText
                || e instanceof GaussDBACteTableReference || e instanceof GaussDBAManuelPredicate
                || e instanceof GaussDBAColumnReference || e instanceof GaussDBAConstant
                || e instanceof GaussDBAColumnValue;
    }
}