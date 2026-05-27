package sqlancer.postgres.oracle.ext.eet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresCaseWhen;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * Two-phase reduction for EET reproducer (component NULL substitution + CASE stripping).
 */
public final class PostgresEETComponentReducer {

    private PostgresEETComponentReducer() {
    }

    public static void runPhase1And2(PostgresEETReproducer r, PostgresGlobalState g) {
        if (r.getOriginalAst() == null) {
            return;
        }
        PostgresExpression curOrig = PostgresEETExpressionTree.copyAst(r.getOriginalAst());
        PostgresExpression curXform = retransform(g, r, curOrig);
        if (curXform == null && r.getTransformedAst() != null) {
            curXform = PostgresEETExpressionTree.copyAst(r.getTransformedAst());
        }
        if (curXform == null) {
            return;
        }
        boolean improved = true;
        while (improved) {
            improved = false;
            List<PostgresExpression> nodes = PostgresEETExpressionTree.collectDfs(curOrig);
            Collections.reverse(nodes);
            for (PostgresExpression n : nodes) {
                if (PostgresEETExpressionTree.isEetReductionLeaf(n)) {
                    continue;
                }
                PostgresExpression tryOrig = PostgresEETExpressionTree.replaceNode(curOrig, n,
                        PostgresConstant.createNullConstant());
                if (tryOrig == curOrig) {
                    continue;
                }
                PostgresExpression tryXform = retransform(g, r, tryOrig);
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
            List<PostgresExpression> xNodes = new ArrayList<>(PostgresEETExpressionTree.collectDfs(curXform));
            Collections.reverse(xNodes);
            for (PostgresExpression n : xNodes) {
                if (!(n instanceof PostgresCaseWhen)) {
                    continue;
                }
                PostgresCaseWhen c = (PostgresCaseWhen) n;
                List<PostgresExpression> candidates = new ArrayList<>();
                candidates.add(c.getElseExpr());
                candidates.add(c.getThenExpr());
                for (PostgresExpression cand : candidates) {
                    PostgresExpression tryX = PostgresEETExpressionTree.replaceNode(curXform, n, cand);
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

    private static PostgresExpression retransform(PostgresGlobalState g, PostgresEETReproducer r, PostgresExpression orig) {
        Randomly saved = g.getRandomly();
        try {
            g.setRandomly(new Randomly(r.getReductionSeed()));
            PostgresExpressionGenerator gen = r.getExpressionGenerator().setTablesAndColumns(r.getTargetTables());
            PostgresEETQueryTransformer qtf = new PostgresEETQueryTransformer(gen);
            return qtf.eqTransformRoot(orig);
        } catch (sqlancer.IgnoreMeException e) {
            return null;
        } finally {
            g.setRandomly(saved);
        }
    }

    private static boolean stillMismatch(PostgresEETReproducer r, PostgresExpression origAst, PostgresExpression xformAst) {
        String o = PostgresVisitor.asString(origAst);
        String x = PostgresVisitor.asString(xformAst);
        try {
            List<List<String>> a = r.getExecutor().executeQuery(o);
            List<List<String>> b = r.getExecutor().executeQuery(x);
            return !EETMultisetComparator.compareResultMultisets(a, b);
        } catch (SQLException e) {
            return false;
        }
    }
}

