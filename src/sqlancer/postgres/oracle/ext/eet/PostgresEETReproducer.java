package sqlancer.postgres.oracle.ext.eet;

import java.util.List;

import sqlancer.Reproducer;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

public final class PostgresEETReproducer implements Reproducer<PostgresGlobalState> {

    private final EETQueryExecutor executor;
    private String originalQuery;
    private String transformedQuery;

    private final PostgresExpression originalAst;
    private final PostgresExpression transformedAst;
    private final PostgresExpressionGenerator expressionGenerator;
    private final AbstractTables<PostgresTable, PostgresColumn> targetTables;
    private final long reductionSeed;
    private boolean componentReductionDone;

    public PostgresEETReproducer(String originalQuery, String transformedQuery, EETQueryExecutor executor) {
        this.originalQuery = originalQuery;
        this.transformedQuery = transformedQuery;
        this.executor = executor;
        this.originalAst = null;
        this.transformedAst = null;
        this.expressionGenerator = null;
        this.targetTables = null;
        this.reductionSeed = 0L;
    }

    public PostgresEETReproducer(PostgresExpression originalAst, PostgresExpression transformedAst,
            PostgresExpressionGenerator expressionGenerator, AbstractTables<PostgresTable, PostgresColumn> targetTables,
            long reductionSeed, EETQueryExecutor executor) {
        this.originalAst = originalAst;
        this.transformedAst = transformedAst;
        this.expressionGenerator = expressionGenerator;
        this.targetTables = targetTables;
        this.reductionSeed = reductionSeed;
        this.executor = executor;
        this.originalQuery = PostgresVisitor.asString(originalAst);
        this.transformedQuery = PostgresVisitor.asString(transformedAst);
    }

    void updateFromReduction(PostgresExpression newOrig, PostgresExpression newXform) {
        this.originalQuery = PostgresVisitor.asString(newOrig);
        this.transformedQuery = PostgresVisitor.asString(newXform);
    }

    PostgresExpression getOriginalAst() {
        return originalAst;
    }

    PostgresExpression getTransformedAst() {
        return transformedAst;
    }

    PostgresExpressionGenerator getExpressionGenerator() {
        return expressionGenerator;
    }

    AbstractTables<PostgresTable, PostgresColumn> getTargetTables() {
        return targetTables;
    }

    long getReductionSeed() {
        return reductionSeed;
    }

    EETQueryExecutor getExecutor() {
        return executor;
    }

    @Override
    public boolean bugStillTriggers(PostgresGlobalState globalState) {
        if (globalState.getOptions() != null && globalState.getOptions().useReducer() && originalAst != null
                && !componentReductionDone) {
            PostgresEETComponentReducer.runPhase1And2(this, globalState);
            componentReductionDone = true;
        }
        try {
            List<List<String>> a = executor.executeQuery(originalQuery);
            List<List<String>> b = executor.executeQuery(transformedQuery);
            return !EETMultisetComparator.compareResultMultisets(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}

