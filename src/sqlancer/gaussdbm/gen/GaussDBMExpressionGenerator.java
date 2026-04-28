package sqlancer.gaussdbm.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import sqlancer.Randomly;
import sqlancer.common.gen.CERTGenerator;
import sqlancer.common.gen.NoRECGenerator;
import sqlancer.common.gen.TLPWhereGenerator;
import sqlancer.common.gen.UntypedExpressionGenerator;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBRowValue;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTables;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;
import sqlancer.gaussdbm.ast.GaussDBBetweenOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation.GaussDBArithmeticOperator;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation.GaussDBBinaryLogicalOperator;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBCastOperation;
import sqlancer.gaussdbm.ast.GaussDBCastOperation.GaussDBCastType;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction.GaussDBFunction;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBIfFunction;
import sqlancer.gaussdbm.ast.GaussDBInOperation;
import sqlancer.gaussdbm.ast.GaussDBJsonFunction;
import sqlancer.gaussdbm.ast.GaussDBJsonFunction.GaussDBJsonFunctionType;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBManuelPredicate;
import sqlancer.gaussdbm.ast.GaussDBPostfixText;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBSelect.SelectType;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction.GaussDBTemporalFunctionType;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdbm.ast.GaussDBWindowFunction;

public class GaussDBMExpressionGenerator extends UntypedExpressionGenerator<GaussDBExpression, GaussDBColumn>
        implements NoRECGenerator<GaussDBSelect, GaussDBJoin, GaussDBExpression, GaussDBTable, GaussDBColumn>,
        TLPWhereGenerator<GaussDBSelect, GaussDBJoin, GaussDBExpression, GaussDBTable, GaussDBColumn>,
        CERTGenerator<GaussDBSelect, GaussDBJoin, GaussDBExpression, GaussDBTable, GaussDBColumn> {

    private final GaussDBMGlobalState state;
    private List<GaussDBTable> tables = new ArrayList<>();
    private GaussDBRowValue rowVal;

    public GaussDBMExpressionGenerator(GaussDBMGlobalState state) {
        this.state = state;
    }

    public GaussDBMExpressionGenerator setRowVal(GaussDBRowValue rowVal) {
        this.rowVal = rowVal;
        return this;
    }

    private enum Actions {
        COLUMN, LITERAL, BINARY_LOGICAL_OPERATOR, BINARY_COMPARISON_OPERATION, BETWEEN_OPERATOR, UNARY_PREFIX_OPERATION,
        UNARY_POSTFIX_OPERATION, CASE_OPERATOR, ARITHMETIC_OPERATION, IN_OPERATION, COMPUTABLE_FUNCTION,
        TEMPORAL_FUNCTION, CAST, JSON_FUNCTION;
        // EXISTS - TODO: requires subquery support
    }

    /**
     * Expression types for generating fetch column expressions (used in SonarOracle).
     */
    private enum ColumnExpressionActions {
        BINARY_COMPARISON_OPERATION, BINARY_ARITHMETIC_OPERATION, COLUMN, AGGREGATE_FUNCTION, CASE_OPERATOR,
        COMPUTABLE_FUNCTION, WINDOW_FUNCTION, TEMPORAL_FUNCTION, CAST
    }

    /**
     * Expression types for generating WHERE column expressions (used in SonarOracle).
     */
    private enum WhereColumnExpressionActions {
        BINARY_COMPARISON_OPERATION, AGGREGATE_FUNCTION, COLUMN
    }

    @Override
    protected GaussDBExpression generateExpression(int depth) {
        if (depth >= state.getOptions().getMaxExpressionDepth()) {
            return generateLeafNode();
        }
        switch (Randomly.fromOptions(Actions.values())) {
        case COLUMN:
            return generateColumn();
        case LITERAL:
            return generateConstant();
        case BINARY_LOGICAL_OPERATOR:
            return new GaussDBBinaryLogicalOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    GaussDBBinaryLogicalOperator.getRandom());
        case BINARY_COMPARISON_OPERATION:
            return new GaussDBBinaryComparisonOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    Randomly.fromOptions(BinaryComparisonOperator.values()));
        case BETWEEN_OPERATOR: {
            GaussDBExpression a = generateExpression(depth + 1);
            GaussDBExpression x = generateLeafNode();
            GaussDBExpression y = generateLeafNode();
            return new GaussDBBetweenOperation(a, x, y, Randomly.getBoolean());
        }
        case UNARY_PREFIX_OPERATION:
            return new GaussDBUnaryPrefixOperation(generateExpression(depth + 1), UnaryPrefixOperator.NOT);
        case UNARY_POSTFIX_OPERATION:
            return new GaussDBUnaryPostfixOperation(generateExpression(depth + 1), UnaryPostfixOperator.getRandom());
        case CASE_OPERATOR:
            return getCaseOperator(depth + 1);
        case ARITHMETIC_OPERATION:
            return new GaussDBBinaryArithmeticOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    GaussDBArithmeticOperator.getRandom());
        case IN_OPERATION: {
            GaussDBExpression expr = generateExpression(depth + 1);
            int nrElements = Randomly.smallNumber() + 1;
            List<GaussDBExpression> listElements = IntStream.range(0, nrElements).mapToObj(i -> generateLeafNode())
                    .collect(Collectors.toList());
            return new GaussDBInOperation(expr, listElements, Randomly.getBoolean());
        }
        case COMPUTABLE_FUNCTION:
            return generateComputableFunction(depth + 1);
        case TEMPORAL_FUNCTION:
            return generateTemporalFunction(depth + 1);
        case CAST:
            return generateCast(depth + 1);
        case JSON_FUNCTION:
            return generateJsonFunction(depth + 1);
        default:
            throw new AssertionError();
        }
    }

    @Override
    protected GaussDBExpression generateColumn() {
        GaussDBColumn c = Randomly.fromList(columns);
        GaussDBConstant val = rowVal == null ? null : rowVal.getValues().get(c);
        return GaussDBColumnReference.create(c, val);
    }

    @Override
    public GaussDBConstant generateConstant() {
        return GaussDBConstant.createRandomConstant();
    }

    private GaussDBExpression getCaseOperator(int depth) {
        GaussDBExpression whenExpr = generateExpression(depth);
        GaussDBExpression thenExpr = generateExpression(depth);
        GaussDBExpression elseExpr = Randomly.getBoolean() ? generateExpression(depth)
                : GaussDBConstant.createNullConstant();
        return new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
    }

    /**
     * Generates a computable function expression. Supports both fixed-argument and variadic functions.
     *
     * @param depth
     *            the current recursion depth
     *
     * @return the generated computable function expression
     */
    private GaussDBExpression generateComputableFunction(int depth) {
        GaussDBFunction func = GaussDBFunction.getRandomFunction();
        int nrArgs = func.getMinNrArgs();
        if (func.isVariadic()) {
            // Variadic functions (CONCAT, COALESCE) can have 2-5 arguments
            nrArgs += Randomly.smallNumber();
        }
        List<GaussDBExpression> args = new ArrayList<>();
        for (int i = 0; i < nrArgs; i++) {
            args.add(generateExpression(depth + 1));
        }
        return new GaussDBComputableFunction(func, args);
    }

    /**
     * Generates a temporal function expression. Supports current time functions (NOW, CURDATE, CURTIME), extraction
     * functions (YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, DAYOFWEEK), and calculation functions (DATEDIFF, LAST_DAY).
     *
     * @param depth
     *            the current recursion depth
     *
     * @return the generated temporal function expression
     */
    private GaussDBExpression generateTemporalFunction(int depth) {
        GaussDBTemporalFunctionType func = GaussDBTemporalFunctionType.getRandom();
        int arity = func.getArity();

        if (arity == 0) {
            // Current time functions (NOW, CURDATE, CURTIME) - no arguments
            return new GaussDBTemporalFunction(func);
        } else {
            // Functions requiring arguments
            List<GaussDBExpression> args = new ArrayList<>();
            for (int i = 0; i < arity; i++) {
                // Use leaf nodes for temporal arguments to avoid deep nesting
                args.add(generateLeafNode());
            }
            return new GaussDBTemporalFunction(func, args);
        }
    }

    /**
     * Generates a computable function with a specific first argument. Used by generateFetchColumnExpression for
     * SonarOracle.
     *
     * @param depth
     *            the current recursion depth
     * @param firstArg
     *            the first argument to use in the function
     *
     * @return the generated computable function expression
     */
    private GaussDBExpression generateComputableFunctionWithFirstArg(int depth, GaussDBExpression firstArg) {
        GaussDBFunction func = GaussDBFunction.getRandomFunction();
        int nrArgs = func.getMinNrArgs();
        if (func.isVariadic()) {
            nrArgs += Randomly.smallNumber();
        }
        List<GaussDBExpression> args = new ArrayList<>();
        for (int i = 0; i < nrArgs; i++) {
            if (i == 0) {
                args.add(firstArg);
            } else {
                args.add(generateExpression(depth + 1));
            }
        }
        return new GaussDBComputableFunction(func, args);
    }

    /**
     * Generates a CAST expression. CAST(expr AS type) converts an expression to a specified type.
     *
     * @param depth
     *            the current recursion depth
     *
     * @return the generated CAST expression
     */
    private GaussDBExpression generateCast(int depth) {
        GaussDBExpression expr = generateExpression(depth + 1);
        GaussDBCastType type = GaussDBCastType.getRandom();
        return new GaussDBCastOperation(expr, type);
    }

    /**
     * Generates a JSON function expression. Supports query functions (JSON_EXTRACT, JSON_CONTAINS, JSON_KEYS,
     * JSON_TYPE, JSON_VALID), construction functions (JSON_ARRAY, JSON_OBJECT), and modification functions
     * (JSON_REMOVE, JSON_REPLACE, JSON_SET). Variadic functions generate 2-5 arguments.
     *
     * @param depth
     *            the current recursion depth
     *
     * @return the generated JSON function expression
     */
    private GaussDBExpression generateJsonFunction(int depth) {
        GaussDBJsonFunctionType func = GaussDBJsonFunctionType.getRandomFunction();
        int nrArgs = func.getMinNrArgs();

        if (func.isVariadic()) {
            // Variadic functions (JSON_ARRAY, JSON_OBJECT) can have 2-5 arguments
            nrArgs = 2 + Randomly.smallNumber();
        }

        List<GaussDBExpression> args = new ArrayList<>();
        for (int i = 0; i < nrArgs; i++) {
            if (func == GaussDBJsonFunctionType.JSON_EXTRACT && i == 0) {
                // First argument of JSON_EXTRACT should be a JSON document string
                args.add(generateJsonStringConstant());
            } else if (func == GaussDBJsonFunctionType.JSON_EXTRACT && i == 1) {
                // Second argument of JSON_EXTRACT should be a JSON path string
                args.add(generateJsonPathConstant());
            } else if (func == GaussDBJsonFunctionType.JSON_TYPE || func == GaussDBJsonFunctionType.JSON_VALID
                    || func == GaussDBJsonFunctionType.JSON_KEYS) {
                // These functions take a JSON document string as argument
                args.add(generateJsonStringConstant());
            } else {
                // Other arguments can be any expression
                args.add(generateExpression(depth + 1));
            }
        }
        return new GaussDBJsonFunction(func, args);
    }

    /**
     * Generates a JSON string constant. Produces simple valid JSON strings like '{"a":1}', '[1,2,3]', etc.
     *
     * @return the generated JSON string constant
     */
    private GaussDBExpression generateJsonStringConstant() {
        // Generate simple valid JSON strings
        String[] jsonTemplates = { "{}", "[]", "{\"a\":1}", "{\"key\":\"value\"}", "[1,2,3]", "\"string\"", "true",
                "false", "null", "42", "3.14" };
        String jsonStr = Randomly.fromOptions(jsonTemplates);
        return GaussDBConstant.createStringConstant(jsonStr);
    }

    /**
     * Generates a JSON path constant. Produces simple JSON path expressions like '$', '$.a', '$[0]', etc.
     *
     * @return the generated JSON path constant
     */
    private GaussDBExpression generateJsonPathConstant() {
        // Generate simple JSON path strings
        String[] pathTemplates = { "$", "$.a", "$[0]", "$.key", "$.*", "$[*]" };
        String pathStr = Randomly.fromOptions(pathTemplates);
        return GaussDBConstant.createStringConstant(pathStr);
    }

    @Override
    public GaussDBExpression generateBooleanExpression() {
        return generateExpression();
    }

    @Override
    public GaussDBExpression negatePredicate(GaussDBExpression predicate) {
        return new GaussDBUnaryPrefixOperation(predicate, UnaryPrefixOperator.NOT);
    }

    @Override
    public GaussDBExpression isNull(GaussDBExpression expr) {
        return new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
    }

    @Override
    public GaussDBSelect generateSelect() {
        return new GaussDBSelect();
    }

    @Override
    public List<GaussDBJoin> getRandomJoinClauses() {
        if (tables == null || tables.size() <= 1) {
            return List.of();
        }
        List<GaussDBTable> tablesCopy = new ArrayList<>(tables);
        List<GaussDBJoin> joins = GaussDBJoin.getRandomJoinClauses(tablesCopy, state);
        this.tables = tablesCopy;
        return joins;
    }

    @Override
    public List<GaussDBExpression> getTableRefs() {
        return tables.stream().map(GaussDBTableReference::create).collect(Collectors.toList());
    }

    @Override
    public List<GaussDBExpression> generateFetchColumns(boolean shouldCreateDummy) {
        return columns.stream().map(c -> GaussDBColumnReference.create(c, null)).collect(Collectors.toList());
    }

    @Override
    public String generateOptimizedQueryString(GaussDBSelect select, GaussDBExpression whereCondition,
            boolean shouldUseAggregate) {
        if (shouldUseAggregate) {
            GaussDBAggregate countAgg = new GaussDBAggregate(List.of(GaussDBConstant.createIntConstant(1)),
                    GaussDBAggregateFunction.COUNT);
            select.setFetchColumns(List.of(countAgg));
        } else {
            select.setFetchColumns(generateFetchColumns(false));
        }
        select.setWhereClause(whereCondition);
        return select.asString();
    }

    @Override
    public String generateUnoptimizedQueryString(GaussDBSelect select, GaussDBExpression whereCondition) {
        GaussDBExpression countExpr = new GaussDBIfFunction(whereCondition, GaussDBConstant.createIntConstant(1),
                GaussDBConstant.createIntConstant(0));
        select.setFetchColumns(List.of(countExpr));
        select.setWhereClause(null);
        select.setOrderByClauses(List.of());
        return "SELECT SUM(ref0) FROM (" + select.asString() + ") AS res";
    }

    @Override
    public String generateExplainQuery(GaussDBSelect select) {
        return "EXPLAIN " + select.asString();
    }

    public GaussDBAggregate generateAggregate() {
        GaussDBAggregateFunction func = Randomly.fromOptions(GaussDBAggregateFunction.values());
        if (func.isVariadic()) {
            int nrExprs = Randomly.smallNumber() + 1;
            List<GaussDBExpression> exprs = IntStream.range(0, nrExprs).mapToObj(index -> generateExpression())
                    .collect(Collectors.toList());
            return new GaussDBAggregate(exprs, func);
        } else {
            return new GaussDBAggregate(List.of(generateExpression()), func);
        }
    }

    /**
     * Generates a fetch column expression for SonarOracle. Uses ColumnExpressionActions to select the expression type.
     *
     * @param targetTables
     *            the tables to reference columns from
     *
     * @return the generated expression
     */
    public GaussDBExpression generateFetchColumnExpression(GaussDBTables targetTables) {
        if (targetTables.getColumns().isEmpty()) {
            return generateColumn();
        }
        GaussDBColumn firstColumn = targetTables.getColumns().get(0);
        switch (Randomly.fromOptions(ColumnExpressionActions.values())) {
        case BINARY_COMPARISON_OPERATION:
            return new GaussDBBinaryComparisonOperation(GaussDBColumnReference.create(firstColumn, null),
                    generateExpression(2), Randomly.fromOptions(BinaryComparisonOperator.values()));
        case BINARY_ARITHMETIC_OPERATION:
            return new GaussDBBinaryArithmeticOperation(GaussDBColumnReference.create(firstColumn, null),
                    generateExpression(2), GaussDBArithmeticOperator.getRandom());
        case AGGREGATE_FUNCTION:
            return new GaussDBAggregate(List.of(GaussDBColumnReference.create(firstColumn, null)),
                    Randomly.fromOptions(GaussDBAggregateFunction.values()));
        case CASE_OPERATOR:
            return getCaseOperator(2);
        case COMPUTABLE_FUNCTION:
            return generateComputableFunctionWithFirstArg(1, GaussDBColumnReference.create(firstColumn, null));
        case WINDOW_FUNCTION:
            return generateWindowFunction(firstColumn, targetTables);
        case TEMPORAL_FUNCTION:
            return generateTemporalFunction(1);
        case CAST:
            return new GaussDBCastOperation(GaussDBColumnReference.create(firstColumn, null),
                    GaussDBCastType.getRandom());
        case COLUMN:
            return generateColumn();
        default:
            throw new AssertionError();
        }
    }

    /**
     * Generates a window function expression for SonarOracle. Supports both ranking functions (ROW_NUMBER, RANK,
     * DENSE_RANK - no arguments) and aggregate window functions (SUM, AVG, COUNT, MAX, MIN - one argument).
     *
     * @param firstColumn
     *            the first column to use as partition/order column
     * @param targetTables
     *            the tables to reference columns from
     *
     * @return the generated window function expression
     */
    private GaussDBExpression generateWindowFunction(GaussDBColumn firstColumn, GaussDBTables targetTables) {
        GaussDBWindowFunction.GaussDBFunction func = GaussDBWindowFunction.GaussDBFunction.getRandomFunction();

        // Generate PARTITION BY clause using table columns
        List<GaussDBExpression> partitionBy = new ArrayList<>();
        if (Randomly.getBoolean()) {
            partitionBy.add(GaussDBColumnReference.create(firstColumn, null));
        }

        // Generate ORDER BY clause using table columns
        List<GaussDBExpression> orderBy = new ArrayList<>();
        if (Randomly.getBoolean() && targetTables.getColumns().size() > 1) {
            orderBy.add(GaussDBColumnReference.create(targetTables.getColumns().get(1), null));
        }

        // Generate expression argument for aggregate window functions
        GaussDBExpression expr = null;
        if (func.isAggregateWindowFunction()) {
            expr = GaussDBColumnReference.create(firstColumn, null);
        }

        return new GaussDBWindowFunction(func, expr, partitionBy, orderBy);
    }

    /**
     * Generates a WHERE column expression for SonarOracle. Uses WhereColumnExpressionActions to select the expression
     * type.
     *
     * @param postfixText
     *            the postfix text containing the fetch column alias
     *
     * @return the generated expression for WHERE clause
     */
    public GaussDBExpression generateWhereColumnExpression(GaussDBPostfixText postfixText) {
        switch (Randomly.fromOptions(WhereColumnExpressionActions.values())) {
        case BINARY_COMPARISON_OPERATION:
            return new GaussDBBinaryComparisonOperation(new GaussDBManuelPredicate(postfixText.getText()),
                    generateConstant(), Randomly.fromOptions(BinaryComparisonOperator.values()));
        case AGGREGATE_FUNCTION:
            return new GaussDBAggregate(List.of(new GaussDBManuelPredicate(postfixText.getText())),
                    Randomly.fromOptions(GaussDBAggregateFunction.values()));
        case COLUMN:
            return new GaussDBManuelPredicate(postfixText.getText());
        default:
            throw new AssertionError();
        }
    }

    /**
     * Generates a window function expression for SonarOracle. Supports both zero-argument (ROW_NUMBER, RANK, etc.) and
     * one-argument (SUM, AVG, etc.) window functions. Simplified version similar to MySQL's generateWindowFuc() for use
     * in SonarOracle.
     *
     * @return the generated window function expression
     */
    public GaussDBExpression generateWindowFuc() {
        GaussDBWindowFunction.GaussDBFunction func = GaussDBWindowFunction.GaussDBFunction.getRandomFunction();

        // Generate PARTITION BY clause (optional, using a single column)
        List<GaussDBExpression> partitionBy = new ArrayList<>();
        if (Randomly.getBoolean() && columns != null && !columns.isEmpty()) {
            partitionBy.add(generateColumn());
        }

        // Generate ORDER BY clause (optional, using a single column)
        List<GaussDBExpression> orderBy = new ArrayList<>();
        if (Randomly.getBoolean() && columns != null && !columns.isEmpty()) {
            orderBy.add(generateColumn());
        }

        // Generate expression argument for aggregate window functions
        GaussDBExpression expr = null;
        if (func.isAggregateWindowFunction()) {
            expr = generateExpression(2);
        }

        return new GaussDBWindowFunction(func, expr, partitionBy, orderBy);
    }

    @Override
    public boolean mutate(GaussDBSelect select) {
        List<Function<GaussDBSelect, Boolean>> mutators = new ArrayList<>();
        mutators.add(this::mutateWhere);
        mutators.add(this::mutateGroupBy);
        mutators.add(this::mutateHaving);
        mutators.add(this::mutateAnd);
        mutators.add(this::mutateOr);
        mutators.add(this::mutateDistinct);
        return Randomly.fromList(mutators).apply(select);
    }

    boolean mutateDistinct(GaussDBSelect select) {
        if (select.getSelectType() != SelectType.ALL) {
            select.setSelectType(SelectType.ALL);
            return true;
        } else {
            select.setSelectType(SelectType.DISTINCT);
            return false;
        }
    }

    boolean mutateWhere(GaussDBSelect select) {
        boolean increase = select.getWhereClause() != null;
        if (increase) {
            select.setWhereClause(null);
        } else {
            select.setWhereClause(generateExpression());
        }
        return increase;
    }

    boolean mutateGroupBy(GaussDBSelect select) {
        boolean increase = !select.getGroupByExpressions().isEmpty();
        if (increase) {
            select.clearGroupByExpressions();
        } else {
            select.setGroupByExpressions(select.getFetchColumns());
        }
        return increase;
    }

    boolean mutateHaving(GaussDBSelect select) {
        if (select.getGroupByExpressions().isEmpty()) {
            select.setGroupByExpressions(select.getFetchColumns());
            select.setHavingClause(generateExpression());
            return false;
        } else {
            if (select.getHavingClause() == null) {
                select.setHavingClause(generateExpression());
                return false;
            } else {
                select.setHavingClause(null);
                return true;
            }
        }
    }

    boolean mutateAnd(GaussDBSelect select) {
        if (select.getWhereClause() == null) {
            select.setWhereClause(generateExpression());
        } else {
            GaussDBExpression newWhere = new GaussDBBinaryLogicalOperation(select.getWhereClause(),
                    generateExpression(), GaussDBBinaryLogicalOperator.AND);
            select.setWhereClause(newWhere);
        }
        return false;
    }

    boolean mutateOr(GaussDBSelect select) {
        if (select.getWhereClause() == null) {
            select.setWhereClause(generateExpression());
            return false;
        } else {
            GaussDBExpression newWhere = new GaussDBBinaryLogicalOperation(select.getWhereClause(),
                    generateExpression(), GaussDBBinaryLogicalOperator.OR);
            select.setWhereClause(newWhere);
            return true;
        }
    }

    @Override
    public List<GaussDBExpression> generateOrderBys() {
        return generateExpressions(Randomly.smallNumber() + 1);
    }

    @Override
    public GaussDBMExpressionGenerator setTablesAndColumns(AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        this.tables = tables.getTables();
        this.columns = tables.getColumns();
        return this;
    }
}
