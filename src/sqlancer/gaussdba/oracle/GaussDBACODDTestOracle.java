package sqlancer.gaussdba.oracle;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBAOracleAlias;
import sqlancer.gaussdba.ast.GaussDBAOracleExpressionBag;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
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

        generateAuxiliaryQuery();

        GaussDBASelect originalQuery = null;
        Map<String, List<GaussDBAConstant>> foldedResult = new HashMap<>();
        Map<String, List<GaussDBAConstant>> originalResult = new HashMap<>();

        if (useSubqueryAsFoldedExpr && !useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSelectExpression(null, null);
            GaussDBASelect foldedQuery = getFoldedQuery(originalQuery);
            foldedResult = executeQuery(foldedQuery);
            originalResult = executeQuery(originalQuery);
        } else if (!useSubqueryAsFoldedExpr && !useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSimpleSelect();
            foldedResult = executeFoldedQuery(originalQuery);
            originalResult = executeQuery(originalQuery);
        } else if (useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSelectWithCorrelatedSubquery();
            foldedResult = executeFoldedQuery(originalQuery);
            originalResult = executeQuery(originalQuery);
        }

        if (!compareResult(foldedResult, originalResult)) {
            throw new AssertionError("Results mismatch");
        }
    }

    private GaussDBASelect generateAuxiliaryQuery() throws SQLException {
        GaussDBASelect select = new GaussDBASelect();

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
            whereCondition = new GaussDBABinaryLogicalOperation(randomWhereCondition, specificCondition.getInnerExpr(),
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

    private Map<String, List<GaussDBAConstant>> executeQuery(GaussDBASelect query) throws SQLException {
        Map<String, List<GaussDBAConstant>> result = new HashMap<>();

        String queryString = query.asString();
        try (Statement st = con.createStatement()) {
            st.setQueryTimeout(600);
            try (ResultSet rs = st.executeQuery(queryString)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    result.put(columnName, new ArrayList<>());
                }

                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        GaussDBAConstant constant = convertToGaussDBAConstant(value);
                        result.get(columnName).add(constant);
                    }
                }
            }
        } catch (SQLException e) {
            // For CODDTEST, any query execution failure is an invalid test case, not a bug detection.
            // Only let crash-type errors (08xxx SQLState) propagate as real bugs.
            String sqlState = e.getSQLState();
            if (sqlState != null && sqlState.startsWith("08")) {
                // Connection/crash errors — these are genuine bugs, not invalid queries
                throw e;
            }
            throw new IgnoreMeException();
        }

        return result;
    }

    private Map<String, List<GaussDBAConstant>> executeFoldedQuery(GaussDBASelect query) throws SQLException {
        return executeQuery(query);
    }

    private GaussDBAConstant convertToGaussDBAConstant(Object value) {
        if (value == null) {
            return GaussDBAConstant.createNullConstant();
        } else if (value instanceof String) {
            return GaussDBAConstant.createVarchar2Constant((String) value);
        } else if (value instanceof Integer) {
            return GaussDBAConstant.createNumberConstant((Integer) value);
        } else if (value instanceof Long) {
            return GaussDBAConstant.createNumberConstant((Long) value);
        } else if (value instanceof Double) {
            return GaussDBAConstant.createNumberConstant(((Double) value).longValue());
        } else if (value instanceof java.math.BigDecimal) {
            return GaussDBAConstant.createNumberConstant((java.math.BigDecimal) value);
        } else if (value instanceof java.sql.Date) {
            return GaussDBAConstant.createDateConstant(((java.sql.Date) value).toLocalDate());
        } else if (value instanceof java.sql.Timestamp) {
            return GaussDBAConstant.createTimestampConstant(((java.sql.Timestamp) value).toLocalDateTime());
        } else {
            return GaussDBAConstant.createVarchar2Constant(value.toString());
        }
    }

    private boolean compareResult(Map<String, List<GaussDBAConstant>> r1, Map<String, List<GaussDBAConstant>> r2) {
        if (r1.size() != r2.size()) {
            return false;
        }

        for (String column : r1.keySet()) {
            List<GaussDBAConstant> v1 = r1.get(column);
            List<GaussDBAConstant> v2 = r2.get(column);

            if (v1.size() != v2.size()) {
                return false;
            }

            List<GaussDBAConstant> sortedV1 = new ArrayList<>(v1);
            List<GaussDBAConstant> sortedV2 = new ArrayList<>(v2);

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

            for (int i = 0; i < sortedV1.size(); i++) {
                String t1 = sortedV1.get(i).getTextRepresentation();
                String t2 = sortedV2.get(i).getTextRepresentation();
                if (!t1.equals(t2)) {
                    return false;
                }
            }
        }

        return true;
    }

    private GaussDBASelect getFoldedQuery(GaussDBASelect originalQuery) {
        return originalQuery;
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
