package sqlancer.gaussdbm.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBBetweenOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBCastOperation;
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
import sqlancer.gaussdbm.ast.GaussDBOracleExpressionBag;
import sqlancer.gaussdbm.ast.GaussDBPostfixText;
import sqlancer.gaussdbm.ast.GaussDBPrintedExpression;
import sqlancer.gaussdbm.ast.GaussDBScalarSubquery;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBText;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnionSelect;
import sqlancer.gaussdbm.ast.GaussDBWindowFunction;
import sqlancer.gaussdbm.ast.GaussDBWithSelect;

public final class GaussDBMEETExpressionTree {

    private GaussDBMEETExpressionTree() {
    }

    public static GaussDBExpression copyAst(GaussDBExpression e) {
        return mapChildren(GaussDBMEETExpressionTree::copyAst, e);
    }

    public static GaussDBExpression replaceNode(GaussDBExpression root, GaussDBExpression target,
            GaussDBExpression replacement) {
        if (root == target) {
            return replacement;
        }
        return mapChildren(c -> replaceNode(c, target, replacement), root);
    }

    public static List<GaussDBExpression> collectDfs(GaussDBExpression root) {
        List<GaussDBExpression> out = new ArrayList<>();
        collectDfs(root, out);
        return out;
    }

    private static void collectDfs(GaussDBExpression e, List<GaussDBExpression> out) {
        if (e == null) {
            return;
        }
        out.add(e);
        forEachChild(e, c -> collectDfs(c, out));
    }

    public static GaussDBExpression mapChildren(Function<GaussDBExpression, GaussDBExpression> mapChild,
            GaussDBExpression e) {
        if (e == null) {
            return null;
        }
        // Leaf nodes: no children, return self
        if (e instanceof GaussDBText || e instanceof GaussDBTableReference || e instanceof GaussDBCteTableReference) {
            return e;
        }
        if (e instanceof GaussDBColumnReference || e instanceof GaussDBConstant || e instanceof GaussDBManuelPredicate) {
            return e;
        }
        // Expression-level nodes with children
        if (e instanceof GaussDBBinaryLogicalOperation) {
            GaussDBBinaryLogicalOperation b = (GaussDBBinaryLogicalOperation) e;
            return new GaussDBBinaryLogicalOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof GaussDBBinaryComparisonOperation) {
            GaussDBBinaryComparisonOperation b = (GaussDBBinaryComparisonOperation) e;
            return new GaussDBBinaryComparisonOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof GaussDBBinaryArithmeticOperation) {
            GaussDBBinaryArithmeticOperation b = (GaussDBBinaryArithmeticOperation) e;
            return new GaussDBBinaryArithmeticOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof GaussDBUnaryPrefixOperation) {
            GaussDBUnaryPrefixOperation u = (GaussDBUnaryPrefixOperation) e;
            return new GaussDBUnaryPrefixOperation(mapChild.apply(u.getExpr()), u.getOp());
        }
        if (e instanceof GaussDBUnaryPostfixOperation) {
            GaussDBUnaryPostfixOperation u = (GaussDBUnaryPostfixOperation) e;
            return new GaussDBUnaryPostfixOperation(mapChild.apply(u.getExpr()), u.getOp());
        }
        if (e instanceof GaussDBCaseWhen) {
            GaussDBCaseWhen c = (GaussDBCaseWhen) e;
            return new GaussDBCaseWhen(mapChild.apply(c.getWhenExpr()), mapChild.apply(c.getThenExpr()),
                    mapChild.apply(c.getElseExpr()));
        }
        if (e instanceof GaussDBIfFunction) {
            GaussDBIfFunction f = (GaussDBIfFunction) e;
            return new GaussDBIfFunction(mapChild.apply(f.getCondition()), mapChild.apply(f.getThenExpr()),
                    mapChild.apply(f.getElseExpr()));
        }
        if (e instanceof GaussDBBetweenOperation) {
            GaussDBBetweenOperation b = (GaussDBBetweenOperation) e;
            return new GaussDBBetweenOperation(mapChild.apply(b.getExpr()), mapChild.apply(b.getLeft()),
                    mapChild.apply(b.getRight()), b.isNegated());
        }
        if (e instanceof GaussDBCastOperation) {
            GaussDBCastOperation c = (GaussDBCastOperation) e;
            return new GaussDBCastOperation(mapChild.apply(c.getExpr()), c.getType());
        }
        if (e instanceof GaussDBPrintedExpression) {
            GaussDBPrintedExpression p = (GaussDBPrintedExpression) e;
            return new GaussDBPrintedExpression(mapChild.apply(p.getOriginal()));
        }
        if (e instanceof GaussDBPostfixText) {
            GaussDBPostfixText p = (GaussDBPostfixText) e;
            return new GaussDBPostfixText(mapChild.apply(p.getExpr()), p.getText());
        }
        if (e instanceof GaussDBComputableFunction) {
            GaussDBComputableFunction f = (GaussDBComputableFunction) e;
            List<GaussDBExpression> mappedArgs = new ArrayList<>();
            for (GaussDBExpression arg : f.getArguments()) {
                mappedArgs.add(mapChild.apply(arg));
            }
            return new GaussDBComputableFunction(f.getFunction(), mappedArgs);
        }
        if (e instanceof GaussDBJsonFunction) {
            GaussDBJsonFunction f = (GaussDBJsonFunction) e;
            List<GaussDBExpression> mappedArgs = new ArrayList<>();
            for (GaussDBExpression arg : f.getArguments()) {
                mappedArgs.add(mapChild.apply(arg));
            }
            return new GaussDBJsonFunction(f.getFunction(), mappedArgs);
        }
        if (e instanceof GaussDBTemporalFunction) {
            GaussDBTemporalFunction t = (GaussDBTemporalFunction) e;
            List<GaussDBExpression> mappedArgs = new ArrayList<>();
            for (GaussDBExpression arg : t.getArgs()) {
                mappedArgs.add(mapChild.apply(arg));
            }
            return new GaussDBTemporalFunction(t.getFunc(), mappedArgs);
        }
        if (e instanceof GaussDBAggregate) {
            GaussDBAggregate a = (GaussDBAggregate) e;
            List<GaussDBExpression> list = new ArrayList<>();
            for (GaussDBExpression x : a.getExprs()) {
                list.add(mapChild.apply(x));
            }
            return new GaussDBAggregate(list, a.getFunc());
        }
        if (e instanceof GaussDBWindowFunction) {
            GaussDBWindowFunction w = (GaussDBWindowFunction) e;
            GaussDBExpression mappedExpr = w.getExpr() == null ? null : mapChild.apply(w.getExpr());
            List<GaussDBExpression> mappedPartition = new ArrayList<>();
            for (GaussDBExpression p : w.getPartitionBy()) {
                mappedPartition.add(mapChild.apply(p));
            }
            List<GaussDBExpression> mappedOrderBy = new ArrayList<>();
            for (GaussDBExpression o : w.getOrderBy()) {
                mappedOrderBy.add(mapChild.apply(o));
            }
            return new GaussDBWindowFunction(w.getFunction(), mappedExpr, mappedPartition, mappedOrderBy);
        }
        if (e instanceof GaussDBExists) {
            GaussDBExists ex = (GaussDBExists) e;
            return new GaussDBExists(mapChild.apply(ex.getExpr()));
        }
        if (e instanceof GaussDBInOperation) {
            GaussDBInOperation in = (GaussDBInOperation) e;
            List<GaussDBExpression> newList = new ArrayList<>();
            for (GaussDBExpression x : in.getListElements()) {
                newList.add(mapChild.apply(x));
            }
            return new GaussDBInOperation(mapChild.apply(in.getExpr()), newList, in.isTrue());
        }
        if (e instanceof GaussDBScalarSubquery) {
            GaussDBScalarSubquery ss = (GaussDBScalarSubquery) e;
            return new GaussDBScalarSubquery((GaussDBSelect) mapChild.apply(ss.getSubquery()));
        }
        if (e instanceof GaussDBOracleAlias) {
            GaussDBOracleAlias a = (GaussDBOracleAlias) e;
            GaussDBExpression mappedOriginal = a.getOriginalExpression() == null ? null
                    : mapChild.apply(a.getOriginalExpression());
            GaussDBExpression mappedAlias = a.getAliasExpression() == null ? null
                    : mapChild.apply(a.getAliasExpression());
            return new GaussDBOracleAlias(mappedOriginal, mappedAlias);
        }
        if (e instanceof GaussDBOracleExpressionBag) {
            GaussDBOracleExpressionBag b = (GaussDBOracleExpressionBag) e;
            return new GaussDBOracleExpressionBag(
                    b.getInnerExpr() == null ? null : mapChild.apply(b.getInnerExpr()));
        }
        if (e instanceof GaussDBJoin) {
            GaussDBJoin j = (GaussDBJoin) e;
            GaussDBExpression mappedOn = j.getOnCondition() == null ? null : mapChild.apply(j.getOnCondition());
            return new GaussDBJoin(j.getTableReference(), mappedOn, j.getJoinType());
        }
        if (e instanceof GaussDBDerivedTable) {
            GaussDBDerivedTable d = (GaussDBDerivedTable) e;
            return new GaussDBDerivedTable((GaussDBSelect) mapChild.apply(d.getSubquery()), d.getAlias());
        }
        // Query-level nodes: recurse into branches/fields, node itself not wrapped
        if (e instanceof GaussDBUnionSelect) {
            GaussDBUnionSelect u = (GaussDBUnionSelect) e;
            List<GaussDBSelect> newBranches = new ArrayList<>();
            for (GaussDBSelect b : u.getBranches()) {
                newBranches.add((GaussDBSelect) mapChild.apply(b));
            }
            return new GaussDBUnionSelect(newBranches, u.isUnionAll());
        }
        if (e instanceof GaussDBSelect) {
            GaussDBSelect s = (GaussDBSelect) e;
            List<GaussDBExpression> newFetch = new ArrayList<>();
            for (GaussDBExpression col : s.getFetchColumns()) {
                newFetch.add(mapChild.apply(col));
            }
            GaussDBExpression newWhere = s.getWhereClause() == null ? null : mapChild.apply(s.getWhereClause());
            GaussDBExpression newHaving = s.getHavingClause() == null ? null : mapChild.apply(s.getHavingClause());
            List<GaussDBExpression> newGroupBy = new ArrayList<>();
            if (s.getGroupByExpressions() != null) {
                for (GaussDBExpression gb : s.getGroupByExpressions()) {
                    newGroupBy.add(mapChild.apply(gb));
                }
            }
            GaussDBSelect copy = new GaussDBSelect();
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
        if (e instanceof GaussDBWithSelect) {
            GaussDBWithSelect w = (GaussDBWithSelect) e;
            List<GaussDBCteDefinition> newCtes = new ArrayList<>();
            for (GaussDBCteDefinition c : w.getCtes()) {
                newCtes.add(new GaussDBCteDefinition(c.getName(), (GaussDBSelect) mapChild.apply(c.getSubquery())));
            }
            return new GaussDBWithSelect(newCtes, (GaussDBSelect) mapChild.apply(w.getMainQuery()));
        }
        return e;
    }

    private static void forEachChild(GaussDBExpression e, java.util.function.Consumer<GaussDBExpression> sink) {
        if (e == null) {
            return;
        }
        if (e instanceof GaussDBBinaryLogicalOperation) {
            GaussDBBinaryLogicalOperation b = (GaussDBBinaryLogicalOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBBinaryComparisonOperation) {
            GaussDBBinaryComparisonOperation b = (GaussDBBinaryComparisonOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBBinaryArithmeticOperation) {
            GaussDBBinaryArithmeticOperation b = (GaussDBBinaryArithmeticOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBUnaryPrefixOperation) {
            sink.accept(((GaussDBUnaryPrefixOperation) e).getExpr());
        } else if (e instanceof GaussDBUnaryPostfixOperation) {
            sink.accept(((GaussDBUnaryPostfixOperation) e).getExpr());
        } else if (e instanceof GaussDBCaseWhen) {
            GaussDBCaseWhen c = (GaussDBCaseWhen) e;
            sink.accept(c.getWhenExpr());
            sink.accept(c.getThenExpr());
            sink.accept(c.getElseExpr());
        } else if (e instanceof GaussDBIfFunction) {
            GaussDBIfFunction f = (GaussDBIfFunction) e;
            sink.accept(f.getCondition());
            sink.accept(f.getThenExpr());
            sink.accept(f.getElseExpr());
        } else if (e instanceof GaussDBBetweenOperation) {
            GaussDBBetweenOperation b = (GaussDBBetweenOperation) e;
            sink.accept(b.getExpr());
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBCastOperation) {
            sink.accept(((GaussDBCastOperation) e).getExpr());
        } else if (e instanceof GaussDBPrintedExpression) {
            sink.accept(((GaussDBPrintedExpression) e).getOriginal());
        } else if (e instanceof GaussDBPostfixText) {
            sink.accept(((GaussDBPostfixText) e).getExpr());
        } else if (e instanceof GaussDBComputableFunction) {
            for (GaussDBExpression arg : ((GaussDBComputableFunction) e).getArguments()) {
                sink.accept(arg);
            }
        } else if (e instanceof GaussDBJsonFunction) {
            for (GaussDBExpression arg : ((GaussDBJsonFunction) e).getArguments()) {
                sink.accept(arg);
            }
        } else if (e instanceof GaussDBTemporalFunction) {
            for (GaussDBExpression arg : ((GaussDBTemporalFunction) e).getArgs()) {
                sink.accept(arg);
            }
        } else if (e instanceof GaussDBAggregate) {
            for (GaussDBExpression x : ((GaussDBAggregate) e).getExprs()) {
                sink.accept(x);
            }
        } else if (e instanceof GaussDBWindowFunction) {
            GaussDBWindowFunction w = (GaussDBWindowFunction) e;
            if (w.getExpr() != null) {
                sink.accept(w.getExpr());
            }
            for (GaussDBExpression p : w.getPartitionBy()) {
                sink.accept(p);
            }
            for (GaussDBExpression o : w.getOrderBy()) {
                sink.accept(o);
            }
        } else if (e instanceof GaussDBExists) {
            sink.accept(((GaussDBExists) e).getExpr());
        } else if (e instanceof GaussDBInOperation) {
            GaussDBInOperation in = (GaussDBInOperation) e;
            sink.accept(in.getExpr());
            for (GaussDBExpression x : in.getListElements()) {
                sink.accept(x);
            }
        } else if (e instanceof GaussDBScalarSubquery) {
            sink.accept(((GaussDBScalarSubquery) e).getSubquery());
        } else if (e instanceof GaussDBOracleAlias) {
            GaussDBOracleAlias a = (GaussDBOracleAlias) e;
            if (a.getOriginalExpression() != null) {
                sink.accept(a.getOriginalExpression());
            }
            if (a.getAliasExpression() != null) {
                sink.accept(a.getAliasExpression());
            }
        } else if (e instanceof GaussDBOracleExpressionBag) {
            GaussDBOracleExpressionBag b = (GaussDBOracleExpressionBag) e;
            if (b.getInnerExpr() != null) {
                sink.accept(b.getInnerExpr());
            }
        } else if (e instanceof GaussDBJoin) {
            GaussDBJoin j = (GaussDBJoin) e;
            if (j.getOnCondition() != null) {
                sink.accept(j.getOnCondition());
            }
        } else if (e instanceof GaussDBDerivedTable) {
            sink.accept(((GaussDBDerivedTable) e).getSubquery());
        } else if (e instanceof GaussDBUnionSelect) {
            for (GaussDBSelect b : ((GaussDBUnionSelect) e).getBranches()) {
                sink.accept(b);
            }
        } else if (e instanceof GaussDBSelect) {
            GaussDBSelect s = (GaussDBSelect) e;
            for (GaussDBExpression col : s.getFetchColumns()) {
                sink.accept(col);
            }
            if (s.getWhereClause() != null) {
                sink.accept(s.getWhereClause());
            }
            if (s.getHavingClause() != null) {
                sink.accept(s.getHavingClause());
            }
            if (s.getGroupByExpressions() != null) {
                for (GaussDBExpression gb : s.getGroupByExpressions()) {
                    sink.accept(gb);
                }
            }
        } else if (e instanceof GaussDBWithSelect) {
            GaussDBWithSelect w = (GaussDBWithSelect) e;
            for (GaussDBCteDefinition c : w.getCtes()) {
                sink.accept(c.getSubquery());
            }
            sink.accept(w.getMainQuery());
        }
    }

    public static boolean isEetReductionLeaf(GaussDBExpression e) {
        return e instanceof GaussDBText || e instanceof GaussDBTableReference || e instanceof GaussDBCteTableReference
                || e instanceof GaussDBColumnReference || e instanceof GaussDBConstant
                || e instanceof GaussDBManuelPredicate;
    }
}