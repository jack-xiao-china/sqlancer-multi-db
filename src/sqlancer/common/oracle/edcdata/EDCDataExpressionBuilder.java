package sqlancer.common.oracle.edcdata;

import sqlancer.Randomly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Expression generator for EDC_DATA test scenarios.
 * Generates random expressions, values, and WHERE conditions.
 * Equivalent to EDC Python's ExprGenerator.
 */
public class EDCDataExpressionBuilder {
    protected final EDCDataConfig config;
    protected final Randomly r;

    public EDCDataExpressionBuilder(EDCDataConfig config, Randomly r) {
        this.config = config;
        this.r = r;
    }

    public String generateExprOnColumn(String table, List<String> columnNames, List<String> columnTypes, int depth) {
        List<String> type = new ArrayList<>();

        if (columnNames != null && !columnNames.isEmpty()) {
            if (depth == 1) {
                type.add("COLUMN");
                type.add("CONSTANT");
            }
            if (depth > 1) {
                type.add("=");
                type.add("!=");
                type.add(">");
                type.add("<=");
                type.add("AND");
                type.add("OR");
            }
            if (depth > 2) {
                type.add("CASE");
                type.add("SUBQUERY");
            }
        }

        if (type.isEmpty()) {
            return "NULL";
        }

        String op = Randomly.fromList(type);

        switch (op) {
            case "CONSTANT":
                if (r.getInteger(0, 6) < 3) {
                    return generateSingleConstant(Randomly.fromList(columnTypes)).toString();
                } else if (Randomly.getBoolean()) {
                    return generateExprConstant(Randomly.fromList(columnTypes)).toString();
                } else {
                    return "NULL";
                }
            case "COLUMN":
                return Randomly.fromList(columnNames);
            case "CASE":
                return String.format("(CASE WHEN %s THEN (%s) ELSE (%s) END)",
                        generateExprOnColumn(table, columnNames, columnTypes, depth - 1),
                        generateExprConstant(Randomly.fromList(columnTypes)),
                        generateExprConstant(Randomly.fromList(columnTypes)));
            case "SUBQUERY":
                String agg = Randomly.fromOptions("IN", "NOT IN");
                return String.format("%s %s (SELECT %s FROM %s WHERE %s)",
                        generateExprOnColumn(table, columnNames, columnTypes, depth - 1),
                        agg,
                        Randomly.fromList(columnNames),
                        table,
                        generateExprOnColumn(table, columnNames, columnTypes, depth - 1));
            default:
                return String.format("(%s %s %s)",
                        generateExprOnColumn(table, columnNames, columnTypes, depth - 1),
                        op,
                        generateExprOnColumn(table, columnNames, columnTypes, depth - 1));
        }
    }

    public Constant generateExprConstant(String oriType) {
        String[] exprOps = {"+", "*", "/", "<<", ">>", "&", "|", "^"};
        String op = Randomly.fromOptions(exprOps);
        Constant arg = generateSingleConstant(oriType);
        return new Constant("(" + arg.getValue() + " " + op + " " + generateSingleConstant(oriType).getValue() + ")", oriType, null);
    }

    public Constant generateSingleConstant(String oriType) {
        String value = generateRandomValue(oriType);
        return new Constant(value, oriType, null);
    }

    public String generateRandomValue(String dataType) {
        return generateRandomValue(dataType, 0, 3);
    }

    protected String generateRandomValue(String dataType, int depth, int maxDepth) {
        if (depth >= maxDepth) {
            return "0";
        }

        if (dataType == null) {
            dataType = "INT";
        }
        dataType = dataType.toUpperCase();

        if (dataType.startsWith("ARRAY")) {
            return generateArrayValue(dataType, depth, maxDepth);
        } else if (dataType.startsWith("TUPLE")) {
            return generateTupleValue(dataType, depth, maxDepth);
        } else if (dataType.startsWith("MAP")) {
            return generateMapValue(dataType, depth, maxDepth);
        } else if (dataType.contains("JSON")) {
            return generateJsonValue(depth, maxDepth);
        } else if (dataType.equals("TINYINT") || dataType.equals("BOOL") || dataType.equals("BOOLEAN")
                || dataType.equals("INT8") || dataType.equals("UINT8")) {
            return String.valueOf(r.getInteger(-128, 127));
        } else if (dataType.equals("TINYINT UNSIGNED")) {
            return String.valueOf(r.getInteger(0, 255));
        } else if (dataType.equals("SMALLINT") || dataType.equals("INT16") || dataType.equals("UINT16")) {
            return String.valueOf(r.getInteger(-32768, 32767));
        } else if (dataType.equals("SMALLINT UNSIGNED")) {
            return String.valueOf(r.getInteger(0, 65535));
        } else if (dataType.equals("MEDIUMINT") || dataType.equals("INT32") || dataType.equals("UINT32")) {
            return String.valueOf(r.getInteger(-8388608, 8388607));
        } else if (dataType.equals("MEDIUMINT UNSIGNED")) {
            return String.valueOf(r.getInteger(0, 16777215));
        } else if (dataType.equals("INT") || dataType.equals("INTEGER") || dataType.equals("UINTEGER")) {
            return String.valueOf(r.getInteger(-2147483648, 2147483647));
        } else if (dataType.equals("INT UNSIGNED") || dataType.equals("INTEGER UNSIGNED")) {
            return String.valueOf(Integer.toUnsignedLong(r.getInteger(0, Integer.MAX_VALUE)));
        } else if (dataType.equals("BIGINT") || dataType.equals("HUGEINT") || dataType.equals("UBIGINT")
                || dataType.equals("INT64") || dataType.equals("UINT64")) {
            return String.valueOf(r.getLong(-9223372036854775807L, 9223372036854775807L));
        } else if (dataType.equals("BIGINT UNSIGNED")) {
            return String.valueOf(Math.abs(r.getLong(Long.MIN_VALUE + 1, Long.MAX_VALUE)));
        } else if (dataType.startsWith("DECIMAL") || dataType.contains("DEC") || dataType.equals("NUMBER")) {
            return String.valueOf(Math.round(Randomly.getUncachedDouble() * 1e10 * 1e6) / 1e6);
        } else if (dataType.equals("FLOAT") || dataType.equals("FLOAT4") || dataType.equals("FLOAT8")
                || dataType.equals("REAL") || dataType.equals("FLOAT64")
                || dataType.equals("NUMERIC")) {
            return generateFloatValue();
        } else if (dataType.contains("DOUBLE")) {
            return generateDoubleValue();
        } else if (dataType.equals("BIT")) {
            return String.valueOf(r.getInteger(0, 2));
        } else if (dataType.startsWith("VARBINARY") || dataType.startsWith("BINARY")) {
            return generateBinaryValue(dataType);
        } else if (dataType.equals("TINYBLOB") || dataType.equals("BLOB") || dataType.equals("MEDIUMBLOB")
                || dataType.equals("LONGBLOB")) {
            return generateBlobValue();
        } else if (dataType.equals("TINYTEXT") || dataType.equals("TEXT") || dataType.equals("MEDIUMTEXT")
                || dataType.equals("LONGTEXT") || dataType.equals("CLOB") || dataType.equals("STRING")) {
            return generateTextValue();
        } else if (dataType.startsWith("VARCHAR") || dataType.startsWith("CHAR") || dataType.startsWith("FIXEDSTRING")
                || dataType.startsWith("VARCHAR2")) {
            return generateStringValue(dataType);
        } else if (dataType.startsWith("ENUM")) {
            return generateEnumValue(dataType);
        } else if (dataType.equals("INET4") || dataType.equals("IPV4")) {
            return generateIPv4Value();
        } else if (dataType.equals("INET6") || dataType.equals("IPV6")) {
            return generateIPv6Value();
        } else if (dataType.equals("UUID")) {
            return "'" + UUID.randomUUID().toString() + "'";
        } else if (dataType.startsWith("DATETIME")) {
            return generateDatetimeValue();
        } else if (dataType.startsWith("TIMESTAMP")) {
            return generateTimestampValue();
        } else if (dataType.startsWith("DATE")) {
            return generateDateValue();
        } else if (dataType.startsWith("TIME")) {
            return generateTimeValue();
        } else if (dataType.equals("YEAR")) {
            return String.valueOf(r.getInteger(1901, 2156));
        } else if (dataType.startsWith("INTERVAL")) {
            return generateIntervalValue();
        } else if (dataType.equals("GEOMETRY") || dataType.equals("POINT") || dataType.equals("LINESTRING")
                || dataType.equals("POLYGON") || dataType.equals("MULTIPOINT") || dataType.equals("MULTILINESTRING")
                || dataType.equals("MULTIPOLYGON") || dataType.equals("GEOMCOLLECTION")
                || dataType.equals("GEOMETRYCOLLECTION") || dataType.equals("RING")) {
            return generateSpatialValue(dataType);
        } else if (dataType.startsWith("VECTOR")) {
            return generateVectorValue(dataType);
        } else {
            // Fallback: treat as INT
            return String.valueOf(r.getInteger(-2147483648, 2147483647));
        }
    }

    private String generateArrayValue(String dataType, int depth, int maxDepth) {
        int open = dataType.indexOf('(');
        int close = dataType.lastIndexOf(')');
        String innerType = (open >= 0 && close > open) ? dataType.substring(open + 1, close) : "INT";
        int size = r.getInteger(1, 3);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(generateRandomValue(innerType, depth + 1, maxDepth));
        }
        return "[" + String.join(", ", values) + "]";
    }

    private String generateTupleValue(String dataType, int depth, int maxDepth) {
        if (dataType.contains("(") && dataType.contains(")")) {
            String inner = dataType.substring(dataType.indexOf('(') + 1, dataType.indexOf(')'));
            String[] innerTypes = inner.split(",");
            List<String> values = new ArrayList<>();
            for (String type : innerTypes) {
                values.add(generateRandomValue(type.trim(), depth + 1, maxDepth));
            }
            return "(" + String.join(", ", values) + ")";
        }
        return "(0, 0)";
    }

    private String generateMapValue(String dataType, int depth, int maxDepth) {
        if (dataType.contains("(") && dataType.contains(")")) {
            String inner = dataType.substring(dataType.indexOf('(') + 1, dataType.indexOf(')'));
            String[] parts = inner.split(",", 2);
            if (parts.length == 2) {
                int size = r.getInteger(1, 3);
                List<String> pairs = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    pairs.add(generateRandomValue(parts[0].trim(), depth + 1, maxDepth) + ": "
                            + generateRandomValue(parts[1].trim(), depth + 1, maxDepth));
                }
                return "{" + String.join(", ", pairs) + "}";
            }
        }
        return "{}";
    }

    private String generateJsonValue(int currDepth, int maxDepth) {
        if (currDepth >= maxDepth) {
            return "\"value\"";
        }
        List<String> types = new ArrayList<>();
        types.add("string");
        types.add("number");
        types.add("boolean");
        if (currDepth < maxDepth - 1) {
            types.add("array");
        }
        String choice = Randomly.fromList(types);
        switch (choice) {
            case "string":
                return "\"v" + r.getInteger(0, 100) + "\"";
            case "number":
                return String.valueOf(r.getInteger(-100, 100));
            case "boolean":
                return Randomly.getBoolean() ? "true" : "false";
            case "array":
                int size = r.getInteger(0, 3);
                List<String> values = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    values.add(generateJsonValue(currDepth + 1, maxDepth));
                }
                return "[" + String.join(", ", values) + "]";
            default:
                return "null";
        }
    }

    private String generateFloatValue() {
        double value = Randomly.getUncachedDouble() * 2e38 - 1e38;
        if (Double.isInfinite(value)) {
            value = Randomly.getUncachedDouble() * 2e30 - 1e30;
        }
        return String.valueOf(Math.round(value * 1e6) / 1e6);
    }

    private String generateDoubleValue() {
        double value = Randomly.getUncachedDouble() * Double.MAX_VALUE * 0.5;
        if (Randomly.getBoolean()) {
            value = -value;
        }
        if (Double.isInfinite(value)) {
            value = Randomly.getUncachedDouble() * 2e30 - 1e30;
        }
        return String.valueOf(Math.round(value * 1e6) / 1e6);
    }

    private String generateBinaryValue(String dataType) {
        int length = 10;
        if (dataType.contains("(") && dataType.contains(")")) {
            length = Integer.parseInt(dataType.substring(dataType.indexOf('(') + 1, dataType.indexOf(')')));
        }
        StringBuilder sb = new StringBuilder("0x");
        String hex = "0123456789ABCDEF";
        for (int i = 0; i < length; i++) {
            sb.append(hex.charAt(r.getInteger(0, 16)));
        }
        return sb.toString();
    }

    private String generateBlobValue() {
        return "'" + generateRandomString(r.getInteger(1, 50)) + "'";
    }

    private String generateTextValue() {
        return "'" + generateRandomString(r.getInteger(1, 50)) + "'";
    }

    private String generateStringValue(String dataType) {
        int length = 10;
        if (dataType.contains("(") && dataType.contains(")")) {
            try {
                length = Integer.parseInt(dataType.substring(dataType.indexOf('(') + 1, dataType.indexOf(')')));
            } catch (NumberFormatException e) {
                length = 10;
            }
        }
        return "'" + generateRandomString(length) + "'";
    }

    private String generateEnumValue(String dataType) {
        String inner = dataType.substring(dataType.indexOf('(') + 1, dataType.indexOf(')'));
        String[] choices = inner.split(",");
        String choice = Randomly.fromOptions(choices).trim();
        if (choice.startsWith("'") && choice.endsWith("'")) {
            choice = choice.substring(1, choice.length() - 1);
        }
        return "'" + choice + "'";
    }

    private String generateIPv4Value() {
        return "'" + r.getInteger(0, 256) + "." + r.getInteger(0, 256) + "."
                + r.getInteger(0, 256) + "." + r.getInteger(0, 256) + "'";
    }

    private String generateIPv6Value() {
        StringBuilder sb = new StringBuilder("'");
        String hex = "0123456789abcdef";
        for (int i = 0; i < 8; i++) {
            if (i > 0) sb.append(":");
            for (int j = 0; j < 4; j++) {
                sb.append(hex.charAt(r.getInteger(0, 16)));
            }
        }
        sb.append("'");
        return sb.toString();
    }

    private String generateDatetimeValue() {
        return String.format("'%04d-%02d-%02d %02d:%02d:%02d'",
                r.getInteger(1900, 2100), r.getInteger(1, 13), r.getInteger(1, 29),
                r.getInteger(0, 24), r.getInteger(0, 60), r.getInteger(0, 60));
    }

    private String generateTimestampValue() {
        return String.format("'%04d-%02d-%02d %02d:%02d:%02d'",
                r.getInteger(1970, 2035), r.getInteger(1, 13), r.getInteger(1, 29),
                r.getInteger(0, 24), r.getInteger(0, 60), r.getInteger(0, 60));
    }

    private String generateDateValue() {
        return String.format("'%04d-%02d-%02d'",
                r.getInteger(1900, 2100), r.getInteger(1, 13), r.getInteger(1, 29));
    }

    private String generateTimeValue() {
        return String.format("'%02d:%02d:%02d'",
                r.getInteger(0, 24), r.getInteger(0, 60), r.getInteger(0, 60));
    }

    private String generateIntervalValue() {
        return String.format("INTERVAL '%d %d:%d:%d' DAY TO SECOND",
                r.getInteger(-30, 31), r.getInteger(0, 24), r.getInteger(0, 60), r.getInteger(0, 60));
    }

    protected String generateSpatialValue(String dataType) {
        return "ST_GeomFromText('POINT(0 0)')";
    }

    protected String generateVectorValue(String dataType) {
        return "''";
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(r.getInteger(0, chars.length())));
        }
        return sb.toString();
    }

    public static class Constant {
        private final String value;
        private final String oriType;
        private final String destType;

        public Constant(String value, String oriType, String destType) {
            this.value = value;
            this.oriType = oriType;
            this.destType = destType;
        }

        public String getValue() { return value; }
        public String getOriType() { return oriType; }
        public String getDestType() { return destType; }

        @Override
        public String toString() {
            if (destType != null) {
                return "(CAST((" + value + ") AS " + destType + "))";
            }
            return value;
        }
    }
}
