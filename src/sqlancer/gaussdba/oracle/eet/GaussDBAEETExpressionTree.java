package sqlancer.gaussdba.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import sqlancer.gaussdba.ast.GaussDBABetweenOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation;
import sqlancer.gaussdba.ast.GaussDBACaseWhen;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBAExists;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAInOperation;
import sqlancer.gaussdba.ast.GaussDBAPrintedExpression;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;

/**
 * Structural map/copy/replace on GaussDB A AST (EET recursive traversal support).
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
        if (e instanceof GaussDBATableReference) {
            return e;
        }
        if (e instanceof GaussDBAColumnReference || e instanceof GaussDBAConstant) {
            return e;
        }
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
        }
    }

    public static boolean isEetReductionLeaf(GaussDBAExpression e) {
        return e instanceof GaussDBATableReference || e instanceof GaussDBAColumnReference
                || e instanceof GaussDBAConstant;
    }
}