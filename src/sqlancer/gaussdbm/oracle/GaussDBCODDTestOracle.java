package sqlancer.gaussdbm.oracle;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;
import sqlancer.common.oracle.CODDTestBase;
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
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBOracleAlias;
import sqlancer.gaussdbm.ast.GaussDBOracleExpressionBag;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBCODDTestOracle extends CODDTestBase<GaussDBMGlobalState>
        implements TestOracle<GaussDBMGlobalState> {

    private final GaussDBMSchema s;
    private GaussDBMExpressionGenerator gen;

    private List<GaussDBTable> tablesFromOuterContext = new ArrayList<>();
    private List<GaussDBJoin> joinsInExpr;

    private Boolean useSubqueryAsFoldedExpr;
    private Boolean useCorrelatedSubqueryAsFoldedExpr;

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

        generateAuxiliaryQuery();

        GaussDBSelect originalQuery = null;
        Map<String, List<GaussDBConstant>> foldedResult = new HashMap<>();
        Map<String, List<GaussDBConstant>> originalResult = new HashMap<>();

        if (useSubqueryAsFoldedExpr && !useCorrelatedSubqueryAsFoldedExpr) {
            originalQuery = genSelectExpression(null, null);
            GaussDBSelect foldedQuery = getFoldedQuery(originalQuery);
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

    private GaussDBSelect generateAuxiliaryQuery() throws SQLException {
        GaussDBSelect select = new GaussDBSelect();

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
        for (GaussDBTable t : outerQueryRandomTables.getTables()) {
            if (innerQueryRandomTables.isContained(t)) {
                GaussDBTable correlatedTable = t;
                GaussDBOracleAlias alias = new GaussDBOracleAlias(GaussDBTableReference.create(correlatedTable),
                        GaussDBTableReference.create(correlatedTable));
                innerQueryFromTables.add(alias);
            } else {
                innerQueryFromTables.add(GaussDBTableReference.create(t));
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
            whereCondition = new GaussDBBinaryLogicalOperation(randomWhereCondition, specificCondition.getInnerExpr(),
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

    private Map<String, List<GaussDBConstant>> executeQuery(GaussDBSelect query) throws SQLException {
        Map<String, List<GaussDBConstant>> result = new HashMap<>();

        try (Statement st = con.createStatement()) {
            st.setQueryTimeout(600);
            String queryString = query.asString();
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
                        GaussDBConstant constant = convertToGaussDBConstant(value);
                        result.get(columnName).add(constant);
                    }
                }
            }
        }

        return result;
    }

    private Map<String, List<GaussDBConstant>> executeFoldedQuery(GaussDBSelect query) throws SQLException {
        return executeQuery(query);
    }

    private GaussDBConstant convertToGaussDBConstant(Object value) {
        if (value == null) {
            return GaussDBConstant.createNullConstant();
        } else if (value instanceof String) {
            return GaussDBConstant.createStringConstant((String) value);
        } else if (value instanceof Integer) {
            return GaussDBConstant.createIntConstant((Integer) value);
        } else if (value instanceof Long) {
            return GaussDBConstant.createIntConstant((Long) value);
        } else if (value instanceof Double) {
            return GaussDBConstant.createIntConstant(((Double) value).longValue());
        } else {
            return GaussDBConstant.createStringConstant(value.toString());
        }
    }

    private boolean compareResult(Map<String, List<GaussDBConstant>> r1, Map<String, List<GaussDBConstant>> r2) {
        if (r1.size() != r2.size()) {
            return false;
        }

        for (String column : r1.keySet()) {
            List<GaussDBConstant> v1 = r1.get(column);
            List<GaussDBConstant> v2 = r2.get(column);

            if (v1.size() != v2.size()) {
                return false;
            }

            List<GaussDBConstant> sortedV1 = new ArrayList<>(v1);
            List<GaussDBConstant> sortedV2 = new ArrayList<>(v2);

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

    private GaussDBSelect getFoldedQuery(GaussDBSelect originalQuery) {
        return originalQuery;
    }

    public boolean useSubquery() {
        return Randomly.getBoolean();
    }

    public boolean useCorrelatedSubquery() {
        return Randomly.getBoolean();
    }

    private List<GaussDBJoin> genJoinExpression(GaussDBMExpressionGenerator gen, List<GaussDBTable> tables,
            GaussDBExpression specificCondition) {
        List<GaussDBJoin> joinStatements = new ArrayList<>();
        List<GaussDBJoin.JoinType> options = new ArrayList<>(List.of(GaussDBJoin.JoinType.values()));

        int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());
        for (int i = 0; i < nrJoinClauses; i++) {
            GaussDBTable table = Randomly.fromList(tables);
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
