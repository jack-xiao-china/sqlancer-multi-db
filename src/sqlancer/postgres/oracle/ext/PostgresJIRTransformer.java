package sqlancer.postgres.oracle.ext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.jir.JIRQuerySet;
import sqlancer.common.oracle.jir.JIRResultType;
import sqlancer.common.oracle.jir.JIRRule;
import sqlancer.common.oracle.jir.JIRTransformer;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.schema.AbstractTable;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresBinaryComparisonOperation;
import sqlancer.postgres.ast.PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation.BinaryLogicalOperator;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExists;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresJoin;
import sqlancer.postgres.ast.PostgresJoin.PostgresJoinType;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresPrefixOperation.PrefixOperator;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL-specific JIR transformer implementing all 6 rules.
 */
public class PostgresJIRTransformer implements JIRTransformer<PostgresGlobalState> {

    private PostgresTable leftTable;
    private PostgresTable rightTable;
    private PostgresExpressionGenerator gen;
    private final ExpectedErrors errors;

    public PostgresJIRTransformer() {
        this.errors = ExpectedErrors.newErrors().with(PostgresCommon.getCommonExpressionErrors())
                .with(PostgresCommon.getCommonFetchErrors())
                .withRegex(PostgresCommon.getCommonExpressionRegexErrors()).build();
    }

    @Override
    public void initialize(PostgresGlobalState state, List<? extends AbstractTable<?, ?, ?>> tables) {
        this.leftTable = (PostgresTable) tables.get(0);
        this.rightTable = (PostgresTable) tables.get(1);

        List<PostgresColumn> allColumns = new ArrayList<>();
        allColumns.addAll(leftTable.getColumns());
        allColumns.addAll(rightTable.getColumns());
        this.gen = new PostgresExpressionGenerator(state).setColumns(allColumns);
    }

    @Override
    public JIRQuerySet generateQuerySet(JIRRule rule) {
        switch (rule) {
        case LEFT_JOIN_DECOMPOSITION:
            return generateLeftJoinDecomposition();
        case LEFT_RIGHT_SYMMETRY:
            return generateLeftRightSymmetry();
        case SEMI_ANTI_COMPLEMENT:
            return generateSemiAntiComplement();
        case FULL_JOIN_DECOMPOSITION:
            return generateFullJoinDecomposition();
        case CROSS_JOIN_EQUIVALENCE:
            return generateCrossJoinEquivalence();
        case NATURAL_JOIN_EXPLICATION:
            return generateNaturalJoinExplication();
        default:
            return null;
        }
    }

    @Override
    public ExpectedErrors getExpectedErrors() {
        return errors;
    }

    // ========== Rule 1: Left Join Decomposition ==========
    // Strictly following original GeneralJIROracle: random multi-columns from both tables,
    // with right table columns replaced by NULL in the anti-join variant.

    private JIRQuerySet generateLeftJoinDecomposition() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = generateRandomFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        PostgresSelect sourceSelect = new PostgresSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
        sourceSelect.setJoinClauses(Collections
                .singletonList(new PostgresJoin(new PostgresTableReference(rightTable), onCondition, PostgresJoinType.LEFT)));
        sourceSelect.setAllowForClause(false);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target 1: SELECT {fetchColumns} FROM L INNER JOIN R ON cond
        PostgresSelect innerSelect = new PostgresSelect();
        innerSelect.setFetchColumns(fetchColumns);
        innerSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
        innerSelect.setJoinClauses(Collections
                .singletonList(new PostgresJoin(new PostgresTableReference(rightTable), onCondition, PostgresJoinType.INNER)));
        innerSelect.setAllowForClause(false);
        String innerSQL = PostgresVisitor.asString(innerSelect);

        // Target 2: SELECT {antiJoinCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
        List<PostgresExpression> antiJoinCols = buildAntiJoinFetchColumns(fetchColumns);
        String antiSQL = buildAntiJoinSQLWithCols(onCondition, antiJoinCols);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.LEFT_JOIN_DECOMPOSITION);
    }

    // ========== Rule 2: Left/Right Symmetry ==========

    private JIRQuerySet generateLeftRightSymmetry() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        PostgresSelect sourceSelect = createBaseSelectWithCols(PostgresJoinType.LEFT, onCondition, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM R RIGHT JOIN L ON cond
        PostgresSelect targetSelect = new PostgresSelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(new PostgresFromTable(rightTable, false)));

        PostgresJoin rightJoin = new PostgresJoin(new PostgresTableReference(leftTable), onCondition,
                PostgresJoinType.RIGHT);
        targetSelect.setJoinClauses(Collections.singletonList(rightJoin));
        targetSelect.setAllowForClause(false);

        String targetSQL = PostgresVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.LEFT_RIGHT_SYMMETRY);
    }

    // ========== Rule 3: Semi/Anti Complement ==========

    private JIRQuerySet generateSemiAntiComplement() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        PostgresJoinType joinType = Randomly.fromOptions(PostgresJoinType.INNER, PostgresJoinType.LEFT,
                PostgresJoinType.RIGHT, PostgresJoinType.FULL);
        List<PostgresExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L {type} JOIN R ON cond
        PostgresSelect sourceSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Build EXISTS subquery
        PostgresSelect existsSubquery = buildExistsSubquery(onCondition);

        // Target 1: Source + WHERE EXISTS
        PostgresSelect semiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        semiSelect.setWhereClause(new PostgresExists(existsSubquery));
        String semiSQL = PostgresVisitor.asString(semiSelect);

        // Target 2: Source + WHERE NOT EXISTS
        PostgresSelect antiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(existsSubquery),
                PrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);
        String antiSQL = PostgresVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.SEMI_ANTI_COMPLEMENT);
    }

    // ========== Rule 4: Full Join Decomposition (PG only) ==========

    private JIRQuerySet generateFullJoinDecomposition() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L FULL OUTER JOIN R ON cond
        PostgresSelect sourceSelect = createBaseSelectWithCols(PostgresJoinType.FULL, onCondition, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target 1: INNER JOIN
        PostgresSelect innerSelect = createBaseSelectWithCols(PostgresJoinType.INNER, onCondition, fetchColumns);
        String innerSQL = PostgresVisitor.asString(innerSelect);

        // Target 2: Left anti-join (L rows with no match in R)
        String leftAntiSQL = buildAntiJoinSQL(onCondition, fetchColumns);

        // Target 3: Right anti-join (R rows with no match in L) → SELECT NULL
        String rightAntiSQL = buildReverseAntiJoinSQL(onCondition);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, leftAntiSQL, rightAntiSQL), JIRResultType.UNION_ALL,
                JIRRule.FULL_JOIN_DECOMPOSITION);
    }

    // ========== Rule 5: Cross Join Equivalence ==========

    private JIRQuerySet generateCrossJoinEquivalence() {
        List<PostgresExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
        PostgresSelect sourceSelect = createBaseSelectWithCols(PostgresJoinType.CROSS, null, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L INNER JOIN R ON TRUE
        PostgresExpression onTrue = PostgresConstant.createTrue();
        PostgresSelect targetSelect = createBaseSelectWithCols(PostgresJoinType.INNER, onTrue, fetchColumns);
        String targetSQL = PostgresVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.CROSS_JOIN_EQUIVALENCE);
    }

    // ========== Rule 6: Natural Join Explication ==========

    private JIRQuerySet generateNaturalJoinExplication() {
        // Find common column names
        List<PostgresColumn> leftCols = leftTable.getColumns();
        List<PostgresColumn> rightCols = rightTable.getColumns();

        List<PostgresExpression> equalities = new ArrayList<>();
        for (PostgresColumn lc : leftCols) {
            for (PostgresColumn rc : rightCols) {
                if (lc.getName().equals(rc.getName())) {
                    PostgresExpression eq = new PostgresBinaryComparisonOperation(
                            PostgresColumnValue.create(lc, null), PostgresColumnValue.create(rc, null),
                            PostgresBinaryComparisonOperator.EQUALS);
                    equalities.add(eq);
                    break;
                }
            }
        }

        if (equalities.isEmpty()) {
            return null; // No common columns, skip
        }

        // Build AND chain
        PostgresExpression explicitOn = equalities.get(0);
        for (int i = 1; i < equalities.size(); i++) {
            explicitOn = new PostgresBinaryLogicalOperation(explicitOn, equalities.get(i),
                    BinaryLogicalOperator.AND);
        }

        List<PostgresExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: NATURAL JOIN via AST (PostgresJoinType.NATURAL)
        PostgresSelect sourceSelect = createBaseSelectWithCols(PostgresJoinType.NATURAL, null, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target: INNER JOIN with explicit equality conditions
        PostgresSelect targetSelect = createBaseSelectWithCols(PostgresJoinType.INNER, explicitOn, fetchColumns);
        String targetSQL = PostgresVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.NATURAL_JOIN_EXPLICATION);
    }

    // ========== Helper Methods ==========

    /**
     * Pick ONE random column from either table for Rule 1 fetch columns.
     * Single-column ensures NULL substitution is precisely verified by getString(1).
     */
    private List<PostgresExpression> generateRandomFetchColumns() {
        List<PostgresColumn> allCols = new ArrayList<>();
        allCols.addAll(leftTable.getColumns());
        allCols.addAll(rightTable.getColumns());
        return Collections.singletonList(PostgresColumnValue.create(Randomly.fromList(allCols), null));
    }

    /**
     * Build anti-join fetch columns: replace right table columns with NULL constants.
     * Aligns with original GeneralJIROracle anti-join NULL substitution logic.
     */
    private List<PostgresExpression> buildAntiJoinFetchColumns(List<PostgresExpression> fetchColumns) {
        List<PostgresExpression> result = new ArrayList<>();
        for (PostgresExpression expr : fetchColumns) {
            if (expr instanceof PostgresColumnValue) {
                PostgresColumn col = ((PostgresColumnValue) expr).getColumn();
                if (col.getTable().getName().equals(rightTable.getName())) {
                    result.add(PostgresConstant.createNullConstant());
                } else {
                    result.add(expr);
                }
            } else {
                result.add(expr);
            }
        }
        return result;
    }

    /**
     * Build anti-join SQL string with custom fetch columns:
     * SELECT {antiJoinCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond).
     */
    private String buildAntiJoinSQLWithCols(PostgresExpression onCondition, List<PostgresExpression> antiJoinCols) {
        PostgresSelect antiSelect = new PostgresSelect();
        antiSelect.setFetchColumns(antiJoinCols);
        antiSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
        antiSelect.setAllowForClause(false);

        PostgresSelect existsSubquery = buildExistsSubquery(onCondition);
        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(existsSubquery),
                PrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);

        return PostgresVisitor.asString(antiSelect);
    }

    /**
     * Create a base SELECT with external fetch columns, FROM left table, and a single JOIN to right table.
     * Used by Rules 2-6 where fetch columns must be shared across source/target queries.
     */
    private PostgresSelect createBaseSelectWithCols(PostgresJoinType joinType, PostgresExpression onCondition,
            List<PostgresExpression> fetchColumns) {
        PostgresSelect select = new PostgresSelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
        select.setAllowForClause(false);

        if (joinType == PostgresJoinType.CROSS || joinType == PostgresJoinType.NATURAL) {
            PostgresJoin crossJoin = new PostgresJoin(new PostgresTableReference(rightTable), null, joinType);
            select.setJoinClauses(Collections.singletonList(crossJoin));
        } else {
            PostgresJoin join = new PostgresJoin(new PostgresTableReference(rightTable), onCondition, joinType);
            select.setJoinClauses(Collections.singletonList(join));
        }

        return select;
    }

    private List<PostgresExpression> getLeftTableFetchColumns() {
        List<PostgresColumn> cols = leftTable.getColumns();
        if (cols.isEmpty()) {
            throw new IgnoreMeException();
        }
        return Collections.singletonList(PostgresColumnValue.create(Randomly.fromList(cols), null));
    }

    private PostgresSelect buildExistsSubquery(PostgresExpression onCondition) {
        PostgresSelect existsSub = new PostgresSelect();
        existsSub.setFetchColumns(Collections.singletonList(PostgresConstant.createIntConstant(1)));
        existsSub.setFromList(Collections.singletonList(new PostgresFromTable(rightTable, false)));
        existsSub.setWhereClause(onCondition);
        existsSub.setAllowForClause(false);
        return existsSub;
    }

    private String buildAntiJoinSQL(PostgresExpression onCondition, List<PostgresExpression> fetchColumns) {
        PostgresSelect antiSelect = new PostgresSelect();
        antiSelect.setFetchColumns(fetchColumns);
        antiSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
        antiSelect.setAllowForClause(false);

        PostgresSelect existsSubquery = buildExistsSubquery(onCondition);
        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(existsSubquery),
                PrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);

        return PostgresVisitor.asString(antiSelect);
    }

    /**
     * Build reverse anti-join for Rule 4: SELECT NULL FROM R WHERE NOT EXISTS (SELECT 1 FROM L WHERE cond).
     */
    private String buildReverseAntiJoinSQL(PostgresExpression onCondition) {
        PostgresSelect reverseAntiSelect = new PostgresSelect();
        reverseAntiSelect.setFetchColumns(Collections.singletonList(PostgresConstant.createNullConstant()));
        reverseAntiSelect.setFromList(Collections.singletonList(new PostgresFromTable(rightTable, false)));
        reverseAntiSelect.setAllowForClause(false);

        // Reverse EXISTS: SELECT 1 FROM L WHERE cond
        PostgresSelect reverseExistsSub = new PostgresSelect();
        reverseExistsSub.setFetchColumns(Collections.singletonList(PostgresConstant.createIntConstant(1)));
        reverseExistsSub.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
        reverseExistsSub.setWhereClause(onCondition);
        reverseExistsSub.setAllowForClause(false);

        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(reverseExistsSub),
                PrefixOperator.NOT);
        reverseAntiSelect.setWhereClause(notExists);

        return PostgresVisitor.asString(reverseAntiSelect);
    }
}
