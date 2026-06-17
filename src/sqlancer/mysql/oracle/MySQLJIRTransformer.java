package sqlancer.mysql.oracle;

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
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExists;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLJoin.JoinType;
import sqlancer.mysql.ast.MySQLJoin.OuterType;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;
import sqlancer.mysql.ast.MySQLWildcard;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL-specific JIR transformer implementing 5 rules (MySQL lacks FULL JOIN). Strictly aligned with original
 * GeneralJIROracle + GeneralJoin implementation from sqlancer-scale.
 *
 * Key enhancements over original:
 * - 5 rules (original only implements Rule 1)
 * - SELECT * + multi-column fetch (matching original's generateFetchColumns)
 * - NATURAL OUTER JOIN variants (OuterType.LEFT/RIGHT, matching original's OuterType enum)
 * - CROSS JOIN multi-variant comparison (INNER/LEFT/RIGHT ON TRUE)
 * - Multi-table JOIN tree support (2-4 tables, 1-2 JOINs)
 * - Row-level result comparison (all columns, not just column 1)
 */
public class MySQLJIRTransformer implements JIRTransformer<MySQLGlobalState> {

    private List<MySQLTable> tables;
    private MySQLTable primaryTable; // First table (FROM table)
    private MySQLExpressionGenerator gen;
    private final ExpectedErrors errors;

    public MySQLJIRTransformer() {
        this.errors = ExpectedErrors.newErrors().with(MySQLErrors.getExpressionErrors())
                .withRegex(MySQLErrors.getExpressionRegexErrors()).build();
    }

    @Override
    public void initialize(MySQLGlobalState state, List<? extends AbstractTable<?, ?, ?>> selectedTables) {
        this.tables = new ArrayList<>();
        for (AbstractTable<?, ?, ?> t : selectedTables) {
            this.tables.add((MySQLTable) t);
        }
        this.primaryTable = this.tables.get(0);

        List<MySQLColumn> allColumns = new ArrayList<>();
        for (MySQLTable table : this.tables) {
            allColumns.addAll(table.getColumns());
        }
        this.gen = new MySQLExpressionGenerator(state).setColumns(allColumns);
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
            return null; // MySQL does not support FULL JOIN
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
        MySQLExpression onCondition = gen.generateExpression(0);
        List<MySQLExpression> fetchColumns = generateFetchColumns();
        int joinCount = decideJoinCount();

        // Source: SELECT {fetchColumns} FROM primaryTable {preceding joins} LEFT JOIN lastTable ON cond
        List<MySQLJoin> sourceJoins = buildJoinChain(joinCount, JoinType.LEFT, onCondition);
        MySQLSelect sourceSelect = createSelectWithJoins(fetchColumns, sourceJoins);
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target 1: Same structure but LEFT → INNER for the last join
        List<MySQLJoin> innerJoins = buildJoinChain(joinCount, JoinType.INNER, onCondition);
        MySQLSelect innerSelect = createSelectWithJoins(fetchColumns, innerJoins);
        String innerSQL = MySQLVisitor.asString(innerSelect);

        // Target 2: Anti-join — remove last LEFT JOIN, add NOT EXISTS
        List<MySQLJoin> precedingJoins = buildJoinChain(joinCount, JoinType.LEFT, onCondition);
        MySQLJoin removedJoin = precedingJoins.remove(precedingJoins.size() - 1);
        List<MySQLExpression> antiJoinCols = buildAntiJoinFetchColumns(fetchColumns);

        MySQLSelect antiSelect = createSelectWithJoins(antiJoinCols,
                precedingJoins.isEmpty() ? null : precedingJoins);

        // Build NOT EXISTS subquery using the removed join's table and ON condition
        MySQLSelect existsSubquery = buildExistsSubquery(removedJoin.getTable(), removedJoin.getOnClause());
        MySQLExists existsExpr = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
        MySQLUnaryPrefixOperation notExists = new MySQLUnaryPrefixOperation(existsExpr, MySQLUnaryPrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);
        String antiSQL = MySQLVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.LEFT_JOIN_DECOMPOSITION);
    }

    // ========== Rule 2: Left/Right Symmetry ==========
    // A LEFT JOIN B ON cond ≡ B RIGHT JOIN A ON cond (for left-table columns)

    private JIRQuerySet generateLeftRightSymmetry() {
        MySQLExpression onCondition = gen.generateExpression(0);
        List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();
        int joinCount = decideJoinCount();

        // Source: SELECT {fetchColumns} FROM primaryTable {preceding joins} LEFT JOIN lastTable ON cond
        List<MySQLJoin> sourceJoins = buildJoinChain(joinCount, JoinType.LEFT, onCondition);
        MySQLSelect sourceSelect = createSelectWithJoins(fetchColumns, sourceJoins);
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target: Same structure but LEFT → RIGHT for the last join
        List<MySQLJoin> targetJoins = buildJoinChain(joinCount, JoinType.RIGHT, onCondition);
        MySQLSelect targetSelect = createSelectWithJoins(fetchColumns, targetJoins);
        String targetSQL = MySQLVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.LEFT_RIGHT_SYMMETRY);
    }

    // ========== Rule 3: Semi/Anti Complement ==========
    // L = (L WHERE EXISTS (SELECT 1 FROM R WHERE cond)) UNION ALL (L WHERE NOT EXISTS (...))
    // Source is pure left table (no JOIN), per paper definition

    private JIRQuerySet generateSemiAntiComplement() {
        MySQLExpression onCondition = gen.generateExpression(0);
        List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();

        // Source: SELECT {leftCols} FROM L (pure left table, no JOIN)
        MySQLSelect sourceSelect = new MySQLSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Build EXISTS subquery: SELECT 1 FROM R WHERE onCondition
        MySQLSelect existsSubquery = buildExistsSubquery(getRightTableForRule3(), onCondition);

        // Target 1 (SEMI): SELECT {leftCols} FROM L WHERE EXISTS (SELECT 1 FROM R WHERE cond)
        MySQLSelect semiSelect = new MySQLSelect();
        semiSelect.setFetchColumns(fetchColumns);
        semiSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        MySQLExists existsExpr = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
        semiSelect.setWhereClause(existsExpr);
        String semiSQL = MySQLVisitor.asString(semiSelect);

        // Target 2 (ANTI): SELECT {leftCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
        MySQLSelect antiSelect = new MySQLSelect();
        antiSelect.setFetchColumns(fetchColumns);
        antiSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        MySQLExists notExistsSubquery = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
        MySQLUnaryPrefixOperation notExistsExpr = new MySQLUnaryPrefixOperation(notExistsSubquery,
                MySQLUnaryPrefixOperator.NOT);
        antiSelect.setWhereClause(notExistsExpr);
        String antiSQL = MySQLVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.SEMI_ANTI_COMPLEMENT);
    }

    // ========== Rule 5: Cross Join Equivalence ==========
    // CROSS JOIN ≡ INNER JOIN ON TRUE ≡ LEFT JOIN ON TRUE ≡ RIGHT JOIN ON TRUE
    // Randomly pick one variant per check() call; multiple iterations cover all variants

    private JIRQuerySet generateCrossJoinEquivalence() {
        List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
        MySQLSelect sourceSelect = new MySQLSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        sourceSelect.setJoinClauses(
                Collections.singletonList(new MySQLJoin(tables.get(1), null, JoinType.CROSS)));
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Randomly pick target variant: INNER/LEFT/RIGHT ON TRUE
        JoinType targetJoinType = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
        MySQLExpression onTrue = MySQLConstant.createIntConstant(1); // ON 1 (TRUE in MySQL)

        MySQLSelect targetSelect = new MySQLSelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        targetSelect.setJoinClauses(
                Collections.singletonList(new MySQLJoin(tables.get(1), onTrue, targetJoinType)));
        String targetSQL = MySQLVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.CROSS_JOIN_EQUIVALENCE);
    }

    // ========== Rule 6: Natural Join Explication ==========
    // NATURAL [LEFT|RIGHT] JOIN ≡ [INNER|LEFT|RIGHT] JOIN ON (equalities)
    // Supports NATURAL LEFT JOIN and NATURAL RIGHT JOIN (OuterType)

    private JIRQuerySet generateNaturalJoinExplication() {
        // Randomly pick NATURAL variant: plain, LEFT, RIGHT
        OuterType outerType = Randomly.fromOptions((OuterType) null, OuterType.LEFT, OuterType.RIGHT);

        // Find common column names between primary table and second table
        List<MySQLColumn> leftCols = primaryTable.getColumns();
        List<MySQLColumn> rightCols = tables.get(1).getColumns();

        List<MySQLExpression> equalities = new ArrayList<>();
        for (MySQLColumn lc : leftCols) {
            for (MySQLColumn rc : rightCols) {
                if (lc.getName().equals(rc.getName())) {
                    MySQLExpression eq = new MySQLBinaryComparisonOperation(
                            MySQLColumnReference.create(lc, null), MySQLColumnReference.create(rc, null),
                            BinaryComparisonOperator.EQUALS);
                    equalities.add(eq);
                    break;
                }
            }
        }

        if (equalities.isEmpty()) {
            // No common columns: NATURAL JOIN ≡ CROSS JOIN, skip
            return null;
        }

        // Build AND chain of equalities
        MySQLExpression explicitOn = equalities.get(0);
        for (int i = 1; i < equalities.size(); i++) {
            explicitOn = new MySQLBinaryLogicalOperation(explicitOn, equalities.get(i),
                    MySQLBinaryLogicalOperator.AND);
        }

        List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();

        // Determine target JoinType based on OuterType:
        // null → INNER, LEFT → LEFT, RIGHT → RIGHT
        JoinType targetJoinType;
        if (outerType == null) {
            targetJoinType = JoinType.INNER;
        } else if (outerType == OuterType.LEFT) {
            targetJoinType = JoinType.LEFT;
        } else {
            targetJoinType = JoinType.RIGHT;
        }

        // Source: SELECT {fetchColumns} FROM L NATURAL [{outerType}] JOIN R
        MySQLSelect sourceSelect = new MySQLSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        MySQLJoin naturalJoin = new MySQLJoin(tables.get(1), null, JoinType.NATURAL);
        naturalJoin.setOuterType(outerType);
        sourceSelect.setJoinClauses(Collections.singletonList(naturalJoin));
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L {targetJoinType} JOIN R ON (equalities)
        MySQLSelect targetSelect = new MySQLSelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        targetSelect.setJoinClauses(
                Collections.singletonList(new MySQLJoin(tables.get(1), explicitOn, targetJoinType)));
        String targetSQL = MySQLVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.NATURAL_JOIN_EXPLICATION);
    }

    // ========== Helper Methods ==========

    /**
     * Generate fetch columns following original GeneralJIROracle pattern: 50% SELECT * vs 50% multi-column random
     * subset from both tables. Used by Rule 1 (LEFT JOIN Decomposition) where both left and right table columns are
     * valid fetch targets.
     */
    private List<MySQLExpression> generateFetchColumns() {
        List<MySQLExpression> columns = new ArrayList<>();
        if (Randomly.getBoolean()) {
            // SELECT * — 50% probability
            columns.add(new MySQLWildcard());
        } else {
            // Multi-column subset from both tables — 50% probability
            List<MySQLColumn> allCols = new ArrayList<>();
            allCols.addAll(primaryTable.getColumns());
            allCols.addAll(tables.get(1).getColumns());
            List<MySQLColumn> subset = Randomly.nonEmptySubset(allCols);
            for (MySQLColumn col : subset) {
                columns.add(MySQLColumnReference.create(col, null));
            }
        }
        return columns;
    }

    /**
     * Generate fetch columns from left table only: 50% SELECT * vs 50% multi-column random subset from primary
     * table. Used by Rules 2, 3, 5, 6 where comparison must be on left-table columns only.
     */
    private List<MySQLExpression> generateLeftTableFetchColumns() {
        List<MySQLExpression> columns = new ArrayList<>();
        List<MySQLColumn> leftCols = primaryTable.getColumns();
        if (leftCols.isEmpty()) {
            throw new IgnoreMeException();
        }
        if (Randomly.getBoolean()) {
            // SELECT * — 50% probability
            columns.add(new MySQLWildcard());
        } else {
            // Multi-column subset from left table — 50% probability
            List<MySQLColumn> subset = Randomly.nonEmptySubset(leftCols);
            for (MySQLColumn col : subset) {
                columns.add(MySQLColumnReference.create(col, null));
            }
        }
        return columns;
    }

    /**
     * Build anti-join fetch columns: replace right table columns with NULL constants. Strictly follows original
     * GeneralJIROracle anti-join NULL substitution logic (lines 145-167):
     * - SELECT * branch: append *, then rightTable.getColumns().size() NULL columns
     * - Explicit columns branch: replace right-table column references with NULL
     */
    private List<MySQLExpression> buildAntiJoinFetchColumns(List<MySQLExpression> fetchColumns) {
        List<MySQLExpression> result = new ArrayList<>();

        // SELECT * branch: *, NULL, NULL, ... (one NULL per right-table column)
        if (fetchColumns.size() == 1 && fetchColumns.get(0) instanceof MySQLWildcard) {
            result.add(new MySQLWildcard()); // * expands to left-table columns (FROM is only L)
            MySQLTable lastTable = tables.get(tables.size() - 1);
            for (int i = 0; i < lastTable.getColumns().size(); i++) {
                result.add(MySQLConstant.createNullConstant());
            }
            return result;
        }

        // Explicit columns branch: replace right-table column references with NULL
        MySQLTable lastTable = tables.get(tables.size() - 1);
        for (MySQLExpression expr : fetchColumns) {
            if (expr instanceof MySQLColumnReference) {
                MySQLColumn col = ((MySQLColumnReference) expr).getColumn();
                if (col.getTable().getName().equals(lastTable.getName())) {
                    result.add(MySQLConstant.createNullConstant());
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
     * Build a chain of JOINs from the table list. The last JOIN is the transformation target. Earlier JOINs form the
     * context (random types: INNER/LEFT/RIGHT). Matches original GeneralJoin.getJoins() pattern.
     */
    private List<MySQLJoin> buildJoinChain(int joinCount, JoinType lastJoinType, MySQLExpression lastOnCondition) {
        List<MySQLJoin> joins = new ArrayList<>();

        // Build preceding JOINs (random types, random ON conditions)
        for (int i = 1; i < joinCount; i++) {
            MySQLTable joinTable = tables.get(i);
            MySQLExpression onCond = gen.generateExpression(0);
            JoinType type = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
            joins.add(new MySQLJoin(joinTable, onCond, type));
        }

        // Build the last JOIN (transformation target with specified type and ON condition)
        MySQLTable lastTable = tables.get(joinCount);
        joins.add(new MySQLJoin(lastTable, lastOnCondition, lastJoinType));

        return joins;
    }

    /**
     * Decide how many JOINs to generate. 1 JOIN most of the time (matching original), 2 JOINs with rather low
     * probability (for multi-table JOIN tree support).
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
    private MySQLSelect createSelectWithJoins(List<MySQLExpression> fetchColumns, List<MySQLJoin> joinClauses) {
        MySQLSelect select = new MySQLSelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
        if (joinClauses != null && !joinClauses.isEmpty()) {
            select.setJoinClauses(joinClauses);
        }
        return select;
    }

    /**
     * Build EXISTS subquery: SELECT 1 FROM table WHERE condition.
     */
    private MySQLSelect buildExistsSubquery(MySQLTable table, MySQLExpression onCondition) {
        MySQLSelect subquery = new MySQLSelect();
        subquery.setFetchColumns(Collections.singletonList(MySQLConstant.createIntConstant(1)));
        subquery.setFromList(Collections.singletonList(new MySQLTableReference(table)));
        subquery.setWhereClause(onCondition);
        return subquery;
    }

    /**
     * Get the right table for Rule 3 (SEMI_ANTI_COMPLEMENT). With multi-table support, this is the last table in the
     * list (tables.get(1) for 2-table scenarios).
     */
    private MySQLTable getRightTableForRule3() {
        return tables.get(1);
    }

}
