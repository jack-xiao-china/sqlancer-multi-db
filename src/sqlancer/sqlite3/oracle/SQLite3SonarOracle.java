package sqlancer.sqlite3.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.SQLite3Visitor;
import sqlancer.sqlite3.ast.SQLite3Aggregate;
import sqlancer.sqlite3.ast.SQLite3Aggregate.SQLite3AggregateFunction;
import sqlancer.sqlite3.ast.SQLite3Expression;
import sqlancer.sqlite3.ast.SQLite3Expression.BinaryComparisonOperation;
import sqlancer.sqlite3.ast.SQLite3Expression.BinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.sqlite3.ast.SQLite3Expression.Join;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3ColumnName;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3PostfixText;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3TableReference;
import sqlancer.sqlite3.ast.SQLite3Expression.Sqlite3BinaryOperation;
import sqlancer.sqlite3.ast.SQLite3Expression.Sqlite3BinaryOperation.BinaryOperator;
import sqlancer.sqlite3.ast.SQLite3Select;
import sqlancer.sqlite3.ast.SQLite3Expression.SQLite3Text;
import sqlancer.sqlite3.gen.SQLite3ExpressionGenerator;
import sqlancer.sqlite3.schema.SQLite3Schema;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Tables;

/**
 * SQLite3 SONAR Oracle implementation.
 *
 * SONAR (Select Optimization N-gram Analysis Runtime) is a testing technique that compares the results of optimized
 * queries (with WHERE clause filtering) against unoptimized queries (using flag-based filtering in outer query). This
 * helps detect optimization bugs where the database optimizer incorrectly transforms queries.
 *
 * The oracle generates: 1. Optimized query: SELECT ... FROM ... WHERE condition 2. Unoptimized query: SELECT ... FROM
 * (SELECT ..., condition IS TRUE AS flag FROM ...) WHERE flag=1
 *
 * If the result sets differ, a bug is detected.
 */
public class SQLite3SonarOracle extends NoRECBase<SQLite3GlobalState> implements TestOracle<SQLite3GlobalState> {

    private final SQLite3Schema s;
    private SQLite3ExpressionGenerator gen;
    private boolean useAggregate = false;

    public SQLite3SonarOracle(SQLite3GlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        SQLite3Errors.addExpectedExpressionErrors(errors);
        SQLite3Errors.addMatchQueryErrors(errors);
        SQLite3Errors.addQueryErrors(errors);
        errors.add("misuse of aggregate");
        errors.add("misuse of window function");
        errors.add("second argument to nth_value must be a positive integer");
        errors.add("no such table");
        errors.add("no query solution");
        errors.add("unable to use function MATCH in the requested context");
    }

    @Override
    public void check() throws SQLException {
        SQLite3Tables randomTables = s.getRandomTableNonEmptyTables();
        List<SQLite3Column> columns = randomTables.getColumns();
        gen = new SQLite3ExpressionGenerator(state).setColumns(columns);
        SQLite3Expression randomWhereCondition = null;
        List<SQLite3Table> tables = randomTables.getTables();
        List<Join> joinStatements = gen.getRandomJoinClauses(tables);
        List<SQLite3Expression> tableRefs = tables.stream().map(t -> new SQLite3TableReference(t))
                .collect(Collectors.toList());
        List<SQLite3Expression> groupByColumn = Arrays
                .asList(new SQLite3ColumnName(SQLite3Column.createDummy(columns.get(0).getName()), null));

        boolean useColumnExp = Randomly.getBoolean();
        List<SQLite3Expression> fetchColumns = new ArrayList<>();
        SQLite3PostfixText asText = null;

        if (useColumnExp) {
            SQLite3Expression exp = null;
            useAggregate = Randomly.getBoolean();
            if (useAggregate) {
                exp = new SQLite3Aggregate(Arrays.asList(gen.getRandomExpression(2)),
                        Randomly.fromOptions(SQLite3AggregateFunction.values()));
            } else {
                exp = gen.generateFetchColumnExp(columns.get(0));
            }

            asText = new SQLite3PostfixText(exp, " AS f1", null);
            fetchColumns.add(asText);
            randomWhereCondition = gen.generateWhereColumnExpression(new SQLite3Text("f1", null));
        } else {
            this.useAggregate = false;
            fetchColumns = generateFetchColumns(columns);
            randomWhereCondition = gen.generateExpression();
        }

        SQLite3Select select = new SQLite3Select();
        select.setFetchColumns(fetchColumns);
        select.setFromList(tableRefs);
        select.setJoinClauses(joinStatements);

        optimizedQueryString = getOptimizedQuery(select, randomWhereCondition, groupByColumn);
        unoptimizedQueryString = getUnoptimizedQuery(select, randomWhereCondition, useColumnExp, asText);

        List<String> optimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(optimizedQueryString, errors,
                state);
        List<String> unoptimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(unoptimizedQueryString,
                errors, state);

        ComparatorHelper.assumeResultSetsAreEqual(optimizedResultSet, unoptimizedResultSet, optimizedQueryString,
                Arrays.asList(unoptimizedQueryString), state);
    }

    List<SQLite3Expression> generateFetchColumns(List<SQLite3Column> targetColumns) {
        List<SQLite3Expression> columns = new ArrayList<>();
        if (Randomly.getBoolean()) {
            columns.add(new SQLite3ColumnName(SQLite3Column.createDummy("*"), null));
        } else {
            columns = Randomly.nonEmptySubset(targetColumns).stream().map(c -> new SQLite3ColumnName(c, null))
                    .collect(Collectors.toList());
        }
        return columns;
    }

    @Override
    public String getLastQueryString() {
        return optimizedQueryString;
    }

    private String getUnoptimizedQuery(SQLite3Select select, SQLite3Expression randomWhereCondition,
            Boolean useColumnExp, SQLite3PostfixText asText) throws SQLException {

        if (!useColumnExp) {
            List<SQLite3Expression> clonedFetchColumns = select.getFetchColumns().stream()
                    .map(c -> new SQLite3ColumnName(((SQLite3ColumnName) c).getColumn(), null))
                    .collect(Collectors.toList());
            String predicateColumn = SQLite3Visitor.asString(randomWhereCondition);
            String flagName = "(" + predicateColumn + ")" + " IS TRUE AS flag";
            clonedFetchColumns.add(new SQLite3ColumnName(SQLite3Column.createDummy(flagName), null));

            SQLite3Expression whereCondition = new SQLite3Text("flag=1", null);

            SQLite3Select subSelect = new SQLite3Select(select);
            subSelect.setFetchColumns(clonedFetchColumns);
            subSelect.setWhereClause(whereCondition);
            List<SQLite3Expression> fromRef = new ArrayList<>();
            fromRef.add(subSelect);

            SQLite3Select unoptimizedSelect = new SQLite3Select();
            unoptimizedSelect.setFetchColumns(select.getFetchColumns());
            unoptimizedSelect.setFromList(fromRef);
            unoptimizedQueryString = SQLite3Visitor.asString(unoptimizedSelect);
        } else {
            List<SQLite3Expression> fetchColumns = new ArrayList<>();
            fetchColumns.add(select.getFetchColumns().get(0));

            if (randomWhereCondition instanceof SQLite3Text) {
                String flagName = "("
                        + SQLite3Visitor
                                .asString(((SQLite3PostfixText) select.getFetchColumns().get(0)).getExpression())
                        + ")" + " IS TRUE AS flag";
                fetchColumns.add(new SQLite3ColumnName(SQLite3Column.createDummy(flagName), null));
            } else {
                SQLite3Expression right = null;
                if (randomWhereCondition instanceof Sqlite3BinaryOperation) {
                    right = ((Sqlite3BinaryOperation) randomWhereCondition).getRight();
                    BinaryOperator op = ((Sqlite3BinaryOperation) randomWhereCondition).getOperator();
                    Sqlite3BinaryOperation exp = new Sqlite3BinaryOperation(
                            ((SQLite3PostfixText) select.getFetchColumns().get(0)).getExpression(), right, op);
                    String flagName = "(" + SQLite3Visitor.asString(exp) + ")" + " IS TRUE AS flag";
                    fetchColumns.add(new SQLite3Text(flagName, null));
                } else if (randomWhereCondition instanceof BinaryComparisonOperation) {
                    right = ((BinaryComparisonOperation) randomWhereCondition).getRight();
                    BinaryComparisonOperator op = ((BinaryComparisonOperation) randomWhereCondition).getOperator();
                    BinaryComparisonOperation exp = new BinaryComparisonOperation(
                            ((SQLite3PostfixText) select.getFetchColumns().get(0)).getExpression(), right, op);
                    String flagName = "(" + SQLite3Visitor.asString(exp) + ")" + " IS TRUE AS flag";
                    fetchColumns.add(new SQLite3Text(flagName, null));
                }
            }

            SQLite3Expression whereCondition = new SQLite3Text("flag=1", null);

            SQLite3Select subSelect = new SQLite3Select(select);
            subSelect.setFetchColumns(fetchColumns);

            if (useAggregate) {
                subSelect.setHavingClause(whereCondition);
            } else {
                subSelect.setWhereClause(whereCondition);
            }

            List<SQLite3Expression> fromRef = new ArrayList<>();
            fromRef.add(subSelect);

            SQLite3Select unoptimizedSelect = new SQLite3Select();
            unoptimizedSelect.setFetchColumns(Arrays.asList(new SQLite3Text("f1", null)));
            unoptimizedSelect.setFromList(fromRef);
            unoptimizedQueryString = SQLite3Visitor.asString(unoptimizedSelect);
        }

        return unoptimizedQueryString;
    }

    private String getOptimizedQuery(SQLite3Select select, SQLite3Expression randomWhereCondition,
            List<SQLite3Expression> groupByColumn) throws SQLException {

        if (useAggregate) {
            select.setHavingClause(randomWhereCondition);
            select.setGroupByClause(groupByColumn);
        } else {
            select.setWhereClause(randomWhereCondition);
        }
        optimizedQueryString = SQLite3Visitor.asString(select);
        return optimizedQueryString;
    }
}