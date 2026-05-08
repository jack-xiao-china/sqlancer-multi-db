package sqlancer.gaussdba.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATables;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;

public final class GaussDBARandomQueryGenerator {

    private GaussDBARandomQueryGenerator() {
    }

    public static GaussDBASelect createRandomQuery(int nrColumns, GaussDBAGlobalState globalState) {
        List<GaussDBAExpression> columns = new ArrayList<>();
        GaussDBATables tables = globalState.getSchema().getRandomTableNonEmptyTables();
        GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState).setColumns(tables.getColumns());
        for (int i = 0; i < nrColumns; i++) {
            columns.add(gen.generateExpression(0));
        }
        GaussDBASelect select = new GaussDBASelect();
        select.setFetchColumns(columns);
        select.setFromList(tables.getTables().stream().map(GaussDBATableReference::new).collect(Collectors.toList()));
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateBooleanExpression());
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            List<GaussDBAExpression> groupByExprs = new ArrayList<>();
            for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
                groupByExprs.add(gen.generateExpression(0));
            }
            select.setGroupByExpressions(groupByExprs);
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateBooleanExpression());
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(gen.generateOrderBys());
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(GaussDBAConstant.createNumberConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            if (Randomly.getBoolean()) {
                select.setOffsetClause(
                        GaussDBAConstant.createNumberConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            }
        }
        return select;
    }
}