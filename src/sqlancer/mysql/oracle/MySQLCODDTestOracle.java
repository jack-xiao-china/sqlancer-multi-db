package sqlancer.mysql.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.IgnoreMeException;
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
import sqlancer.mysql.ast.MySQLOracleAlias;
import sqlancer.mysql.ast.MySQLOracleExpressionBag;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

public class MySQLCODDTestOracle extends CODDTestBase<MySQLGlobalState> implements TestOracle<MySQLGlobalState> {

    private final MySQLSchema s;
    private MySQLExpressionGenerator gen;

    private List<MySQLTable> tablesFromOuterContext = new ArrayList<>();
    private List<MySQLJoin> joinsInExpr;

    private Boolean useSubqueryAsFoldedExpr;
    private Boolean useCorrelatedSubqueryAsFoldedExpr;

    // State for the non-correlated scalar-subquery constant folding (SUBQUERY mode).
    private String foldTableName;
    private String foldCmp;
    private long foldConst;

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

        useSubqueryAsFoldedExpr = useSubquery();
        useCorrelatedSubqueryAsFoldedExpr = useCorrelatedSubquery();

        // Real constant folding across all modes (see rationale in the GaussDB-M oracle): embed a
        // non-correlated scalar-subquery predicate "(k <cmp> (SELECT COUNT(*) FROM t2))" via a
        // mutable bag, run it, then fold the subquery to its constant and run again — a strict
        // equivalence. The former EXPRESSION/CORRELATED branches re-ran the same query twice (a
        // no-op that could only surface query non-determinism as a spurious mismatch), and
        // getFoldedQuery() returned the original query unchanged, so they were removed.
        MySQLOracleExpressionBag foldBag = buildFoldableCondition();
        if (foldBag == null) {
            throw new IgnoreMeException();
        }
        MySQLSelect originalQuery;
        if (useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSelectWithCorrelatedSubquery();
            injectFoldCondition(originalQuery, foldBag);
        } else if (useSubqueryAsFoldedExpr) {
            originalQuery = genSelectExpression(null, foldBag);
        } else {
            originalQuery = genSimpleSelect();
            injectFoldCondition(originalQuery, foldBag);
        }
        String originalSql = originalQuery.asString();
        if (containsNondeterministicCall(originalSql)) {
            // Non-deterministic projections (CURTIME()/NOW()/RAND()/...) legitimately differ
            // between the two executions; the folding comparison is meaningless for such rounds.
            throw new IgnoreMeException();
        }
        List<List<String>> originalResult;
        List<List<String>> foldedResult;
        String foldedSql;
        try {
            originalResult = executeQuery(originalQuery);
            long foldedValue = evalCount(foldTableName);
            foldBag.updateInnerExpr(new MySQLText("(" + foldConst + " " + foldCmp + " " + foldedValue + ")"));
            foldedSql = originalQuery.asString();
            foldedResult = executeQuery(originalQuery);
        } catch (SQLException e) {
            if (isBenignExpectedError(e)) {
                // The folded query would fail identically, so skip the round instead of
                // aborting the run.
                throw new IgnoreMeException();
            }
            throw e;
        }
        if (!compareResult(foldedResult, originalResult)) {
            throw new AssertionError("CODDTEST folding mismatch\n-- original: " + originalSql
                    + "\n-- folded:   " + foldedSql);
        }
    }

    /** AND the mutable fold predicate (a bag) into a generated query's WHERE clause. */
    private void injectFoldCondition(MySQLSelect query, MySQLOracleExpressionBag foldBag) {
        MySQLExpression where = query.getWhereClause();
        query.setWhereClause(where == null ? foldBag
                : new MySQLBinaryLogicalOperation(where, foldBag, MySQLBinaryLogicalOperator.AND));
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
        // Subquery-local tables: list tables that appear only in the inner set in the inner FROM.
        // Without them, references to their columns dangle ("Unknown column" / missing FROM).
        for (MySQLTable t : innerQueryRandomTables.getTables()) {
            if (!outerQueryRandomTables.isContained(t)) {
                innerQueryFromTables.add(new MySQLTableReference(t));
            }
        }
        // Tables present in both sets are listed in the inner FROM (self-aliased) so their inner
        // references bind locally; references to outer-only tables remain true correlated
        // references resolved against the outer query (upstream SQLite3 CODDTest construction).
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
            // Inject the bag itself (not a snapshot of its inner expr) so a later updateInnerExpr()
            // that folds the scalar subquery to a constant is reflected on re-render.
            whereCondition = new MySQLBinaryLogicalOperation(
                randomWhereCondition,
                specificCondition,
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

    private List<List<String>> executeQuery(MySQLSelect query) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        String sql = query.asString();
        try (Statement st = con.createStatement()) {
            st.setQueryTimeout(600);
            try (ResultSet rs = st.executeQuery(sql)) {
                int columnCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    List<String> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getString(i)); // JDBC getString returns null for SQL NULL
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new SQLException("CODDTEST query failed: " + sql, e);
        }
        return rows;
    }

    /**
     * Compares two result sets as ROW multisets (order-insensitive, duplicates preserved). The
     * earlier per-column-label grouping was fragile: duplicate labels from multi-table queries
     * (e.g. two "c0") collided in the map, and comparing each column's values independently loses
     * the row association — both of which can make identical result sets compare unequal.
     */
    private boolean compareResult(List<List<String>> r1, List<List<String>> r2) {
        if (r1.size() != r2.size()) {
            return false;
        }
        List<String> k1 = new ArrayList<>(r1.size());
        for (List<String> row : r1) {
            k1.add(rowKey(row));
        }
        List<String> k2 = new ArrayList<>(r2.size());
        for (List<String> row : r2) {
            k2.add(rowKey(row));
        }
        Collections.sort(k1);
        Collections.sort(k2);
        return k1.equals(k2);
    }

    private static String rowKey(List<String> row) {
        // Length-prefixed, type-tagged encoding: NULL (tag N) never collides with the string "null",
        // and cell values are self-delimiting so "a"+"b" cannot collide with "ab".
        StringBuilder sb = new StringBuilder();
        for (String c : row) {
            if (c == null) {
                sb.append("N;");
            } else {
                sb.append('S').append(c.length()).append(';').append(c);
            }
        }
        return sb.toString();
    }

    private MySQLOracleExpressionBag buildFoldableCondition() {
        MySQLTables subTables = s.getRandomTableNonEmptyTables();
        if (subTables.getTables().isEmpty()) {
            return null;
        }
        foldTableName = subTables.getTables().get(0).getName();
        foldConst = Randomly.getNotCachedInteger(0, 6);
        foldCmp = Randomly.fromOptions("<=", "<", ">=", ">", "=", "<>");
        String cond = "(" + foldConst + " " + foldCmp + " (SELECT COUNT(*) FROM " + foldTableName + "))";
        return new MySQLOracleExpressionBag(new MySQLText(cond));
    }

    private long evalCount(String tableName) throws SQLException {
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static boolean containsNondeterministicCall(String sql) {
        // The visitor renders function names canonically (upper case), so a plain contains() is
        // sufficient; SLEEP also perturbs run-to-run timing semantics.
        for (String fn : new String[] { "CURTIME(", "CURDATE(", "NOW(", "SYSDATE(", "RAND(", "UUID(",
                "UUID_SHORT(", "UNIX_TIMESTAMP(", "CURRENT_TIMESTAMP", "SLEEP(" }) {
            if (sql.contains(fn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Known-benign execution errors, skipped by retrying the round (the folded query fails
     * identically, so the folding equivalence is trivially preserved):
     * - JSON functions over non-JSON or invalid-JSON arguments: MySQL validates JSON content at
     *   runtime, and the expression generator is not JSON-type-aware.
     * - Out-of-domain generated constants (garbage temporal/numeric strings) make MySQL raise
     *   conversion errors — the same message family the INSERT/UPDATE error list tolerates.
     */
    private static boolean isBenignExpectedError(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            if (msg.contains("Invalid JSON text") || msg.contains("Invalid JSON path")
                    || msg.contains("Invalid data type for JSON")
                    || msg.contains("Cannot create a JSON value")
                    || msg.contains("JSON documents may not contain NULL member names")
                    || msg.contains("Incorrect DATE value") || msg.contains("Incorrect TIME value")
                    || msg.contains("Incorrect DATETIME value") || msg.contains("Incorrect TIMESTAMP value")
                    || msg.contains("Incorrect integer value") || msg.contains("Incorrect decimal value")
                    || msg.contains("Incorrect double value") || msg.contains("Incorrect string value")
                    || msg.contains("Data truncated") || msg.contains("Truncated incorrect")
                    || msg.contains("out of range") || msg.contains("Out of range value")
                    // Bitwise operators require equal-width BIT operands; generated bit literals
                    // have random widths.
                    || msg.contains("must be of equal length")
                    // Binary-to-utf8 conversion of generated binary strings can fail.
                    || msg.contains("Cannot convert string")) {
                return true;
            }
        }
        return false;
    }

    public boolean useSubquery() {
        CODDTestModel model = state.getDbmsSpecificOptions().coddTestModel;
        if (model.isRandom()) {
            return Randomly.getBoolean();
        } else if (model.isExpression()) {
            return false;
        } else if (model.isSubquery()) {
            return true;
        } else {
            return Randomly.getBoolean();
        }
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
            // Remove the picked table so it is provided by the JOIN clause only (upstream SQLite3
            // construction); leaving it in the FROM list as well duplicates the table name.
            tables.remove(table);
            MySQLExpression randomOnCondition = gen.generateExpression();
            MySQLExpression onCondition = randomOnCondition;
            if (specificCondition != null) {
                onCondition = new MySQLBinaryLogicalOperation(randomOnCondition, specificCondition,
                        MySQLBinaryLogicalOperator.AND);
            }
            MySQLJoin.JoinType selectedOption = Randomly.fromList(options);
            if (selectedOption == MySQLJoin.JoinType.NATURAL) {
                // NATURAL joins do not have an ON clause (upstream behavior); rendering
                // "NATURAL JOIN t ON ..." is a syntax error.
                onCondition = null;
            }
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

}