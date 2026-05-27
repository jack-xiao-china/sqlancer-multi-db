package sqlancer.mysql.gen;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.MySQLVisitor;

public class MySQLInsertGenerator {

    private final MySQLTable table;
    private final StringBuilder sb = new StringBuilder();
    private final ExpectedErrors errors = new ExpectedErrors();
    private final MySQLGlobalState globalState;
    private final MySQLSchema schema;
    private boolean useInsert;

    public MySQLInsertGenerator(MySQLGlobalState globalState, MySQLTable table) {
        this.globalState = globalState;
        this.table = table;
        this.schema = globalState.getSchema();
    }

    public static SQLQueryAdapter insertRow(MySQLGlobalState globalState) throws SQLException {
        // 获取随机表，过滤掉视图（视图不可插入）
        MySQLTable table = globalState.getSchema().getRandomTableNonView();
        if (table == null) {
            // 没有非视图表可用，跳过插入
            throw new IgnoreMeException();
        }
        return insertRow(globalState, table);
    }

    public static SQLQueryAdapter insertRow(MySQLGlobalState globalState, MySQLTable table) throws SQLException {
        if (Randomly.getBoolean()) {
            return new MySQLInsertGenerator(globalState, table).generateInsert();
        } else {
            return new MySQLInsertGenerator(globalState, table).generateReplace();
        }
    }

    private SQLQueryAdapter generateReplace() {
        useInsert = false;
        sb.append("REPLACE");
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("LOW_PRIORITY", "DELAYED"));
        }
        return generateInto();

    }

    private SQLQueryAdapter generateInsert() {
        useInsert = true;
        sb.append("INSERT");
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("LOW_PRIORITY", "DELAYED", "HIGH_PRIORITY"));
        }
        if (Randomly.getBoolean()) {
            sb.append(" IGNORE");
        }
        return generateInto();
    }

    private SQLQueryAdapter generateInto() {
        sb.append(" INTO ");
        sb.append(table.getName());
        List<MySQLColumn> columns = table.getRandomNonEmptyColumnSubset();
        sb.append("(");
        sb.append(columns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
        sb.append(") ");

        // Randomly choose between VALUES and SELECT syntax
        boolean useSelect = Randomly.getBooleanWithRatherLowProbability() && schema.getDatabaseTables().size() > 1;

        if (useSelect) {
            // INSERT ... SELECT syntax
            generateInsertSelect(columns);
        } else {
            // VALUES syntax
            sb.append("VALUES");
            MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState);
            boolean testDates = globalState.getDbmsSpecificOptions().testDates;
            int nrRows;
            if (Randomly.getBoolean()) {
                nrRows = 1;
            } else {
                nrRows = 1 + Randomly.smallNumber();
            }
            for (int row = 0; row < nrRows; row++) {
                if (row != 0) {
                    sb.append(", ");
                }
                sb.append("(");
                for (int c = 0; c < columns.size(); c++) {
                    if (c != 0) {
                        sb.append(", ");
                    }
                    MySQLColumn column = columns.get(c);
                    MySQLDataType colType = column.getType();
                    if (testDates && isTemporalType(colType)) {
                        sb.append(MySQLVisitor.asString(generateTemporalConstant(column)));
                    } else if (isBitEnumSetType(colType)) {
                        // 为 BIT/ENUM/SET 类型生成适当的常量
                        sb.append(MySQLVisitor.asString(generateBitEnumSetConstant(column, globalState.getRandomly())));
                    } else {
                        sb.append(MySQLVisitor.asString(gen.generateConstant()));
                    }

                }
                sb.append(")");
            }
        }

        // Add ON DUPLICATE KEY UPDATE (only for INSERT, not REPLACE)
        if (useInsert && Randomly.getBooleanWithRatherLowProbability()) {
            sb.append(" ON DUPLICATE KEY UPDATE ");
            // Generate update assignments
            List<MySQLColumn> updateColumns = table.getRandomNonEmptyColumnSubset();
            MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState).setColumns(table.getColumns());
            for (int i = 0; i < updateColumns.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(updateColumns.get(i).getName());
                sb.append("=");
                // Use VALUES() function or a new expression
                if (Randomly.getBoolean()) {
                    sb.append("VALUES(");
                    sb.append(updateColumns.get(i).getName());
                    sb.append(")");
                } else {
                    sb.append(MySQLVisitor.asString(gen.generateExpression()));
                }
            }
        }

        MySQLErrors.addInsertUpdateErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    /**
     * Generate INSERT ... SELECT statement
     */
    private void generateInsertSelect(List<MySQLColumn> columns) {
        sb.append("SELECT ");
        // Use source table columns for expression generation
        MySQLTable sourceTable = schema.getRandomTableNonView();
        if (sourceTable == null) {
            throw new IgnoreMeException();
        }
        MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState)
                .setColumns(sourceTable.getColumns());
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(MySQLVisitor.asString(gen.generateExpression()));
        }
        sb.append(" FROM ");
        if (sourceTable.getName().equals(table.getName())) {
            sb.append(table.getName());
        } else {
            sb.append(sourceTable.getName());
        }
        // Optionally add WHERE clause
        if (Randomly.getBoolean()) {
            sb.append(" WHERE ");
            sb.append(MySQLVisitor.asString(gen.generateExpression()));
        }
        // Optionally add LIMIT
        if (Randomly.getBoolean()) {
            sb.append(" LIMIT ");
            sb.append(globalState.getRandomly().getInteger(1, 100));
        }
        // Add expected errors for INSERT SELECT
        errors.add("Column count doesn't match value count");
        errors.add("Unknown column");
        MySQLErrors.addExpressionErrors(errors);
        MySQLErrors.addInsertUpdateErrors(errors);
    }

    private static boolean isTemporalType(MySQLDataType type) {
        switch (type) {
        case DATE:
        case TIME:
        case DATETIME:
        case TIMESTAMP:
        case YEAR:
            return true;
        default:
            return false;
        }
    }

    private static boolean isBitEnumSetType(MySQLDataType type) {
        return type == MySQLDataType.BIT || type == MySQLDataType.ENUM || type == MySQLDataType.SET
                || type == MySQLDataType.JSON || type == MySQLDataType.BINARY || type == MySQLDataType.VARBINARY
                || type == MySQLDataType.BLOB;
    }

    /**
     * 为 BIT/ENUM/SET/JSON/BINARY 类型列生成常量值
     */
    private static MySQLConstant generateBitEnumSetConstant(MySQLColumn column, Randomly r) {
        switch (column.getType()) {
        case BIT:
            int width = column.getBitWidth();
            if (width <= 0) width = 1;  // 默认宽度
            long maxValue = (1L << Math.min(width, 63)) - 1;  // 限制在 63 位以内
            long bitValue = r.getLong(0, maxValue + 1);
            return MySQLConstant.createBitConstant(bitValue, width);
        case ENUM:
            List<String> enumValues = column.getEnumValues();
            if (enumValues == null || enumValues.isEmpty()) {
                throw new IgnoreMeException();
            }
            String enumValue = Randomly.fromList(enumValues);
            int enumIndex = enumValues.indexOf(enumValue) + 1;  // MySQL ENUM 索引从 1 开始
            return MySQLConstant.createEnumConstant(enumValue, enumIndex);
        case SET:
            List<String> setValues = column.getSetValues();
            if (setValues == null || setValues.isEmpty()) {
                throw new IgnoreMeException();
            }
            // 选择一个或多个值组成 SET
            Set<String> selectedValues = new HashSet<>(Randomly.nonEmptySubset(setValues));
            long bitmap = 0;
            for (String val : selectedValues) {
                bitmap |= (1L << setValues.indexOf(val));
            }
            return MySQLConstant.createSetConstant(selectedValues, bitmap);
        case JSON:
            // 生成简单的 JSON 值
            return MySQLConstant.createJSONConstant(generateRandomJSONValue(r));
        case BINARY:
        case VARBINARY:
        case BLOB:
            // 生成二进制数据
            int binaryLength = 1 + Randomly.smallNumber();
            byte[] binaryValue = new byte[binaryLength];
            for (int i = 0; i < binaryLength; i++) {
                binaryValue[i] = (byte) r.getInteger(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
            }
            return MySQLConstant.createBinaryConstant(binaryValue);
        default:
            throw new AssertionError(column.getType());
        }
    }

    /**
     * 生成随机 JSON 值用于插入
     */
    private static String generateRandomJSONValue(Randomly r) {
        int depth = Randomly.smallNumber();
        return generateRandomJSONRecursive(r, depth);
    }

    private static String generateRandomJSONRecursive(Randomly r, int maxDepth) {
        if (maxDepth <= 0) {
            // 生成基本值
            switch (Randomly.fromOptions(0, 1, 2, 3)) {
            case 0:
                return "null";
            case 1:
                return String.valueOf(r.getInteger());
            case 2:
                String str = r.getString().replace("\"", "\\\"").replace("\\", "\\\\");
                return "\"" + str + "\"";
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
                obj.append(generateRandomJSONRecursive(r, maxDepth - 1));
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
                arr.append(generateRandomJSONRecursive(r, maxDepth - 1));
            }
            arr.append("]");
            return arr.toString();
        default:
            return "{}";
        }
    }

    private static int clampFsp(int precision) {
        if (precision < 0) {
            return 0;
        }
        return Math.min(6, precision);
    }

    private static MySQLConstant generateTemporalConstant(MySQLColumn column) {
        int fsp = clampFsp(column.getPrecision());
        MySQLDataType type = column.getType();
        switch (type) {
        case DATE: {
            int year = (int) Randomly.getNotCachedInteger(1901, 2155);
            int month = (int) Randomly.getNotCachedInteger(1, 12 + 1);
            int day = (int) Randomly.getNotCachedInteger(1, 28 + 1); // keep always-valid across months
            return new MySQLConstant.MySQLDateConstant(year, month, day);
        }
        case YEAR: {
            int year = (int) Randomly.getNotCachedInteger(1901, 2155);
            return new MySQLConstant.MySQLYearConstant(year);
        }
        case TIME: {
            int hour = (int) Randomly.getNotCachedInteger(0, 23 + 1);
            int minute = (int) Randomly.getNotCachedInteger(0, 59 + 1);
            int second = (int) Randomly.getNotCachedInteger(0, 59 + 1);
            if (fsp > 0) {
                // keep within [0, 10^fsp) so it always renders with exactly fsp digits.
                int fraction = (int) Randomly.getNotCachedInteger(0, 999999 + 1);
                return new MySQLConstant.MySQLTimeConstant(hour, minute, second, fraction, fsp);
            }
            return new MySQLConstant.MySQLTimeConstant(hour, minute, second);
        }
        case DATETIME: {
            int year = (int) Randomly.getNotCachedInteger(1901, 2155);
            int month = (int) Randomly.getNotCachedInteger(1, 12 + 1);
            int day = (int) Randomly.getNotCachedInteger(1, 28 + 1);
            int hour = (int) Randomly.getNotCachedInteger(0, 23 + 1);
            int minute = (int) Randomly.getNotCachedInteger(0, 59 + 1);
            int second = (int) Randomly.getNotCachedInteger(0, 59 + 1);
            if (fsp > 0) {
                int fraction = (int) Randomly.getNotCachedInteger(0, 999999 + 1);
                return new MySQLConstant.MySQLDateTimeConstant(year, month, day, hour, minute, second, fraction, fsp);
            }
            return new MySQLConstant.MySQLDateTimeConstant(year, month, day, hour, minute, second);
        }
        case TIMESTAMP: {
            int year = (int) Randomly.getNotCachedInteger(1901, 2155);
            int month = (int) Randomly.getNotCachedInteger(1, 12 + 1);
            int day = (int) Randomly.getNotCachedInteger(1, 28 + 1);
            int hour = (int) Randomly.getNotCachedInteger(0, 23 + 1);
            int minute = (int) Randomly.getNotCachedInteger(0, 59 + 1);
            int second = (int) Randomly.getNotCachedInteger(0, 59 + 1);
            if (fsp > 0) {
                int fraction = (int) Randomly.getNotCachedInteger(0, 999999 + 1);
                return new MySQLConstant.MySQLTimestampConstant(year, month, day, hour, minute, second, fraction, fsp);
            }
            return new MySQLConstant.MySQLTimestampConstant(year, month, day, hour, minute, second);
        }
        default:
            throw new AssertionError(type);
        }
    }

}
