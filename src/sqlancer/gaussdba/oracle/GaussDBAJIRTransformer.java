package sqlancer.gaussdba.oracle;

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
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation.GaussDBABinaryComparisonOperator;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation.GaussDBABinaryLogicalOperator;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBAExists;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBAJoin.GaussDBAJoinType;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * GaussDB-A (Oracle compatibility mode) JIR transformer implementing all 6 rules.
 * Oracle compat mode supports FULL OUTER JOIN and NATURAL JOIN, so all rules are enabled.
 * Key Oracle differences: no BOOLEAN type (uses NUMBER(1)), empty string = NULL, three-valued logic.
 */
public class GaussDBAJIRTransformer implements JIRTransformer<GaussDBAGlobalState> {

    private GaussDBATable leftTable;
    private GaussDBATable rightTable;
    private GaussDBAExpressionGenerator gen;
    private final ExpectedErrors errors;

    public GaussDBAJIRTransformer() {
        ExpectedErrors errorsBuilder = new ExpectedErrors();
        GaussDBAErrors.addExpressionErrors(errorsBuilder);
        GaussDBAErrors.addFetchErrors(errorsBuilder);
        this.errors = errorsBuilder;
    }

    @Override
    public void initialize(GaussDBAGlobalState state, List<? extends AbstractTable<?, ?, ?>> tables) {
        this.leftTable = (GaussDBATable) tables.get(0);
        this.rightTable = (GaussDBATable) tables.get(1);

        List<GaussDBAColumn> allColumns = new ArrayList<>();
        allColumns.addAll(leftTable.getColumns());
        allColumns.addAll(rightTable.getColumns());
        this.gen = new GaussDBAExpressionGenerator(state).setColumns(allColumns);
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
        GaussDBAExpression onCondition = gen.generateBooleanExpression();
        List<GaussDBAExpression> fetchColumns = generateRandomFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        GaussDBASelect sourceSelect = new GaussDBASelect();
        sourceSelect.setFetchColumns(fetchColumns);
        sourceSelect.setFromList(Collections.singletonList(GaussDBATableReference.create(leftTable)));
        sourceSelect.setJoinClauses(Collections
                .singletonList(new GaussDBAJoin(GaussDBATableReference.create(rightTable), onCondition, GaussDBAJoinType.LEFT)));
        String sourceSQL = GaussDBAToStringVisitor.asString(sourceSelect);

        // Target 1: SELECT {fetchColumns} FROM L INNER JOIN R ON cond
        GaussDBASelect innerSelect = new GaussDBASelect();
        innerSelect.setFetchColumns(fetchColumns);
        innerSelect.setFromList(Collections.singletonList(GaussDBATableReference.create(leftTable)));
        innerSelect.setJoinClauses(Collections
                .singletonList(new GaussDBAJoin(GaussDBATableReference.create(rightTable), onCondition, GaussDBAJoinType.INNER)));
        String innerSQL = GaussDBAToStringVisitor.asString(innerSelect);

        // Target 2: SELECT {antiJoinCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
        List<GaussDBAExpression> antiJoinCols = buildAntiJoinFetchColumns(fetchColumns);
        String antiSQL = buildAntiJoinSQLWithCols(onCondition, antiJoinCols);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.LEFT_JOIN_DECOMPOSITION);
    }

    // ========== Rule 2: Left/Right Symmetry ==========

    private JIRQuerySet generateLeftRightSymmetry() {
        GaussDBAExpression onCondition = gen.generateBooleanExpression();
        List<GaussDBAExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L LEFT JOIN R ON cond
        GaussDBASelect sourceSelect = createBaseSelectWithCols(GaussDBAJoinType.LEFT, onCondition, fetchColumns);
        String sourceSQL = GaussDBAToStringVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM R RIGHT JOIN L ON cond
        GaussDBASelect targetSelect = new GaussDBASelect();
        targetSelect.setFetchColumns(fetchColumns);
        targetSelect.setFromList(Collections.singletonList(GaussDBATableReference.create(rightTable)));

        GaussDBAJoin rightJoin = new GaussDBAJoin(GaussDBATableReference.create(leftTable), onCondition,
                GaussDBAJoinType.RIGHT);
        targetSelect.setJoinClauses(Collections.singletonList(rightJoin));

        String targetSQL = GaussDBAToStringVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.LEFT_RIGHT_SYMMETRY);
    }

    // ========== Rule 3: Semi/Anti Complement ==========

    private JIRQuerySet generateSemiAntiComplement() {
        GaussDBAExpression onCondition = gen.generateBooleanExpression();
        // GaussDB-A (Oracle compat) supports INNER, LEFT, RIGHT, FULL JOINs
        GaussDBAJoinType joinType = Randomly.fromOptions(GaussDBAJoinType.INNER, GaussDBAJoinType.LEFT,
                GaussDBAJoinType.RIGHT, GaussDBAJoinType.FULL);
        List<GaussDBAExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L {type} JOIN R ON cond
        GaussDBASelect sourceSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        String sourceSQL = GaussDBAToStringVisitor.asString(sourceSelect);

        // Build EXISTS subquery
        GaussDBASelect existsSubquery = buildExistsSubquery(onCondition);

        // Target 1: Source + WHERE EXISTS
        GaussDBASelect semiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        semiSelect.setWhereClause(new GaussDBAExists(existsSubquery));
        String semiSQL = GaussDBAToStringVisitor.asString(semiSelect);

        // Target 2: Source + WHERE NOT EXISTS
        GaussDBASelect antiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
        antiSelect.setWhereClause(
                new GaussDBAUnaryPrefixOperation(new GaussDBAExists(existsSubquery), UnaryPrefixOperator.NOT));
        String antiSQL = GaussDBAToStringVisitor.asString(antiSelect);

        return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
                JIRRule.SEMI_ANTI_COMPLEMENT);
    }

    // ========== Rule 4: Full Join Decomposition (GaussDB-A only) ==========

    private JIRQuerySet generateFullJoinDecomposition() {
        GaussDBAExpression onCondition = gen.generateBooleanExpression();
        List<GaussDBAExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L FULL JOIN R ON cond
        GaussDBASelect sourceSelect = createBaseSelectWithCols(GaussDBAJoinType.FULL, onCondition, fetchColumns);
        String sourceSQL = GaussDBAToStringVisitor.asString(sourceSelect);

        // Target 1: INNER JOIN
        GaussDBASelect innerSelect = createBaseSelectWithCols(GaussDBAJoinType.INNER, onCondition, fetchColumns);
        String innerSQL = GaussDBAToStringVisitor.asString(innerSelect);

        // Target 2: Left anti-join (NOT EXISTS)
        String leftAntiSQL = buildAntiJoinSQL(onCondition, fetchColumns);

        // Target 3: Right anti-join (SELECT NULL FROM R WHERE NOT EXISTS (SELECT 1 FROM L WHERE cond))
        String rightAntiSQL = buildReverseAntiJoinSQL(onCondition);

        return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, leftAntiSQL, rightAntiSQL), JIRResultType.UNION_ALL,
                JIRRule.FULL_JOIN_DECOMPOSITION);
    }

    // ========== Rule 5: Cross Join Equivalence ==========

    private JIRQuerySet generateCrossJoinEquivalence() {
        List<GaussDBAExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
        GaussDBASelect sourceSelect = createBaseSelectWithCols(GaussDBAJoinType.CROSS, null, fetchColumns);
        String sourceSQL = GaussDBAToStringVisitor.asString(sourceSelect);

        // Target: SELECT {fetchColumns} FROM L INNER JOIN R ON 1 = 1
        // Oracle compat: no BOOLEAN type, use explicit comparison for TRUE
        GaussDBAExpression onTrue = new GaussDBABinaryComparisonOperation(
                GaussDBAConstant.createNumberConstant(1), GaussDBAConstant.createNumberConstant(1),
                GaussDBABinaryComparisonOperator.EQUALS);
        GaussDBASelect targetSelect = createBaseSelectWithCols(GaussDBAJoinType.INNER, onTrue, fetchColumns);
        String targetSQL = GaussDBAToStringVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.CROSS_JOIN_EQUIVALENCE);
    }

    // ========== Rule 6: Natural Join Explication ==========

    private JIRQuerySet generateNaturalJoinExplication() {
        // Find common column names
        List<GaussDBAColumn> leftCols = leftTable.getColumns();
        List<GaussDBAColumn> rightCols = rightTable.getColumns();

        List<GaussDBAExpression> equalities = new ArrayList<>();
        for (GaussDBAColumn lc : leftCols) {
            for (GaussDBAColumn rc : rightCols) {
                if (lc.getName().equals(rc.getName())) {
                    GaussDBAExpression eq = new GaussDBABinaryComparisonOperation(
                            GaussDBAColumnReference.create(lc, null), GaussDBAColumnReference.create(rc, null),
                            GaussDBABinaryComparisonOperator.EQUALS);
                    equalities.add(eq);
                    break;
                }
            }
        }

        if (equalities.isEmpty()) {
            return null; // No common columns, skip
        }

        // Build AND chain
        GaussDBAExpression explicitOn = equalities.get(0);
        for (int i = 1; i < equalities.size(); i++) {
            explicitOn = new GaussDBABinaryLogicalOperation(explicitOn, equalities.get(i),
                    GaussDBABinaryLogicalOperator.AND);
        }

        List<GaussDBAExpression> fetchColumns = getLeftTableFetchColumns();

        // Source: NATURAL JOIN via AST (GaussDBAJoinType.NATURAL renders as "NATURAL JOIN")
        GaussDBASelect sourceSelect = createBaseSelectWithCols(GaussDBAJoinType.NATURAL, null, fetchColumns);
        String sourceSQL = GaussDBAToStringVisitor.asString(sourceSelect);

        // Target: INNER JOIN with explicit equality conditions
        GaussDBASelect targetSelect = createBaseSelectWithCols(GaussDBAJoinType.INNER, explicitOn, fetchColumns);
        String targetSQL = GaussDBAToStringVisitor.asString(targetSelect);

        return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
                JIRRule.NATURAL_JOIN_EXPLICATION);
    }

    // ========== Helper Methods ==========

    /**
     * Pick ONE random column from either table for Rule 1 fetch columns.
     * Single-column ensures NULL substitution is precisely verified by getString(1).
     */
    private List<GaussDBAExpression> generateRandomFetchColumns() {
        List<GaussDBAColumn> allCols = new ArrayList<>();
        allCols.addAll(leftTable.getColumns());
        allCols.addAll(rightTable.getColumns());
        return Collections.singletonList(GaussDBAColumnReference.create(Randomly.fromList(allCols), null));
    }

    /**
     * Build anti-join fetch columns: replace right table columns with NULL constants.
     * Aligns with original GeneralJIROracle anti-join NULL substitution logic.
     */
    private List<GaussDBAExpression> buildAntiJoinFetchColumns(List<GaussDBAExpression> fetchColumns) {
        List<GaussDBAExpression> result = new ArrayList<>();
        for (GaussDBAExpression expr : fetchColumns) {
            if (expr instanceof GaussDBAColumnReference) {
                GaussDBAColumn col = ((GaussDBAColumnReference) expr).getColumn();
                if (col.getTable().getName().equals(rightTable.getName())) {
                    result.add(GaussDBAConstant.createNullConstant());
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
    private String buildAntiJoinSQLWithCols(GaussDBAExpression onCondition, List<GaussDBAExpression> antiJoinCols) {
        GaussDBASelect antiSelect = new GaussDBASelect();
        antiSelect.setFetchColumns(antiJoinCols);
        antiSelect.setFromList(Collections.singletonList(GaussDBATableReference.create(leftTable)));

        GaussDBASelect existsSubquery = buildExistsSubquery(onCondition);
        antiSelect.setWhereClause(
                new GaussDBAUnaryPrefixOperation(new GaussDBAExists(existsSubquery), UnaryPrefixOperator.NOT));

        return GaussDBAToStringVisitor.asString(antiSelect);
    }

    /**
     * Create a base SELECT with external fetch columns, FROM left table, and a single JOIN to right table.
     * Used by Rules 2-6 where fetch columns must be shared across source/target queries.
     */
    private GaussDBASelect createBaseSelectWithCols(GaussDBAJoinType joinType, GaussDBAExpression onCondition,
            List<GaussDBAExpression> fetchColumns) {
        GaussDBASelect select = new GaussDBASelect();
        select.setFetchColumns(fetchColumns);
        select.setFromList(Collections.singletonList(GaussDBATableReference.create(leftTable)));

        if (joinType == GaussDBAJoinType.CROSS || joinType == GaussDBAJoinType.NATURAL) {
            GaussDBAJoin join = new GaussDBAJoin(GaussDBATableReference.create(rightTable), null, joinType);
            select.setJoinClauses(Collections.singletonList(join));
        } else {
            GaussDBAJoin join = new GaussDBAJoin(GaussDBATableReference.create(rightTable), onCondition, joinType);
            select.setJoinClauses(Collections.singletonList(join));
        }

        return select;
    }

    private List<GaussDBAExpression> getLeftTableFetchColumns() {
        List<GaussDBAColumn> cols = leftTable.getColumns();
        if (cols.isEmpty()) {
            throw new IgnoreMeException();
        }
        return Collections.singletonList(GaussDBAColumnReference.create(Randomly.fromList(cols), null));
    }

    private GaussDBASelect buildExistsSubquery(GaussDBAExpression onCondition) {
        GaussDBASelect existsSub = new GaussDBASelect();
        // Oracle compat: SELECT 1 (NUMBER, not TRUE since no BOOLEAN type)
        existsSub.setFetchColumns(Collections.singletonList(GaussDBAConstant.createNumberConstant(1)));
        existsSub.setFromList(Collections.singletonList(GaussDBATableReference.create(rightTable)));
        existsSub.setWhereClause(onCondition);
        return existsSub;
    }

    private String buildAntiJoinSQL(GaussDBAExpression onCondition, List<GaussDBAExpression> fetchColumns) {
        GaussDBASelect antiSelect = new GaussDBASelect();
        antiSelect.setFetchColumns(fetchColumns);
        antiSelect.setFromList(Collections.singletonList(GaussDBATableReference.create(leftTable)));

        GaussDBASelect existsSubquery = buildExistsSubquery(onCondition);
        antiSelect.setWhereClause(
                new GaussDBAUnaryPrefixOperation(new GaussDBAExists(existsSubquery), UnaryPrefixOperator.NOT));

        return GaussDBAToStringVisitor.asString(antiSelect);
    }

    /**
     * Build reverse anti-join for Rule 4: SELECT NULL FROM R WHERE NOT EXISTS (SELECT 1 FROM L WHERE cond).
     */
    private String buildReverseAntiJoinSQL(GaussDBAExpression onCondition) {
        GaussDBASelect reverseAntiSelect = new GaussDBASelect();
        reverseAntiSelect.setFetchColumns(Collections.singletonList(GaussDBAConstant.createNullConstant()));
        reverseAntiSelect.setFromList(Collections.singletonList(GaussDBATableReference.create(rightTable)));

        // Reverse EXISTS: SELECT 1 FROM L WHERE cond
        GaussDBASelect reverseExistsSub = new GaussDBASelect();
        reverseExistsSub.setFetchColumns(Collections.singletonList(GaussDBAConstant.createNumberConstant(1)));
        reverseExistsSub.setFromList(Collections.singletonList(GaussDBATableReference.create(leftTable)));
        reverseExistsSub.setWhereClause(onCondition);

        GaussDBAUnaryPrefixOperation notExists = new GaussDBAUnaryPrefixOperation(
                new GaussDBAExists(reverseExistsSub), UnaryPrefixOperator.NOT);
        reverseAntiSelect.setWhereClause(notExists);

        return GaussDBAToStringVisitor.asString(reverseAntiSelect);
    }
}