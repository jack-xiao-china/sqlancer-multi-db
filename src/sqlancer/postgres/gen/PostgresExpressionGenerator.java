package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.util.UUID;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.gen.CERTGenerator;
import sqlancer.common.gen.ExpressionGenerator;
import sqlancer.common.gen.NoRECGenerator;
import sqlancer.common.gen.TLPWhereGenerator;
import sqlancer.postgres.PostgresBugs;
import sqlancer.postgres.PostgresCompoundDataType;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresProvider;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresRowValue;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTables;
import sqlancer.postgres.ast.PostgresAggregate;
import sqlancer.postgres.ast.PostgresAggregate.PostgresAggregateFunction;
import sqlancer.postgres.ast.PostgresBetweenOperation;
import sqlancer.postgres.ast.PostgresBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresBinaryArithmeticOperation.PostgresBinaryOperator;
import sqlancer.postgres.ast.PostgresBinaryBitOperation;
import sqlancer.postgres.ast.PostgresBinaryBitOperation.PostgresBinaryBitOperator;
import sqlancer.postgres.ast.PostgresBinaryComparisonOperation;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation.BinaryLogicalOperator;
import sqlancer.postgres.ast.PostgresBinaryJsonOperation;
import sqlancer.postgres.ast.PostgresBinaryJsonOperation.JsonOperator;
import sqlancer.postgres.ast.PostgresBinaryRangeOperation;
import sqlancer.postgres.ast.PostgresBinaryRangeOperation.PostgresBinaryRangeComparisonOperator;
import sqlancer.postgres.ast.PostgresBinaryRangeOperation.PostgresBinaryRangeOperator;
import sqlancer.postgres.ast.PostgresCastOperation;
import sqlancer.postgres.ast.PostgresCollate;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresConcatOperation;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresFunction;
import sqlancer.postgres.ast.PostgresFunction.PostgresFunctionWithResult;
import sqlancer.postgres.ast.PostgresFunctionWithUnknownResult;
import sqlancer.postgres.ast.PostgresInOperation;
import sqlancer.postgres.ast.PostgresJsonContainOperation;
import sqlancer.postgres.ast.PostgresJsonContainOperation.JsonContainOperator;
import sqlancer.postgres.ast.PostgresJoin;
import sqlancer.postgres.ast.PostgresJoin.PostgresJoinType;
import sqlancer.postgres.ast.PostgresLikeOperation;
import sqlancer.postgres.ast.PostgresOrderByTerm;
import sqlancer.postgres.ast.PostgresPOSIXRegularExpression;
import sqlancer.postgres.ast.PostgresPOSIXRegularExpression.POSIXRegex;
import sqlancer.postgres.ast.PostgresPostfixOperation;
import sqlancer.postgres.ast.PostgresPostfixOperation.PostfixOperator;
import sqlancer.postgres.ast.PostgresPostfixText;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresPrefixOperation.PrefixOperator;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.LockingClauseContext;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresSelect.PostgresSubquery;
import sqlancer.postgres.ast.PostgresSelect.SelectType;
import sqlancer.postgres.ast.PostgresSimilarTo;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.ast.PostgresTemporalBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresTemporalBinaryArithmeticOperation.TemporalBinaryOperator;
import sqlancer.postgres.ast.PostgresTemporalFunction;
import sqlancer.postgres.ast.PostgresTemporalFunction.TemporalFunctionKind;
import sqlancer.postgres.ast.PostgresWindowFunction;
import sqlancer.postgres.ast.PostgresWindowFunction.WindowFrame;
import sqlancer.postgres.ast.PostgresWindowFunction.WindowSpecification;

public class PostgresExpressionGenerator implements ExpressionGenerator<PostgresExpression>,
        NoRECGenerator<PostgresSelect, PostgresJoin, PostgresExpression, PostgresTable, PostgresColumn>,
        TLPWhereGenerator<PostgresSelect, PostgresJoin, PostgresExpression, PostgresTable, PostgresColumn>,
        CERTGenerator<PostgresSelect, PostgresJoin, PostgresExpression, PostgresTable, PostgresColumn> {

    private final int maxDepth;

    private final Randomly r;

    private List<PostgresColumn> columns;

    private List<PostgresTable> targetTables;

    private PostgresRowValue rw;

    private boolean expectedResult;

    private PostgresGlobalState globalState;

    private boolean allowAggregateFunctions;

    private LockingClauseContext lockingClauseContext = LockingClauseContext.DIRECT_SELECT;

    private final Map<String, Character> functionsAndTypes;

    private final List<Character> allowedFunctionTypes;

    public PostgresExpressionGenerator(PostgresGlobalState globalState) {
        this.r = globalState.getRandomly();
        this.maxDepth = globalState.getOptions().getMaxExpressionDepth();
        this.globalState = globalState;
        this.functionsAndTypes = globalState.getFunctionsAndTypes();
        this.allowedFunctionTypes = globalState.getAllowedFunctionTypes();
    }

    public PostgresExpressionGenerator setColumns(List<PostgresColumn> columns) {
        this.columns = columns;
        return this;
    }

    public PostgresExpressionGenerator setRowValue(PostgresRowValue rw) {
        this.rw = rw;
        return this;
    }

    public PostgresExpression generateExpression(int depth) {
        return generateExpression(depth, PostgresDataType.getRandomType());
    }

    @Override
    public List<PostgresExpression> generateOrderBys() {
        List<PostgresExpression> orderBys = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber(); i++) {
            PostgresExpression expr = PostgresColumnValue.create(Randomly.fromList(columns), null);
            orderBys.add(expr);
        }
        return orderBys;
    }

    private enum BooleanExpression {
        POSTFIX_OPERATOR, NOT, BINARY_LOGICAL_OPERATOR, BINARY_COMPARISON, FUNCTION, CAST, LIKE, BETWEEN, IN_OPERATION,
        SIMILAR_TO, POSIX_REGEX, BINARY_RANGE_COMPARISON, JSON_CONTAIN;
    }

    private PostgresExpression generateFunctionWithUnknownResult(int depth, PostgresDataType type) {
        List<PostgresFunctionWithUnknownResult> supportedFunctions = PostgresFunctionWithUnknownResult
                .getSupportedFunctions(type);
        // filters functions by allowed type (STABLE 's', IMMUTABLE 'i', VOLATILE 'v')
        supportedFunctions = supportedFunctions.stream()
                .filter(f -> allowedFunctionTypes.contains(functionsAndTypes.get(f.getName())))
                .collect(Collectors.toList());
        if (supportedFunctions.isEmpty()) {
            throw new IgnoreMeException();
        }
        PostgresFunctionWithUnknownResult randomFunction = Randomly.fromList(supportedFunctions);
        return new PostgresFunction(randomFunction, type, randomFunction.getArguments(type, this, depth + 1));
    }

    private PostgresExpression generateFunctionWithKnownResult(int depth, PostgresDataType type) {
        List<PostgresFunctionWithResult> functions = Stream.of(PostgresFunction.PostgresFunctionWithResult.values())
                .filter(f -> f.supportsReturnType(type)).collect(Collectors.toList());
        // filters functions by allowed type (STABLE 's', IMMUTABLE 'i', VOLATILE 'v')
        functions = functions.stream().filter(f -> allowedFunctionTypes.contains(functionsAndTypes.get(f.getName())))
                .collect(Collectors.toList());
        if (functions.isEmpty()) {
            throw new IgnoreMeException();
        }
        PostgresFunctionWithResult randomFunction = Randomly.fromList(functions);
        int nrArgs = randomFunction.getNrArgs();
        if (randomFunction.isVariadic()) {
            nrArgs += Randomly.smallNumber();
        }
        PostgresDataType[] argTypes = randomFunction.getInputTypesForReturnType(type, nrArgs);
        PostgresExpression[] args = new PostgresExpression[nrArgs];
        do {
            for (int i = 0; i < args.length; i++) {
                args[i] = generateExpression(depth + 1, argTypes[i]);
            }
        } while (!randomFunction.checkArguments(args));
        return new PostgresFunction(randomFunction, type, args);
    }

    private PostgresExpression generateBooleanExpression(int depth) {
        BooleanExpression option = Randomly.fromList(Arrays.asList(BooleanExpression.values()));
        switch (option) {
        case POSTFIX_OPERATOR:
            PostfixOperator random = PostfixOperator.getRandom();
            return PostgresPostfixOperation
                    .create(generateExpression(depth + 1, Randomly.fromOptions(random.getInputDataTypes())), random);
        case IN_OPERATION:
            return inOperation(depth + 1);
        case NOT:
            return new PostgresPrefixOperation(generateExpression(depth + 1, PostgresDataType.BOOLEAN),
                    PrefixOperator.NOT);
        case BINARY_LOGICAL_OPERATOR:
            PostgresExpression first = generateExpression(depth + 1, PostgresDataType.BOOLEAN);
            int nr = Randomly.smallNumber() + 1;
            for (int i = 0; i < nr; i++) {
                first = new PostgresBinaryLogicalOperation(first,
                        generateExpression(depth + 1, PostgresDataType.BOOLEAN), BinaryLogicalOperator.getRandom());
            }
            return first;
        case BINARY_COMPARISON:
            PostgresDataType dataType = getMeaningfulType();
            return generateComparison(depth, dataType);
        case CAST:
            /*
             * Avoid casting arbitrary types (e.g., numeric) to boolean, which is rejected by PostgreSQL.
             * Casting from a known-parseable text literal is allowed and still exercises cast handling while remaining
             * DB-valid.
             */
            return new PostgresCastOperation(
                    sqlancer.postgres.ast.PostgresConstant.createTextConstant(Randomly.fromOptions("true", "false")),
                    getCompoundDataType(PostgresDataType.BOOLEAN));
        case FUNCTION:
            return generateFunction(depth + 1, PostgresDataType.BOOLEAN);
        case LIKE:
            return new PostgresLikeOperation(generateExpression(depth + 1, PostgresDataType.TEXT),
                    generateExpression(depth + 1, PostgresDataType.TEXT));
        case BETWEEN:
            PostgresDataType type = getMeaningfulType();
            return new PostgresBetweenOperation(generateExpression(depth + 1, type),
                    generateExpression(depth + 1, type), generateExpression(depth + 1, type), Randomly.getBoolean());
        case SIMILAR_TO:
            assert !expectedResult;
            // TODO also generate the escape character
            return new PostgresSimilarTo(generateExpression(depth + 1, PostgresDataType.TEXT),
                    generateExpression(depth + 1, PostgresDataType.TEXT), null);
        case POSIX_REGEX:
            assert !expectedResult;
            return new PostgresPOSIXRegularExpression(generateExpression(depth + 1, PostgresDataType.TEXT),
                    generateExpression(depth + 1, PostgresDataType.TEXT), POSIXRegex.getRandom());
        case BINARY_RANGE_COMPARISON:
            // TODO element check
            return new PostgresBinaryRangeOperation(PostgresBinaryRangeComparisonOperator.getRandom(),
                    generateExpression(depth + 1, PostgresDataType.RANGE),
                    generateExpression(depth + 1, PostgresDataType.RANGE));
        case JSON_CONTAIN:
            // JSON containment operators (@>, <@, ?, ?|, ?&) work with JSONB only
            JsonContainOperator jsonOp = JsonContainOperator.getRandom();
            PostgresExpression left = generateJsonExpression(depth + 1, PostgresDataType.JSONB);
            PostgresExpression right;
            if (jsonOp.requiresArrayInput()) {
                // ?| and ?& require text array on right side
                right = generateConstant(r, PostgresCompoundDataType.createArray(PostgresDataType.TEXT));
            } else {
                right = generateJsonExpression(depth + 1, PostgresDataType.JSONB);
            }
            return new PostgresJsonContainOperation(jsonOp, left, right);
        default:
            throw new AssertionError();
        }
    }

    private PostgresDataType getMeaningfulType() {
        // make it more likely that the expression does not only consist of constant expressions
        if (Randomly.getBooleanWithSmallProbability() || columns == null || columns.isEmpty()) {
            return PostgresDataType.getRandomType();
        } else {
            return Randomly.fromList(columns).getType();
        }
    }

    private PostgresExpression generateFunction(int depth, PostgresDataType type) {
        if (Randomly.getBoolean()) {
            return generateFunctionWithKnownResult(depth, type);
        } else {
            return generateFunctionWithUnknownResult(depth, type);
        }
    }

    private PostgresExpression generateComparison(int depth, PostgresDataType dataType) {
        PostgresExpression leftExpr = generateExpression(depth + 1, dataType);
        PostgresExpression rightExpr = generateExpression(depth + 1, dataType);
        return getComparison(leftExpr, rightExpr);
    }

    private PostgresExpression getComparison(PostgresExpression leftExpr, PostgresExpression rightExpr) {
        PostgresBinaryComparisonOperation op = new PostgresBinaryComparisonOperation(leftExpr, rightExpr,
                PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator.getRandom());
        return op;
    }

    private PostgresExpression inOperation(int depth) {
        PostgresDataType type = PostgresDataType.getRandomType();
        PostgresExpression leftExpr = generateExpression(depth + 1, type);
        List<PostgresExpression> rightExpr = new ArrayList<>();
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            rightExpr.add(generateExpression(depth + 1, type));
        }
        return new PostgresInOperation(leftExpr, rightExpr, Randomly.getBoolean());
    }

    public static PostgresExpression generateExpression(PostgresGlobalState globalState, PostgresDataType type) {
        return new PostgresExpressionGenerator(globalState).generateExpression(0, type);
    }

    public static PostgresExpression generateExpression(PostgresGlobalState globalState, PostgresCompoundDataType type) {
        return new PostgresExpressionGenerator(globalState).generateExpression(0, type);
    }

    public PostgresExpression generateExpression(int depth, PostgresDataType originalType) {
        return generateExpression(depth, getCompoundDataType(originalType));
    }

    public PostgresExpression generateExpression(int depth, PostgresCompoundDataType originalType) {
        if (originalType.isArray()) {
            if (!filterColumns(originalType).isEmpty() && Randomly.getBoolean()) {
                return createColumnOfType(originalType);
            }
            return generateConstant(r, originalType);
        }
        PostgresDataType dataType = originalType.getDataType();
        if (dataType == PostgresDataType.REAL && Randomly.getBoolean()) {
            dataType = Randomly.fromOptions(PostgresDataType.INT, PostgresDataType.FLOAT);
        }
        if (dataType == PostgresDataType.FLOAT && Randomly.getBoolean()) {
            dataType = PostgresDataType.INT;
        }
        PostgresCompoundDataType effectiveType = getCompoundDataType(dataType);
        if (!filterColumns(effectiveType).isEmpty() && Randomly.getBoolean()) {
            return potentiallyWrapInCollate(effectiveType, createColumnOfType(effectiveType));
        }
        PostgresExpression exprInternal = generateExpressionInternal(depth, dataType);
        return potentiallyWrapInCollate(effectiveType, exprInternal);
    }

    private PostgresExpression potentiallyWrapInCollate(PostgresCompoundDataType dataType, PostgresExpression exprInternal) {
        return exprInternal;
    }

    private PostgresExpression generateExpressionInternal(int depth, PostgresDataType dataType) throws AssertionError {
        if (allowAggregateFunctions && Randomly.getBoolean()) {
            allowAggregateFunctions = false; // aggregate function calls cannot be nested
            return getAggregate(dataType);
        }
        if (Randomly.getBooleanWithRatherLowProbability() || depth > maxDepth) {
            // generic expression
            if (Randomly.getBoolean() || depth > maxDepth) {
                if (Randomly.getBooleanWithRatherLowProbability()) {
                    return generateConstant(r, dataType);
                } else {
                    if (filterColumns(dataType).isEmpty()) {
                        return generateConstant(r, dataType);
                    } else {
                        return createColumnOfType(dataType);
                    }
                }
            } else {
                if (dataType == PostgresDataType.ENUM) {
                    return generateConstant(r, dataType);
                }
                if (Randomly.getBoolean()) {
                    return new PostgresCastOperation(generateExpression(depth + 1), getCompoundDataType(dataType));
                } else {
                    return generateFunctionWithUnknownResult(depth, dataType);
                }
            }
        } else {
            switch (dataType) {
            case BOOLEAN:
                return generateBooleanExpression(depth);
            case INT:
                return generateIntExpression(depth);
            case TEXT:
                return generateTextExpression(depth);
            case VARCHAR:
            case CHAR:
                return new PostgresCastOperation(generateTextExpression(depth), getCompoundDataType(dataType));
            case DECIMAL:
            case REAL:
            case FLOAT:
            case MONEY:
            case INET:
                return generateConstant(r, dataType);
            case BIT:
                return generateBitExpression(depth);
            case RANGE:
                return generateRangeExpression(depth);
            case DATE:
            case TIME:
            case TIMETZ:
            case TIMESTAMP:
            case TIMESTAMPTZ:
            case INTERVAL:
                return generateTemporalExpression(depth, dataType);
            case JSON:
            case JSONB:
                return generateJsonExpression(depth, dataType);
            case UUID:
            case BYTEA:
            case ENUM:
                return generateConstant(r, dataType);
            default:
                throw new AssertionError(dataType);
            }
        }
    }

    public static PostgresCompoundDataType getCompoundDataType(PostgresDataType type) {
        if (type == PostgresDataType.ARRAY) {
            return getRandomArrayType();
        }
        switch (type) {
        case BOOLEAN:
        case DECIMAL: // TODO
        case FLOAT:
        case INT:
        case MONEY:
        case RANGE:
        case REAL:
        case INET:
        case DATE:
        case TIME:
        case TIMETZ:
        case TIMESTAMP:
        case TIMESTAMPTZ:
        case INTERVAL:
        case JSON:
        case JSONB:
        case UUID:
        case BYTEA:
        case ENUM:
            return PostgresCompoundDataType.create(type);
        case TEXT: // TODO
        case VARCHAR:
        case CHAR:
        case BIT:
            if (Randomly.getBoolean()) {
                return PostgresCompoundDataType.create(type);
            } else {
                return PostgresCompoundDataType.create(type, (int) Randomly.getNotCachedInteger(1, 1000));
            }
        default:
            throw new AssertionError(type);
        }

    }

    public static PostgresCompoundDataType getRandomArrayType() {
        return getRandomArrayType(1);
    }

    public static PostgresCompoundDataType getRandomArrayType(int minDimensions) {
        PostgresDataType elementType = Randomly.fromOptions(PostgresDataType.INT, PostgresDataType.BOOLEAN,
                PostgresDataType.TEXT, PostgresDataType.VARCHAR, PostgresDataType.CHAR, PostgresDataType.DATE,
                PostgresDataType.TIME, PostgresDataType.TIMESTAMP, PostgresDataType.TIMESTAMPTZ,
                PostgresDataType.INTERVAL);
        int dimensions = (int) Randomly.getNotCachedInteger(minDimensions, PostgresCompoundDataType.MAX_ARRAY_DIMENSIONS);
        return PostgresCompoundDataType.createArray(PostgresCompoundDataType.create(elementType), dimensions);
    }

    private enum RangeExpression {
        BINARY_OP;
    }

    private enum JsonExpression {
        CONSTANT, COLUMN, OPERATOR, FUNCTION
    }

    private PostgresExpression generateJsonExpression(int depth, PostgresDataType dataType) {
        JsonExpression option = Randomly.fromOptions(JsonExpression.values());

        switch (option) {
        case CONSTANT:
            return generateConstant(r, dataType);
        case COLUMN:
            List<PostgresColumn> jsonColumns = filterColumns(dataType);
            if (jsonColumns.isEmpty()) {
                return generateConstant(r, dataType);
            }
            PostgresColumn column = Randomly.fromList(jsonColumns);
            PostgresConstant value = rw == null ? null : rw.getValues().get(column);
            return PostgresColumnValue.create(column, value);
        case OPERATOR:
            // ->, ->>, #>, #>> operators
            JsonOperator op = JsonOperator.getRandom();
            PostgresExpression left;
            if (dataType == PostgresDataType.JSONB) {
                left = generateJsonExpression(depth + 1, PostgresDataType.JSONB);
            } else {
                left = generateJsonExpression(depth + 1, PostgresDataType.JSON);
            }
            PostgresExpression right;
            if (op == JsonOperator.GET_PATH || op == JsonOperator.GET_PATH_TEXT) {
                // Path operators require a path array like '{a,b}'
                right = generateJsonPathLiteral();
            } else {
                // Key operators require text or int
                if (Randomly.getBoolean()) {
                    right = generateConstant(r, PostgresDataType.TEXT);
                } else {
                    right = generateConstant(r, PostgresDataType.INT);
                }
            }
            return new PostgresBinaryJsonOperation(op, left, right);
        case FUNCTION:
            return generateFunctionWithUnknownResult(depth, dataType);
        default:
            throw new AssertionError(option);
        }
    }

    private PostgresExpression generateJsonPathLiteral() {
        // Generate a simple JSON path like '{a, b, c}'
        int depth = r.getInteger(1, 3);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < depth; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(generateSafeJsonString(r)).append("\"");
        }
        sb.append("}");
        return PostgresConstant.createTextConstant(sb.toString());
    }

    private PostgresExpression generateRangeExpression(int depth) {
        RangeExpression option;
        List<RangeExpression> validOptions = new ArrayList<>(Arrays.asList(RangeExpression.values()));
        option = Randomly.fromList(validOptions);
        switch (option) {
        case BINARY_OP:
            return new PostgresBinaryRangeOperation(PostgresBinaryRangeOperator.getRandom(),
                    generateExpression(depth + 1, PostgresDataType.RANGE),
                    generateExpression(depth + 1, PostgresDataType.RANGE));
        default:
            throw new AssertionError(option);
        }
    }

    private enum TextExpression {
        CAST, FUNCTION, CONCAT, COLLATE
    }

    private PostgresExpression generateTextExpression(int depth) {
        TextExpression option;
        List<TextExpression> validOptions = new ArrayList<>(Arrays.asList(TextExpression.values()));
        if (expectedResult) {
            validOptions.remove(TextExpression.COLLATE);
        }
        if (!globalState.getDbmsSpecificOptions().testCollations) {
            validOptions.remove(TextExpression.COLLATE);
        }
        option = Randomly.fromList(validOptions);

        switch (option) {
        case CAST:
            return new PostgresCastOperation(generateExpression(depth + 1), getCompoundDataType(PostgresDataType.TEXT));
        case FUNCTION:
            return generateFunction(depth + 1, PostgresDataType.TEXT);
        case CONCAT:
            return generateConcat(depth);
        case COLLATE:
            assert !expectedResult;
            return new PostgresCollate(generateExpression(depth + 1, PostgresDataType.TEXT), globalState == null
                    ? Randomly.fromOptions("C", "POSIX", "de_CH.utf8", "es_CR.utf8") : globalState.getRandomCollate());
        default:
            throw new AssertionError();
        }
    }

    public PostgresExpression generateWindowFunction(int depth, PostgresDataType returnType) {
        List<PostgresExpression> arguments = generateWindowFunctionArguments(depth);
        List<PostgresExpression> partitionBy = generatePartitionByExpressions(depth);
        List<PostgresOrderByTerm> orderBy = generateOrderByExpressions(depth);
        WindowFrame frame = generateWindowFrame();

        WindowSpecification windowSpec = new WindowSpecification(partitionBy, orderBy, frame);
        String functionName = selectWindowFunctionName();

        return new PostgresWindowFunction(functionName, arguments, windowSpec, returnType);
    }

    private List<PostgresExpression> generateWindowFunctionArguments(int depth) {
        List<PostgresExpression> arguments = new ArrayList<>();
        if (Randomly.getBoolean()) {
            arguments.add(generateExpression(depth + 1));
        }
        return arguments;
    }

    private List<PostgresExpression> generatePartitionByExpressions(int depth) {
        List<PostgresExpression> partitionBy = new ArrayList<>();
        if (Randomly.getBoolean()) {
            int count = Randomly.smallNumber();
            for (int i = 0; i < count; i++) {
                partitionBy.add(generateExpression(depth + 1));
            }
        }
        return partitionBy;
    }

    private List<PostgresOrderByTerm> generateOrderByExpressions(int depth) {
        List<PostgresOrderByTerm> orderBy = new ArrayList<>();
        if (Randomly.getBoolean()) {
            int count = Randomly.smallNumber();
            for (int i = 0; i < count; i++) {
                PostgresExpression expr = generateExpression(depth + 1);
                // Call the second constructor in PostgresOrderByTerm, might be removed in the future to have only one
                // constructor
                orderBy.add(new PostgresOrderByTerm(expr, Randomly.getBoolean()));
            }
        }
        return orderBy;
    }

    private WindowFrame generateWindowFrame() {
        if (Randomly.getBoolean()) {
            WindowFrame.FrameType frameType = Randomly.fromOptions(WindowFrame.FrameType.values());
            PostgresExpression startExpr = generateConstant(globalState.getRandomly(), PostgresDataType.INT);
            PostgresExpression endExpr = generateConstant(globalState.getRandomly(), PostgresDataType.INT);
            return new WindowFrame(frameType, startExpr, endExpr);
        }
        return null;
    }

    private String selectWindowFunctionName() {
        return Randomly.fromList(Arrays.asList("row_number", "rank", "dense_rank", "percent_rank", "cume_dist", "ntile",
                "lag", "lead", "first_value", "last_value", "nth_value"));
    }

    private PostgresExpression generateConcat(int depth) {
        PostgresExpression left = generateExpression(depth + 1, PostgresDataType.TEXT);
        PostgresExpression right = generateExpression(depth + 1);
        return new PostgresConcatOperation(left, right);
    }

    private enum BitExpression {
        BINARY_OPERATION
    };

    private PostgresExpression generateBitExpression(int depth) {
        BitExpression option;
        option = Randomly.fromOptions(BitExpression.values());
        switch (option) {
        case BINARY_OPERATION:
            return new PostgresBinaryBitOperation(PostgresBinaryBitOperator.getRandom(),
                    generateExpression(depth + 1, PostgresDataType.BIT),
                    generateExpression(depth + 1, PostgresDataType.BIT));
        default:
            throw new AssertionError();
        }
    }

    // Removed WINDOW_FUNCTION option from the integer expression generation.
    private enum IntExpression {
        UNARY_OPERATION, FUNCTION, CAST, BINARY_ARITHMETIC_EXPRESSION
    }

    private PostgresExpression generateIntExpression(int depth) {
        IntExpression option;
        option = Randomly.fromOptions(IntExpression.values());
        switch (option) {
        case CAST:
            return new PostgresCastOperation(generateExpression(depth + 1), getCompoundDataType(PostgresDataType.INT));
        case UNARY_OPERATION:
            PostgresExpression intExpression = generateExpression(depth + 1, PostgresDataType.INT);
            return new PostgresPrefixOperation(intExpression,
                    Randomly.getBoolean() ? PrefixOperator.UNARY_PLUS : PrefixOperator.UNARY_MINUS);
        case FUNCTION:
            return generateFunction(depth + 1, PostgresDataType.INT);
        case BINARY_ARITHMETIC_EXPRESSION:
            if (Randomly.getBooleanWithRatherLowProbability()) {
                return generateTemporalIntExpression(depth + 1);
            }
            return new PostgresBinaryArithmeticOperation(generateExpression(depth + 1, PostgresDataType.INT),
                    generateExpression(depth + 1, PostgresDataType.INT), PostgresBinaryOperator.getRandom());
        default:
            throw new AssertionError();
        }
    }

    private PostgresExpression createColumnOfType(PostgresDataType type) {
        List<PostgresColumn> columns = filterColumns(type);
        PostgresColumn fromList = Randomly.fromList(columns);
        PostgresConstant value = rw == null ? null : rw.getValues().get(fromList);
        return PostgresColumnValue.create(fromList, value);
    }

    private PostgresExpression createColumnOfType(PostgresCompoundDataType type) {
        List<PostgresColumn> columns = filterColumns(type);
        PostgresColumn fromList = Randomly.fromList(columns);
        PostgresConstant value = rw == null ? null : rw.getValues().get(fromList);
        return PostgresColumnValue.create(fromList, value);
    }

    final List<PostgresColumn> filterColumns(PostgresDataType type) {
        if (columns == null) {
            return Collections.emptyList();
        } else {
            return columns.stream().filter(c -> c.getType() == type).collect(Collectors.toList());
        }
    }

    final List<PostgresColumn> filterColumns(PostgresCompoundDataType type) {
        if (columns == null) {
            return Collections.emptyList();
        } else {
            return columns.stream().filter(c -> c.getCompoundType().equals(type)).collect(Collectors.toList());
        }
    }

    public PostgresExpression generateExpressionWithExpectedResult(PostgresDataType type) {
        this.expectedResult = true;
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState).setColumns(columns)
                .setRowValue(rw);
        PostgresExpression expr;
        do {
            expr = gen.generateExpression(type);
        } while (expr.getExpectedValue() == null);
        return expr;
    }

    public static PostgresExpression generateConstant(Randomly r, PostgresDataType type) {
        return generateConstant(r, getCompoundDataType(type));
    }

    public static PostgresExpression generateConstant(Randomly r, PostgresCompoundDataType type) {
        if (Randomly.getBooleanWithSmallProbability()) {
            return PostgresConstant.createNullConstant();
        }
        if (type.isArray()) {
            return PostgresConstant.createArrayConstant(generateArrayElements(r, type.getElemType()), type.getElemType());
        }
        PostgresDataType baseType = type.getDataType();
        switch (baseType) {
        case INT:
            if (Randomly.getBooleanWithSmallProbability()) {
                return PostgresConstant.createTextConstant(String.valueOf(r.getInteger()));
            } else {
                return PostgresConstant.createIntConstant(r.getInteger());
            }
        case BOOLEAN:
            if (Randomly.getBooleanWithSmallProbability()) {
                return PostgresConstant
                        .createTextConstant(Randomly.fromOptions("TR", "TRUE", "FA", "FALSE", "0", "1", "ON", "off"));
            } else {
                return PostgresConstant.createBooleanConstant(Randomly.getBoolean());
            }
        case TEXT:
            return PostgresConstant.createTextConstant(r.getString());
        case VARCHAR:
            return PostgresConstant.createVarcharConstant(r.getString());
        case CHAR:
            return PostgresConstant.createCharConstant(r.getString());
        case DECIMAL:
            return PostgresConstant.createDecimalConstant(r.getRandomBigDecimal());
        case FLOAT:
            return PostgresConstant.createFloatConstant((float) r.getDouble());
        case REAL:
            return PostgresConstant.createDoubleConstant(r.getDouble());
        case RANGE:
            return PostgresConstant.createRange(r.getInteger(), Randomly.getBoolean(), r.getInteger(),
                    Randomly.getBoolean());
        case MONEY:
            return new PostgresCastOperation(generateConstant(r, PostgresDataType.FLOAT),
                    getCompoundDataType(PostgresDataType.MONEY));
        case INET:
            return PostgresConstant.createInetConstant(getRandomInet(r));
        case BIT:
            return PostgresConstant.createBitConstant(r.getInteger());
        case DATE: {
            int year = r.getInteger(2000, 2030);
            int month = r.getInteger(1, 12);
            int day = r.getInteger(1, 28);
            return PostgresConstant.createDateConstant(LocalDate.of(year, month, day));
        }
        case TIME: {
            int hour = r.getInteger(0, 23);
            int minute = r.getInteger(0, 59);
            int second = r.getInteger(0, 59);
            return PostgresConstant.createTimeConstant(LocalTime.of(hour, minute, second));
        }
        case TIMETZ: {
            int hour = r.getInteger(0, 23);
            int minute = r.getInteger(0, 59);
            int second = r.getInteger(0, 59);
            int offsetHours = Randomly.fromOptions(-5, 0, 8);
            return PostgresConstant.createTimeWithTimeZoneConstant(
                    OffsetTime.of(hour, minute, second, 0, java.time.ZoneOffset.ofHours(offsetHours)));
        }
        case TIMESTAMP: {
            int year = r.getInteger(2000, 2030);
            int month = r.getInteger(1, 12);
            int day = r.getInteger(1, 28);
            int hour = r.getInteger(0, 23);
            int minute = r.getInteger(0, 59);
            int second = r.getInteger(0, 59);
            return PostgresConstant.createTimestampConstant(LocalDateTime.of(year, month, day, hour, minute, second));
        }
        case TIMESTAMPTZ: {
            long epoch = r.getLong(946684800L /* 2000-01-01 */, 1924991999L /* 2030-12-31 */);
            return PostgresConstant.createTimestamptzConstant(Instant.ofEpochSecond(epoch));
        }
        case INTERVAL: {
            long seconds = r.getLong(0, 86400L * 30); // up to 30 days
            return PostgresConstant.createIntervalSecondsConstant(seconds);
        }
        case JSON:
        case JSONB: {
            String json = generateValidJsonLiteral(r, 0);
            if (baseType == PostgresDataType.JSONB) {
                // Prefer explicit ::jsonb to minimize planner/unknown-type ambiguity.
                return new PostgresPostfixText(PostgresConstant.createTextConstant(json), "::jsonb", null,
                        PostgresDataType.JSONB);
            } else {
                return new PostgresPostfixText(PostgresConstant.createTextConstant(json), "::json", null,
                        PostgresDataType.JSON);
            }
        }
        case UUID: {
            // Deterministic per Randomly seed (avoid UUID.randomUUID()).
            UUID uuid = new UUID(r.getLong(Long.MIN_VALUE, Long.MAX_VALUE), r.getLong(Long.MIN_VALUE, Long.MAX_VALUE));
            return PostgresConstant.createUUIDConstant(uuid.toString());
        }
        case BYTEA: {
            return PostgresConstant.createByteaConstant("DEADBEEF");
        }
        case ENUM: {
            String typeName = PostgresProvider.getRandomEnumTypeName();
            return PostgresConstant.createEnumConstant(typeName, PostgresProvider.getRandomEnumLabel(typeName, r));
        }
        case ARRAY:
            return generateConstant(r, getRandomArrayType());
        default:
            throw new AssertionError(baseType);
        }
    }

    private static List<PostgresConstant> generateArrayElements(Randomly r, PostgresCompoundDataType elementType) {
        int n = r.getInteger(0, 3);
        List<PostgresConstant> elems = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PostgresExpression e = generateConstant(r, elementType);
            elems.add((PostgresConstant) e);
        }
        return elems;
    }

    private static String generateValidJsonLiteral(Randomly r, int depth) {
        // Keep literals small and always valid JSON (no backslashes/control chars).
        if (depth > 2) {
            return "null";
        }
        int option = r.getInteger(0, 5);
        switch (option) {
        case 0:
            return "null";
        case 1:
            return Randomly.getBoolean() ? "true" : "false";
        case 2:
            // number
            return String.valueOf(r.getInteger(-10, 10));
        case 3:
            // string
            return "\"" + generateSafeJsonString(r) + "\"";
        case 4:
            // array
            return "[" + generateValidJsonLiteral(r, depth + 1) + "]";
        case 5:
            // object
            return "{\"a\":" + generateValidJsonLiteral(r, depth + 1) + ",\"b\":\"" + generateSafeJsonString(r)
                    + "\"}";
        default:
            throw new AssertionError(option);
        }
    }

    private static String generateSafeJsonString(Randomly r) {
        // Only ASCII letters/digits/spaces to avoid escaping and server-dependent string settings.
        int len = r.getInteger(0, 8);
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(r.getInteger(0, alphabet.length() - 1)));
        }
        return sb.toString().trim();
    }

    private static String getRandomInet(Randomly r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i != 0) {
                sb.append('.');
            }
            sb.append(r.getInteger() & 255);
        }
        return sb.toString();
    }

    public static PostgresExpression generateExpression(PostgresGlobalState globalState, List<PostgresColumn> columns,
            PostgresDataType type) {
        return new PostgresExpressionGenerator(globalState).setColumns(columns).generateExpression(0, type);
    }

    public static PostgresExpression generateExpression(PostgresGlobalState globalState, List<PostgresColumn> columns,
            PostgresCompoundDataType type) {
        return new PostgresExpressionGenerator(globalState).setColumns(columns).generateExpression(0, type);
    }

    public static PostgresExpression generateExpression(PostgresGlobalState globalState, List<PostgresColumn> columns) {
        return new PostgresExpressionGenerator(globalState).setColumns(columns).generateExpression(0);

    }

    public List<PostgresExpression> generateExpressions(int nr) {
        List<PostgresExpression> expressions = new ArrayList<>();
        for (int i = 0; i < nr; i++) {
            expressions.add(generateExpression(0));
        }
        return expressions;
    }

    public PostgresExpression generateExpression(PostgresDataType dataType) {
        return generateExpression(0, dataType);
    }

    public PostgresExpression generateExpression(PostgresCompoundDataType dataType) {
        return generateExpression(0, dataType);
    }

    private static boolean isTemporalType(PostgresDataType type) {
        switch (type) {
        case DATE:
        case TIME:
        case TIMETZ:
        case TIMESTAMP:
        case TIMESTAMPTZ:
        case INTERVAL:
            return true;
        default:
            return false;
        }
    }

    private enum TemporalExpression {
        CONSTANT, CAST, FUNCTION, ARITHMETIC
    }

    private PostgresExpression generateTemporalExpression(int depth, PostgresDataType dataType) {
        if (depth > maxDepth || Randomly.getBooleanWithRatherLowProbability()) {
            return generateConstant(r, dataType);
        }
        List<TemporalExpression> options = new ArrayList<>(Arrays.asList(TemporalExpression.values()));
        if (dataType == PostgresDataType.DATE) {
            options.remove(TemporalExpression.FUNCTION);
        }
        if (!canGenerateTemporalFunction(dataType)) {
            options.remove(TemporalExpression.FUNCTION);
        }
        if (!canGenerateTemporalArithmetic(dataType)) {
            options.remove(TemporalExpression.ARITHMETIC);
        }
        TemporalExpression option = Randomly.fromList(options);
        switch (option) {
        case CONSTANT:
            return generateConstant(r, dataType);
        case CAST:
            return new PostgresCastOperation(PostgresConstant.createTextConstant(getStableTemporalTextValue(dataType)),
                    getCompoundDataType(dataType));
        case FUNCTION:
            return generateTemporalFunctionExpression(depth + 1, dataType);
        case ARITHMETIC:
            return generateTemporalArithmeticExpression(depth + 1, dataType);
        default:
            throw new AssertionError(option);
        }
    }

    private boolean canGenerateTemporalFunction(PostgresDataType dataType) {
        return dataType == PostgresDataType.INTERVAL || dataType == PostgresDataType.TIMESTAMP
                || dataType == PostgresDataType.TIMESTAMPTZ;
    }

    private boolean canGenerateTemporalArithmetic(PostgresDataType dataType) {
        return isTemporalType(dataType) && dataType != PostgresDataType.DATE;
    }

    private PostgresExpression generateTemporalFunctionExpression(int depth, PostgresDataType returnType) {
        switch (returnType) {
        case INTERVAL:
            return generateIntervalFunction(depth);
        case TIMESTAMP:
            if (Randomly.getBoolean()) {
                return new PostgresTemporalFunction(TemporalFunctionKind.DATE_TRUNC, PostgresDataType.TIMESTAMP,
                        getTimestampField(), true, generateExpression(depth + 1, PostgresDataType.TIMESTAMP));
            }
            return new PostgresTemporalFunction(TemporalFunctionKind.TIMEZONE, PostgresDataType.TIMESTAMP,
                    getTimezoneLiteral(), true, generateExpression(depth + 1, PostgresDataType.TIMESTAMPTZ));
        case TIMESTAMPTZ:
            return new PostgresTemporalFunction(TemporalFunctionKind.DATE_TRUNC, PostgresDataType.TIMESTAMPTZ,
                    getTimestampField(), true, generateExpression(depth + 1, PostgresDataType.TIMESTAMPTZ));
        default:
            throw new AssertionError(returnType);
        }
    }

    private PostgresExpression generateIntervalFunction(int depth) {
        switch (Randomly.fromOptions(TemporalFunctionKind.MAKE_INTERVAL, TemporalFunctionKind.JUSTIFY_DAYS,
                TemporalFunctionKind.JUSTIFY_HOURS, TemporalFunctionKind.JUSTIFY_INTERVAL)) {
        case MAKE_INTERVAL:
            return new PostgresTemporalFunction(TemporalFunctionKind.MAKE_INTERVAL, PostgresDataType.INTERVAL, null,
                    true, generateExpression(depth + 1, PostgresDataType.INT),
                    generateExpression(depth + 1, PostgresDataType.INT), generateExpression(depth + 1, PostgresDataType.INT),
                    generateExpression(depth + 1, PostgresDataType.INT), generateExpression(depth + 1, PostgresDataType.INT),
                    generateExpression(depth + 1, PostgresDataType.INT), generateExpression(depth + 1, PostgresDataType.INT));
        case JUSTIFY_DAYS:
            return new PostgresTemporalFunction(TemporalFunctionKind.JUSTIFY_DAYS, PostgresDataType.INTERVAL, null,
                    true, generateExpression(depth + 1, PostgresDataType.INTERVAL));
        case JUSTIFY_HOURS:
            return new PostgresTemporalFunction(TemporalFunctionKind.JUSTIFY_HOURS, PostgresDataType.INTERVAL, null,
                    true, generateExpression(depth + 1, PostgresDataType.INTERVAL));
        case JUSTIFY_INTERVAL:
            return new PostgresTemporalFunction(TemporalFunctionKind.JUSTIFY_INTERVAL, PostgresDataType.INTERVAL,
                    null, true, generateExpression(depth + 1, PostgresDataType.INTERVAL));
        default:
            throw new AssertionError();
        }
    }

    private PostgresExpression generateTemporalArithmeticExpression(int depth, PostgresDataType returnType) {
        switch (returnType) {
        case TIME:
            return new PostgresTemporalBinaryArithmeticOperation(generateExpression(depth + 1, PostgresDataType.TIME),
                    generateExpression(depth + 1, PostgresDataType.INTERVAL),
                    Randomly.fromOptions(TemporalBinaryOperator.values()), PostgresDataType.TIME);
        case TIMETZ:
            return new PostgresTemporalBinaryArithmeticOperation(generateExpression(depth + 1, PostgresDataType.TIMETZ),
                    generateExpression(depth + 1, PostgresDataType.INTERVAL),
                    Randomly.fromOptions(TemporalBinaryOperator.values()), PostgresDataType.TIMETZ);
        case TIMESTAMP:
            if (Randomly.getBoolean()) {
                return new PostgresTemporalBinaryArithmeticOperation(
                        generateExpression(depth + 1, PostgresDataType.TIMESTAMP),
                        generateExpression(depth + 1, PostgresDataType.INTERVAL),
                        Randomly.fromOptions(TemporalBinaryOperator.values()), PostgresDataType.TIMESTAMP);
            }
            return new PostgresTemporalBinaryArithmeticOperation(generateExpression(depth + 1, PostgresDataType.DATE),
                    generateExpression(depth + 1, PostgresDataType.INTERVAL),
                    Randomly.fromOptions(TemporalBinaryOperator.values()), PostgresDataType.TIMESTAMP);
        case TIMESTAMPTZ:
            return new PostgresTemporalBinaryArithmeticOperation(
                    generateExpression(depth + 1, PostgresDataType.TIMESTAMPTZ),
                    generateExpression(depth + 1, PostgresDataType.INTERVAL),
                    Randomly.fromOptions(TemporalBinaryOperator.values()), PostgresDataType.TIMESTAMPTZ);
        case INTERVAL:
            if (Randomly.getBoolean()) {
                return new PostgresTemporalBinaryArithmeticOperation(
                        generateExpression(depth + 1, PostgresDataType.INTERVAL),
                        generateExpression(depth + 1, PostgresDataType.INTERVAL),
                        Randomly.fromOptions(TemporalBinaryOperator.values()), PostgresDataType.INTERVAL);
            } else if (Randomly.getBoolean()) {
                return new PostgresTemporalBinaryArithmeticOperation(
                        generateExpression(depth + 1, PostgresDataType.TIMESTAMP),
                        generateExpression(depth + 1, PostgresDataType.TIMESTAMP),
                        TemporalBinaryOperator.SUBTRACTION, PostgresDataType.INTERVAL);
            }
            return new PostgresTemporalBinaryArithmeticOperation(
                    generateExpression(depth + 1, PostgresDataType.TIMESTAMPTZ),
                    generateExpression(depth + 1, PostgresDataType.TIMESTAMPTZ), TemporalBinaryOperator.SUBTRACTION,
                    PostgresDataType.INTERVAL);
        default:
            throw new AssertionError(returnType);
        }
    }

    private PostgresExpression generateTemporalIntExpression(int depth) {
        if (Randomly.getBoolean()) {
            return new PostgresTemporalBinaryArithmeticOperation(generateExpression(depth + 1, PostgresDataType.DATE),
                    generateExpression(depth + 1, PostgresDataType.DATE), TemporalBinaryOperator.SUBTRACTION,
                    PostgresDataType.INT);
        }
        PostgresDataType sourceType = Randomly.fromOptions(PostgresDataType.DATE, PostgresDataType.TIME,
                PostgresDataType.TIMETZ, PostgresDataType.TIMESTAMP, PostgresDataType.TIMESTAMPTZ,
                PostgresDataType.INTERVAL);
        String field = getTemporalField(sourceType);
        TemporalFunctionKind kind = Randomly.fromOptions(TemporalFunctionKind.DATE_PART, TemporalFunctionKind.EXTRACT);
        return new PostgresTemporalFunction(kind, PostgresDataType.INT, field, true,
                generateExpression(depth + 1, sourceType));
    }

    private String getStableTemporalTextValue(PostgresDataType type) {
        switch (type) {
        case DATE:
            return String.format("%04d-%02d-%02d", r.getInteger(2000, 2030), r.getInteger(1, 12), r.getInteger(1, 28));
        case TIME:
            return String.format("%02d:%02d:%02d", r.getInteger(0, 23), r.getInteger(0, 59), r.getInteger(0, 59));
        case TIMETZ:
            return String.format("%02d:%02d:%02d%s", r.getInteger(0, 23), r.getInteger(0, 59), r.getInteger(0, 59),
                    Randomly.fromOptions("+00:00", "+08:00", "-05:00"));
        case TIMESTAMP:
            return String.format("%04d-%02d-%02d %02d:%02d:%02d", r.getInteger(2000, 2030), r.getInteger(1, 12),
                    r.getInteger(1, 28), r.getInteger(0, 23), r.getInteger(0, 59), r.getInteger(0, 59));
        case TIMESTAMPTZ:
            return getStableTemporalTextValue(PostgresDataType.TIMESTAMP) + Randomly.fromOptions("+00:00", "+08:00", "-05:00");
        case INTERVAL:
            return String.format("%d days %02d:%02d:%02d", r.getInteger(0, 30), r.getInteger(0, 23), r.getInteger(0, 59),
                    r.getInteger(0, 59));
        default:
            throw new AssertionError(type);
        }
    }

    private String getTemporalField(PostgresDataType sourceType) {
        switch (sourceType) {
        case DATE:
            return Randomly.fromOptions("year", "month", "day");
        case TIME:
        case TIMETZ:
            return Randomly.fromOptions("hour", "minute", "second");
        case TIMESTAMP:
        case TIMESTAMPTZ:
        case INTERVAL:
            return Randomly.fromOptions("year", "month", "day", "hour", "minute", "second");
        default:
            throw new AssertionError(sourceType);
        }
    }

    private String getTimestampField() {
        return Randomly.fromOptions("year", "month", "day", "hour", "minute", "second");
    }

    private String getTimezoneLiteral() {
        return Randomly.fromOptions("UTC", "+00:00", "+08:00", "-05:00");
    }

    public PostgresExpressionGenerator setGlobalState(PostgresGlobalState globalState) {
        this.globalState = globalState;
        return this;
    }

    public PostgresExpression generateHavingClause() {
        this.allowAggregateFunctions = true;
        PostgresExpression expression = generateExpression(PostgresDataType.BOOLEAN);
        this.allowAggregateFunctions = false;
        return expression;
    }

    public PostgresExpression generateAggregate() {
        return getAggregate(PostgresDataType.getRandomType());
    }

    private PostgresExpression getAggregate(PostgresDataType dataType) {
        List<PostgresAggregateFunction> aggregates = PostgresAggregateFunction.getAggregates(dataType);
        PostgresAggregateFunction agg = Randomly.fromList(aggregates);
        return generateArgsForAggregate(dataType, agg);
    }

    public PostgresAggregate generateArgsForAggregate(PostgresDataType dataType, PostgresAggregateFunction agg) {
        List<PostgresDataType> types = agg.getTypes(dataType);
        List<PostgresExpression> args = new ArrayList<>();
        for (PostgresDataType argType : types) {
            args.add(generateExpression(argType));
        }
        return new PostgresAggregate(args, agg);
    }

    public PostgresExpressionGenerator allowAggregates(boolean value) {
        allowAggregateFunctions = value;
        return this;
    }

    public PostgresExpressionGenerator setLockingClauseContext(LockingClauseContext lockingClauseContext) {
        this.lockingClauseContext = lockingClauseContext;
        return this;
    }

    public static PostgresSubquery createSubquery(PostgresGlobalState globalState, String name, PostgresTables tables) {
        return createSubquery(globalState, name, tables, LockingClauseContext.STRUCTURAL);
    }

    public static List<String> getLockableTableRefs(PostgresSelect select) {
        List<String> refs = new ArrayList<>();
        select.getFromList().stream().filter(PostgresFromTable.class::isInstance).map(PostgresFromTable.class::cast)
                .map(PostgresFromTable::getTable).filter(t -> !t.isView()).map(t -> t.getName()).forEach(refs::add);
        select.getJoinClauses().stream()
                .filter(j -> j.getType() == PostgresJoinType.INNER || j.getType() == PostgresJoinType.CROSS)
                .map(PostgresJoin::getTableReference).filter(PostgresFromTable.class::isInstance)
                .map(PostgresFromTable.class::cast).map(PostgresFromTable::getTable).filter(t -> !t.isView())
                .map(t -> t.getName()).forEach(refs::add);
        return refs;
    }

    public static PostgresSubquery createSubquery(PostgresGlobalState globalState, String name, PostgresTables tables,
            LockingClauseContext lockingClauseContext) {
        List<PostgresExpression> columns = new ArrayList<>();
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState).setColumns(tables.getColumns());
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            columns.add(gen.generateExpression(0));
        }
        PostgresSelect select = new PostgresSelect();
        select.setFromList(tables.getTables().stream().map(t -> new PostgresFromTable(t, Randomly.getBoolean()))
                .collect(Collectors.toList()));
        select.setFetchColumns(columns);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression(0, PostgresDataType.BOOLEAN));
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(gen.generateOrderBys());
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            if (Randomly.getBoolean()) {
                select.setOffsetClause(
                        PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            }
        }
        select.configureForClause(lockingClauseContext);
        return new PostgresSubquery(select, name);
    }

    @Override
    public PostgresExpression generatePredicate() {
        return generateExpression(PostgresDataType.BOOLEAN);
    }

    @Override
    public PostgresExpression negatePredicate(PostgresExpression predicate) {
        return new PostgresPrefixOperation(predicate, PostgresPrefixOperation.PrefixOperator.NOT);
    }

    @Override
    public PostgresExpression isNull(PostgresExpression expr) {
        return new PostgresPostfixOperation(expr, PostfixOperator.IS_NULL);
    }

    @Override
    public PostgresExpressionGenerator setTablesAndColumns(
            sqlancer.common.schema.AbstractTables<PostgresTable, PostgresColumn> targetTables) {
        this.targetTables = targetTables.getTables();
        this.columns = targetTables.getColumns();
        return this;
    }

    @Override
    public PostgresExpression generateBooleanExpression() {
        return generateExpression(PostgresDataType.BOOLEAN);
    }

    @Override
    public PostgresSelect generateSelect() {
        return generateSelect(lockingClauseContext);
    }

    public PostgresSelect generateSelect(LockingClauseContext lockingClauseContext) {
        PostgresSelect select = new PostgresSelect();

        if (Randomly.getBooleanWithRatherLowProbability()) {
            List<PostgresExpression> windowFunctions = generateWindowFunctions();
            select.setWindowFunctions(windowFunctions);
        }

        select.configureForClause(lockingClauseContext);

        return select;
    }

    private List<PostgresExpression> generateWindowFunctions() {
        List<PostgresExpression> windowFunctions = new ArrayList<>();
        int numWindowFunctions = Randomly.smallNumber();
        for (int i = 0; i < numWindowFunctions; i++) {
            windowFunctions.add(generateWindowFunction(0,
                    Randomly.fromList(Arrays.asList(PostgresDataType.INT, PostgresDataType.FLOAT))));
        }
        return windowFunctions;
    }

    @Override
    public List<PostgresJoin> getRandomJoinClauses() {
        List<PostgresJoin> joinStatements = new ArrayList<>();
        for (int i = 1; i < targetTables.size(); i++) {
            PostgresExpression joinClause = generateExpression(PostgresDataType.BOOLEAN);
            PostgresTable table = Randomly.fromList(targetTables);
            targetTables.remove(table);
            PostgresJoinType options = PostgresJoinType.getRandom();
            PostgresJoin j = new PostgresJoin(new PostgresFromTable(table, Randomly.getBoolean()), joinClause, options);
            joinStatements.add(j);
        }
        // JOIN subqueries
        for (int i = 0; i < Randomly.smallNumber(); i++) {
            PostgresTables subqueryTables = globalState.getSchema().getRandomTableNonEmptyTables();
            PostgresSubquery subquery = createSubquery(globalState, String.format("sub%d", i), subqueryTables,
                    LockingClauseContext.STRUCTURAL);
            PostgresExpression joinClause = generateExpression(PostgresDataType.BOOLEAN);
            PostgresJoinType options = PostgresJoinType.getRandom();
            PostgresJoin j = new PostgresJoin(subquery, joinClause, options);
            joinStatements.add(j);
        }
        return joinStatements;
    }

    @Override
    public List<PostgresExpression> getTableRefs() {
        return targetTables.stream().map(t -> new PostgresFromTable(t, Randomly.getBoolean()))
                .collect(Collectors.toList());
    }

    @Override
    public List<PostgresExpression> generateFetchColumns(boolean shouldCreateDummy) {
        if (shouldCreateDummy && Randomly.getBooleanWithRatherLowProbability()) {
            return Arrays.asList(new PostgresColumnValue(PostgresColumn.createDummy("*"), null));
        }
        allowAggregateFunctions = true;
        List<PostgresExpression> fetchColumns = new ArrayList<>();
        List<PostgresColumn> targetColumns = Randomly.nonEmptySubset(columns);
        for (PostgresColumn c : targetColumns) {
            fetchColumns.add(new PostgresColumnValue(c, null));
        }
        allowAggregateFunctions = false;
        return fetchColumns;
    }

    @Override
    public String generateOptimizedQueryString(PostgresSelect select, PostgresExpression whereCondition,
            boolean shouldUseAggregate) {
        PostgresColumnValue allColumns = new PostgresColumnValue(PostgresColumn.createDummy("*"), null);
        if (shouldUseAggregate) {
            select.setFetchColumns(
                    Arrays.asList(new PostgresAggregate(List.of(allColumns), PostgresAggregateFunction.COUNT)));
        } else {
            select.setFetchColumns(Arrays.asList(allColumns));
        }
        select.setWhereClause(whereCondition);
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(generateOrderBys());
        }
        select.setSelectType(SelectType.ALL);
        return select.asString();
    }

    @Override
    public String generateUnoptimizedQueryString(PostgresSelect select, PostgresExpression whereCondition) {
        PostgresCastOperation isTrue = new PostgresCastOperation(whereCondition,
                PostgresCompoundDataType.create(PostgresDataType.INT));
        PostgresPostfixText asText = new PostgresPostfixText(isTrue, " as count", null, PostgresDataType.INT);
        select.setFetchColumns(Arrays.asList(asText));
        select.setWhereClause(null);
        select.setOrderByClauses(List.of());
        select.setSelectType(SelectType.ALL);

        return "SELECT SUM(count) FROM (" + select.asString() + ") as res";
    }

    @Override
    public String generateExplainQuery(PostgresSelect select) {
        return "EXPLAIN " + select.asString();
    }

    @Override
    public boolean mutate(PostgresSelect select) {
        List<Function<PostgresSelect, Boolean>> mutators = new ArrayList<>();

        mutators.add(this::mutateJoin);
        mutators.add(this::mutateWhere);
        mutators.add(this::mutateGroupBy);
        mutators.add(this::mutateHaving);
        mutators.add(this::mutateWindowFunction);
        if (!PostgresBugs.bug18643) {
            mutators.add(this::mutateAnd);
            mutators.add(this::mutateOr);
        }
        mutators.add(this::mutateDistinct);

        return Randomly.fromList(mutators).apply(select);
    }

    private boolean mutateWindowFunction(PostgresSelect select) {
        List<PostgresExpression> windowFunctions = select.getWindowFunctions();
        if (windowFunctions == null || windowFunctions.isEmpty()) {
            windowFunctions = new ArrayList<>();
            windowFunctions.add(generateWindowFunction(0, PostgresDataType.INT));
            select.setWindowFunctions(windowFunctions);
            return false;
        } else {
            windowFunctions.remove(Randomly.fromList(windowFunctions));
            if (windowFunctions.isEmpty()) {
                select.setWindowFunctions(null);
            }
            return true;
        }
    }

    boolean mutateJoin(PostgresSelect select) {
        if (select.getJoinList().isEmpty()) {
            return false;
        }
        PostgresJoin join = (PostgresJoin) Randomly.fromList(select.getJoinList());

        // Exclude CROSS for on condition
        if (join.getType() == PostgresJoinType.CROSS) {
            List<PostgresColumn> columns = new ArrayList<>();
            columns.addAll(((PostgresTableReference) join.getLeftTable()).getTable().getColumns());
            columns.addAll(((PostgresTableReference) join.getRightTable()).getTable().getColumns());
            PostgresExpressionGenerator joinGen2 = new PostgresExpressionGenerator(globalState).setColumns(columns);
            join.setOnClause(joinGen2.generateExpression(0, PostgresDataType.BOOLEAN));
        }

        PostgresJoinType newJoinType = PostgresJoinType.INNER;
        if (join.getType() == PostgresJoinType.LEFT || join.getType() == PostgresJoinType.RIGHT) {
            newJoinType = PostgresJoinType.getRandomExcept(PostgresJoinType.LEFT, PostgresJoinType.RIGHT);
        } else {
            newJoinType = PostgresJoinType.getRandomExcept(join.getType());
        }
        boolean increase = join.getType().ordinal() < newJoinType.ordinal();
        join.setType(newJoinType);
        if (newJoinType == PostgresJoinType.CROSS) {
            join.setOnClause(null);
        }
        return increase;
    }

    boolean mutateDistinct(PostgresSelect select) {
        PostgresSelect.SelectType selectType = select.getSelectOption();
        if (selectType != PostgresSelect.SelectType.ALL) {
            select.setSelectType(PostgresSelect.SelectType.ALL);
            return true;
        } else {
            select.setSelectType(PostgresSelect.SelectType.DISTINCT);
            return false;
        }
    }

    boolean mutateWhere(PostgresSelect select) {
        boolean increase = select.getWhereClause() != null;
        if (increase) {
            select.setWhereClause(null);
        } else {
            select.setWhereClause(generateExpression(0, PostgresDataType.BOOLEAN));
        }
        return increase;
    }

    boolean mutateGroupBy(PostgresSelect select) {
        boolean increase = !select.getGroupByExpressions().isEmpty();
        if (increase) {
            select.clearGroupByExpressions();
        } else {
            select.setGroupByExpressions(select.getFetchColumns());
        }
        return increase;
    }

    boolean mutateHaving(PostgresSelect select) {
        if (select.getGroupByExpressions().isEmpty()) {
            select.setGroupByExpressions(select.getFetchColumns());
            select.setHavingClause(generateExpression(0, PostgresDataType.BOOLEAN));
            return false;
        } else {
            if (select.getHavingClause() == null) {
                select.setHavingClause(generateExpression(0, PostgresDataType.BOOLEAN));
                return false;
            } else {
                select.setHavingClause(null);
                return true;
            }
        }
    }

    boolean mutateAnd(PostgresSelect select) {
        if (select.getWhereClause() == null) {
            select.setWhereClause(generateExpression(0, PostgresDataType.BOOLEAN));
        } else {
            PostgresExpression newWhere = new PostgresBinaryLogicalOperation(select.getWhereClause(),
                    generateExpression(0, PostgresDataType.BOOLEAN), BinaryLogicalOperator.AND);
            select.setWhereClause(newWhere);
        }
        return false;
    }

    boolean mutateOr(PostgresSelect select) {
        if (select.getWhereClause() == null) {
            select.setWhereClause(generateExpression(0, PostgresDataType.BOOLEAN));
            return false;
        } else {
            PostgresExpression newWhere = new PostgresBinaryLogicalOperation(select.getWhereClause(),
                    generateExpression(0, PostgresDataType.BOOLEAN), BinaryLogicalOperator.OR);
            select.setWhereClause(newWhere);
            return true;
        }
    }

    boolean mutateLimit(PostgresSelect select) {
        boolean increase = select.getLimitClause() != null;
        if (increase) {
            select.setLimitClause(null);
        } else {
            Randomly r = new Randomly();
            select.setLimitClause(PostgresConstant.createIntConstant((int) Math.abs(r.getInteger())));
        }
        return increase;
    }
}
