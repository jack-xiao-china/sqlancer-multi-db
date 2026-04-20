package sqlancer.postgres.oracle.ext.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import sqlancer.Randomly;
import sqlancer.common.schema.AbstractTables;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.ast.PostgresAlias;
import sqlancer.postgres.ast.PostgresCteDefinition;
import sqlancer.postgres.ast.PostgresCteTableReference;
import sqlancer.postgres.ast.PostgresDerivedTable;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.LockingClauseContext;
import sqlancer.postgres.ast.PostgresText;
import sqlancer.postgres.ast.PostgresUnionSelect;
import sqlancer.postgres.ast.PostgresWithSelect;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * Builds random SELECT shapes for EET: plain SELECT, UNION, WITH (CTE), and derived table (subquery in FROM).
 */
public final class PostgresEETQueryGenerator {

    private static final String CTE_NAME = "eet_cte";
    private static final String DERIVED_ALIAS = "eet_sub";

    private PostgresEETQueryGenerator() {
    }

    private static PostgresExpression outerScopeWhereClause(String aliasPrefix, int refColumnCount) {
        if (refColumnCount <= 0) {
            return new PostgresText("TRUE");
        }
        int refIdx = (int) Randomly.getNotCachedInteger(0, refColumnCount);
        String col = aliasPrefix + ".ref" + refIdx;
        int kind = (int) Randomly.getNotCachedInteger(0, 3);
        switch (kind) {
        case 0:
            return new PostgresText("(" + col + " IS NOT NULL)");
        case 1:
            return new PostgresText("(" + col + " = " + col + ")");
        default:
            return new PostgresText("((" + col + " IS NOT NULL) OR (" + col + " IS NULL))");
        }
    }

    public static PostgresExpression generateEETQueryRandomShape(PostgresGlobalState state, PostgresExpressionGenerator gen,
            AbstractTables<PostgresTable, PostgresColumn> tables) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(gen);
        Objects.requireNonNull(tables);
        int mode;
        switch (state.getDbmsSpecificOptions().coveragePolicy) {
        case CONSERVATIVE:
            // Mostly plain SELECT; occasionally allow a bit of structure.
            mode = (int) Randomly.getNotCachedInteger(0, 8) == 0 ? (int) Randomly.getNotCachedInteger(1, 4) : 0;
            break;
        case AGGRESSIVE:
            // Prefer structured shapes to increase transformation surface.
            mode = (int) Randomly.getNotCachedInteger(0, 8) == 0 ? 0 : (int) Randomly.getNotCachedInteger(1, 4);
            break;
        case BALANCED:
        default:
            mode = (int) Randomly.getNotCachedInteger(0, 4);
            break;
        }
        switch (mode) {
        case 1:
            return buildUnionSelect(gen);
        case 2:
            return buildWithSelect(gen);
        case 3:
            return buildDerivedSelect(gen);
        case 0:
        default:
            return buildBaseSelect(gen);
        }
    }

    public static PostgresSelect buildBaseSelect(PostgresExpressionGenerator gen) {
        return buildBaseSelect(gen, LockingClauseContext.DIRECT_SELECT);
    }

    private static PostgresSelect buildBaseSelect(PostgresExpressionGenerator gen, LockingClauseContext lockingContext) {
        PostgresSelect select = gen.generateSelect(lockingContext);
        List<PostgresExpression> fetch = gen.generateFetchColumns(true);
        // alias fetch columns as ref0..refN for outer query references
        List<PostgresExpression> aliased = new ArrayList<>();
        for (int i = 0; i < fetch.size(); i++) {
            aliased.add(new PostgresAlias(fetch.get(i), "ref" + i));
        }
        select.setFetchColumns(aliased);
        select.setJoinClauses(gen.getRandomJoinClauses());
        select.setFromList(gen.getTableRefs());
        select.setWhereClause(gen.generateBooleanExpression());
        select.setGroupByExpressions(List.of());
        select.setOrderByClauses(List.of());
        return select;
    }

    private static PostgresUnionSelect buildUnionSelect(PostgresExpressionGenerator gen) {
        PostgresSelect left = buildBaseSelect(gen, LockingClauseContext.STRUCTURAL);
        List<PostgresExpression> sharedCols = new ArrayList<>(left.getFetchColumns());
        PostgresSelect right = gen.generateSelect(LockingClauseContext.STRUCTURAL);
        right.setFetchColumns(new ArrayList<>(sharedCols));
        right.setJoinClauses(gen.getRandomJoinClauses());
        right.setFromList(gen.getTableRefs());
        right.setWhereClause(gen.generateBooleanExpression());
        right.setGroupByExpressions(List.of());
        right.setOrderByClauses(List.of());
        return new PostgresUnionSelect(List.of(left, right), Randomly.getBoolean());
    }

    private static PostgresWithSelect buildWithSelect(PostgresExpressionGenerator gen) {
        PostgresSelect cteBody = buildBaseSelect(gen);
        PostgresCteDefinition cte = new PostgresCteDefinition(CTE_NAME, cteBody);

        PostgresSelect main = gen.generateSelect(LockingClauseContext.STRUCTURAL);
        List<PostgresExpression> outerFetch = new ArrayList<>();
        for (int i = 0; i < cteBody.getFetchColumns().size(); i++) {
            outerFetch.add(new PostgresText(CTE_NAME + ".ref" + i));
        }
        main.setFetchColumns(outerFetch);
        main.setFromList(List.of(new PostgresCteTableReference(CTE_NAME)));
        main.setJoinClauses(List.of());
        main.setWhereClause(outerScopeWhereClause(CTE_NAME, cteBody.getFetchColumns().size()));
        main.setGroupByExpressions(List.of());
        main.setOrderByClauses(List.of());

        return new PostgresWithSelect(List.of(cte), main);
    }

    private static PostgresSelect buildDerivedSelect(PostgresExpressionGenerator gen) {
        PostgresSelect inner = buildBaseSelect(gen);
        PostgresDerivedTable derived = new PostgresDerivedTable(inner, DERIVED_ALIAS);

        List<PostgresExpression> outerFetch = new ArrayList<>();
        for (int i = 0; i < inner.getFetchColumns().size(); i++) {
            outerFetch.add(new PostgresText(DERIVED_ALIAS + ".ref" + i));
        }

        PostgresSelect outer = gen.generateSelect(LockingClauseContext.STRUCTURAL);
        outer.setFetchColumns(outerFetch);
        outer.setFromList(List.of(derived));
        outer.setJoinClauses(List.of());
        outer.setWhereClause(outerScopeWhereClause(DERIVED_ALIAS, inner.getFetchColumns().size()));
        outer.setGroupByExpressions(List.of());
        outer.setOrderByClauses(List.of());
        return outer;
    }
}
