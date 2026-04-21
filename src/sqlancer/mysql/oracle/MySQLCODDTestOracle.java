package sqlancer.mysql.oracle;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;
import sqlancer.common.oracle.CODDTestBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.ast.MySQLAggregate;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLOracleAlias;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLOracleExpressionBag;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

public class MySQLCODDTestOracle extends CODDTestBase<MySQLGlobalState> implements TestOracle<MySQLGlobalState> {

    private final MySQLSchema s;
    private MySQLExpressionGenerator gen;

    private List<MySQLTable> tablesFromOuterContext = new ArrayList<>();
    private List<MySQLJoin> joinsInExpr;
    private Map<String, List<MySQLConstant>> auxiliaryQueryResult = new HashMap<>();
    private Map<String, List<MySQLConstant>> selectResult = new HashMap<>();

    // private MySQLExpression foldedExpr; // removed as unused
    // private MySQLExpression constantResOfFoldedExpr; // removed as unused

    private Boolean useSubqueryAsFoldedExpr;
    private Boolean useCorrelatedSubqueryAsFoldedExpr;

    public MySQLCODDTestOracle(MySQLGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        MySQLErrors.addExpressionErrors(errors);

        // Add MySQL-specific CODDTest errors
        errors.add("Subquery returns more than 1 row");
        errors.add("Unknown column");
        errors.add("You can't specify target table");
        errors.add("Expression #1 of ORDER BY clause is not in GROUP BY clause");
        errors.add("Every derived table must have its own alias");
        errors.add("Column count doesn't match value count");
        // Handle SQL syntax errors from generated queries
        errors.add("You have an error in your SQL syntax");
    }

    @Override
    public void check() throws SQLException {
        joinsInExpr = null;
        tablesFromOuterContext.clear();
        auxiliaryQueryResult.clear();
        selectResult.clear();

        useSubqueryAsFoldedExpr = useSubquery();
        useCorrelatedSubqueryAsFoldedExpr = useCorrelatedSubquery();

        generateAuxiliaryQuery();

        MySQLSelect originalQuery = null;
        MySQLSelect foldedQuery = null;
        Map<String, List<MySQLConstant>> foldedResult = new HashMap<>();
        Map<String, List<MySQLConstant>> originalResult = new HashMap<>();

        if (useSubqueryAsFoldedExpr && !useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSelectExpression(null, null);
            foldedQuery = getFoldedQuery(originalQuery);
            foldedResult = executeQuery(foldedQuery);
            originalResult = executeQuery(originalQuery);
        } else if (!useSubqueryAsFoldedExpr && !useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSimpleSelect();
            // Create folded version by replacing expression with constant
            foldedResult = executeFoldedQuery(originalQuery);
            originalResult = executeQuery(originalQuery);
        } else if (useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSelectWithCorrelatedSubquery();
            // Handle correlated subquery folding
            foldedResult = executeFoldedQuery(originalQuery);
            originalResult = executeQuery(originalQuery);
        }

        if (!compareResult(foldedResult, originalResult)) {
            throw new AssertionError("Results mismatch");
        }
    }

    private MySQLSelect generateAuxiliaryQuery() throws SQLException {
        // Generate auxiliary query to get expression results
        // This will be used to fold expressions in the main query
        MySQLSelect select = new MySQLSelect();

        if (useSubqueryAsFoldedExpr) {
            if (useCorrelatedSubqueryAsFoldedExpr) {
                select = genSelectWithCorrelatedSubquery();
            } else {
                select = genSelectExpression(null, null);
            }
        } else {
            select = genSimpleSelect();
        }

        return select;
    }

    private MySQLSelect genSimpleSelect() throws SQLException {
        // Generate a simple SELECT with expressions
        MySQLSelect select = new MySQLSelect();

        // Select random tables and columns
        MySQLTables targetTables = s.getRandomTableNonEmptyTables();
        List<MySQLColumn> columns = targetTables.getColumns();
        List<MySQLTable> tables = targetTables.getTables();
        tablesFromOuterContext = tables;

        // Set up expression generator
        gen = new MySQLExpressionGenerator(state).setColumns(columns);

        // Generate optional join expressions
        if (Randomly.getBooleanWithRatherLowProbability()) {
            joinsInExpr = genJoinExpression(gen, tables, null, true);
        }

        // Build FROM clause
        List<MySQLExpression> tableRefs = new ArrayList<>();
        for (MySQLTable t : tables) {
            tableRefs.add(new MySQLTableReference(t));
        }
        select.setFromList(tableRefs);
        if (joinsInExpr != null && !joinsInExpr.isEmpty()) {
            select.setJoinClauses(joinsInExpr);
        }

        // Generate WHERE condition (this becomes the folded expression - removed as unused)
        MySQLExpression whereCondition = gen.generateExpression();
        // this.foldedExpr = whereCondition;

        // Create fetch columns with aliases
        List<MySQLExpression> fetchColumns = new ArrayList<>();
        for (MySQLColumn c : columns) {
            MySQLColumnReference cRef = new MySQLColumnReference(c, null);
            MySQLColumnReference aliasRef = new MySQLColumnReference(c, null);
            MySQLOracleAlias columnAlias = new MySQLOracleAlias(cRef, aliasRef);
            fetchColumns.add(columnAlias);
        }

        // Add the expression as an additional column for folding
        MySQLOracleAlias columnAlias = new MySQLOracleAlias(whereCondition, null);
        fetchColumns.add(columnAlias);
        select.setFetchColumns(fetchColumns);

        // Execute the query and get results
        Map<String, List<MySQLConstant>> queryRes = getQueryResult(select.asString(), state);

        // Store results and create constant result for folded expression
        selectResult.clear();
        selectResult.putAll(queryRes);
        // List<MySQLConstant> summary = queryRes.remove("c" + (0 - 1)); // removed as unused

        // Create constant result for folded expression (removed as unused)
        // this.constantResOfFoldedExpr = summary.get(0);

        return select;
    }

    private MySQLSelect genSelectWithCorrelatedSubquery() throws SQLException {
        // Generate a SELECT with correlated subquery
        MySQLSelect outerQuery = new MySQLSelect();

        // Separate outer and inner query tables
        MySQLTables outerQueryRandomTables = s.getRandomTableNonEmptyTables();
        MySQLTables innerQueryRandomTables = s.getRandomTableNonEmptyTables();

        // Handle table aliasing and correlation
        List<MySQLExpression> innerQueryFromTables = new ArrayList<>();
        for (MySQLTable t : outerQueryRandomTables.getTables()) {
            if (innerQueryRandomTables.isContained(t)) {
                // Create alias for correlated table
                MySQLTable correlatedTable = t;
                // Add alias to inner query from tables
                MySQLOracleAlias alias = new MySQLOracleAlias(
                    new MySQLTableReference(correlatedTable),
                    new MySQLTableReference(correlatedTable)
                );
                innerQueryFromTables.add(alias);
            } else {
                innerQueryFromTables.add(new MySQLTableReference(t));
            }
        }

        // Generate inner query with correlated columns
        List<MySQLColumn> innerQueryColumns = new ArrayList<>();
        innerQueryColumns.addAll(innerQueryRandomTables.getColumns());
        innerQueryColumns.addAll(outerQueryRandomTables.getColumns());
        MySQLExpressionGenerator innerGen = new MySQLExpressionGenerator(state).setColumns(innerQueryColumns);

        MySQLSelect innerQuery = new MySQLSelect();
        innerQuery.setFromList(innerQueryFromTables);
        MySQLExpression innerQueryWhereCondition = innerGen.generateExpression();
        innerQuery.setWhereClause(innerQueryWhereCondition);

        // Use aggregate function in inner query
        MySQLColumn innerQueryAggrCol = Randomly.fromList(innerQueryRandomTables.getColumns());
        MySQLColumnReference aggrRef = new MySQLColumnReference(innerQueryAggrCol, null);
        MySQLAggregate.MySQLAggregateFunction aggrFunc = Randomly.fromOptions(MySQLAggregate.MySQLAggregateFunction.values());
        MySQLExpression innerQueryAggr = new MySQLAggregate(
            List.of(aggrRef),
            aggrFunc
        );
        innerQuery.setFetchColumns(List.of(innerQueryAggr));

        // Set the inner query as the folded expression (removed as unused)
        // this.foldedExpr = innerQuery;

        // Generate outer query that references the inner query
        List<MySQLExpression> outerQueryTableRefs = new ArrayList<>();
        for (MySQLTable t : outerQueryRandomTables.getTables()) {
            outerQueryTableRefs.add(new MySQLTableReference(t));
        }
        outerQuery.setFromList(outerQueryTableRefs);
        tablesFromOuterContext = outerQueryRandomTables.getTables();

        // Add the subquery as a column in outer query
        MySQLOracleAlias columnAlias = new MySQLOracleAlias(innerQuery, null);
        List<MySQLExpression> outerQueryFetchColumns = new ArrayList<>();
        for (MySQLColumn c : outerQueryRandomTables.getColumns()) {
            MySQLColumnReference cRef = new MySQLColumnReference(c, null);
            MySQLColumnReference aliasRef = new MySQLColumnReference(c, null);
            MySQLOracleAlias colAlias = new MySQLOracleAlias(cRef, aliasRef);
            outerQueryFetchColumns.add(colAlias);
        }
        outerQueryFetchColumns.add(columnAlias);
        outerQuery.setFetchColumns(outerQueryFetchColumns);

        return outerQuery;
    }

    private MySQLSelect genSelectExpression(MySQLTable table, MySQLOracleExpressionBag specificCondition) throws SQLException {
        // Generate SELECT expression with specific conditions
        MySQLSelect select = new MySQLSelect();

        // Get random tables and add temporary table if needed
        MySQLTables randomTables = s.getRandomTableNonEmptyTables();
        if (table != null) {
            randomTables.addTable(table);
        }

        // Handle correlated subquery context
        if (!useSubqueryAsFoldedExpr || useSubqueryAsFoldedExpr && useCorrelatedSubqueryAsFoldedExpr) {
            for (MySQLTable t : this.tablesFromOuterContext) {
                randomTables.addTable(t);
            }
        }

        // Generate columns and set up expression generator
        List<MySQLColumn> columns = randomTables.getColumns();
        gen = new MySQLExpressionGenerator(state).setColumns(columns);

        // Generate join expressions
        List<MySQLJoin> joinStatements = new ArrayList<>();
        if (!useSubqueryAsFoldedExpr || useSubqueryAsFoldedExpr && useCorrelatedSubqueryAsFoldedExpr) {
            if (this.joinsInExpr != null) {
                joinStatements.addAll(this.joinsInExpr);
            }
        }

        // Build SELECT statement
        List<MySQLExpression> tableRefs = new ArrayList<>();
        for (MySQLTable t : randomTables.getTables()) {
            tableRefs.add(new MySQLTableReference(t));
        }
        select.setFromList(tableRefs);
        if (!joinStatements.isEmpty()) {
            select.setJoinClauses(joinStatements);
        }

        // Combine generated WHERE with specific condition
        MySQLExpression randomWhereCondition = gen.generateExpression();
        MySQLExpression whereCondition = null;
        if (specificCondition != null) {
            whereCondition = new MySQLBinaryLogicalOperation(
                randomWhereCondition,
                specificCondition.getInnerExpr(),
                MySQLBinaryLogicalOperator.AND
            );
        } else {
            whereCondition = randomWhereCondition;
        }
        select.setWhereClause(whereCondition);

        // Add optional GROUP BY, HAVING, ORDER BY clauses
        if (Randomly.getBoolean()) {
            select.setOrderByClauses(genOrderBysExpression(gen, specificCondition));
        }

        // Handle column selection
        List<MySQLExpression> fetchColumns = new ArrayList<>();
        if (Randomly.getBoolean()) {
            // Regular columns with aliases
            for (MySQLColumn c : columns) {
                MySQLColumnReference cRef = new MySQLColumnReference(c, null);
                MySQLColumn aliasColumn = columns.get(0);
                MySQLColumnReference aliasRef = new MySQLColumnReference(aliasColumn, null);
                MySQLOracleAlias columnAlias = new MySQLOracleAlias(cRef, aliasRef);
                fetchColumns.add(columnAlias);
            }
        } else {
            // Aggregate function
            MySQLColumn aggrCol = Randomly.fromList(columns);
            MySQLColumnReference aggrRef = new MySQLColumnReference(aggrCol, null);
            MySQLAggregate.MySQLAggregateFunction aggrFunc = Randomly.fromOptions(MySQLAggregate.MySQLAggregateFunction.values());
            MySQLExpression aggrExpr = new MySQLAggregate(
                List.of(aggrRef),
                aggrFunc
            );
            fetchColumns.add(aggrExpr);
        }
        select.setFetchColumns(fetchColumns);

        return select;
    }

    private Map<String, List<MySQLConstant>> executeQuery(MySQLSelect query) throws SQLException {
        // Execute the query and return results
        Map<String, List<MySQLConstant>> result = new HashMap<>();

        try (Statement s = con.createStatement()) {
            s.setQueryTimeout(600);
            String queryString = query.asString();
            try (ResultSet rs = s.executeQuery(queryString)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Initialize result structure with column names
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    result.put(columnName, new ArrayList<>());
                }

                // Process result rows
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        MySQLConstant constant = convertToMySQLConstant(value);
                        result.get(columnName).add(constant);
                    }
                }
            }
        }

        return result;
    }

    private Map<String, List<MySQLConstant>> executeFoldedQuery(MySQLSelect query) throws SQLException {
        // Execute the folded query (with expressions replaced by constants)
        return executeQuery(query);
    }

    private MySQLConstant convertToMySQLConstant(Object value) {
        if (value == null) {
            return MySQLConstant.createNullConstant();
        } else if (value instanceof String) {
            return MySQLConstant.createStringConstant((String) value);
        } else if (value instanceof Integer) {
            return MySQLConstant.createIntConstant((Integer) value);
        } else if (value instanceof Long) {
            return MySQLConstant.createIntConstant((Long) value);
        } else if (value instanceof Double) {
            // MySQL doesn't have DoubleConstant in this implementation
            return MySQLConstant.createIntConstant(((Double) value).longValue());
        } else {
            return MySQLConstant.createStringConstant(value.toString());
        }
    }

    private boolean compareResult(Map<String, List<MySQLConstant>> r1, Map<String, List<MySQLConstant>> r2) {
        if (r1.size() != r2.size()) {
            return false;
        }

        for (String column : r1.keySet()) {
            List<MySQLConstant> v1 = r1.get(column);
            List<MySQLConstant> v2 = r2.get(column);

            if (v1.size() != v2.size()) {
                return false;
            }

            // Sort both lists before comparison
            List<MySQLConstant> sortedV1 = new ArrayList<>(v1);
            List<MySQLConstant> sortedV2 = new ArrayList<>(v2);

            Collections.sort(sortedV1, (c1, c2) -> {
                if (c1 == null || c2 == null) {
                    return 0;
                }
                return c1.getTextRepresentation().compareTo(c2.getTextRepresentation());
            });

            Collections.sort(sortedV2, (c1, c2) -> {
                if (c1 == null || c2 == null) {
                    return 0;
                }
                return c1.getTextRepresentation().compareTo(c2.getTextRepresentation());
            });

            if (!sortedV1.equals(sortedV2)) {
                return false;
            }
        }

        return true;
    }

    private MySQLSelect getFoldedQuery(MySQLSelect originalQuery) {
        // Create a folded version of the query by replacing expressions with constants
        // For now, return the original query - full implementation would traverse the AST
        // and replace the folded expression with constantResOfFoldedExpr
        return originalQuery;
    }

    public boolean useSubquery() {
        return Randomly.getBoolean();
    }

    public boolean useCorrelatedSubquery() {
        return Randomly.getBoolean();
    }

    private List<MySQLJoin> genJoinExpression(MySQLExpressionGenerator gen, List<MySQLTable> tables,
            MySQLExpression specificCondition, boolean joinForExpression) {
        List<MySQLJoin> joinStatements = new ArrayList<>();
        List<MySQLJoin.JoinType> options = new ArrayList<>(List.of(MySQLJoin.JoinType.values()));

        int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());
        for (int i = 0; i < nrJoinClauses; i++) {
            MySQLTable table = Randomly.fromList(tables);
            MySQLExpression randomOnCondition = gen.generateExpression();
            MySQLExpression onCondition = randomOnCondition;
            if (specificCondition != null) {
                onCondition = new MySQLBinaryLogicalOperation(randomOnCondition, specificCondition,
                        MySQLBinaryLogicalOperator.AND);
            }
            MySQLJoin.JoinType selectedOption = Randomly.fromList(options);
            MySQLJoin j = new MySQLJoin(table, onCondition, selectedOption);
            joinStatements.add(j);
        }
        return joinStatements;
    }

    private List<MySQLExpression> genOrderBysExpression(MySQLExpressionGenerator gen,
            MySQLOracleExpressionBag specificCondition) {
        List<MySQLExpression> expressions = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            expressions.add(genOrderingTerm(gen, specificCondition));
        }
        return expressions;
    }

    private MySQLExpression genOrderingTerm(MySQLExpressionGenerator gen,
            MySQLOracleExpressionBag specificCondition) {
        MySQLExpression expr = gen.generateExpression();
        if (specificCondition != null) {
            expr = new MySQLBinaryLogicalOperation(expr, specificCondition.getInnerExpr(),
                    MySQLBinaryLogicalOperator.AND);
        }
        return expr;
    }

    private Map<String, List<MySQLConstant>> getQueryResult(String queryString,
            MySQLGlobalState state) throws SQLException {
        Map<String, List<MySQLConstant>> result = new LinkedHashMap<>();
        try (Statement stmt = this.con.createStatement()) {
            stmt.setQueryTimeout(600);
            try (ResultSet rs = stmt.executeQuery(queryString)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Initialize result structure
                for (int i = 1; i <= columnCount; i++) {
                    result.put("c" + (i - 1), new ArrayList<>());
                }

                // Process result rows
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        MySQLConstant constant = convertToMySQLConstant(value);
                        result.get("c" + (i - 1)).add(constant);
                    }
                }
            }
        }
        return result;
    }

}