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
import sqlancer.postgres.ast.PostgresJoin.PostgresOuterType;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresPrefixOperation.PrefixOperator;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.ast.PostgresWildcard;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL-specific JIR transformer implementing all 6 rules. Strictly aligned with
 * MySQLJIRTransformer + original GeneralJIROracle from sqlancer-scale.
 *
 * Key enhancements over original:
 * - All 6 rules (original only implements Rule 1)
 * - SELECT * + multi-column fetch (matching original's generateFetchColumns)
 * - NATURAL OUTER JOIN variants (OuterType.LEFT/RIGHT/FULL, PostgreSQL supports FULL)
 * - Multi-table JOIN tree support (2-4 tables, 1-2 JOINs)
 * - Row-level result comparison (all columns, not just column 1)
 * - PostgresFromTable → PostgresTableReference (avoids random * suffix false positive)
 */
public class PostgresJIRTransformer implements JIRTransformer<PostgresGlobalState> {

    private List<PostgresTable> tables;
    private PostgresTable primaryTable; // First table (FROM table)
    private PostgresExpressionGenerator gen;
    private final ExpectedErrors errors;

    public PostgresJIRTransformer() {
        this.errors = ExpectedErrors.newErrors().with(PostgresCommon.getCommonExpressionErrors())
                .with(PostgresCommon.getCommonFetchErrors())
                .withRegex(PostgresCommon.getCommonExpressionRegexErrors()).build();
    }

    @Override
    public void initialize(PostgresGlobalState state, List<? extends AbstractTable<?, ?, ?>> selectedTables) {
        this.tables = new ArrayList<>();
        for (AbstractTable<?, ?, ?> t : selectedTables) {
            this.tables.add((PostgresTable) t);
        }
        this.primaryTable = this.tables.get(0);

        List<PostgresColumn> allColumns = new ArrayList<>();
        for (PostgresTable table : this.tables) {
            allColumns.addAll(table.getColumns());
        }
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
    // LEFT JOIN = INNER JOIN UNION ALL ANTI JOIN (NOT EXISTS)
    // Strictly following original GeneralJIROracle:
    // - 50/50 SELECT * vs multi-column fetch
    // - SELECT * branch: append right-table-column-count NULLs after *
    // - Explicit columns branch: replace right-table column references with NULL
    // - Multi-table JOIN tree support: transform only the last LEFT JOIN

    private JIRQuerySet generateLeftJoinDecomposition() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = generateFetchColumns();
        int joinCount = decideJoinCount();

        // Source: SELECT {fetchColumns} FROM primaryTable {preceding joins} LEFT JOIN lastTable ON cond
        List<PostgresJoin> sourceJoins = buildJoinChain(joinCount, PostgresJoinType.LEFT, onCondition);
        PostgresSelect sourceSelect = createSelectWithJoins(fetchColumns, sourceJoins);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target 1: Same structure but LEFT → INNER for the last join
        List<PostgresJoin> innerJoins = buildJoinChain(joinCount, PostgresJoinType.INNER, onCondition);
        PostgresSelect innerSelect = createSelectWithJoins(fetchColumns, innerJoins);
        String innerSQL = PostgresVisitor.asString(innerSelect);

        // Target 2: Anti-join — remove last LEFT JOIN, add NOT EXISTS
        List<PostgresJoin> precedingJoins = buildJoinChain(joinCount, PostgresJoinType.LEFT, onCondition);
        PostgresJoin removedJoin = precedingJoins.remove(precedingJoins.size() - 1);
        List<PostgresExpression> antiJoinCols = buildAntiJoinFetchColumns(fetchColumns);

        PostgresSelect antiSelect = createSelectWithJoins(antiJoinCols,
                precedingJoins.isEmpty() ? null : precedingJoins);

        // Build NOT EXISTS subquery using the removed join's table and ON condition
        PostgresTable removedTable = ((PostgresTableReference) removedJoin.getTableReference()).getTable();
        PostgresSelect existsSubquery = buildExistsSubquery(removedTable, removedJoin.getOnClause());
        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(existsSubquery),
                PrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);
        String antiSQL = PostgresVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.LEFT_JOIN_DECOMPOSITION);
    }

    // ========== Rule 2: Left/Right Symmetry ==========
    // A LEFT JOIN B ON cond ≡ B RIGHT JOIN A ON cond (for left-table columns)

    private JIRQuerySet generateLeftRightSymmetry() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = generateLeftTableFetchColumns();
        int joinCount = decideJoinCount();

        // Source: SELECT {fetchColumns} FROM primaryTable {preceding joins} LEFT JOIN lastTable ON cond
        List<PostgresJoin> sourceJoins = buildJoinChain(joinCount, PostgresJoinType.LEFT, onCondition);
        PostgresSelect sourceSelect = createSelectWithJoins(fetchColumns, sourceJoins);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target: Same structure but LEFT → RIGHT for the last join
        List<PostgresJoin> targetJoins = buildJoinChain(joinCount, PostgresJoinType.RIGHT, onCondition);
        PostgresSelect targetSelect = createSelectWithJoins(fetchColumns, targetJoins);
        String targetSQL = PostgresVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.LEFT_RIGHT_SYMMETRY);
    }

    // ========== Rule 3: Semi/Anti Complement ==========
    // L = (L WHERE EXISTS (SELECT 1 FROM R WHERE cond)) UNION ALL (L WHERE NOT EXISTS (...))
    // Source is pure left table (no JOIN), per paper definition

    private JIRQuerySet generateSemiAntiComplement() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = generateLeftTableFetchColumns();

        // Source: SELECT {leftCols} FROM L (pure left table, no JOIN)
        PostgresSelect sourceSelect = new PostgresSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        sourceSelect.setAllowForClause(false);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Build EXISTS subquery on right table
        PostgresSelect existsSubquery = buildExistsSubquery(getRightTable(), onCondition);

        // Target 1 (SEMI): SELECT {leftCols} FROM L WHERE EXISTS (SELECT 1 FROM R WHERE cond)
        PostgresSelect semiSelect = new PostgresSelect();
        semiSelect.setFetchColumns(fetchColumns);
        semiSelect.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        semiSelect.setAllowForClause(false);
        semiSelect.setWhereClause(new PostgresExists(existsSubquery));
        String semiSQL = PostgresVisitor.asString(semiSelect);

        // Target 2 (ANTI): SELECT {leftCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
        PostgresSelect antiSelect = new PostgresSelect();
        antiSelect.setFetchColumns(fetchColumns);
        antiSelect.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        antiSelect.setAllowForClause(false);
        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(existsSubquery),
                PrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);
        String antiSQL = PostgresVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.SEMI_ANTI_COMPLEMENT);
    }

    // ========== Rule 4: Full Join Decomposition (PG only) ==========
    // FULL OUTER JOIN = INNER JOIN UNION ALL left-anti UNION ALL right-anti
    // Uses generateFetchColumns() (both tables) for complete column coverage:
    // - left-anti: left cols present + right cols → NULL
    // - right-anti: left cols → NULL + right cols present

    private JIRQuerySet generateFullJoinDecomposition() {
        PostgresExpression onCondition = gen.generateExpression(0, PostgresDataType.BOOLEAN);
        List<PostgresExpression> fetchColumns = generateFetchColumns();

        // Source: SELECT {fetchColumns} FROM L FULL OUTER JOIN R ON cond
        PostgresSelect sourceSelect = createBaseSelectWithCols(PostgresJoinType.FULL, onCondition, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target 1: INNER JOIN — all columns present
        PostgresSelect innerSelect = createBaseSelectWithCols(PostgresJoinType.INNER, onCondition, fetchColumns);
        String innerSQL = PostgresVisitor.asString(innerSelect);

        // Target 2: Left anti-join (L rows with no match in R) — left cols present, right cols → NULL
        List<PostgresExpression> leftAntiCols = buildAntiJoinFetchColumns(fetchColumns);
        String leftAntiSQL = buildAntiJoinSQLWithCols(onCondition, leftAntiCols);

        // Target 3: Right anti-join (R rows with no match in L) — left cols → NULL, right cols present
        List<PostgresExpression> rightAntiCols = buildReverseAntiJoinFetchColumns(fetchColumns);
        String rightAntiSQL = buildReverseAntiJoinSQLWithCols(onCondition, rightAntiCols);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, leftAntiSQL, rightAntiSQL), JIRResultType.UNION_ALL,
                JIRRule.FULL_JOIN_DECOMPOSITION);
    }

    // ========== Rule 5: Cross Join Equivalence ==========
    // CROSS JOIN ≡ INNER JOIN ON TRUE ≡ LEFT JOIN ON TRUE ≡ RIGHT JOIN ON TRUE ≡ FULL JOIN ON TRUE
    // Randomly pick one variant per check() call; multiple iterations cover all variants

    private JIRQuerySet generateCrossJoinEquivalence() {
        List<PostgresExpression> fetchColumns = generateLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
        PostgresSelect sourceSelect = createBaseSelectWithCols(PostgresJoinType.CROSS, null, fetchColumns);
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Randomly pick target variant: INNER/LEFT/RIGHT/FULL ON TRUE
        // PostgreSQL supports FULL JOIN, so include it (MySQL only has INNER/LEFT/RIGHT)
        PostgresJoinType targetJoinType = Randomly.fromOptions(PostgresJoinType.INNER, PostgresJoinType.LEFT,
                PostgresJoinType.RIGHT, PostgresJoinType.FULL);
        PostgresExpression onTrue = PostgresConstant.createTrue();
        PostgresSelect targetSelect = createBaseSelectWithCols(targetJoinType, onTrue, fetchColumns);
        String targetSQL = PostgresVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.CROSS_JOIN_EQUIVALENCE);
    }

    // ========== Rule 6: Natural Join Explication ==========
    // NATURAL [LEFT|RIGHT|FULL OUTER] JOIN ≡ [INNER|LEFT|RIGHT|FULL OUTER] JOIN ON (equalities)
    // Supports NATURAL LEFT/RIGHT/FULL OUTER JOIN variants via OuterType.

    private JIRQuerySet generateNaturalJoinExplication() {
        // Randomly pick NATURAL variant: plain, LEFT, RIGHT, FULL
        PostgresOuterType outerType = Randomly.fromOptions((PostgresOuterType) null, PostgresOuterType.LEFT,
                PostgresOuterType.RIGHT, PostgresOuterType.FULL);

        // Find common column names between primary table and second table
        List<PostgresColumn> leftCols = primaryTable.getColumns();
        List<PostgresColumn> rightCols = tables.get(1).getColumns();

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

        // Determine target JoinType based on OuterType:
        // null → INNER, LEFT → LEFT, RIGHT → RIGHT, FULL → FULL
        PostgresJoinType targetJoinType;
        if (outerType == null) {
            targetJoinType = PostgresJoinType.INNER;
        } else {
            switch (outerType) {
            case LEFT:
                targetJoinType = PostgresJoinType.LEFT;
                break;
            case RIGHT:
                targetJoinType = PostgresJoinType.RIGHT;
                break;
            case FULL:
                targetJoinType = PostgresJoinType.FULL;
                break;
            default:
                throw new AssertionError(outerType);
            }
        }

        // Rule 6 must NOT use SELECT * — NATURAL JOIN deduplicates common columns,
        // producing fewer result columns than INNER JOIN ON (equalities).
        List<PostgresExpression> fetchColumns = generateLeftTableFetchColumnsNoWildcard();

        // Source: SELECT {fetchColumns} FROM L NATURAL [{outerType}] JOIN R
        PostgresSelect sourceSelect = new PostgresSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        sourceSelect.setAllowForClause(false);
        PostgresJoin naturalJoin = new PostgresJoin(new PostgresTableReference(tables.get(1)), null,
                PostgresJoinType.NATURAL);
        naturalJoin.setOuterType(outerType);
        sourceSelect.setJoinClauses(Collections.singletonList(naturalJoin));
        String sourceSQL = PostgresVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L {targetJoinType} JOIN R ON (equalities)
        PostgresSelect targetSelect = new PostgresSelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        targetSelect.setAllowForClause(false);
        targetSelect.setJoinClauses(Collections.singletonList(
                new PostgresJoin(new PostgresTableReference(tables.get(1)), explicitOn, targetJoinType)));
        String targetSQL = PostgresVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.NATURAL_JOIN_EXPLICATION);
    }

    // ========== Helper Methods ==========

    /**
     * Generate fetch columns for Rule 1 (LEFT JOIN Decomposition): 50/50 SELECT * vs multi-column
     * random subset from both tables. Matches original GeneralJIROracle.generateFetchColumns().
     */
    private List<PostgresExpression> generateFetchColumns() {
        List<PostgresExpression> columns = new ArrayList<>();
        if (Randomly.getBoolean()) {
            // SELECT * — 50% probability
            columns.add(new PostgresWildcard());
        } else {
            // Multi-column subset from both tables — 50% probability
            List<PostgresColumn> allCols = new ArrayList<>();
            allCols.addAll(primaryTable.getColumns());
            allCols.addAll(tables.get(1).getColumns());
            List<PostgresColumn> subset = Randomly.nonEmptySubset(allCols);
            for (PostgresColumn col : subset) {
                columns.add(PostgresColumnValue.create(col, null));
            }
        }
        return columns;
    }

    /**
     * Generate fetch columns from left table only: 50/50 SELECT * vs multi-column random subset.
     * Used by Rules 2, 3, 5 where comparison must be on left-table columns only.
     */
    private List<PostgresExpression> generateLeftTableFetchColumns() {
        List<PostgresExpression> columns = new ArrayList<>();
        List<PostgresColumn> leftCols = primaryTable.getColumns();
        if (leftCols.isEmpty()) {
            throw new IgnoreMeException();
        }
        if (Randomly.getBoolean()) {
            // SELECT * — 50% probability
            columns.add(new PostgresWildcard());
        } else {
            // Multi-column subset from left table — 50% probability
            List<PostgresColumn> subset = Randomly.nonEmptySubset(leftCols);
            for (PostgresColumn col : subset) {
                columns.add(PostgresColumnValue.create(col, null));
            }
        }
        return columns;
    }

    /**
     * Generate fetch columns from left table only WITHOUT SELECT * wildcard.
     * Used by Rule 6 (NATURAL_JOIN_EXPLICATION) because NATURAL JOIN deduplicates
     * common columns (fewer result columns) while INNER JOIN ON (equalities) does not.
     * SELECT * would cause column-count mismatch → false positive.
     */
    private List<PostgresExpression> generateLeftTableFetchColumnsNoWildcard() {
        List<PostgresColumn> leftCols = primaryTable.getColumns();
        if (leftCols.isEmpty()) {
            throw new IgnoreMeException();
        }
        List<PostgresColumn> subset = Randomly.nonEmptySubset(leftCols);
        List<PostgresExpression> columns = new ArrayList<>();
        for (PostgresColumn col : subset) {
            columns.add(PostgresColumnValue.create(col, null));
        }
        return columns;
    }

    /**
     * Build anti-join fetch columns: replace right table columns with NULL constants.
     * Strictly follows original GeneralJIROracle anti-join NULL substitution logic:
     * - SELECT * branch: append *, then lastTable.getColumns().size() NULL columns
     * - Explicit columns branch: replace right-table column references with NULL
     * Uses tables.get(tables.size()-1) for multi-table chain correctness.
     */
    private List<PostgresExpression> buildAntiJoinFetchColumns(List<PostgresExpression> fetchColumns) {
        List<PostgresExpression> result = new ArrayList<>();
        PostgresTable lastTable = tables.get(tables.size() - 1);

        // SELECT * branch: *, NULL, NULL, ... (one NULL per right-table column)
        if (fetchColumns.size() == 1 && fetchColumns.get(0) instanceof PostgresWildcard) {
            result.add(new PostgresWildcard()); // * expands to left-table columns (FROM is only L)
            for (int i = 0; i < lastTable.getColumns().size(); i++) {
                result.add(PostgresConstant.createNullConstant());
            }
            return result;
        }

        // Explicit columns branch: replace right-table column references with NULL
        for (PostgresExpression expr : fetchColumns) {
            if (expr instanceof PostgresColumnValue) {
                PostgresColumn col = ((PostgresColumnValue) expr).getColumn();
                if (col.getTable().getName().equals(lastTable.getName())) {
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
     * Build a chain of JOINs from the table list. The last JOIN is the transformation target.
     * Earlier JOINs form the context (random types: INNER/LEFT/RIGHT).
     * Matches original GeneralJoin.getJoins() pattern.
     */
    private List<PostgresJoin> buildJoinChain(int joinCount, PostgresJoinType lastJoinType,
            PostgresExpression lastOnCondition) {
        List<PostgresJoin> joins = new ArrayList<>();

        // Build preceding JOINs (random types, random ON conditions)
        for (int i = 1; i < joinCount; i++) {
            PostgresTable joinTable = tables.get(i);
            PostgresExpression onCond = gen.generateExpression(0, PostgresDataType.BOOLEAN);
            PostgresJoinType type = Randomly.fromOptions(PostgresJoinType.INNER, PostgresJoinType.LEFT,
                    PostgresJoinType.RIGHT);
            joins.add(new PostgresJoin(new PostgresTableReference(joinTable), onCond, type));
        }

        // Build the last JOIN (transformation target with specified type and ON condition)
        PostgresTable lastTable = tables.get(joinCount);
        joins.add(new PostgresJoin(new PostgresTableReference(lastTable), lastOnCondition, lastJoinType));

        return joins;
    }

    /**
     * Decide how many JOINs to generate. 1 JOIN most of the time (matching original),
     * 2 JOINs with rather low probability (for multi-table JOIN tree support).
     */
    private int decideJoinCount() {
        if (tables.size() >= 3 && Randomly.getBooleanWithRatherLowProbability()) {
            return Math.min(tables.size() - 1, 2); // Up to 2 JOINs (3 tables)
        }
        return 1; // 1 JOIN (2 tables) — most common, matching original
    }

    /**
     * Create a SELECT with specified fetch columns, FROM primaryTable, and optional JOIN clauses.
     */
    private PostgresSelect createSelectWithJoins(List<PostgresExpression> fetchColumns,
            List<PostgresJoin> joinClauses) {
        PostgresSelect select = new PostgresSelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        select.setAllowForClause(false);
        if (joinClauses != null && !joinClauses.isEmpty()) {
            select.setJoinClauses(joinClauses);
        }
        return select;
    }

    /**
     * Create a base SELECT with external fetch columns, FROM primaryTable, and a single JOIN to right table.
     * Used by Rules 4, 5, 6 where only 2 tables are involved.
     */
    private PostgresSelect createBaseSelectWithCols(PostgresJoinType joinType, PostgresExpression onCondition,
            List<PostgresExpression> fetchColumns) {
        PostgresSelect select = new PostgresSelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        select.setAllowForClause(false);

        if (joinType == PostgresJoinType.CROSS || joinType == PostgresJoinType.NATURAL) {
            PostgresJoin crossJoin = new PostgresJoin(new PostgresTableReference(tables.get(1)), null, joinType);
            select.setJoinClauses(Collections.singletonList(crossJoin));
        } else {
            PostgresJoin join = new PostgresJoin(new PostgresTableReference(tables.get(1)), onCondition, joinType);
            select.setJoinClauses(Collections.singletonList(join));
        }

        return select;
    }

    /**
     * Build EXISTS subquery: SELECT 1 FROM table WHERE condition.
     */
    private PostgresSelect buildExistsSubquery(PostgresTable table, PostgresExpression onCondition) {
        PostgresSelect subquery = new PostgresSelect();
        subquery.setFetchColumns(Collections.singletonList(PostgresConstant.createIntConstant(1)));
        subquery.setFromList(Collections.singletonList(new PostgresTableReference(table)));
        subquery.setWhereClause(onCondition);
        subquery.setAllowForClause(false);
        return subquery;
    }

    /**
     * Build anti-join SQL string with custom fetch columns:
     * SELECT {antiJoinCols} FROM primaryTable WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond).
     * Used by Rule 1 (with preceding joins) and Rule 4 (left-anti branch).
     */
    private String buildAntiJoinSQLWithCols(PostgresExpression onCondition, List<PostgresExpression> antiJoinCols) {
        PostgresSelect antiSelect = new PostgresSelect();
        antiSelect.setFetchColumns(antiJoinCols);
        antiSelect.setFromList(Collections.singletonList(new PostgresTableReference(primaryTable)));
        antiSelect.setAllowForClause(false);

        PostgresSelect existsSubquery = buildExistsSubquery(getRightTable(), onCondition);
        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(existsSubquery),
                PrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);

        return PostgresVisitor.asString(antiSelect);
    }

    /**
     * Build reverse anti-join fetch columns for Rule 4: left-table columns → NULL, right-table columns preserved.
     * Inverse of buildAntiJoinFetchColumns() which replaces right-table columns with NULL.
     * - SELECT * branch: primaryTable.getColumns().size() NULLs + all right-table columns
     * - Explicit columns branch: replace left-table column references with NULL, keep right-table ones
     */
    private List<PostgresExpression> buildReverseAntiJoinFetchColumns(List<PostgresExpression> fetchColumns) {
        List<PostgresExpression> result = new ArrayList<>();

        // SELECT * branch: |primaryTable.columns| NULLs + all right-table columns
        if (fetchColumns.size() == 1 && fetchColumns.get(0) instanceof PostgresWildcard) {
            for (int i = 0; i < primaryTable.getColumns().size(); i++) {
                result.add(PostgresConstant.createNullConstant());
            }
            for (PostgresColumn col : getRightTable().getColumns()) {
                result.add(PostgresColumnValue.create(col, null));
            }
            return result;
        }

        // Explicit columns branch: replace left-table column references with NULL, keep right-table ones
        for (PostgresExpression expr : fetchColumns) {
            if (expr instanceof PostgresColumnValue) {
                PostgresColumn col = ((PostgresColumnValue) expr).getColumn();
                if (col.getTable().getName().equals(primaryTable.getName())) {
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
     * Build reverse anti-join SQL with custom fetch columns:
     * SELECT {rightAntiCols} FROM R WHERE NOT EXISTS (SELECT 1 FROM L WHERE cond).
     */
    private String buildReverseAntiJoinSQLWithCols(PostgresExpression onCondition,
            List<PostgresExpression> rightAntiCols) {
        PostgresSelect reverseAntiSelect = new PostgresSelect();
        reverseAntiSelect.setFetchColumns(rightAntiCols);
        reverseAntiSelect.setFromList(Collections.singletonList(new PostgresTableReference(getRightTable())));
        reverseAntiSelect.setAllowForClause(false);

        // Reverse EXISTS: SELECT 1 FROM L WHERE cond
        PostgresSelect reverseExistsSub = buildExistsSubquery(primaryTable, onCondition);

        PostgresPrefixOperation notExists = new PostgresPrefixOperation(new PostgresExists(reverseExistsSub),
                PrefixOperator.NOT);
        reverseAntiSelect.setWhereClause(notExists);

        return PostgresVisitor.asString(reverseAntiSelect);
    }

    /**
     * Get the right table for Rules 3, 4, 5, 6 (always tables.get(1)).
     */
    private PostgresTable getRightTable() {
        return tables.get(1);
    }
}
