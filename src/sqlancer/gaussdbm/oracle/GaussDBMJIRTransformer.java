package sqlancer.gaussdbm.oracle;

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
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation.GaussDBBinaryLogicalOperator;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExists;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBJoin.JoinType;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * GaussDB-M (MySQL compatibility mode) JIR transformer implementing 5 rules.
 * Excludes FULL_JOIN_DECOMPOSITION since MySQL compat mode has no FULL OUTER JOIN.
 */
public class GaussDBMJIRTransformer implements JIRTransformer<GaussDBMGlobalState> {

    private GaussDBTable leftTable;
    private GaussDBTable rightTable;
    private GaussDBMExpressionGenerator gen;
    private final ExpectedErrors errors;

    public GaussDBMJIRTransformer() {
        ExpectedErrors errorsBuilder = new ExpectedErrors();
        GaussDBMErrors.addExpressionErrors(errorsBuilder);
        this.errors = errorsBuilder;
    }

    @Override
    public void initialize(GaussDBMGlobalState state, List<? extends AbstractTable<?, ?, ?>> tables) {
        this.leftTable = (GaussDBTable) tables.get(0);
        this.rightTable = (GaussDBTable) tables.get(1);

        List<GaussDBColumn> allColumns = new ArrayList<>();
        allColumns.addAll(leftTable.getColumns());
        allColumns.addAll(rightTable.getColumns());
        this.gen = new GaussDBMExpressionGenerator(state).setColumns(allColumns);
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
        case CROSS_JOIN_EQUIVALENCE:
            return generateCrossJoinEquivalence();
        case NATURAL_JOIN_EXPLICATION:
            return generateNaturalJoinExplication();
        default:
            return null; // FULL_JOIN_DECOMPOSITION not supported in MySQL compat
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
        GaussDBExpression onCondition = gen.generateExpression();
        List<GaussDBExpression> fetchColumns = generateRandomFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        GaussDBSelect sourceSelect = new GaussDBSelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(GaussDBTableReference.create(leftTable)));
        sourceSelect.setJoinClauses(
                Collections.singletonList(new GaussDBJoin(GaussDBTableReference.create(rightTable), onCondition, JoinType.LEFT)));
        String sourceSQL = GaussDBToStringVisitor.asString(sourceSelect);

        // Target 1: SELECT {fetchColumns} FROM L INNER JOIN R ON cond
        GaussDBSelect innerSelect = new GaussDBSelect();
        innerSelect.setFetchColumns(fetchColumns);
        innerSelect.setFromList(Collections.singletonList(GaussDBTableReference.create(leftTable)));
        innerSelect.setJoinClauses(
                Collections.singletonList(new GaussDBJoin(GaussDBTableReference.create(rightTable), onCondition, JoinType.INNER)));
        String innerSQL = GaussDBToStringVisitor.asString(innerSelect);

        // Target 2: SELECT {antiJoinCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
        List<GaussDBExpression> antiJoinCols = buildAntiJoinFetchColumns(fetchColumns);
        String antiSQL = buildAntiJoinSQLWithCols(onCondition, antiJoinCols);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.LEFT_JOIN_DECOMPOSITION);
    }

    // ========== Rule 2: Left/Right Symmetry ==========

    private JIRQuerySet generateLeftRightSymmetry() {
        GaussDBExpression onCondition = gen.generateExpression();
        List<GaussDBExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        GaussDBSelect sourceSelect = createBaseSelectWithCols(JoinType.LEFT, onCondition, fetchColumns);
        String sourceSQL = GaussDBToStringVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM R RIGHT JOIN L ON cond
        GaussDBSelect targetSelect = new GaussDBSelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(GaussDBTableReference.create(rightTable)));

        GaussDBJoin rightJoin = new GaussDBJoin(GaussDBTableReference.create(leftTable), onCondition, JoinType.RIGHT);
        targetSelect.setJoinClauses(Collections.singletonList(rightJoin));

        String targetSQL = GaussDBToStringVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.LEFT_RIGHT_SYMMETRY);
    }

    // ========== Rule 3: Semi/Anti Complement ==========

    private JIRQuerySet generateSemiAntiComplement() {
        GaussDBExpression onCondition = gen.generateExpression();
        JoinType joinType = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
        List<GaussDBExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L {type} JOIN R ON cond
        GaussDBSelect sourceSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        String sourceSQL = GaussDBToStringVisitor.asString(sourceSelect);

        // Build EXISTS subquery
        GaussDBSelect existsSubquery = buildExistsSubquery(onCondition);

        // Target 1: Source + WHERE EXISTS
        GaussDBSelect semiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        semiSelect.setWhereClause(new GaussDBExists(existsSubquery, GaussDBConstant.createIntConstant(1)));
        String semiSQL = GaussDBToStringVisitor.asString(semiSelect);

        // Target 2: Source + WHERE NOT EXISTS
        GaussDBSelect antiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        antiSelect.setWhereClause(
                new GaussDBUnaryPrefixOperation(
                        new GaussDBExists(existsSubquery, GaussDBConstant.createIntConstant(1)),
                        UnaryPrefixOperator.NOT));
        String antiSQL = GaussDBToStringVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.SEMI_ANTI_COMPLEMENT);
    }

    // ========== Rule 5: Cross Join Equivalence ==========

    private JIRQuerySet generateCrossJoinEquivalence() {
        List<GaussDBExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
        GaussDBSelect sourceSelect = createBaseSelectWithCols(JoinType.CROSS, null, fetchColumns);
        String sourceSQL = GaussDBToStringVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L INNER JOIN R ON 1
        // MySQL compat: no TRUE/FALSE keyword, use 1 for boolean TRUE
        GaussDBExpression onTrue = GaussDBConstant.createIntConstant(1);
        GaussDBSelect targetSelect = createBaseSelectWithCols(JoinType.INNER, onTrue, fetchColumns);
        String targetSQL = GaussDBToStringVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.CROSS_JOIN_EQUIVALENCE);
    }

    // ========== Rule 6: Natural Join Explication ==========

    private JIRQuerySet generateNaturalJoinExplication() {
        // Find common column names
        List<GaussDBColumn> leftCols = leftTable.getColumns();
        List<GaussDBColumn> rightCols = rightTable.getColumns();

        List<GaussDBExpression> equalities = new ArrayList<>();
        for (GaussDBColumn lc : leftCols) {
            for (GaussDBColumn rc : rightCols) {
                if (lc.getName().equals(rc.getName())) {
                    GaussDBExpression eq = new GaussDBBinaryComparisonOperation(
                            GaussDBColumnReference.create(lc, null), GaussDBColumnReference.create(rc, null),
                            BinaryComparisonOperator.EQUALS);
                    equalities.add(eq);
                    break;
                }
            }
        }

        if (equalities.isEmpty()) {
            return null; // No common columns, skip
        }

        // Build AND chain
        GaussDBExpression explicitOn = equalities.get(0);
        for (int i = 1; i < equalities.size(); i++) {
            explicitOn = new GaussDBBinaryLogicalOperation(explicitOn, equalities.get(i),
                    GaussDBBinaryLogicalOperator.AND);
        }

        List<GaussDBExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: NATURAL JOIN via AST (JoinType.NATURAL renders as "NATURAL JOIN")
        GaussDBSelect sourceSelect = createBaseSelectWithCols(JoinType.NATURAL, null, fetchColumns);
        String sourceSQL = GaussDBToStringVisitor.asString(sourceSelect);

        // Target: INNER JOIN with explicit equality conditions
        GaussDBSelect targetSelect = createBaseSelectWithCols(JoinType.INNER, explicitOn, fetchColumns);
        String targetSQL = GaussDBToStringVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.NATURAL_JOIN_EXPLICATION);
    }

    // ========== Helper Methods ==========

    /**
     * Pick ONE random column from either table for Rule 1 fetch columns.
     * Single-column ensures NULL substitution is precisely verified by getString(1).
     */
    private List<GaussDBExpression> generateRandomFetchColumns() {
        List<GaussDBColumn> allCols = new ArrayList<>();
        allCols.addAll(leftTable.getColumns());
        allCols.addAll(rightTable.getColumns());
        return Collections.singletonList(GaussDBColumnReference.create(Randomly.fromList(allCols), null));
    }

    /**
     * Build anti-join fetch columns: replace right table columns with NULL constants.
     * Aligns with original GeneralJIROracle anti-join NULL substitution logic.
     */
    private List<GaussDBExpression> buildAntiJoinFetchColumns(List<GaussDBExpression> fetchColumns) {
        List<GaussDBExpression> result = new ArrayList<>();
        for (GaussDBExpression expr : fetchColumns) {
            if (expr instanceof GaussDBColumnReference) {
                GaussDBColumn col = ((GaussDBColumnReference) expr).getColumn();
                if (col.getTable().getName().equals(rightTable.getName())) {
                    result.add(GaussDBConstant.createNullConstant());
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
    private String buildAntiJoinSQLWithCols(GaussDBExpression onCondition, List<GaussDBExpression> antiJoinCols) {
        GaussDBSelect antiSelect = new GaussDBSelect();
        antiSelect.setFetchColumns(antiJoinCols);
        antiSelect.setFromList(Collections.singletonList(GaussDBTableReference.create(leftTable)));

        GaussDBSelect existsSubquery = buildExistsSubquery(onCondition);
        antiSelect.setWhereClause(
                new GaussDBUnaryPrefixOperation(
                        new GaussDBExists(existsSubquery, GaussDBConstant.createIntConstant(1)),
                        UnaryPrefixOperator.NOT));

        return GaussDBToStringVisitor.asString(antiSelect);
    }

    /**
     * Create a base SELECT with external fetch columns, FROM left table, and a single JOIN to right table.
     * Used by Rules 2-6 where fetch columns must be shared across source/target queries.
     */
    private GaussDBSelect createBaseSelectWithCols(JoinType joinType, GaussDBExpression onCondition,
            List<GaussDBExpression> fetchColumns) {
        GaussDBSelect select = new GaussDBSelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(GaussDBTableReference.create(leftTable)));

        if (joinType == JoinType.CROSS) {
            GaussDBJoin crossJoin = new GaussDBJoin(GaussDBTableReference.create(rightTable), null, JoinType.CROSS);
            select.setJoinClauses(Collections.singletonList(crossJoin));
        } else if (joinType == JoinType.NATURAL) {
            GaussDBJoin naturalJoin = new GaussDBJoin(GaussDBTableReference.create(rightTable), null, JoinType.NATURAL);
            select.setJoinClauses(Collections.singletonList(naturalJoin));
        } else {
            GaussDBJoin join = new GaussDBJoin(GaussDBTableReference.create(rightTable), onCondition, joinType);
            select.setJoinClauses(Collections.singletonList(join));
        }

        return select;
    }

    private List<GaussDBExpression> getLeftTableFetchColumns() {
        List<GaussDBColumn> cols = leftTable.getColumns();
        if (cols.isEmpty()) {
            throw new IgnoreMeException();
        }
        return Collections.singletonList(GaussDBColumnReference.create(Randomly.fromList(cols), null));
    }

    private GaussDBSelect buildExistsSubquery(GaussDBExpression onCondition) {
        GaussDBSelect existsSub = new GaussDBSelect();
        existsSub.setFetchColumns(Collections.singletonList(GaussDBConstant.createIntConstant(1)));
        existsSub.setFromList(Collections.singletonList(GaussDBTableReference.create(rightTable)));
        existsSub.setWhereClause(onCondition);
        return existsSub;
    }
}