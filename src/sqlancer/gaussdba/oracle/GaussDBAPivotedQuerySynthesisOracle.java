package sqlancer.gaussdba.oracle;

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
import sqlancer.gaussdba.GaussDBAExpectedValueVisitor;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBARowValue;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATables;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation.GaussDBABinaryComparisonOperator;
import sqlancer.gaussdba.ast.GaussDBAColumnValue;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBADataType;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

public class GaussDBAPivotedQuerySynthesisOracle
        extends PivotedQuerySynthesisBase<GaussDBAGlobalState, GaussDBARowValue, GaussDBAExpression, SQLConnection> {

    private List<GaussDBAColumn> fetchColumns;

    public GaussDBAPivotedQuerySynthesisOracle(GaussDBAGlobalState globalState) throws SQLException {
        super(globalState);
        GaussDBAErrors.addExpressionErrors(errors);
    }

    @Override
    public SQLQueryAdapter getRectifiedQuery() throws SQLException {
        GaussDBATables randomFromTables = globalState.getSchema().getRandomTableNonEmptyTables();

        GaussDBASelect selectStatement = new GaussDBASelect();
        selectStatement.setSelectType(Randomly.fromOptions(GaussDBASelect.GaussDBASelectType.values()));
        List<GaussDBAColumn> columns = randomFromTables.getColumns();
        pivotRow = randomFromTables.getRandomRowValue(globalState.getConnection(), globalState);

        fetchColumns = columns;
        selectStatement.setFromList(randomFromTables.getTables().stream().map(t -> GaussDBATableReference.create(t))
                .collect(Collectors.toList()));
        selectStatement.setFetchColumns(fetchColumns.stream()
                .map(c -> new GaussDBAColumnValue(getFetchValueAliasedColumn(c), pivotRow.getValues().get(c)))
                .collect(Collectors.toList()));
        GaussDBAExpression whereClause = generateRectifiedExpression(columns, pivotRow);
        selectStatement.setWhereClause(whereClause);
        List<GaussDBAExpression> groupByClause = generateGroupByClause(columns, pivotRow);
        selectStatement.setGroupByExpressions(groupByClause);
        GaussDBAExpression limitClause = generateLimit();
        selectStatement.setLimitClause(limitClause);
        if (limitClause != null) {
            GaussDBAExpression offsetClause = generateOffset();
            selectStatement.setOffsetClause(offsetClause);
        }
        List<GaussDBAExpression> orderBy = new GaussDBAExpressionGenerator(globalState).setColumns(columns)
                .generateOrderBys();
        selectStatement.setOrderByClauses(orderBy);
        return new SQLQueryAdapter(GaussDBAToStringVisitor.asString(selectStatement));
    }

    private GaussDBAColumn getFetchValueAliasedColumn(GaussDBAColumn c) {
        GaussDBAColumn aliasedColumn = new GaussDBAColumn(c.getName() + " AS " + c.getTable().getName() + c.getName(),
                c.getType());
        aliasedColumn.setTable(c.getTable());
        return aliasedColumn;
    }

    private List<GaussDBAExpression> generateGroupByClause(List<GaussDBAColumn> columns, GaussDBARowValue rw) {
        if (Randomly.getBoolean()) {
            return columns.stream().map(c -> GaussDBAColumnValue.create(c, rw.getValues().get(c)))
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private GaussDBAConstant generateLimit() {
        if (Randomly.getBoolean()) {
            return GaussDBAConstant.createNumberConstant(Integer.MAX_VALUE);
        } else {
            return null;
        }
    }

    private GaussDBAExpression generateOffset() {
        if (Randomly.getBoolean()) {
            return GaussDBAConstant.createNumberConstant(0);
        } else {
            return null;
        }
    }

    private GaussDBAExpression generateRectifiedExpression(List<GaussDBAColumn> columns, GaussDBARowValue rw) {
        GaussDBAExpression expr = new GaussDBAExpressionGenerator(globalState).setColumns(columns).setRowValue(rw)
                .generateExpressionWithExpectedResult(GaussDBADataType.NUMBER);
        GaussDBAExpression result;

        GaussDBAConstant expectedVal = expr.getExpectedValue();
        if (expectedVal == null) {
            // The engine cannot reliably predict this expression's pivot-row value (e.g. it involves
            // division, whose scale/rounding may not match the DB). Building a rectified predicate on
            // an unreliable prediction risks excluding the pivot row (a false positive), so skip.
            throw new IgnoreMeException();
        }
        if (expectedVal.isNull()) {
            // Oracle semantics: empty string is NULL — matched by IS NULL.
            result = GaussDBAUnaryPostfixOperation.create(expr, UnaryPostfixOperator.IS_NULL);
        } else if (expectedVal.isNumber()) {
            // Truthiness rectification, robust to arithmetic precision: GaussDB-A treats non-zero as
            // true. Use "(expr) <> 0" when the pivot value is non-zero and "(expr) = 0" when it is
            // zero — both depend only on the engine agreeing with the DB on zero vs non-zero, not on
            // an exact decimal value, so a rounded/truncated engine result cannot exclude the pivot row.
            // (Do NOT use IS TRUE/IS FALSE: on GaussDB-A their semantics are unstable for non-canonical
            // values — measured 0.0 IS FALSE = false, 0.5 IS TRUE = true.)
            GaussDBAConstant zero = GaussDBAConstant.createNumberConstant(0);
            GaussDBABinaryComparisonOperator cmp = expectedVal.asBigDecimal().signum() != 0
                    ? GaussDBABinaryComparisonOperator.NOT_EQUALS
                    : GaussDBABinaryComparisonOperator.EQUALS;
            result = new GaussDBABinaryComparisonOperation(expr, zero, cmp);
        } else {
            // Non-numeric expected value (string/date): equality with the exact predicted constant.
            result = new GaussDBABinaryComparisonOperation(expr, expectedVal,
                    GaussDBABinaryComparisonOperator.EQUALS);
        }
        rectifiedPredicates.add(result);
        return result;
    }

    @Override
    protected Query<SQLConnection> getContainmentCheckQuery(Query<?> query) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM (");
        sb.append(query.getUnterminatedQueryString());
        sb.append(") as result WHERE ");
        int i = 0;
        for (GaussDBAColumn c : fetchColumns) {
            if (i++ != 0) {
                sb.append(" AND ");
            }
            sb.append("result.");
            sb.append(c.getTable().getName());
            sb.append(c.getName());

            GaussDBAConstant columnValue = pivotRow.getValues().get(c);
            // Oracle语义：空串被视为NULL
            if (columnValue.isNull() || (columnValue.isString() && columnValue.asString().isEmpty())) {
                sb.append(" IS NULL");
            } else {
                sb.append(" = ");
                sb.append(columnValue.getTextRepresentation());
            }
        }
        String resultingQueryString = sb.toString();
        return new SQLQueryAdapter(resultingQueryString, errors);
    }

    @Override
    protected String getExpectedValues(GaussDBAExpression expr) {
        return GaussDBAExpectedValueVisitor.asExpectedValues(expr);
    }
}