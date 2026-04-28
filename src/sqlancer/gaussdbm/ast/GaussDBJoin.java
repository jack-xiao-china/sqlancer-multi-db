package sqlancer.gaussdbm.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.ast.newast.Join;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBJoin implements GaussDBExpression, Join<GaussDBExpression, GaussDBTable, GaussDBColumn> {

    public enum JoinType {
        NATURAL, INNER, LEFT, RIGHT, CROSS;
    }

    private final GaussDBExpression tableReference;
    private GaussDBExpression onCondition;
    private final JoinType joinType;

    public GaussDBJoin(GaussDBExpression tableReference, GaussDBExpression onCondition, JoinType joinType) {
        this.tableReference = tableReference;
        this.onCondition = onCondition;
        this.joinType = joinType;
    }

    public GaussDBExpression getTableReference() {
        return tableReference;
    }

    public GaussDBExpression getOnCondition() {
        return onCondition;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        return GaussDBConstant.createNullConstant();
    }

    @Override
    public void setOnClause(GaussDBExpression onClause) {
        this.onCondition = onClause;
    }

    public static List<GaussDBJoin> getRandomJoinClauses(List<GaussDBTable> tables, GaussDBMGlobalState globalState) {
        List<GaussDBJoin> joinStatements = new ArrayList<>();
        List<JoinType> options = new ArrayList<>(Arrays.asList(JoinType.values()));
        List<GaussDBColumn> columns = new ArrayList<>();
        if (tables.size() > 1) {
            int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());
            // Natural join is incompatible with other joins
            // because it needs unique column names
            // while other joins will produce duplicate column names
            if (nrJoinClauses > 1) {
                options.remove(JoinType.NATURAL);
            }
            for (int i = 0; i < nrJoinClauses; i++) {
                GaussDBTable table = Randomly.fromList(tables);
                tables.remove(table);
                columns.addAll(table.getColumns());
                GaussDBMExpressionGenerator joinGen = new GaussDBMExpressionGenerator(globalState).setColumns(columns);
                JoinType selectedOption = Randomly.fromList(options);
                GaussDBExpression joinClause = (selectedOption == JoinType.CROSS || selectedOption == JoinType.NATURAL)
                        ? null : joinGen.generateExpression();
                GaussDBExpression tableRef = GaussDBTableReference.create(table);
                joinStatements.add(new GaussDBJoin(tableRef, joinClause, selectedOption));
            }
        }
        return joinStatements;
    }
}
