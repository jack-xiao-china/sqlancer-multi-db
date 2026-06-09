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
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL-specific JIR transformer implementing 5 rules (MySQL lacks FULL JOIN).
 */
public class MySQLJIRTransformer implements JIRTransformer<MySQLGlobalState> {

    private MySQLTable leftTable;
    private MySQLTable rightTable;
    private MySQLExpressionGenerator gen;
    private final ExpectedErrors errors;

    public MySQLJIRTransformer() {
        this.errors = ExpectedErrors.newErrors().with(MySQLErrors.getExpressionErrors())
                .withRegex(MySQLErrors.getExpressionRegexErrors()).build();
    }

    @Override
    public void initialize(MySQLGlobalState state, List<? extends AbstractTable<?, ?, ?>> tables) {
        this.leftTable = (MySQLTable) tables.get(0);
        this.rightTable = (MySQLTable) tables.get(1);

        List<MySQLColumn> allColumns = new ArrayList<>();
        allColumns.addAll(leftTable.getColumns());
        allColumns.addAll(rightTable.getColumns());
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
    // Strictly following original GeneralJIROracle: random multi-columns from both tables,
    // with right table columns replaced by NULL in the anti-join variant.

    private JIRQuerySet generateLeftJoinDecomposition() {
        MySQLExpression onCondition = gen.generateExpression(0);
        List<MySQLExpression> fetchColumns = generateRandomFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        MySQLSelect sourceSelect = new MySQLSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));
        sourceSelect.setJoinClauses(
                Collections.singletonList(new MySQLJoin(rightTable, onCondition, JoinType.LEFT)));
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target 1: SELECT {fetchColumns} FROM L INNER JOIN R ON cond
        MySQLSelect innerSelect = new MySQLSelect();
        innerSelect.setFetchColumns(fetchColumns);
        innerSelect.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));
        innerSelect.setJoinClauses(
                Collections.singletonList(new MySQLJoin(rightTable, onCondition, JoinType.INNER)));
        String innerSQL = MySQLVisitor.asString(innerSelect);

        // Target 2: SELECT {antiJoinCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
        List<MySQLExpression> antiJoinCols = buildAntiJoinFetchColumns(fetchColumns);
        String antiSQL = buildAntiJoinSQLWithCols(onCondition, antiJoinCols);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.LEFT_JOIN_DECOMPOSITION);
    }

    // ========== Rule 2: Left/Right Symmetry ==========
    // A LEFT JOIN B = B RIGHT JOIN A

    private JIRQuerySet generateLeftRightSymmetry() {
        MySQLExpression onCondition = gen.generateExpression(0);
        List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        MySQLSelect sourceSelect = createBaseSelectWithCols(JoinType.LEFT, onCondition, fetchColumns);
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM R RIGHT JOIN L ON cond
        MySQLSelect targetSelect = new MySQLSelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(rightTable)));

        MySQLJoin rightJoin = new MySQLJoin(leftTable, onCondition, JoinType.RIGHT);
        targetSelect.setJoinClauses(Collections.singletonList(rightJoin));

        String targetSQL = MySQLVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.LEFT_RIGHT_SYMMETRY);
    }

    // ========== Rule 3: Semi/Anti Complement ==========
    // Q = Q WHERE EXISTS(sub) UNION ALL Q WHERE NOT EXISTS(sub)

    private JIRQuerySet generateSemiAntiComplement() {
        MySQLExpression onCondition = gen.generateExpression(0);
        JoinType joinType = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
        List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L {type} JOIN R ON cond
        MySQLSelect sourceSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Build EXISTS subquery: SELECT 1 FROM R WHERE cond
        MySQLSelect existsSubquery = new MySQLSelect();
        existsSubquery.setFetchColumns(Collections.singletonList(MySQLConstant.createIntConstant(1)));
        existsSubquery.setFromList(Collections.singletonList(new MySQLTableReference(rightTable)));
        existsSubquery.setWhereClause(onCondition);

        // Target 1: Source + WHERE EXISTS (subquery)
        MySQLSelect semiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        MySQLExists existsExpr = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
        semiSelect.setWhereClause(existsExpr);
        String semiSQL = MySQLVisitor.asString(semiSelect);

        // Target 2: Source + WHERE NOT EXISTS (subquery)
        MySQLSelect antiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        MySQLExists notExistsSubquery = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
        MySQLUnaryPrefixOperation notExistsExpr = new MySQLUnaryPrefixOperation(notExistsSubquery,
                MySQLUnaryPrefixOperator.NOT);
        antiSelect.setWhereClause(notExistsExpr);
        String antiSQL = MySQLVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.SEMI_ANTI_COMPLEMENT);
    }

    // ========== Rule 5: Cross Join Equivalence ==========
    // CROSS JOIN = INNER JOIN ON TRUE

    private JIRQuerySet generateCrossJoinEquivalence() {
        List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
        MySQLSelect sourceSelect = createBaseSelectWithCols(JoinType.CROSS, null, fetchColumns);
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L INNER JOIN R ON TRUE (1 in MySQL)
        MySQLExpression onTrue = MySQLConstant.createIntConstant(1);
        MySQLSelect targetSelect = createBaseSelectWithCols(JoinType.INNER, onTrue, fetchColumns);
        String targetSQL = MySQLVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.CROSS_JOIN_EQUIVALENCE);
    }

    // ========== Rule 6: Natural Join Explication ==========
    // NATURAL JOIN = INNER JOIN with explicit equality conditions

    private JIRQuerySet generateNaturalJoinExplication() {
        // Find common column names between left and right tables
        List<MySQLColumn> leftCols = leftTable.getColumns();
        List<MySQLColumn> rightCols = rightTable.getColumns();

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

        List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L NATURAL JOIN R
        MySQLSelect sourceSelect = createBaseSelectWithCols(JoinType.NATURAL, null, fetchColumns);
        String sourceSQL = MySQLVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L INNER JOIN R ON (equalities)
        MySQLSelect targetSelect = createBaseSelectWithCols(JoinType.INNER, explicitOn, fetchColumns);
        String targetSQL = MySQLVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.NATURAL_JOIN_EXPLICATION);
    }

    // ========== Helper Methods ==========

    /**
     * Pick ONE random column from either table for Rule 1 fetch columns.
     * Single-column ensures NULL substitution is precisely verified by getString(1).
     */
    private List<MySQLExpression> generateRandomFetchColumns() {
        List<MySQLColumn> allCols = new ArrayList<>();
        allCols.addAll(leftTable.getColumns());
        allCols.addAll(rightTable.getColumns());
        return Collections.singletonList(MySQLColumnReference.create(Randomly.fromList(allCols), null));
    }

    /**
     * Build anti-join fetch columns: replace right table columns with NULL constants.
     * Aligns with original GeneralJIROracle anti-join NULL substitution logic.
     */
    private List<MySQLExpression> buildAntiJoinFetchColumns(List<MySQLExpression> fetchColumns) {
        List<MySQLExpression> result = new ArrayList<>();
        for (MySQLExpression expr : fetchColumns) {
            if (expr instanceof MySQLColumnReference) {
                MySQLColumn col = ((MySQLColumnReference) expr).getColumn();
                if (col.getTable().getName().equals(rightTable.getName())) {
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
     * Build anti-join SQL string with custom fetch columns:
     * SELECT {antiJoinCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond).
     */
    private String buildAntiJoinSQLWithCols(MySQLExpression onCondition, List<MySQLExpression> antiJoinCols) {
        MySQLSelect antiSelect = new MySQLSelect();
        antiSelect.setFetchColumns(antiJoinCols);
        antiSelect.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));

        // Build NOT EXISTS subquery
        MySQLSelect existsSubquery = new MySQLSelect();
        existsSubquery.setFetchColumns(Collections.singletonList(MySQLConstant.createIntConstant(1)));
        existsSubquery.setFromList(Collections.singletonList(new MySQLTableReference(rightTable)));
        existsSubquery.setWhereClause(onCondition);

        MySQLExists existsExpr = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
        MySQLUnaryPrefixOperation notExists = new MySQLUnaryPrefixOperation(existsExpr,
                MySQLUnaryPrefixOperator.NOT);
        antiSelect.setWhereClause(notExists);

        return MySQLVisitor.asString(antiSelect);
    }

    /**
     * Create a base SELECT with external fetch columns, FROM left table, and a single JOIN to right table.
     * Used by Rules 2-6 where fetch columns must be shared across source/target queries.
     */
    private MySQLSelect createBaseSelectWithCols(JoinType joinType, MySQLExpression onCondition,
            List<MySQLExpression> fetchColumns) {
        MySQLSelect select = new MySQLSelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));

        if (joinType != null) {
            MySQLJoin join = new MySQLJoin(rightTable, onCondition, joinType);
            select.setJoinClauses(Collections.singletonList(join));
        }

        return select;
    }

    /**
     * Get fetch columns from the left table only (random column, for safe comparison across all JOIN variants).
     */
    private List<MySQLExpression> getLeftTableFetchColumns() {
        List<MySQLColumn> cols = leftTable.getColumns();
        if (cols.isEmpty()) {
            throw new IgnoreMeException();
        }
        return Collections.singletonList(MySQLColumnReference.create(Randomly.fromList(cols), null));
    }

}
