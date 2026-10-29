package sqlancer.gaussdbm.oracle;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.oracle.PivotedQuerySynthesisBase;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBRowValue;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTables;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBMPivotedQuerySynthesisOracle
        extends PivotedQuerySynthesisBase<GaussDBMGlobalState, GaussDBRowValue, GaussDBExpression, SQLConnection> {

    private List<GaussDBExpression> fetchColumns;
    private List<GaussDBColumn> columns;

    public GaussDBMPivotedQuerySynthesisOracle(GaussDBMGlobalState globalState) throws SQLException {
        super(globalState);
        GaussDBMErrors.addExpressionErrors(errors);
        errors.add("in 'order clause'");
    }

    @Override
    public Query<SQLConnection> getRectifiedQuery() throws SQLException {
        GaussDBTables randomFromTables = globalState.getSchema().getRandomTableNonEmptyTables();
        List<GaussDBTable> tables = randomFromTables.getTables();

        GaussDBSelect selectStatement = new GaussDBSelect();
        selectStatement.setSelectType(Randomly.fromOptions(GaussDBSelect.SelectType.values()));
        columns = randomFromTables.getColumns();
        pivotRow = randomFromTables.getRandomRowValue(globalState.getConnection());

        selectStatement.setFromList(tables.stream().map(GaussDBTableReference::create).collect(Collectors.toList()));

        fetchColumns = columns.stream().map(c -> GaussDBColumnReference.create(c, null)).collect(Collectors.toList());
        selectStatement.setFetchColumns(fetchColumns);
        GaussDBExpression whereClause = generateRectifiedExpression(columns, pivotRow);
        selectStatement.setWhereClause(whereClause);
        List<GaussDBExpression> groupByClause = generateGroupByClause(columns, pivotRow);
        selectStatement.setGroupByExpressions(groupByClause);
        GaussDBConstant limitClause = generateLimit();
        selectStatement.setLimitClause(limitClause);
        if (limitClause != null) {
            selectStatement.setOffsetClause(generateOffset());
        }
        List<GaussDBExpression> orderBy = new GaussDBMExpressionGenerator(globalState).setColumns(columns)
                .generateOrderBys();
        selectStatement.setOrderByClauses(orderBy);

        return new SQLQueryAdapter(GaussDBToStringVisitor.asString(selectStatement), errors);
    }

    private List<GaussDBExpression> generateGroupByClause(List<GaussDBColumn> cols, GaussDBRowValue rw) {
        if (Randomly.getBoolean()) {
            return cols.stream().map(c -> GaussDBColumnReference.create(c, rw.getValues().get(c)))
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private GaussDBConstant generateLimit() {
        if (Randomly.getBoolean()) {
            return GaussDBConstant.createIntConstant(Integer.MAX_VALUE);
        } else {
            return null;
        }
    }

    private GaussDBExpression generateOffset() {
        if (Randomly.getBoolean()) {
            return GaussDBConstant.createIntConstant(0);
        } else {
            return null;
        }
    }

    private GaussDBExpression generateRectifiedExpression(List<GaussDBColumn> cols, GaussDBRowValue rw) {
        GaussDBExpression expression = new GaussDBMExpressionGenerator(globalState).setRowVal(rw).setColumns(cols)
                .generateExpression();
        GaussDBConstant expectedValue = expression.getExpectedValue();
        GaussDBExpression result;
        if (expectedValue == null) {
            // The generator could not predict this expression; decline the round instead of
            // crashing with a NullPointerException.
            throw new IgnoreMeException();
        }
        if (expectedValue.isNull()) {
            result = new GaussDBUnaryPostfixOperation(expression, UnaryPostfixOperator.IS_NULL);
        } else if (expectedValue.asBooleanNotNull()) {
            result = expression;
        } else {
            result = new GaussDBUnaryPrefixOperation(expression, UnaryPrefixOperator.NOT);
        }
        rectifiedPredicates.add(result);
        return result;
    }

    @Override
    protected Query<SQLConnection> getContainmentCheckQuery(Query<?> query) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM (");
        sb.append(query.getUnterminatedQueryString());
        sb.append(") AS result WHERE ");
        int i = 0;
        for (GaussDBColumn c : columns) {
            if (i++ != 0) {
                sb.append(" AND ");
            }
            sb.append("result.ref");
            sb.append(i - 1);
            if (pivotRow.getValues().get(c).isNull()) {
                sb.append(" IS NULL");
            } else {
                sb.append(" = ");
                sb.append(pivotRow.getValues().get(c).getTextRepresentation());
            }
        }

        return new SQLQueryAdapter(sb.toString(), query.getExpectedErrors());
    }

    @Override
    protected String getExpectedValues(GaussDBExpression expr) {
        return GaussDBToStringVisitor.asExpectedValues(expr);
    }
}
