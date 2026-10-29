package sqlancer.gaussdbm.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.CODDTestBase;
import sqlancer.common.oracle.NondeterminismGuard;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTables;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation.GaussDBBinaryLogicalOperator;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBOracleAlias;
import sqlancer.gaussdbm.ast.GaussDBOracleExpressionBag;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBText;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBCODDTestOracle extends CODDTestBase<GaussDBMGlobalState>
        implements TestOracle<GaussDBMGlobalState> {

    private final GaussDBMSchema s;
    private GaussDBMExpressionGenerator gen;

    private List<GaussDBTable> tablesFromOuterContext = new ArrayList<>();
    private List<GaussDBJoin> joinsInExpr;

    private Boolean useSubqueryAsFoldedExpr;
    private Boolean useCorrelatedSubqueryAsFoldedExpr;

    // State for the non-correlated scalar-subquery constant folding (SUBQUERY mode).
    private String foldTableName;
    private String foldCmp;
    private long foldConst;

    public GaussDBCODDTestOracle(GaussDBMGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        GaussDBMErrors.addExpressionErrors(errors);

        errors.add("Subquery returns more than 1 row");
        errors.add("Unknown column");
        errors.add("You can't specify target table");
        errors.add("Expression #1 of ORDER BY clause is not in GROUP BY clause");
        errors.add("Every derived table must have its own alias");
        errors.add("Column count doesn't match value count");
    }

    @Override
    public void check() throws SQLException {
        joinsInExpr = null;
        tablesFromOuterContext.clear();

        useSubqueryAsFoldedExpr = useSubquery();
        useCorrelatedSubqueryAsFoldedExpr = useCorrelatedSubquery();

        // Real constant folding across all modes (see rationale in the GaussDB-A oracle): embed a
        // non-correlated scalar-subquery predicate "(k <cmp> (SELECT COUNT(*) FROM t2))" via a mutable
        // bag, run it, then fold the subquery to its constant and run again — a strict equivalence.
        // The former EXPRESSION/CORRELATED branches re-ran the same query twice (a no-op that could
        // only surface query non-determinism as a spurious mismatch), so they were removed.
        GaussDBOracleExpressionBag foldBag = buildFoldableCondition();
        if (foldBag == null) {
            throw new IgnoreMeException();
        }
        GaussDBSelect originalQuery;
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
        if (NondeterminismGuard.containsNondeterministicCall(originalSql)) {
            // Nondeterministic projections (NOW()/CURTIME()/...) differ between the original and
            // the folded executions; the folding comparison is meaningless for such rounds.
            throw new IgnoreMeException();
        }
        List<List<String>> originalResult;
        List<List<String>> foldedResult;
        String foldedSql;
        try {
            originalResult = executeQuery(originalQuery);
            long foldedValue = evalCount(foldTableName);
            foldBag.updateInnerExpr(new GaussDBText("(" + foldConst + " " + foldCmp + " " + foldedValue + ")"));
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

    /**
     * Known-benign execution errors, skipped by retrying the round (the folded query fails
     * identically, so the folding equivalence is trivially preserved). Mirrors the GaussDB-A
     * benign runtime-error coverage: the DB legitimately rejects an out-of-domain cast or an
     * unsupported type combination in a generated predicate — not a logic bug. Includes:
     * - JSON type/content errors: the generator is not JSON-type-aware (GaussDB-M, unlike
     *   MySQL, validates JSON arguments at the type level before evaluation).
     * - Type/range strictness: the generator has no type-aware expression path, and GaussDB-M
     *   (unlike MySQL) does not coerce operand types ("operator does not exist", out-of-range).
     * - Comma-vs-join scope: an ON clause referencing a comma-separated FROM sibling is
     *   rejected without LATERAL.
     */
    private static boolean isBenignExpectedError(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            if (msg.contains("Invalid data type for JSON data in argument")
                    || msg.contains("The token is invalid for type json")
                    || msg.contains("Invalid JSON text")
                    || msg.contains("The input value is invalid for type json")
                    || msg.contains("Cannot create a JSON value")
                    || msg.contains("Invalid JSON path expression")
                    || msg.contains("JSON documents may not contain NULL member names")
                    || msg.contains("must be type boolean")
                    || msg.contains("out of range")
                    || msg.contains("Unsupport type")
                    || msg.contains("operator does not exist")
                    || msg.contains("cannot be cast")
                    || msg.contains("cannot cast")
                    || msg.contains("cannot be matched")
                    || msg.contains("failed to find conversion function")
                    || msg.contains("invalid reference to FROM-clause entry")
                    || msg.contains("Missing FROM-clause entry")
                    || msg.contains("appears more than once in left table")) {
                return true;
            }
        }
        return false;
    }

    /** AND the mutable fold predicate (a bag) into a generated query's WHERE clause. */
    private void injectFoldCondition(GaussDBSelect query, GaussDBOracleExpressionBag foldBag) {
        GaussDBExpression where = query.getWhereClause();
        query.setWhereClause(where == null ? foldBag
                : new GaussDBBinaryLogicalOperation(where, foldBag, GaussDBBinaryLogicalOperator.AND));
    }

    private GaussDBSelect genSimpleSelect() throws SQLException {
        GaussDBSelect select = new GaussDBSelect();

        GaussDBTables targetTables = s.getRandomTableNonEmptyTables();
        List<GaussDBColumn> columns = targetTables.getColumns();
        List<GaussDBTable> tables = targetTables.getTables();
        tablesFromOuterContext = tables;

        gen = new GaussDBMExpressionGenerator(state).setColumns(columns);

        if (Randomly.getBooleanWithRatherLowProbability()) {
            joinsInExpr = genJoinExpression(gen, tables, null);
        }

        List<GaussDBExpression> tableRefs = new ArrayList<>();
        for (GaussDBTable t : tables) {
            tableRefs.add(GaussDBTableReference.create(t));
        }
        select.setFromList(tableRefs);
        if (joinsInExpr != null && !joinsInExpr.isEmpty()) {
            select.setJoinClauses(joinsInExpr);
        }

        GaussDBExpression whereCondition = gen.generateExpression();

        List<GaussDBExpression> fetchColumns = new ArrayList<>();
        for (GaussDBColumn c : columns) {
            GaussDBColumnReference cRef = GaussDBColumnReference.create(c, null);
            GaussDBColumnReference aliasRef = GaussDBColumnReference.create(c, null);
            GaussDBOracleAlias columnAlias = new GaussDBOracleAlias(cRef, aliasRef);
            fetchColumns.add(columnAlias);
        }

        GaussDBOracleAlias columnAlias = new GaussDBOracleAlias(whereCondition, null);
        fetchColumns.add(columnAlias);
        select.setFetchColumns(fetchColumns);

        return select;
    }

    private GaussDBSelect genSelectWithCorrelatedSubquery() throws SQLException {
        GaussDBSelect outerQuery = new GaussDBSelect();

        GaussDBTables outerQueryRandomTables = s.getRandomTableNonEmptyTables();
        GaussDBTables innerQueryRandomTables = s.getRandomTableNonEmptyTables();

        List<GaussDBExpression> innerQueryFromTables = new ArrayList<>();
        // Subquery-local tables: list tables that appear only in the inner set in the inner FROM.
        // Without them, references to their columns dangle ("Missing FROM-clause entry").
        for (GaussDBTable t : innerQueryRandomTables.getTables()) {
            if (!outerQueryRandomTables.isContained(t)) {
                innerQueryFromTables.add(GaussDBTableReference.create(t));
            }
        }
        // Tables present in both sets are listed in the inner FROM (self-aliased) so their inner
        // references bind locally; references to outer-only tables remain true correlated
        // references resolved against the outer query (upstream SQLite3 CODDTest construction).
        for (GaussDBTable t : outerQueryRandomTables.getTables()) {
            if (innerQueryRandomTables.isContained(t)) {
                GaussDBOracleAlias alias = new GaussDBOracleAlias(GaussDBTableReference.create(t),
                        GaussDBTableReference.create(t));
                innerQueryFromTables.add(alias);
            }
        }

        List<GaussDBColumn> innerQueryColumns = new ArrayList<>();
        innerQueryColumns.addAll(innerQueryRandomTables.getColumns());
        innerQueryColumns.addAll(outerQueryRandomTables.getColumns());
        GaussDBMExpressionGenerator innerGen = new GaussDBMExpressionGenerator(state).setColumns(innerQueryColumns);

        GaussDBSelect innerQuery = new GaussDBSelect();
        innerQuery.setFromList(innerQueryFromTables);
        GaussDBExpression innerQueryWhereCondition = innerGen.generateExpression();
        innerQuery.setWhereClause(innerQueryWhereCondition);

        GaussDBColumn innerQueryAggrCol = Randomly.fromList(innerQueryRandomTables.getColumns());
        GaussDBColumnReference aggrRef = GaussDBColumnReference.create(innerQueryAggrCol, null);
        GaussDBAggregateFunction aggrFunc = Randomly.fromOptions(GaussDBAggregateFunction.values());
        GaussDBExpression innerQueryAggr = new GaussDBAggregate(List.of(aggrRef), aggrFunc);
        innerQuery.setFetchColumns(List.of(innerQueryAggr));

        List<GaussDBExpression> outerQueryTableRefs = new ArrayList<>();
        for (GaussDBTable t : outerQueryRandomTables.getTables()) {
            outerQueryTableRefs.add(GaussDBTableReference.create(t));
        }
        outerQuery.setFromList(outerQueryTableRefs);
        tablesFromOuterContext = outerQueryRandomTables.getTables();

        GaussDBOracleAlias columnAlias = new GaussDBOracleAlias(innerQuery, null);
        List<GaussDBExpression> outerQueryFetchColumns = new ArrayList<>();
        for (GaussDBColumn c : outerQueryRandomTables.getColumns()) {
            GaussDBColumnReference cRef = GaussDBColumnReference.create(c, null);
            GaussDBColumnReference aliasRef = GaussDBColumnReference.create(c, null);
            GaussDBOracleAlias colAlias = new GaussDBOracleAlias(cRef, aliasRef);
            outerQueryFetchColumns.add(colAlias);
        }
        outerQueryFetchColumns.add(columnAlias);
        outerQuery.setFetchColumns(outerQueryFetchColumns);

        return outerQuery;
    }

    private GaussDBSelect genSelectExpression(GaussDBTable table, GaussDBOracleExpressionBag specificCondition)
            throws SQLException {
        GaussDBSelect select = new GaussDBSelect();

        GaussDBTables randomTables = s.getRandomTableNonEmptyTables();
        if (table != null) {
            randomTables.addTable(table);
        }

        if (!useSubqueryAsFoldedExpr || useSubqueryAsFoldedExpr && useCorrelatedSubqueryAsFoldedExpr) {
            for (GaussDBTable t : this.tablesFromOuterContext) {
                randomTables.addTable(t);
            }
        }

        List<GaussDBColumn> columns = randomTables.getColumns();
        gen = new GaussDBMExpressionGenerator(state).setColumns(columns);

        List<GaussDBJoin> joinStatements = new ArrayList<>();
        if (!useSubqueryAsFoldedExpr || useSubqueryAsFoldedExpr && useCorrelatedSubqueryAsFoldedExpr) {
            if (this.joinsInExpr != null) {
                joinStatements.addAll(this.joinsInExpr);
            }
        }

        List<GaussDBExpression> tableRefs = new ArrayList<>();
        for (GaussDBTable t : randomTables.getTables()) {
            tableRefs.add(GaussDBTableReference.create(t));
        }
        select.setFromList(tableRefs);
        if (!joinStatements.isEmpty()) {
            select.setJoinClauses(joinStatements);
        }

        GaussDBExpression randomWhereCondition = gen.generateExpression();
        GaussDBExpression whereCondition;
        if (specificCondition != null) {
            // Inject the bag itself (not a snapshot of its inner expr) so a later updateInnerExpr()
            // that folds the scalar subquery to a constant is reflected on re-render.
            whereCondition = new GaussDBBinaryLogicalOperation(randomWhereCondition, specificCondition,
                    GaussDBBinaryLogicalOperator.AND);
        } else {
            whereCondition = randomWhereCondition;
        }
        select.setWhereClause(whereCondition);

        if (Randomly.getBoolean()) {
            select.setOrderByClauses(genOrderBysExpression(gen, specificCondition));
        }

        List<GaussDBExpression> fetchColumns = new ArrayList<>();
        if (Randomly.getBoolean()) {
            for (GaussDBColumn c : columns) {
                GaussDBColumnReference cRef = GaussDBColumnReference.create(c, null);
                GaussDBColumn aliasColumn = columns.get(0);
                GaussDBColumnReference aliasRef = GaussDBColumnReference.create(aliasColumn, null);
                GaussDBOracleAlias columnAlias = new GaussDBOracleAlias(cRef, aliasRef);
                fetchColumns.add(columnAlias);
            }
        } else {
            GaussDBColumn aggrCol = Randomly.fromList(columns);
            GaussDBColumnReference aggrRef = GaussDBColumnReference.create(aggrCol, null);
            GaussDBAggregateFunction aggrFunc = Randomly.fromOptions(GaussDBAggregateFunction.values());
            GaussDBExpression aggrExpr = new GaussDBAggregate(List.of(aggrRef), aggrFunc);
            fetchColumns.add(aggrExpr);
        }
        select.setFetchColumns(fetchColumns);

        return select;
    }

    private List<List<String>> executeQuery(GaussDBSelect query) throws SQLException {
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

    private GaussDBOracleExpressionBag buildFoldableCondition() {
        GaussDBTables subTables = s.getRandomTableNonEmptyTables();
        if (subTables.getTables().isEmpty()) {
            return null;
        }
        foldTableName = subTables.getTables().get(0).getName();
        foldConst = Randomly.getNotCachedInteger(0, 6);
        foldCmp = Randomly.fromOptions("<=", "<", ">=", ">", "=", "<>");
        String cond = "(" + foldConst + " " + foldCmp + " (SELECT COUNT(*) FROM " + foldTableName + "))";
        return new GaussDBOracleExpressionBag(new GaussDBText(cond));
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

    private List<GaussDBJoin> genJoinExpression(GaussDBMExpressionGenerator gen, List<GaussDBTable> tables,
            GaussDBExpression specificCondition) {
        List<GaussDBJoin> joinStatements = new ArrayList<>();
        List<GaussDBJoin.JoinType> options = new ArrayList<>(List.of(GaussDBJoin.JoinType.values()));

        int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());
        // Natural join is incompatible with other joins: it needs unique column names, while
        // other joins (and comma-separated tables) produce duplicate names (upstream guard).
        if (nrJoinClauses > 1) {
            options.remove(GaussDBJoin.JoinType.NATURAL);
        }
        for (int i = 0; i < nrJoinClauses; i++) {
            GaussDBTable table = Randomly.fromList(tables);
            // Remove the picked table so it is provided by the JOIN clause only (upstream SQLite3
            // construction); leaving it in the FROM list as well triggers
            // "Table name ... is specified more than once" and repeat picks duplicate joins.
            tables.remove(table);
            GaussDBExpression randomOnCondition = gen.generateExpression();
            GaussDBExpression onCondition = randomOnCondition;
            if (specificCondition != null) {
                onCondition = new GaussDBBinaryLogicalOperation(randomOnCondition, specificCondition,
                        GaussDBBinaryLogicalOperator.AND);
            }
            GaussDBJoin.JoinType selectedOption = Randomly.fromList(options);
            GaussDBJoin j = new GaussDBJoin(GaussDBTableReference.create(table), onCondition, selectedOption);
            joinStatements.add(j);
        }
        return joinStatements;
    }

    private List<GaussDBExpression> genOrderBysExpression(GaussDBMExpressionGenerator gen,
            GaussDBOracleExpressionBag specificCondition) {
        List<GaussDBExpression> expressions = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            expressions.add(genOrderingTerm(gen, specificCondition));
        }
        return expressions;
    }

    private GaussDBExpression genOrderingTerm(GaussDBMExpressionGenerator gen,
            GaussDBOracleExpressionBag specificCondition) {
        GaussDBExpression expr = gen.generateExpression();
        if (specificCondition != null) {
            expr = new GaussDBBinaryLogicalOperation(expr, specificCondition.getInnerExpr(),
                    GaussDBBinaryLogicalOperator.AND);
        }
        return expr;
    }
}
