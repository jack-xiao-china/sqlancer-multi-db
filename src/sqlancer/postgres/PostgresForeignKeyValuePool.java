package sqlancer.postgres;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;

public final class PostgresForeignKeyValuePool {

    private static final int VALUE_POOL_SIZE = 10;

    private PostgresForeignKeyValuePool() {
    }

    public static List<String> getValues(String sqlTypeName) {
        String normalizedType = sqlTypeName.toLowerCase();
        List<String> values = new ArrayList<>();
        if (isIntegerType(normalizedType)) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.valueOf(i * 100));
            }
        } else if (normalizedType.contains("bool")) {
            values.add("TRUE");
            values.add("FALSE");
        } else if (normalizedType.contains("text") || normalizedType.contains("char")
                || normalizedType.contains("name")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add("'fk_" + i + "'");
            }
        } else if (normalizedType.contains("numeric") || normalizedType.contains("decimal")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.125::numeric", i * 100));
            }
        } else if (normalizedType.contains("double precision") || normalizedType.contains("float8")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.25::double precision", i * 100));
            }
        } else if (normalizedType.contains("real") || normalizedType.contains("float4")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.5::real", i * 100));
            }
        } else if (normalizedType.contains("money")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.00::money", i * 100));
            }
        } else if (normalizedType.contains("bit")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add("'" + repeatBitPattern(i, 500) + "'::" + sqlTypeName);
            }
        } else if (normalizedType.contains("inet")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'192.168.0.%d'::inet", i));
            }
        } else if (normalizedType.contains("uuid")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                char digit = i == 10 ? 'a' : (char) ('0' + i);
                values.add("'" + repeat(digit, 8) + "-" + repeat(digit, 4) + "-" + repeat(digit, 4) + "-"
                        + repeat(digit, 4) + "-" + repeat(digit, 12) + "'::uuid");
            }
        } else if (normalizedType.contains("timestamptz")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'2000-01-%02d 00:00:00+00'::timestamptz", i));
            }
        } else if (normalizedType.contains("timetz")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'00:%02d:00+00'::timetz", i));
            }
        } else if (normalizedType.contains("timestamp")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'2000-01-%02d 00:00:00'::timestamp", i));
            }
        } else if (normalizedType.equals("time")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'00:%02d:00'::time", i));
            }
        } else if (normalizedType.contains("date")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'2000-01-%02d'::date", i));
            }
        } else if (normalizedType.contains("interval")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'%d days'::interval", i));
            }
        } else if (normalizedType.contains("bytea")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("decode('%02x', 'hex')", i));
            }
        } else if (normalizedType.contains("range")) {
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'[%d,%d)'::%s", i * 100, i * 100 + 1, sqlTypeName));
            }
        } else {
            for (char label = 'a'; label <= 'd'; label++) {
                values.add("'" + label + "'::" + sqlTypeName);
            }
        }
        return values;
    }

    public static List<String> getValues(PostgresColumn column) {
        if (column == null || column.getCompoundType().isArray()) {
            return Collections.emptyList();
        }
        return getValues(column.getCompoundType().getDataType());
    }

    private static List<String> getValues(PostgresDataType dataType) {
        List<String> values = new ArrayList<>();
        switch (dataType) {
        case INT:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.valueOf(i * 100));
            }
            break;
        case BOOLEAN:
            values.add("TRUE");
            values.add("FALSE");
            break;
        case TEXT:
        case VARCHAR:
        case CHAR:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add("'fk_" + i + "'");
            }
            break;
        case DECIMAL:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.125::numeric", i * 100));
            }
            break;
        case FLOAT:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.25::double precision", i * 100));
            }
            break;
        case REAL:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.5::real", i * 100));
            }
            break;
        case MONEY:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("%d.00::money", i * 100));
            }
            break;
        case BIT:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add("'" + repeatBitPattern(i, 500) + "'::bit varying(500)");
            }
            break;
        case INET:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'192.168.0.%d'::inet", i));
            }
            break;
        case UUID:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                char digit = i == 10 ? 'a' : (char) ('0' + i);
                values.add("'" + repeat(digit, 8) + "-" + repeat(digit, 4) + "-" + repeat(digit, 4) + "-"
                        + repeat(digit, 4) + "-" + repeat(digit, 12) + "'::uuid");
            }
            break;
        case TIMESTAMPTZ:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'2000-01-%02d 00:00:00+00'::timestamptz", i));
            }
            break;
        case TIMETZ:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'00:%02d:00+00'::timetz", i));
            }
            break;
        case TIMESTAMP:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'2000-01-%02d 00:00:00'::timestamp", i));
            }
            break;
        case TIME:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'00:%02d:00'::time", i));
            }
            break;
        case DATE:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'2000-01-%02d'::date", i));
            }
            break;
        case INTERVAL:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'%d days'::interval", i));
            }
            break;
        case BYTEA:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("decode('%02x', 'hex')", i));
            }
            break;
        case RANGE:
            for (int i = 1; i <= VALUE_POOL_SIZE; i++) {
                values.add(String.format("'[%d,%d)'::int4range", i * 100, i * 100 + 1));
            }
            break;
        case ENUM:
            for (char label = 'a'; label <= 'd'; label++) {
                values.add("'" + label + "'");
            }
            break;
        default:
            return Collections.emptyList();
        }
        return values;
    }

    private static boolean isIntegerType(String normalizedType) {
        return normalizedType.equals("smallint") || normalizedType.equals("integer") || normalizedType.equals("bigint")
                || normalizedType.equals("int2") || normalizedType.equals("int4") || normalizedType.equals("int8");
    }

    private static String repeatBitPattern(int seed, int count) {
        String pattern = Integer.toBinaryString(seed);
        StringBuilder sb = new StringBuilder(count);
        while (sb.length() < count) {
            sb.append(pattern);
        }
        return sb.substring(0, count);
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
