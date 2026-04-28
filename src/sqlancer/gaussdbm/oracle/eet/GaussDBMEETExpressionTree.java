package sqlancer.gaussdbm.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBBetweenOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBCteTableReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBPrintedExpression;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBText;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;

/**
 * Structural map/copy/replace on GaussDB AST (EET recursive traversal support).
 */
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
        if (e instanceof GaussDBText || e instanceof GaussDBTableReference || e instanceof GaussDBCteTableReference) {
            return e;
        }
        if (e instanceof GaussDBColumnReference || e instanceof GaussDBConstant) {
            return e;
        }
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
        if (e instanceof GaussDBBetweenOperation) {
            GaussDBBetweenOperation b = (GaussDBBetweenOperation) e;
            return new GaussDBBetweenOperation(mapChild.apply(b.getExpr()), mapChild.apply(b.getLeft()),
                    mapChild.apply(b.getRight()), b.isNegated());
        }
        if (e instanceof GaussDBPrintedExpression) {
            GaussDBPrintedExpression p = (GaussDBPrintedExpression) e;
            return new GaussDBPrintedExpression(mapChild.apply(p.getOriginal()));
        }
        if (e instanceof GaussDBAggregate) {
            GaussDBAggregate a = (GaussDBAggregate) e;
            List<GaussDBExpression> list = new ArrayList<>();
            for (GaussDBExpression x : a.getExprs()) {
                list.add(mapChild.apply(x));
            }
            return new GaussDBAggregate(list, a.getFunc());
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
        } else if (e instanceof GaussDBUnaryPrefixOperation) {
            sink.accept(((GaussDBUnaryPrefixOperation) e).getExpr());
        } else if (e instanceof GaussDBUnaryPostfixOperation) {
            sink.accept(((GaussDBUnaryPostfixOperation) e).getExpr());
        } else if (e instanceof GaussDBCaseWhen) {
            GaussDBCaseWhen c = (GaussDBCaseWhen) e;
            sink.accept(c.getWhenExpr());
            sink.accept(c.getThenExpr());
            sink.accept(c.getElseExpr());
        } else if (e instanceof GaussDBBetweenOperation) {
            GaussDBBetweenOperation b = (GaussDBBetweenOperation) e;
            sink.accept(b.getExpr());
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof GaussDBPrintedExpression) {
            sink.accept(((GaussDBPrintedExpression) e).getOriginal());
        } else if (e instanceof GaussDBAggregate) {
            for (GaussDBExpression x : ((GaussDBAggregate) e).getExprs()) {
                sink.accept(x);
            }
        }
    }

    public static boolean isEetReductionLeaf(GaussDBExpression e) {
        return e instanceof GaussDBText || e instanceof GaussDBTableReference || e instanceof GaussDBCteTableReference
                || e instanceof GaussDBColumnReference || e instanceof GaussDBConstant;
    }
}
