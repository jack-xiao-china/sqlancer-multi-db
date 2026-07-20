package sqlancer.gaussdba.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.CODDTestBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATables;
import sqlancer.gaussdba.ast.GaussDBAAggregate;
import sqlancer.gaussdba.ast.GaussDBAAggregate.GaussDBAAggregateFunction;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation.GaussDBABinaryLogicalOperator;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBAOracleAlias;
import sqlancer.gaussdba.ast.GaussDBAOracleExpressionBag;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAText;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * CODDTEST Oracle for GaussDB-A (Oracle compatibility mode).
 *
 * Implements the same 3-mode algorithm as GaussDB-M/MySQL:
 * - EXPRESSION: genSimpleSelect / genSelectExpression
 * - SUBQUERY: genSelectExpression with subquery folding
 * - CORRELATED_SUBQUERY: genSelectWithCorrelatedSubquery
 *
 * Oracle semantics adaptations:
 * - No BOOLEAN type: uses NUMBER(1) to represent boolean (1=true, 0=false)
 * - Empty string = NULL: VARCHAR2 '' is treated as NULL
 * - Error messages: ORA-xxxxx style Oracle errors
 */
public class GaussDBACODDTestOracle extends CODDTestBase<GaussDBAGlobalState>
        implements TestOracle<GaussDBAGlobalState> {

    private final GaussDBASchema s;
    private GaussDBAExpressionGenerator gen;

    private List<GaussDBATable> tablesFromOuterContext = new ArrayList<>();
    private List<GaussDBAJoin> joinsInExpr;

    private Boolean useSubqueryAsFoldedExpr;
    private Boolean useCorrelatedSubqueryAsFoldedExpr;

    // State for the non-correlated scalar-subquery constant folding (SUBQUERY mode).
    private String foldTableName;
    private String foldCmp;
    private long foldConst;

    public GaussDBACODDTestOracle(GaussDBAGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        GaussDBAErrors.addExpressionErrors(errors);
        GaussDBAErrors.addFetchErrors(errors);
        GaussDBAErrors.addInsertUpdateErrors(errors);

        // GaussDB-A Oracle mode specific errors
        errors.add("single-row subquery returns more than one row");
        errors.add("no data found");
        errors.add("not a single-group group function");
        errors.add("group function is not allowed here");
        errors.add("ORA-01427"); // single-row subquery returns more than one row
        errors.add("ORA-01476"); // divisor is equal to zero
        errors.add("must be used in the GROUP BY clause or aggregate function"); // ORA-00979 variant
        errors.addRegex(Pattern.compile("Function .+ does not exist")); // aggregate on incompatible type (e.g., max(blob))
        errors.add("not a single-group group function"); // ORA-00937
        errors.add("column ambiguously defined"); // ORA-00918
        errors.addRegex(Pattern.compile("argument of .+ must be type boolean, not type .+")); // Oracle mode: non-boolean in logical ops
        errors.add("is specified more than once"); // duplicate table name in FROM + JOIN
        errors.add("Missing FROM-clause entry for table"); // correlated subquery referencing unknown table
    }

    @Override
    public void check() throws SQLException {
        joinsInExpr = null;
        tablesFromOuterContext.clear();

        useSubqueryAsFoldedExpr = useSubquery();
        useCorrelatedSubqueryAsFoldedExpr = useCorrelatedSubquery();

        // Real constant folding across all modes: embed a non-correlated scalar-subquery predicate
        // "(k <cmp> (SELECT COUNT(*) FROM t2))" into a generated query via a mutable bag, run it,
        // then replace the subquery with its computed constant and run again. A non-correlated
        // scalar subquery is invariant across outer rows, so folding it to its value is a strict
        // equivalence — any result difference is a genuine optimizer bug. (The former EXPRESSION/
        // CORRELATED branches re-ran the *same* query twice — a no-op that could only surface query
        // non-determinism as a spurious "Results mismatch" — so they were removed.)
        GaussDBAOracleExpressionBag foldBag = buildFoldableCondition();
        if (foldBag == null) {
            throw new IgnoreMeException();
        }
        GaussDBASelect originalQuery;
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
        List<List<String>> originalResult = executeQuery(originalQuery);
        long foldedValue = evalCount(foldTableName);
        foldBag.updateInnerExpr(new GaussDBAText("(" + foldConst + " " + foldCmp + " " + foldedValue + ")"));
        String foldedSql = originalQuery.asString();
        List<List<String>> foldedResult = executeQuery(originalQuery);
        if (!compareResult(foldedResult, originalResult)) {
            throw new AssertionError("CODDTEST folding mismatch (origRows=" + originalResult.size()
                    + " foldRows=" + foldedResult.size() + " foldedValue=" + foldedValue + ")\n-- original: "
                    + originalSql + "\n-- folded:   " + foldedSql);
        }
    }

    /** AND the mutable fold predicate (a bag) into a generated query's WHERE clause. */
    private void injectFoldCondition(GaussDBASelect query, GaussDBAOracleExpressionBag foldBag) {
        GaussDBAExpression where = query.getWhereClause();
        query.setWhereClause(where == null ? foldBag
                : new GaussDBABinaryLogicalOperation(where, foldBag, GaussDBABinaryLogicalOperator.AND));
    }

    private GaussDBASelect genSimpleSelect() throws SQLException {
        GaussDBASelect select = new GaussDBASelect();

        GaussDBATables targetTables = s.getRandomTableNonEmptyTables();
        List<GaussDBAColumn> columns = targetTables.getColumns();
        List<GaussDBATable> tables = targetTables.getTables();
        tablesFromOuterContext = tables;

        gen = new GaussDBAExpressionGenerator(state).setColumns(columns);

        if (Randomly.getBooleanWithRatherLowProbability()) {
            joinsInExpr = genJoinExpression(gen, tables, null);
        }

        List<GaussDBAExpression> tableRefs = new ArrayList<>();
        for (GaussDBATable t : tables) {
            tableRefs.add(GaussDBATableReference.create(t));
        }
        select.setFromList(tableRefs);
        if (joinsInExpr != null && !joinsInExpr.isEmpty()) {
            select.setJoinClauses(joinsInExpr);
        }

        GaussDBAExpression whereCondition = gen.generateExpression(0);

        List<GaussDBAExpression> fetchColumns = new ArrayList<>();
        for (GaussDBAColumn c : columns) {
            GaussDBAColumnReference cRef = GaussDBAColumnReference.create(c, null);
            GaussDBAColumnReference aliasRef = GaussDBAColumnReference.create(c, null);
            GaussDBAOracleAlias columnAlias = new GaussDBAOracleAlias(cRef, aliasRef);
            fetchColumns.add(columnAlias);
        }

        GaussDBAOracleAlias columnAlias = new GaussDBAOracleAlias(whereCondition, null);
        fetchColumns.add(columnAlias);
        select.setFetchColumns(fetchColumns);

        return select;
    }

    private GaussDBASelect genSelectWithCorrelatedSubquery() throws SQLException {
        GaussDBASelect outerQuery = new GaussDBASelect();

        GaussDBATables outerQueryRandomTables = s.getRandomTableNonEmptyTables();
        GaussDBATables innerQueryRandomTables = s.getRandomTableNonEmptyTables();

        List<GaussDBAExpression> innerQueryFromTables = new ArrayList<>();
        for (GaussDBATable t : outerQueryRandomTables.getTables()) {
            if (innerQueryRandomTables.isContained(t)) {
                GaussDBATable correlatedTable = t;
                GaussDBAOracleAlias alias = new GaussDBAOracleAlias(GaussDBATableReference.create(correlatedTable),
                        GaussDBATableReference.create(correlatedTable));
                innerQueryFromTables.add(alias);
            } else {
                innerQueryFromTables.add(GaussDBATableReference.create(t));
            }
        }

        List<GaussDBAColumn> innerQueryColumns = new ArrayList<>();
        innerQueryColumns.addAll(innerQueryRandomTables.getColumns());
        innerQueryColumns.addAll(outerQueryRandomTables.getColumns());
        GaussDBAExpressionGenerator innerGen = new GaussDBAExpressionGenerator(state).setColumns(innerQueryColumns);

        GaussDBASelect innerQuery = new GaussDBASelect();
        innerQuery.setFromList(innerQueryFromTables);
        GaussDBAExpression innerQueryWhereCondition = innerGen.generateExpression(0);
        innerQuery.setWhereClause(innerQueryWhereCondition);

        GaussDBAColumn innerQueryAggrCol = Randomly.fromList(innerQueryRandomTables.getColumns());
        GaussDBAColumnReference aggrRef = GaussDBAColumnReference.create(innerQueryAggrCol, null);
        GaussDBAAggregateFunction aggrFunc = Randomly.fromOptions(GaussDBAAggregateFunction.values());
        GaussDBAExpression innerQueryAggr = new GaussDBAAggregate(List.of(aggrRef), aggrFunc);
        innerQuery.setFetchColumns(List.of(innerQueryAggr));

        List<GaussDBAExpression> outerQueryTableRefs = new ArrayList<>();
        for (GaussDBATable t : outerQueryRandomTables.getTables()) {
            outerQueryTableRefs.add(GaussDBATableReference.create(t));
        }
        outerQuery.setFromList(outerQueryTableRefs);
        tablesFromOuterContext = outerQueryRandomTables.getTables();

        GaussDBAOracleAlias columnAlias = new GaussDBAOracleAlias(innerQuery, null);
        List<GaussDBAExpression> outerQueryFetchColumns = new ArrayList<>();
        for (GaussDBAColumn c : outerQueryRandomTables.getColumns()) {
            GaussDBAColumnReference cRef = GaussDBAColumnReference.create(c, null);
            GaussDBAColumnReference aliasRef = GaussDBAColumnReference.create(c, null);
            GaussDBAOracleAlias colAlias = new GaussDBAOracleAlias(cRef, aliasRef);
            outerQueryFetchColumns.add(colAlias);
        }
        outerQueryFetchColumns.add(columnAlias);
        outerQuery.setFetchColumns(outerQueryFetchColumns);

        return outerQuery;
    }

    private GaussDBASelect genSelectExpression(GaussDBATable table, GaussDBAOracleExpressionBag specificCondition)
            throws SQLException {
        GaussDBASelect select = new GaussDBASelect();

        GaussDBATables randomTables = s.getRandomTableNonEmptyTables();
        if (table != null) {
            randomTables.addTable(table);
        }

        if (!useSubqueryAsFoldedExpr || useSubqueryAsFoldedExpr && useCorrelatedSubqueryAsFoldedExpr) {
            for (GaussDBATable t : this.tablesFromOuterContext) {
                randomTables.addTable(t);
            }
        }

        List<GaussDBAColumn> columns = randomTables.getColumns();
        gen = new GaussDBAExpressionGenerator(state).setColumns(columns);

        List<GaussDBAJoin> joinStatements = new ArrayList<>();
        if (!useSubqueryAsFoldedExpr || useSubqueryAsFoldedExpr && useCorrelatedSubqueryAsFoldedExpr) {
            if (this.joinsInExpr != null) {
                joinStatements.addAll(this.joinsInExpr);
            }
        }

        List<GaussDBAExpression> tableRefs = new ArrayList<>();
        for (GaussDBATable t : randomTables.getTables()) {
            tableRefs.add(GaussDBATableReference.create(t));
        }
        select.setFromList(tableRefs);
        if (!joinStatements.isEmpty()) {
            select.setJoinClauses(joinStatements);
        }

        GaussDBAExpression randomWhereCondition = gen.generateExpression(0);
        GaussDBAExpression whereCondition;
        if (specificCondition != null) {
            // Inject the bag itself (not a snapshot of its current inner expr) so that a later
            // updateInnerExpr() — used to fold the scalar subquery to a constant — is reflected
            // when the same query object is rendered again.
            whereCondition = new GaussDBABinaryLogicalOperation(randomWhereCondition, specificCondition,
                    GaussDBABinaryLogicalOperator.AND);
        } else {
            whereCondition = randomWhereCondition;
        }
        select.setWhereClause(whereCondition);

        if (Randomly.getBoolean()) {
            select.setOrderByClauses(genOrderBysExpression(gen, specificCondition));
        }

        List<GaussDBAExpression> fetchColumns = new ArrayList<>();
        if (Randomly.getBoolean()) {
            for (GaussDBAColumn c : columns) {
                GaussDBAColumnReference cRef = GaussDBAColumnReference.create(c, null);
                GaussDBAColumn aliasColumn = columns.get(0);
                GaussDBAColumnReference aliasRef = GaussDBAColumnReference.create(aliasColumn, null);
                GaussDBAOracleAlias columnAlias = new GaussDBAOracleAlias(cRef, aliasRef);
                fetchColumns.add(columnAlias);
            }
        } else {
            GaussDBAColumn aggrCol = Randomly.fromList(columns);
            GaussDBAColumnReference aggrRef = GaussDBAColumnReference.create(aggrCol, null);
            GaussDBAAggregateFunction aggrFunc = Randomly.fromOptions(GaussDBAAggregateFunction.values());
            GaussDBAExpression aggrExpr = new GaussDBAAggregate(List.of(aggrRef), aggrFunc);
            fetchColumns.add(aggrExpr);
        }
        select.setFetchColumns(fetchColumns);

        return select;
    }

    private List<List<String>> executeQuery(GaussDBASelect query) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        String queryString = query.asString();
        try (Statement st = con.createStatement()) {
            st.setQueryTimeout(600);
            try (ResultSet rs = st.executeQuery(queryString)) {
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
            // For CODDTEST, any query execution failure is an invalid test case, not a bug detection.
            // Only let crash-type errors (08xxx SQLState) propagate as real bugs.
            String sqlState = e.getSQLState();
            if (sqlState != null && sqlState.startsWith("08")) {
                throw e; // connection/crash errors are genuine bugs, not invalid queries
            }
            throw new IgnoreMeException();
        }
        return rows;
    }

    /**
     * Compares two result sets as ROW multisets (order-insensitive, duplicates preserved). This is
     * the correct comparison for folding equivalence. The earlier per-column-label grouping was
     * fragile: duplicate labels from multi-table queries (e.g. two "c0") collided in the map, and
     * comparing each column's values independently loses the row association entirely — both of which
     * can make identical result sets compare unequal (a false positive).
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

    /**
     * Builds a mutable bag holding a non-correlated scalar-subquery predicate
     * "(k &lt;cmp&gt; (SELECT COUNT(*) FROM t2))". Returns null if no table is available.
     * The subquery is non-correlated (references only its own FROM table), so it evaluates to a
     * single constant that folding can safely inline.
     */
    private GaussDBAOracleExpressionBag buildFoldableCondition() {
        GaussDBATables subTables = s.getRandomTableNonEmptyTables();
        if (subTables.getTables().isEmpty()) {
            return null;
        }
        foldTableName = subTables.getTables().get(0).getName();
        foldConst = Randomly.getNotCachedInteger(0, 6);
        foldCmp = Randomly.fromOptions("<=", "<", ">=", ">", "=", "<>");
        String cond = "(" + foldConst + " " + foldCmp + " (SELECT COUNT(*) FROM " + foldTableName + "))";
        return new GaussDBAOracleExpressionBag(new GaussDBAText(cond));
    }

    private long evalCount(String tableName) throws SQLException {
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
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

    private List<GaussDBAJoin> genJoinExpression(GaussDBAExpressionGenerator gen, List<GaussDBATable> tables,
            GaussDBAExpression specificCondition) {
        List<GaussDBAJoin> joinStatements = new ArrayList<>();
        List<GaussDBAJoin.GaussDBAJoinType> options = new ArrayList<>(List.of(GaussDBAJoin.GaussDBAJoinType.values()));

        int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());
        for (int i = 0; i < nrJoinClauses; i++) {
            GaussDBATable table = Randomly.fromList(tables);
            GaussDBAExpression randomOnCondition = gen.generateExpression(0);
            GaussDBAExpression onCondition = randomOnCondition;
            if (specificCondition != null) {
                onCondition = new GaussDBABinaryLogicalOperation(randomOnCondition, specificCondition,
                        GaussDBABinaryLogicalOperator.AND);
            }
            GaussDBAJoin.GaussDBAJoinType selectedOption = Randomly.fromList(options);
            GaussDBAJoin j = new GaussDBAJoin(GaussDBATableReference.create(table), onCondition, selectedOption);
            joinStatements.add(j);
        }
        return joinStatements;
    }

    private List<GaussDBAExpression> genOrderBysExpression(GaussDBAExpressionGenerator gen,
            GaussDBAOracleExpressionBag specificCondition) {
        List<GaussDBAExpression> expressions = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            expressions.add(genOrderingTerm(gen, specificCondition));
        }
        return expressions;
    }

    private GaussDBAExpression genOrderingTerm(GaussDBAExpressionGenerator gen,
            GaussDBAOracleExpressionBag specificCondition) {
        GaussDBAExpression expr = gen.generateExpression(0);
        if (specificCondition != null) {
            expr = new GaussDBABinaryLogicalOperation(expr, specificCondition.getInnerExpr(),
                    GaussDBABinaryLogicalOperator.AND);
        }
        return expr;
    }
}
