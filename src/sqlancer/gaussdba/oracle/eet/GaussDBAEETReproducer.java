package sqlancer.gaussdba.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.Reproducer;
import sqlancer.common.oracle.eet.EETMultisetComparator;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

public class GaussDBAEETReproducer implements Reproducer<GaussDBAGlobalState> {

    private final EETQueryExecutor executor;
    private String originalQuery;
    private String transformedQuery;
    private final GaussDBAExpression originalAst;
    private final GaussDBAExpression transformedAst;
    private final GaussDBAExpressionGenerator expressionGenerator;
    private final AbstractTables<GaussDBATable, GaussDBAColumn> targetTables;
    private final long reductionSeed;

    public GaussDBAEETReproducer(String originalQuery, String transformedQuery, EETQueryExecutor executor) {
        this.originalQuery = originalQuery;
        this.transformedQuery = transformedQuery;
        this.executor = executor;
        this.originalAst = null;
        this.transformedAst = null;
        this.expressionGenerator = null;
        this.targetTables = null;
        this.reductionSeed = 0L;
    }

    public GaussDBAEETReproducer(GaussDBAExpression originalAst, GaussDBAExpression transformedAst,
            GaussDBAExpressionGenerator expressionGenerator,
            AbstractTables<GaussDBATable, GaussDBAColumn> targetTables,
            long reductionSeed, EETQueryExecutor executor) {
        this.originalAst = originalAst;
        this.transformedAst = transformedAst;
        this.expressionGenerator = expressionGenerator;
        this.targetTables = targetTables;
        this.reductionSeed = reductionSeed;
        this.executor = executor;
        this.originalQuery = GaussDBAToStringVisitor.asString(originalAst);
        this.transformedQuery = GaussDBAToStringVisitor.asString(transformedAst);
    }

    void updateFromReduction(GaussDBAExpression newOrig, GaussDBAExpression newXform) {
        this.originalQuery = GaussDBAToStringVisitor.asString(newOrig);
        this.transformedQuery = GaussDBAToStringVisitor.asString(newXform);
    }

    GaussDBAExpression getOriginalAst() {
        return originalAst;
    }

    GaussDBAExpression getTransformedAst() {
        return transformedAst;
    }

    GaussDBAExpressionGenerator getExpressionGenerator() {
        return expressionGenerator;
    }

    AbstractTables<GaussDBATable, GaussDBAColumn> getTargetTables() {
        return targetTables;
    }

    long getReductionSeed() {
        return reductionSeed;
    }

    EETQueryExecutor getExecutor() {
        return executor;
    }

    @Override
    public boolean bugStillTriggers(GaussDBAGlobalState globalState) {
        try {
            List<List<String>> a = executor.executeQuery(originalQuery);
            List<List<String>> b = executor.executeQuery(transformedQuery);
            return !EETMultisetComparator.compareResultMultisets(a, b);
        } catch (SQLException e) {
            return false;
        }
    }
}