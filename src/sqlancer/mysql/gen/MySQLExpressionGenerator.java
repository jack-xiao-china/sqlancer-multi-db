package sqlancer.mysql.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.gen.CERTGenerator;
import sqlancer.common.gen.NoRECGenerator;
import sqlancer.common.gen.TLPWhereGenerator;
import sqlancer.common.gen.UntypedExpressionGenerator;
import sqlancer.common.schema.AbstractTables;
import sqlancer.mysql.MySQLBugs;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLOptions;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLRowValue;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.ast.MySQLAggregate;
import sqlancer.mysql.ast.MySQLAggregate.MySQLAggregateFunction;
import sqlancer.mysql.ast.MySQLBetweenOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import sqlancer.mysql.ast.MySQLBinaryOperation;
import sqlancer.mysql.ast.MySQLBinaryOperation.MySQLBinaryOperator;
import sqlancer.mysql.ast.MySQLCaseOperator;
import sqlancer.mysql.ast.MySQLCastOperation;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLComputableFunction;
import sqlancer.mysql.ast.MySQLComputableFunction.MySQLFunction;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLConstant.MySQLDoubleConstant;
import sqlancer.mysql.ast.MySQLExists;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLInOperation;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLOrderByTerm;
import sqlancer.mysql.ast.MySQLOrderByTerm.MySQLOrder;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLStringExpression;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;

public class MySQLExpressionGenerator extends UntypedExpressionGenerator<MySQLExpression, MySQLColumn>
        implements NoRECGenerator<MySQLSelect, MySQLJoin, MySQLExpression, MySQLTable, MySQLColumn>,
        TLPWhereGenerator<MySQLSelect, MySQLJoin, MySQLExpression, MySQLTable, MySQLColumn>,
        CERTGenerator<MySQLSelect, MySQLJoin, MySQLExpression, MySQLTable, MySQLColumn> {

    private final MySQLGlobalState state;
    private MySQLRowValue rowVal;
    private List<MySQLTable> tables;

    public MySQLExpressionGenerator(MySQLGlobalState state) {
        this.state = state;
    }

    public MySQLExpressionGenerator setRowVal(MySQLRowValue rowVal) {
        this.rowVal = rowVal;
        return this;
    }

    private enum Actions {
        COLUMN, LITERAL, UNARY_PREFIX_OPERATION, UNARY_POSTFIX, COMPUTABLE_FUNCTION, BINARY_LOGICAL_OPERATOR,
        BINARY_COMPARISON_OPERATION, CAST, IN_OPERATION, BINARY_OPERATION, EXISTS, BETWEEN_OPERATOR, CASE_OPERATOR;
    }

    @Override
    public MySQLExpression generateExpression(int depth) {
        if (depth >= state.getOptions().getMaxExpressionDepth()) {
            return generateLeafNode();
        }
        switch (Randomly.fromOptions(Actions.values())) {
        case COLUMN:
            return generateColumn();
        case LITERAL:
            return generateConstant();
        case UNARY_PREFIX_OPERATION:
            MySQLExpression subExpr = generateExpression(depth + 1);
            MySQLUnaryPrefixOperator random = MySQLUnaryPrefixOperator.getRandom();
            return new MySQLUnaryPrefixOperation(subExpr, random);
        case UNARY_POSTFIX:
            return new MySQLUnaryPostfixOperation(generateExpression(depth + 1),
                    Randomly.fromOptions(MySQLUnaryPostfixOperation.UnaryPostfixOperator.values()),
                    Randomly.getBoolean());
        case COMPUTABLE_FUNCTION:
            return getComputableFunction(depth + 1);
        case BINARY_LOGICAL_OPERATOR:
            return new MySQLBinaryLogicalOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    MySQLBinaryLogicalOperator.getRandom());
        case BINARY_COMPARISON_OPERATION:
            return new MySQLBinaryComparisonOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    BinaryComparisonOperator.getRandom());
        case CAST:
            return new MySQLCastOperation(generateExpression(depth + 1), MySQLCastOperation.CastType.getRandom());
        case IN_OPERATION:
            MySQLExpression expr = generateExpression(depth + 1);
            List<MySQLExpression> rightList = new ArrayList<>();
            for (int i = 0; i < 1 + Randomly.smallNumber(); i++) {
                rightList.add(generateExpression(depth + 1));
            }
            return new MySQLInOperation(expr, rightList, Randomly.getBoolean());
        case BINARY_OPERATION:
            if (MySQLBugs.bug99135) {
                throw new IgnoreMeException();
            }
            return new MySQLBinaryOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    MySQLBinaryOperator.getRandom());
        case EXISTS:
            return getExists();
        case BETWEEN_OPERATOR:
            if (MySQLBugs.bug99181) {
                // TODO: there are a number of bugs that are triggered by the BETWEEN operator
                throw new IgnoreMeException();
            }
            return new MySQLBetweenOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    generateExpression(depth + 1));
        case CASE_OPERATOR:
            int nr = Randomly.smallNumber() + 1;
            return new MySQLCaseOperator(generateExpression(depth + 1), generateExpressions(nr, depth + 1),
                    generateExpressions(nr, depth + 1), generateExpression(depth + 1));
        default:
            throw new AssertionError();
        }
    }

    private MySQLExpression getExists() {
        if (Randomly.getBoolean()) {
            return new MySQLExists(new MySQLStringExpression("SELECT 1", MySQLConstant.createTrue()));
        } else {
            return new MySQLExists(new MySQLStringExpression("SELECT 1 wHERE FALSE", MySQLConstant.createFalse()));
        }
    }

    private MySQLExpression getComputableFunction(int depth) {
        MySQLFunction func = MySQLFunction.getRandomFunction();
        int nrArgs = func.getNrArgs();
        if (func.isVariadic()) {
            nrArgs += Randomly.smallNumber();
        }
        MySQLExpression[] args = new MySQLExpression[nrArgs];
        for (int i = 0; i < args.length; i++) {
            args[i] = generateExpression(depth + 1);
        }
        return new MySQLComputableFunction(func, args);
    }

    private enum ConstantType {
        INT, NULL, STRING, DOUBLE, DATE, TIME, DATETIME, TIMESTAMP, YEAR, BIT, ENUM, SET, JSON, BINARY;
    }

    @Override
    public MySQLExpression generateConstant() {
        final boolean testDates = state.getDbmsSpecificOptions().testDates;
        final MySQLOptions options = state.getDbmsSpecificOptions();

        // 根据参数构建可用的常量类型列表（所有类型在所有 oracle 中可用）
        List<ConstantType> availableTypes = new ArrayList<>();
        availableTypes.add(ConstantType.INT);
        availableTypes.add(ConstantType.NULL);
        availableTypes.add(ConstantType.STRING);
        availableTypes.add(ConstantType.DOUBLE);
        // 时间类型（默认开启）
        if (testDates) {
            availableTypes.add(ConstantType.DATE);
            availableTypes.add(ConstantType.TIME);
            availableTypes.add(ConstantType.DATETIME);
            availableTypes.add(ConstantType.TIMESTAMP);
            availableTypes.add(ConstantType.YEAR);
        }
        // BIT/ENUM/SET/JSON/BINARY 类型（默认开启，覆盖所有 oracle）
        if (options.testBit) {
            availableTypes.add(ConstantType.BIT);
        }
        if (options.testEnums) {
            availableTypes.add(ConstantType.ENUM);
        }
        if (options.testSets) {
            availableTypes.add(ConstantType.SET);
        }
        if (options.testJSONDataType) {
            availableTypes.add(ConstantType.JSON);
        }
        if (options.testBinary) {
            availableTypes.add(ConstantType.BINARY);
        }

        switch (Randomly.fromList(availableTypes)) {
        case INT:
            return MySQLConstant.createIntConstant((int) state.getRandomly().getInteger());
        case NULL:
            return MySQLConstant.createNullConstant();
        case STRING:
            /* Replace characters that still trigger open bugs in MySQL */
            String string = state.getRandomly().getString().replace("\\", "").replace("\n", "");
            return MySQLConstant.createStringConstant(string);
        case DOUBLE:
            double val = state.getRandomly().getDouble();
            return new MySQLDoubleConstant(val);
        case DATE:
            return generateDateConstant();
        case TIME:
            return generateTimeConstant();
        case DATETIME:
            return generateDateTimeConstant();
        case TIMESTAMP:
            return generateTimestampConstant();
        case YEAR:
            return generateYearConstant();
        case BIT:
            return generateBitConstant();
        case ENUM:
            return generateEnumConstant();
        case SET:
            return generateSetConstant();
        case JSON:
            return generateJSONConstant();
        case BINARY:
            return generateBinaryConstant();
        default:
            throw new AssertionError();
        }
    }

    private MySQLConstant generateJSONConstant() {
        // 生成简单的 JSON 值
        int depth = Randomly.smallNumber();  // 控制嵌套深度
        return MySQLConstant.createJSONConstant(generateRandomJSON(depth));
    }

    /**
     * 生成随机 JSON 值
     */
    private String generateRandomJSON(int maxDepth) {
        if (maxDepth <= 0) {
            // 生成基本值
            switch (Randomly.fromOptions(0, 1, 2, 3)) {
            case 0:
                return "null";
            case 1:
                return String.valueOf(state.getRandomly().getInteger());
            case 2:
                return "\"" + state.getRandomly().getString().replace("\"", "\\\"").replace("\\", "\\\\") + "\"";
            case 3:
                return Randomly.fromOptions("true", "false");
            default:
                return "null";
            }
        }

        // 生成对象或数组
        switch (Randomly.fromOptions(0, 1)) {
        case 0:  // JSON 对象
            int numPairs = 1 + Randomly.smallNumber();
            StringBuilder obj = new StringBuilder("{");
            for (int i = 0; i < numPairs; i++) {
                if (i > 0) {
                    obj.append(", ");
                }
                String key = "k" + i;
                obj.append("\"").append(key).append("\": ");
                obj.append(generateRandomJSON(maxDepth - 1));
            }
            obj.append("}");
            return obj.toString();
        case 1:  // JSON 数组
            int numElements = 1 + Randomly.smallNumber();
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < numElements; i++) {
                if (i > 0) {
                    arr.append(", ");
                }
                arr.append(generateRandomJSON(maxDepth - 1));
            }
            arr.append("]");
            return arr.toString();
        default:
            return "{}";
        }
    }

    private MySQLConstant generateBinaryConstant() {
        // 生成随机长度的二进制数据
        int length = 1 + Randomly.smallNumber();  // 通常 1-10 字节
        byte[] value = new byte[length];
        Randomly r = state.getRandomly();
        for (int i = 0; i < length; i++) {
            value[i] = (byte) r.getInteger(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
        }
        return MySQLConstant.createBinaryConstant(value);
    }

    private MySQLConstant generateBitConstant() {
        int width = (int) Randomly.getNotCachedInteger(1, 64);
        // 避免溢出：当 width >= 63 时，maxValue 接近 Long.MAX_VALUE
        long maxValue;
        if (width >= 63) {
            maxValue = Long.MAX_VALUE;
        } else {
            maxValue = (1L << width) - 1;
        }
        long value;
        if (maxValue == Long.MAX_VALUE) {
            // 直接生成随机值，避免溢出
            value = (long) state.getRandomly().getInteger(0, Integer.MAX_VALUE);
        } else {
            value = (long) state.getRandomly().getInteger(0, (int) Math.min(maxValue + 1, Integer.MAX_VALUE));
        }
        return MySQLConstant.createBitConstant(value, width);
    }

    private MySQLConstant generateEnumConstant() {
        // 如果有 ENUM 列可用，从中选择值
        if (columns != null && !columns.isEmpty()) {
            for (MySQLColumn col : columns) {
                if (col.getType() == MySQLDataType.ENUM && col.getEnumValues() != null) {
                    List<String> values = col.getEnumValues();
                    String value = Randomly.fromList(values);
                    int index = values.indexOf(value) + 1;
                    return MySQLConstant.createEnumConstant(value, index);
                }
            }
        }
        // 没有可用 ENUM 列时，生成一个默认的枚举常量
        int index = (int) Randomly.getNotCachedInteger(1, 10);
        String value = "e" + (index - 1);
        return MySQLConstant.createEnumConstant(value, index);
    }

    private MySQLConstant generateSetConstant() {
        // 如果有 SET 列可用，从中选择值
        if (columns != null && !columns.isEmpty()) {
            for (MySQLColumn col : columns) {
                if (col.getType() == MySQLDataType.SET && col.getSetValues() != null) {
                    List<String> values = col.getSetValues();
                    Set<String> selectedValues = new HashSet<>(Randomly.nonEmptySubset(values));
                    long bitmap = 0;
                    for (String val : selectedValues) {
                        bitmap |= (1L << values.indexOf(val));
                    }
                    return MySQLConstant.createSetConstant(selectedValues, bitmap);
                }
            }
        }
        // 没有可用 SET 列时，生成一个默认的 SET 常量
        int numValues = (int) Randomly.getNotCachedInteger(1, 5);
        Set<String> selectedValues = new HashSet<>();
        long bitmap = 0;
        for (int i = 0; i < numValues; i++) {
            String value = "s" + i;
            selectedValues.add(value);
            bitmap |= (1L << i);
        }
        return MySQLConstant.createSetConstant(selectedValues, bitmap);
    }

    private MySQLConstant generateDateConstant() {
        int year = (int) Randomly.getNotCachedInteger(1901, 2155);
        int month = (int) Randomly.getNotCachedInteger(1, 12);
        int day = (int) Randomly.getNotCachedInteger(1, 28); // keep always-valid across months
        return new MySQLConstant.MySQLDateConstant(year, month, day);
    }

    private MySQLConstant generateTimeConstant() {
        int hour = (int) Randomly.getNotCachedInteger(0, 23);
        int minute = (int) Randomly.getNotCachedInteger(0, 59);
        int second = (int) Randomly.getNotCachedInteger(0, 59);
        if (Randomly.getBoolean()) {
            int fsp = 6;
            int fraction = (int) Randomly.getNotCachedInteger(0, 999999);
            return new MySQLConstant.MySQLTimeConstant(hour, minute, second, fraction, fsp);
        }
        return new MySQLConstant.MySQLTimeConstant(hour, minute, second);
    }

    private MySQLConstant generateDateTimeConstant() {
        int year = (int) Randomly.getNotCachedInteger(1901, 2155);
        int month = (int) Randomly.getNotCachedInteger(1, 12);
        int day = (int) Randomly.getNotCachedInteger(1, 28);
        int hour = (int) Randomly.getNotCachedInteger(0, 23);
        int minute = (int) Randomly.getNotCachedInteger(0, 59);
        int second = (int) Randomly.getNotCachedInteger(0, 59);
        if (Randomly.getBoolean()) {
            int fsp = 6;
            int fraction = (int) Randomly.getNotCachedInteger(0, 999999);
            return new MySQLConstant.MySQLDateTimeConstant(year, month, day, hour, minute, second, fraction, fsp);
        }
        return new MySQLConstant.MySQLDateTimeConstant(year, month, day, hour, minute, second);
    }

    private MySQLConstant generateTimestampConstant() {
        int year = (int) Randomly.getNotCachedInteger(1970, 2038);
        int month = (int) Randomly.getNotCachedInteger(1, 12);
        int day = (int) Randomly.getNotCachedInteger(1, 28);
        int hour = (int) Randomly.getNotCachedInteger(0, 23);
        int minute = (int) Randomly.getNotCachedInteger(0, 59);
        int second = (int) Randomly.getNotCachedInteger(0, 59);
        if (Randomly.getBoolean()) {
            int fsp = 6;
            int fraction = (int) Randomly.getNotCachedInteger(0, 999999);
            return new MySQLConstant.MySQLTimestampConstant(year, month, day, hour, minute, second, fraction, fsp);
        }
        return new MySQLConstant.MySQLTimestampConstant(year, month, day, hour, minute, second);
    }

    private MySQLConstant generateYearConstant() {
        int year = (int) Randomly.getNotCachedInteger(1901, 2155);
        return new MySQLConstant.MySQLYearConstant(year);
    }

    @Override
    protected MySQLExpression generateColumn() {
        MySQLColumn c = Randomly.fromList(columns);
        MySQLConstant val;
        if (rowVal == null) {
            val = null;
        } else {
            val = rowVal.getValues().get(c);
        }
        return MySQLColumnReference.create(c, val);
    }

    @Override
    public MySQLExpression negatePredicate(MySQLExpression predicate) {
        return new MySQLUnaryPrefixOperation(predicate, MySQLUnaryPrefixOperator.NOT);
    }

    @Override
    public MySQLExpression isNull(MySQLExpression expr) {
        return new MySQLUnaryPostfixOperation(expr, MySQLUnaryPostfixOperation.UnaryPostfixOperator.IS_NULL, false);
    }

    /** MySQL 将 ORDER BY <整数> 解释为列位置，导致 Unknown column；判断表达式是否为裸整数形式 */
    private static boolean isBareIntegerForOrderBy(MySQLExpression expr) {
        if (expr instanceof MySQLConstant.MySQLIntConstant) {
            return true;
        }
        if (expr instanceof MySQLConstant.MySQLDoubleConstant) {
            double v = Double.parseDouble(((MySQLConstant.MySQLDoubleConstant) expr).getTextRepresentation());
            return v == Math.floor(v) && Math.abs(v) < 1e15;
        }
        if (expr instanceof MySQLUnaryPrefixOperation) {
            MySQLUnaryPrefixOperation unary = (MySQLUnaryPrefixOperation) expr;
            if (unary.getOp() == MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator.PLUS
                    || unary.getOp() == MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator.MINUS) {
                return isBareIntegerForOrderBy(unary.getExpression());
            }
        }
        return false;
    }

    @Override
    public List<MySQLExpression> generateOrderBys() {
        List<MySQLExpression> expressions = super.generateOrderBys();
        List<MySQLExpression> newOrderBys = new ArrayList<>();
        for (MySQLExpression expr : expressions) {
            if (isBareIntegerForOrderBy(expr)) {
                continue; // 跳过会被 MySQL 解释为列位置的整数形式
            }
            if (Randomly.getBoolean()) {
                MySQLOrderByTerm newExpr = new MySQLOrderByTerm(expr, MySQLOrder.getRandomOrder());
                newOrderBys.add(newExpr);
            } else {
                newOrderBys.add(expr);
            }
        }
        // 若过滤后为空，用列引用代替以保留 ORDER BY
        if (newOrderBys.isEmpty() && columns != null && !columns.isEmpty()) {
            MySQLExpression colRef = new MySQLColumnReference(Randomly.fromList(columns), null);
            newOrderBys.add(new MySQLOrderByTerm(colRef, MySQLOrder.getRandomOrder()));
        }
        return newOrderBys;
    }

    @Override
    public MySQLExpressionGenerator setTablesAndColumns(AbstractTables<MySQLTable, MySQLColumn> tables) {
        this.columns = tables.getColumns();
        this.tables = tables.getTables();

        return this;
    }

    @Override
    public MySQLExpression generateBooleanExpression() {
        return generateExpression();
    }

    @Override
    public MySQLSelect generateSelect() {
        return new MySQLSelect();
    }

    @Override
    public List<MySQLJoin> getRandomJoinClauses() {
        if (tables == null || tables.size() <= 1) {
            return List.of();
        }
        List<MySQLTable> tablesCopy = new ArrayList<>(tables);
        List<MySQLJoin> joins = MySQLJoin.getRandomJoinClauses(tablesCopy, state);
        this.tables = tablesCopy;
        return joins;
    }

    @Override
    public List<MySQLExpression> getTableRefs() {
        return tables.stream().map(t -> new MySQLTableReference(t)).collect(Collectors.toList());
    }

    @Override
    public List<MySQLExpression> generateFetchColumns(boolean shouldCreateDummy) {
        return columns.stream().map(c -> new MySQLColumnReference(c, null)).collect(Collectors.toList());
    }

    @Override
    public String generateOptimizedQueryString(MySQLSelect select, MySQLExpression whereCondition,
            boolean shouldUseAggregate) {
        if (shouldUseAggregate) {
            MySQLAggregate countAgg = new MySQLAggregate(List.of(MySQLConstant.createIntConstant(1)),
                    MySQLAggregateFunction.COUNT);
            select.setFetchColumns(List.of(countAgg));
        } else {
            select.setFetchColumns(generateFetchColumns(false));
            // MySQL interprets ORDER BY <integer> as column position (e.g. ORDER BY 1 = 1st column),
            // so bare integer literals in ORDER BY cause "Unknown column" errors. Skip ORDER BY for
            // NoREC non-aggregate mode to avoid this MySQL-specific behavior.
        }
        select.setWhereClause(whereCondition);
        return select.asString();
    }

    @Override
    public String generateUnoptimizedQueryString(MySQLSelect select, MySQLExpression whereCondition) {
        MySQLExpression countExpr = new MySQLComputableFunction(MySQLFunction.IF, whereCondition,
                MySQLConstant.createIntConstant(1), MySQLConstant.createIntConstant(0));
        select.setFetchColumns(List.of(countExpr));
        select.setWhereClause(null);
        select.setOrderByClauses(List.of());
        return "SELECT SUM(ref0) FROM (" + select.asString() + ") AS res";
    }

    @Override
    public String generateExplainQuery(MySQLSelect select) {
        return "EXPLAIN " + select.asString();
    }

    public MySQLAggregate generateAggregate() {
        MySQLAggregateFunction func = Randomly.fromOptions(MySQLAggregateFunction.values());

        if (func.isVariadic()) {
            int nrExprs = Randomly.smallNumber() + 1;
            List<MySQLExpression> exprs = IntStream.range(0, nrExprs).mapToObj(index -> generateExpression())
                    .collect(Collectors.toList());

            return new MySQLAggregate(exprs, func);
        } else {
            return new MySQLAggregate(List.of(generateExpression()), func);
        }
    }

    @Override
    public boolean mutate(MySQLSelect select) {
        List<Function<MySQLSelect, Boolean>> mutators = new ArrayList<>();

        mutators.add(this::mutateWhere);
        mutators.add(this::mutateGroupBy);
        mutators.add(this::mutateHaving);
        mutators.add(this::mutateAnd);
        mutators.add(this::mutateOr);
        mutators.add(this::mutateDistinct);

        return Randomly.fromList(mutators).apply(select);
    }

    boolean mutateDistinct(MySQLSelect select) {
        MySQLSelect.SelectType selectType = select.getFromOptions();
        if (selectType != MySQLSelect.SelectType.ALL) {
            select.setSelectType(MySQLSelect.SelectType.ALL);
            return true;
        } else {
            select.setSelectType(MySQLSelect.SelectType.DISTINCT);
            return false;
        }
    }

    boolean mutateWhere(MySQLSelect select) {
        boolean increase = select.getWhereClause() != null;
        if (increase) {
            select.setWhereClause(null);
        } else {
            select.setWhereClause(generateExpression());
        }
        return increase;
    }

    boolean mutateGroupBy(MySQLSelect select) {
        boolean increase = !select.getGroupByExpressions().isEmpty();
        if (increase) {
            select.clearGroupByExpressions();
        } else {
            select.setGroupByExpressions(select.getFetchColumns());
        }
        return increase;
    }

    boolean mutateHaving(MySQLSelect select) {
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

    boolean mutateAnd(MySQLSelect select) {
        if (select.getWhereClause() == null) {
            select.setWhereClause(generateExpression());
        } else {
            MySQLExpression newWhere = new MySQLBinaryLogicalOperation(select.getWhereClause(), generateExpression(),
                    MySQLBinaryLogicalOperator.AND);
            select.setWhereClause(newWhere);
        }
        return false;
    }

    boolean mutateOr(MySQLSelect select) {
        if (select.getWhereClause() == null) {
            select.setWhereClause(generateExpression());
            return false;
        } else {
            MySQLExpression newWhere = new MySQLBinaryLogicalOperation(select.getWhereClause(), generateExpression(),
                    MySQLBinaryLogicalOperator.OR);
            select.setWhereClause(newWhere);
            return true;
        }
    }
}
