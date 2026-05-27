package sqlancer.mysql.oracle.eet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLCaseOperator;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * §六：两阶段缩减 — 阶段 1 按 component（DFS 节点）在原查询 AST 上将子表达式换为 NULL，并对缩减后的原查询重新 eqTransform（使用与 Oracle 相同的 reductionSeed）；阶段 2 在变换后查询上尝试用 CASE 的 ELSE/THEN 分支替换整棵 CASE 以剥离冗余变换。
 */
public final class MySQLEETComponentReducer {

    private MySQLEETComponentReducer() {
    }

    public static void runPhase1And2(MySQLEETReproducer r, MySQLGlobalState g) {
        if (r.getOriginalAst() == null) {
            return;
        }
        MySQLExpression curOrig = MySQLEETExpressionTree.copyAst(r.getOriginalAst());
        MySQLExpression curXform = retransform(g, r, curOrig);
        if (curXform == null && r.getTransformedAst() != null) {
            curXform = MySQLEETExpressionTree.copyAst(r.getTransformedAst());
        }
        if (curXform == null) {
            return;
        }
        // Phase 1: greedy node -> NULL
        boolean improved = true;
        while (improved) {
            improved = false;
            List<MySQLExpression> nodes = MySQLEETExpressionTree.collectDfs(curOrig);
            Collections.reverse(nodes);
            for (MySQLExpression n : nodes) {
                if (MySQLEETExpressionTree.isEetReductionLeaf(n)) {
                    continue;
                }
                MySQLExpression tryOrig = MySQLEETExpressionTree.replaceNode(curOrig, n, MySQLConstant.createNullConstant());
                if (tryOrig == curOrig) {
                    continue;
                }
                MySQLExpression tryXform = retransform(g, r, tryOrig);
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
        // Phase 2: strip CASE on transformed query only
        if (curXform != null) {
            List<MySQLExpression> xNodes = new ArrayList<>(MySQLEETExpressionTree.collectDfs(curXform));
            Collections.reverse(xNodes);
            for (MySQLExpression n : xNodes) {
                if (!(n instanceof MySQLCaseOperator)) {
                    continue;
                }
                MySQLCaseOperator c = (MySQLCaseOperator) n;
                List<MySQLExpression> candidates = new ArrayList<>();
                if (c.getElseExpr() != null) {
                    candidates.add(c.getElseExpr());
                }
                if (!c.getExpressions().isEmpty() && c.getExpressions().get(0) != null) {
                    candidates.add(c.getExpressions().get(0));
                }
                for (MySQLExpression cand : candidates) {
                    MySQLExpression tryX = MySQLEETExpressionTree.replaceNode(curXform, n, cand);
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

    private static MySQLExpression retransform(MySQLGlobalState g, MySQLEETReproducer r, MySQLExpression orig) {
        Randomly saved = g.getRandomly();
        try {
            g.setRandomly(new Randomly(r.getReductionSeed()));
            MySQLExpressionGenerator gen = r.getExpressionGenerator().setTablesAndColumns(r.getTargetTables());
            MySQLEETQueryTransformer qtf = new MySQLEETQueryTransformer(gen);
            return qtf.eqTransformRoot(orig);
        } catch (sqlancer.IgnoreMeException e) {
            return null;
        } finally {
            g.setRandomly(saved);
        }
    }

    private static boolean stillMismatch(MySQLEETReproducer r, MySQLExpression origAst, MySQLExpression xformAst) {
        String o = MySQLVisitor.asString(origAst);
        String x = MySQLVisitor.asString(xformAst);
        try {
            List<List<String>> a = r.getExecutor().executeQuery(o);
            List<List<String>> b = r.getExecutor().executeQuery(x);
            return !EETMultisetComparator.compareResultMultisets(a, b);
        } catch (SQLException e) {
            return false;
        }
    }
}
