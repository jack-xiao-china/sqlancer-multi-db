package sqlancer.gaussdba.ast;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Join;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;

public class GaussDBAJoin implements GaussDBAExpression, Join<GaussDBAExpression, GaussDBATable, GaussDBAColumn> {

    public enum GaussDBAJoinType {
        INNER, LEFT, RIGHT, CROSS, FULL, NATURAL;

        public static GaussDBAJoinType getRandom() {
            return Randomly.fromOptions(values());
        }
    }

    private final GaussDBAExpression tableReference;
    private GaussDBAExpression onCondition;
    private GaussDBAJoinType joinType;

    public GaussDBAJoin(GaussDBAExpression tableReference, GaussDBAExpression onCondition,
            GaussDBAJoinType joinType) {
        this.tableReference = tableReference;
        this.onCondition = onCondition;
        this.joinType = joinType;
    }

    /**
     * Generate random join clauses for the given tables.
     */
    public static List<GaussDBAJoin> getRandomJoinClauses(List<GaussDBATable> tables, GaussDBAGlobalState state) {
        List<GaussDBAJoin> joinStatements = new ArrayList<>();
        List<GaussDBATable> remainingTables = new ArrayList<>(tables);
        if (remainingTables.size() <= 1) {
            return joinStatements;
        }
        // Remove first table as the base table
        remainingTables.remove(0);

        for (GaussDBATable table : remainingTables) {
            GaussDBAExpression onCondition = new GaussDBABinaryComparisonOperation(
                    new GaussDBAColumnReference(null, null),
                    new GaussDBAColumnReference(null, null),
                    GaussDBABinaryComparisonOperation.GaussDBABinaryComparisonOperator.EQUALS);
            GaussDBAJoinType joinType = GaussDBAJoinType.getRandom();
            GaussDBAJoin join = new GaussDBAJoin(GaussDBATableReference.create(table), onCondition, joinType);
            joinStatements.add(join);
        }
        return joinStatements;
    }

    @Override
    public GaussDBAConstant getExpectedValue() {
        return null;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }

    public GaussDBAExpression getTableReference() {
        return tableReference;
    }

    public GaussDBAExpression getOnCondition() {
        return onCondition;
    }

    public GaussDBAJoinType getJoinType() {
        return joinType;
    }

    @Override
    public void setOnClause(GaussDBAExpression onCondition) {
        this.onCondition = onCondition;
    }

    public void setJoinType(GaussDBAJoinType joinType) {
        this.joinType = joinType;
    }
}