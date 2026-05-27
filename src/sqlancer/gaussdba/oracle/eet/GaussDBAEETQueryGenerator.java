package sqlancer.gaussdba.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import sqlancer.Randomly;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.ast.GaussDBAAlias;
import sqlancer.gaussdba.ast.GaussDBACteDefinition;
import sqlancer.gaussdba.ast.GaussDBACteTableReference;
import sqlancer.gaussdba.ast.GaussDBADerivedTable;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAMinusSelect;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBAText;
import sqlancer.gaussdba.ast.GaussDBAUnionSelect;
import sqlancer.gaussdba.ast.GaussDBAWithSelect;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * Builds random SELECT shapes for EET: plain SELECT, UNION, MINUS, WITH (CTE), and derived table.
 * GaussDB-A (Oracle compatibility) uses MINUS instead of EXCEPT.
 */
public final class GaussDBAEETQueryGenerator {

    private static final String CTE_NAME = "eet_cte";
    private static final String DERIVED_ALIAS = "eet_sub";

    private GaussDBAEETQueryGenerator() {
    }

    private static GaussDBAExpression outerScopeWhereClause(String aliasPrefix, int refColumnCount) {
        if (refColumnCount <= 0) {
            return new GaussDBAText("1=1"); // TRUE in Oracle syntax
        }
        int refIdx = (int) Randomly.getNotCachedInteger(0, refColumnCount);
        String col = aliasPrefix + ".ref" + refIdx;
        int kind = (int) Randomly.getNotCachedInteger(0, 3);
        switch (kind) {
        case 0:
            return new GaussDBAText("(" + col + " IS NOT NULL)");
        case 1:
            return new GaussDBAText("(" + col + " = " + col + ")");
        default:
            return new GaussDBAText("((" + col + " IS NOT NULL) OR (" + col + " IS NULL))");
        }
    }

    public static GaussDBAExpression generateEETQueryRandomShape(GaussDBAGlobalState state,
            GaussDBAExpressionGenerator gen, AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(tables);
        int mode = (int) Randomly.getNotCachedInteger(0, 6);
        switch (mode) {
        case 1:
            return buildUnionSelect(gen);
        case 2:
            return buildWithSelect(gen);
        case 3:
            return buildDerivedSelect(gen);
        case 4:
            return buildMinusSelect(gen);
        case 5:
            // INTERSECT is not supported in Oracle compatibility mode
            return buildBaseSelect(gen);
        case 0:
        default:
            return buildBaseSelect(gen);
        }
    }

    public static GaussDBASelect buildBaseSelect(GaussDBAExpressionGenerator gen) {
        GaussDBASelect select = gen.generateSelect();
        List<GaussDBAExpression> fetch = gen.generateFetchColumns(true);
        // alias fetch columns as ref0..refN for outer query references
        List<GaussDBAExpression> aliased = new ArrayList<>();
        for (int i = 0; i < fetch.size(); i++) {
            aliased.add(new GaussDBAAlias(fetch.get(i), "ref" + i));
        }
        select.setFetchColumns(aliased);
        select.setJoinClauses(gen.getRandomJoinClauses());
        select.setFromList(gen.getTableRefs());
        select.setWhereClause(gen.generateBooleanExpression());
        select.setGroupByExpressions(List.of());
        select.setOrderByClauses(List.of());
        return select;
    }

    private static GaussDBAUnionSelect buildUnionSelect(GaussDBAExpressionGenerator gen) {
        GaussDBASelect left = buildBaseSelect(gen);
        List<GaussDBAExpression> sharedCols = new ArrayList<>(left.getFetchColumns());
        GaussDBASelect right = gen.generateSelect();
        right.setFetchColumns(new ArrayList<>(sharedCols));
        right.setJoinClauses(gen.getRandomJoinClauses());
        right.setFromList(gen.getTableRefs());
        right.setWhereClause(gen.generateBooleanExpression());
        right.setGroupByExpressions(List.of());
        right.setOrderByClauses(List.of());
        return new GaussDBAUnionSelect(List.of(left, right), Randomly.getBoolean());
    }

    private static GaussDBAMinusSelect buildMinusSelect(GaussDBAExpressionGenerator gen) {
        GaussDBASelect left = buildBaseSelect(gen);
        List<GaussDBAExpression> sharedCols = new ArrayList<>(left.getFetchColumns());
        GaussDBASelect right = gen.generateSelect();
        right.setFetchColumns(new ArrayList<>(sharedCols));
        right.setJoinClauses(gen.getRandomJoinClauses());
        right.setFromList(gen.getTableRefs());
        right.setWhereClause(gen.generateBooleanExpression());
        right.setGroupByExpressions(List.of());
        right.setOrderByClauses(List.of());
        return new GaussDBAMinusSelect(List.of(left, right), false); // MINUS ALL not commonly supported
    }

    private static GaussDBAWithSelect buildWithSelect(GaussDBAExpressionGenerator gen) {
        GaussDBASelect cteBody = buildBaseSelect(gen);
        GaussDBACteDefinition cte = new GaussDBACteDefinition(CTE_NAME, cteBody);

        GaussDBASelect main = gen.generateSelect();
        List<GaussDBAExpression> outerFetch = new ArrayList<>();
        for (int i = 0; i < cteBody.getFetchColumns().size(); i++) {
            outerFetch.add(new GaussDBAText(CTE_NAME + ".ref" + i));
        }
        main.setFetchColumns(outerFetch);
        main.setFromList(List.of(new GaussDBACteTableReference(CTE_NAME)));
        main.setJoinClauses(List.of());
        main.setWhereClause(outerScopeWhereClause(CTE_NAME, cteBody.getFetchColumns().size()));
        main.setGroupByExpressions(List.of());
        main.setOrderByClauses(List.of());

        return new GaussDBAWithSelect(List.of(cte), main);
    }

    private static GaussDBASelect buildDerivedSelect(GaussDBAExpressionGenerator gen) {
        GaussDBASelect inner = buildBaseSelect(gen);
        GaussDBADerivedTable derived = new GaussDBADerivedTable(inner, DERIVED_ALIAS);

        List<GaussDBAExpression> outerFetch = new ArrayList<>();
        for (int i = 0; i < inner.getFetchColumns().size(); i++) {
            outerFetch.add(new GaussDBAText(DERIVED_ALIAS + ".ref" + i));
        }

        GaussDBASelect outer = gen.generateSelect();
        outer.setFetchColumns(outerFetch);
        outer.setFromList(List.of(derived));
        outer.setJoinClauses(List.of());
        outer.setWhereClause(outerScopeWhereClause(DERIVED_ALIAS, inner.getFetchColumns().size()));
        outer.setGroupByExpressions(List.of());
        outer.setOrderByClauses(List.of());
        return outer;
    }
}