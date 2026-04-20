package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTable.TableType;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresDerivedTable;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresJoin;
import sqlancer.postgres.ast.PostgresJoin.PostgresJoinType;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.ForClause;
import sqlancer.postgres.ast.PostgresSelect.LockWaitOption;
import sqlancer.postgres.ast.PostgresSelect.LockingClauseContext;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;

class PostgresLockingPolicyTest {

    @Test
    void structuralContextClearsExistingForClauseState() {
        PostgresSelect select = new PostgresSelect();
        select.setForClause(ForClause.UPDATE);
        select.setLockWaitOption(LockWaitOption.NOWAIT);
        select.setForClauseOfReferences(List.of("t0"));

        select.configureForClause(LockingClauseContext.STRUCTURAL, List.of("t0"));

        assertFalse(select.isAllowForClause());
        assertNull(select.getForClause());
        assertEquals(LockWaitOption.NONE, select.getLockWaitOption());
        assertTrue(select.getForClauseOfReferences().isEmpty());
    }

    @Test
    void rendersForClauseForDirectBaseSelect() {
        PostgresTable table = table("t0");
        PostgresSelect select = baseSelect(table);
        select.setForClause(ForClause.UPDATE);
        select.setForClauseOfReferences(List.of("t0"));
        select.setLockWaitOption(LockWaitOption.SKIP_LOCKED);

        String sql = PostgresVisitor.asString(select);

        assertTrue(sql.contains("FOR UPDATE OF t0 SKIP LOCKED"), sql);
    }

    @Test
    void doesNotRenderForClauseForDistinctOrGroupedSelects() {
        PostgresTable table = table("t0");

        PostgresSelect distinctSelect = baseSelect(table);
        distinctSelect.setSelectType(PostgresSelect.SelectType.DISTINCT);
        distinctSelect.setForClause(ForClause.SHARE);
        assertFalse(PostgresVisitor.asString(distinctSelect).contains(" FOR "));

        PostgresSelect groupedSelect = baseSelect(table);
        groupedSelect.setForClause(ForClause.SHARE);
        groupedSelect.setGroupByExpressions(groupedSelect.getFetchColumns());
        assertFalse(PostgresVisitor.asString(groupedSelect).contains(" FOR "));
    }

    @Test
    void doesNotRenderForClauseForDerivedRelations() {
        PostgresTable table = table("t0");
        PostgresSelect inner = baseSelect(table);
        PostgresSelect outer = new PostgresSelect();
        outer.setFetchColumns(List.of(PostgresConstant.createIntConstant(1)));
        outer.setFromList(List.of(new PostgresDerivedTable(inner, "sub0")));
        outer.setJoinClauses(List.of());
        outer.setForClause(ForClause.UPDATE);

        String sql = PostgresVisitor.asString(outer);

        assertFalse(sql.contains(" FOR "), sql);
    }

    @Test
    void doesNotRenderForClauseForOuterJoinsOrWindowFunctions() {
        PostgresTable left = table("t0");
        PostgresTable right = table("t1");

        PostgresSelect outerJoinSelect = baseSelect(left);
        outerJoinSelect.setForClause(ForClause.KEY_SHARE);
        outerJoinSelect.setJoinClauses(List.of(new PostgresJoin(new PostgresFromTable(right, false),
                PostgresConstant.createBooleanConstant(true), PostgresJoinType.LEFT)));
        assertFalse(PostgresVisitor.asString(outerJoinSelect).contains(" FOR "));

        PostgresSelect windowSelect = baseSelect(left);
        windowSelect.setForClause(ForClause.KEY_SHARE);
        windowSelect.setWindowFunctions(List.<PostgresExpression>of(PostgresConstant.createIntConstant(1)));
        assertFalse(PostgresVisitor.asString(windowSelect).contains(" FOR "));
    }

    private static PostgresSelect baseSelect(PostgresTable table) {
        PostgresSelect select = new PostgresSelect();
        select.setFetchColumns(List.of(new PostgresColumnValue(table.getColumns().get(0), null)));
        select.setFromList(List.of(new PostgresFromTable(table, false)));
        select.setJoinClauses(List.of());
        return select;
    }

    private static PostgresTable table(String name) {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        return new PostgresTable(name, List.of(column), List.of(), TableType.STANDARD, List.of(), false, true);
    }
}
