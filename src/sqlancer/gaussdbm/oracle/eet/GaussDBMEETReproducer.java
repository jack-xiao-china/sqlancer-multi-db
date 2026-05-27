package sqlancer.gaussdbm.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.Reproducer;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBMEETReproducer implements Reproducer<GaussDBMGlobalState> {

    private final EETQueryExecutor executor;
    private String originalQuery;
    private String transformedQuery;
    private final GaussDBExpression originalAst;
    private final GaussDBExpression transformedAst;
    private final GaussDBMExpressionGenerator expressionGenerator;
    private final AbstractTables<GaussDBTable, GaussDBColumn> targetTables;
    private final long reductionSeed;
    private boolean componentReductionDone;

    public GaussDBMEETReproducer(String originalQuery, String transformedQuery, EETQueryExecutor executor) {
        this.originalQuery = originalQuery;
        this.transformedQuery = transformedQuery;
        this.executor = executor;
        this.originalAst = null;
        this.transformedAst = null;
        this.expressionGenerator = null;
        this.targetTables = null;
        this.reductionSeed = 0L;
    }

    public GaussDBMEETReproducer(GaussDBExpression originalAst, GaussDBExpression transformedAst,
            GaussDBMExpressionGenerator expressionGenerator, AbstractTables<GaussDBTable, GaussDBColumn> targetTables,
            long reductionSeed, EETQueryExecutor executor) {
        this.originalAst = originalAst;
        this.transformedAst = transformedAst;
        this.expressionGenerator = expressionGenerator;
        this.targetTables = targetTables;
        this.reductionSeed = reductionSeed;
        this.executor = executor;
        this.originalQuery = GaussDBToStringVisitor.asString(originalAst);
        this.transformedQuery = GaussDBToStringVisitor.asString(transformedAst);
    }

    void updateFromReduction(GaussDBExpression newOrig, GaussDBExpression newXform) {
        this.originalQuery = GaussDBToStringVisitor.asString(newOrig);
        this.transformedQuery = GaussDBToStringVisitor.asString(newXform);
    }

    GaussDBExpression getOriginalAst() {
        return originalAst;
    }

    GaussDBExpression getTransformedAst() {
        return transformedAst;
    }

    GaussDBMExpressionGenerator getExpressionGenerator() {
        return expressionGenerator;
    }

    AbstractTables<GaussDBTable, GaussDBColumn> getTargetTables() {
        return targetTables;
    }

    long getReductionSeed() {
        return reductionSeed;
    }

    EETQueryExecutor getExecutor() {
        return executor;
    }

    @Override
    public boolean bugStillTriggers(GaussDBMGlobalState globalState) {
        if (globalState.getOptions() != null && globalState.getOptions().useReducer() && originalAst != null
                && !componentReductionDone) {
            GaussDBMEETComponentReducer.runPhase1And2(this, globalState);
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