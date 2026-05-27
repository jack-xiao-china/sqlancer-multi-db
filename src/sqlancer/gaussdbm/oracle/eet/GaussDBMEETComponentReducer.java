package sqlancer.gaussdbm.oracle.eet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * Two-phase reduction for EET reproducer (component NULL substitution + CASE stripping).
 */
public final class GaussDBMEETComponentReducer {

    private GaussDBMEETComponentReducer() {
    }

    public static void runPhase1And2(GaussDBMEETReproducer r, GaussDBMGlobalState g) {
        if (r.getOriginalAst() == null) {
            return;
        }
        GaussDBExpression curOrig = GaussDBMEETExpressionTree.copyAst(r.getOriginalAst());
        GaussDBExpression curXform = retransform(g, r, curOrig);
        if (curXform == null && r.getTransformedAst() != null) {
            curXform = GaussDBMEETExpressionTree.copyAst(r.getTransformedAst());
        }
        if (curXform == null) {
            return;
        }
        boolean improved = true;
        while (improved) {
            improved = false;
            List<GaussDBExpression> nodes = GaussDBMEETExpressionTree.collectDfs(curOrig);
            Collections.reverse(nodes);
            for (GaussDBExpression n : nodes) {
                if (GaussDBMEETExpressionTree.isEetReductionLeaf(n)) {
                    continue;
                }
                GaussDBExpression tryOrig = GaussDBMEETExpressionTree.replaceNode(curOrig, n,
                        GaussDBConstant.createNullConstant());
                if (tryOrig == curOrig) {
                    continue;
                }
                GaussDBExpression tryXform = retransform(g, r, tryOrig);
                if (tryXform == null) {
                    continue;
                }
                if (!stillMismatch(r, tryOrig, tryXform)) {
                    continue;
                }
                curOrig = tryOrig;
                curXform = tryXform;
                improved = true;
                break;
            }
        }
        if (curXform != null) {
            List<GaussDBExpression> xNodes = new ArrayList<>(GaussDBMEETExpressionTree.collectDfs(curXform));
            Collections.reverse(xNodes);
            for (GaussDBExpression n : xNodes) {
                if (!(n instanceof GaussDBCaseWhen)) {
                    continue;
                }
                GaussDBCaseWhen c = (GaussDBCaseWhen) n;
                List<GaussDBExpression> candidates = new ArrayList<>();
                if (c.getElseExpr() != null) {
                    candidates.add(c.getElseExpr());
                }
                if (c.getThenExpr() != null) {
                    candidates.add(c.getThenExpr());
                }
                for (GaussDBExpression cand : candidates) {
                    GaussDBExpression tryX = GaussDBMEETExpressionTree.replaceNode(curXform, n, cand);
                    if (tryX == curXform) {
                        continue;
                    }
                    if (stillMismatch(r, curOrig, tryX)) {
                        curXform = tryX;
                        break;
                    }
                }
            }
        }
        r.updateFromReduction(curOrig, curXform);
    }

    private static GaussDBExpression retransform(GaussDBMGlobalState g, GaussDBMEETReproducer r,
            GaussDBExpression orig) {
        Randomly saved = g.getRandomly();
        try {
            g.setRandomly(new Randomly(r.getReductionSeed()));
            GaussDBMExpressionGenerator gen = r.getExpressionGenerator().setTablesAndColumns(r.getTargetTables());
            GaussDBMEETTransformer transformer = new GaussDBMEETTransformer(gen, r.getTargetTables());
            GaussDBMEETQueryTransformer qtf = new GaussDBMEETQueryTransformer(transformer);
            return qtf.eqTransformRoot(orig);
        } catch (sqlancer.IgnoreMeException e) {
            return null;
        } finally {
            g.setRandomly(saved);
        }
    }

    private static boolean stillMismatch(GaussDBMEETReproducer r, GaussDBExpression origAst,
            GaussDBExpression xformAst) {
        String o = GaussDBToStringVisitor.asString(origAst);
        String x = GaussDBToStringVisitor.asString(xformAst);
        try {
            List<List<String>> a = r.getExecutor().executeQuery(o);
            List<List<String>> b = r.getExecutor().executeQuery(x);
            return !EETMultisetComparator.compareResultMultisets(a, b);
        } catch (SQLException e) {
            return false;
        }
    }
}
