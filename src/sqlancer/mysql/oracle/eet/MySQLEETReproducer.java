package sqlancer.mysql.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.Reproducer;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

public class MySQLEETReproducer implements Reproducer<MySQLGlobalState> {

    private final EETQueryExecutor executor;
    private String originalQuery;
    private String transformedQuery;
    private final MySQLExpression originalAst;
    private final MySQLExpression transformedAst;
    private final MySQLExpressionGenerator expressionGenerator;
    private final AbstractTables<MySQLTable, MySQLColumn> targetTables;
    private final long reductionSeed;
    private boolean componentReductionDone;

    /**
     * Minimal reproducer (string-only); component reduction is disabled.
     */
    public MySQLEETReproducer(String originalQuery, String transformedQuery, EETQueryExecutor executor) {
        this.originalQuery = originalQuery;
        this.transformedQuery = transformedQuery;
        this.executor = executor;
        this.originalAst = null;
        this.transformedAst = null;
        this.expressionGenerator = null;
        this.targetTables = null;
        this.reductionSeed = 0L;
    }

    /**
     * Full reproducer with AST + generator context for §六 component_id reduction when --use-reducer is enabled.
     */
    public MySQLEETReproducer(MySQLExpression originalAst, MySQLExpression transformedAst,
            MySQLExpressionGenerator expressionGenerator, AbstractTables<MySQLTable, MySQLColumn> targetTables,
            long reductionSeed, EETQueryExecutor executor) {
        this.originalAst = originalAst;
        this.transformedAst = transformedAst;
        this.expressionGenerator = expressionGenerator;
        this.targetTables = targetTables;
        this.reductionSeed = reductionSeed;
        this.executor = executor;
        this.originalQuery = MySQLVisitor.asString(originalAst);
        this.transformedQuery = MySQLVisitor.asString(transformedAst);
    }

    void updateFromReduction(MySQLExpression newOrig, MySQLExpression newXform) {
        this.originalQuery = MySQLVisitor.asString(newOrig);
        this.transformedQuery = MySQLVisitor.asString(newXform);
    }

    MySQLExpression getOriginalAst() {
        return originalAst;
    }

    MySQLExpression getTransformedAst() {
        return transformedAst;
    }

    MySQLExpressionGenerator getExpressionGenerator() {
        return expressionGenerator;
    }

    AbstractTables<MySQLTable, MySQLColumn> getTargetTables() {
        return targetTables;
    }

    long getReductionSeed() {
        return reductionSeed;
    }

    EETQueryExecutor getExecutor() {
        return executor;
    }

    @Override
    public boolean bugStillTriggers(MySQLGlobalState globalState) {
        if (globalState.getOptions() != null && globalState.getOptions().useReducer() && originalAst != null
                && !componentReductionDone) {
            MySQLEETComponentReducer.runPhase1And2(this, globalState);
            componentReductionDone = true;
        }
        try {
            List<List<String>> a = executor.executeQuery(originalQuery);
            List<List<String>> b = executor.executeQuery(transformedQuery);
            return !EETMultisetComparator.compareResultMultisets(a, b);
        } catch (SQLException e) {
            return false;
        }
    }
}
