package sqlancer.postgres.ast;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import sqlancer.IgnoreMeException;
import sqlancer.postgres.PostgresCompoundDataType;
import sqlancer.postgres.PostgresProvider;
import sqlancer.postgres.ast.PostgresTemporalUtil.IntervalValue;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import org.postgresql.util.PGInterval;

public abstract class PostgresConstant implements PostgresExpression {

    public abstract String getTextRepresentation();

    public abstract String getUnquotedTextRepresentation();

    public static class BooleanConstant extends PostgresConstant {

        private final boolean value;

        public BooleanConstant(boolean value) {
            this.value = value;
        }

        @Override
        public String getTextRepresentation() {
            return value ? "TRUE" : "FALSE";
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.BOOLEAN;
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public boolean isBoolean() {
            return true;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.isBoolean()) {
                return PostgresConstant.createBooleanConstant(value == rightVal.asBoolean());
            } else if (rightVal.isString()) {
                return PostgresConstant
                        .createBooleanConstant(value == rightVal.cast(PostgresDataType.BOOLEAN).asBoolean());
            } else {
                throw new AssertionError(rightVal);
            }
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.isString()) {
                return isLessThan(rightVal.cast(PostgresDataType.BOOLEAN));
            } else {
                assert rightVal.isBoolean();
                return PostgresConstant.createBooleanConstant((value ? 1 : 0) < (rightVal.asBoolean() ? 1 : 0));
            }
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            switch (type) {
            case BOOLEAN:
                return this;
            case INT:
                return PostgresConstant.createIntConstant(value ? 1 : 0);
            case TEXT:
                return PostgresConstant.createTextConstant(value ? "true" : "false");
            default:
                return null;
            }
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return getTextRepresentation();
        }

    }

    public static class PostgresNullConstant extends PostgresConstant {

        @Override
        public String getTextRepresentation() {
            return "NULL";
        }

        @Override
        public PostgresDataType getExpressionType() {
            return null;
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            return PostgresConstant.createNullConstant();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            return PostgresConstant.createNullConstant();
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            return PostgresConstant.createNullConstant();
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return getTextRepresentation();
        }

    }

    public static class StringConstant extends PostgresConstant {

        private final String value;

        public StringConstant(String value) {
            this.value = value;
        }

        @Override
        public String getTextRepresentation() {
            return String.format("'%s'", value.replace("'", "''"));
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.isInt()) {
                return cast(PostgresDataType.INT).isEquals(rightVal.cast(PostgresDataType.INT));
            } else if (rightVal.isBoolean()) {
                return cast(PostgresDataType.BOOLEAN).isEquals(rightVal.cast(PostgresDataType.BOOLEAN));
            } else if (rightVal.getExpressionType() == PostgresDataType.UUID) {
                return cast(PostgresDataType.UUID).isEquals(rightVal);
            } else if (rightVal.getExpressionType() == PostgresDataType.BYTEA
                    || rightVal.getExpressionType() == PostgresDataType.ENUM) {
                return rightVal.isEquals(this);
            } else if (rightVal.isString()) {
                return PostgresConstant.createBooleanConstant(value.contentEquals(rightVal.asString()));
            } else {
                return null;
            }
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.isInt()) {
                return cast(PostgresDataType.INT).isLessThan(rightVal.cast(PostgresDataType.INT));
            } else if (rightVal.isBoolean()) {
                return cast(PostgresDataType.BOOLEAN).isLessThan(rightVal.cast(PostgresDataType.BOOLEAN));
            } else if (rightVal.getExpressionType() == PostgresDataType.UUID) {
                return cast(PostgresDataType.UUID).isLessThan(rightVal);
            } else if (rightVal.getExpressionType() == PostgresDataType.BYTEA
                    || rightVal.getExpressionType() == PostgresDataType.ENUM) {
                PostgresConstant casted = rightVal.cast(PostgresDataType.TEXT);
                if (casted == null) {
                    return null;
                }
                return isLessThan(casted);
            } else if (rightVal.isString()) {
                return PostgresConstant.createBooleanConstant(value.compareTo(rightVal.asString()) < 0);
            } else {
                return null;
            }
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            if (type == PostgresDataType.TEXT) {
                return this;
            }
            String s = value.trim();
            switch (type) {
            case BOOLEAN:
                try {
                    return PostgresConstant.createBooleanConstant(Long.parseLong(s) != 0);
                } catch (NumberFormatException e) {
                }
                switch (s.toUpperCase()) {
                case "T":
                case "TR":
                case "TRU":
                case "TRUE":
                case "1":
                case "YES":
                case "YE":
                case "Y":
                case "ON":
                    return PostgresConstant.createTrue();
                case "F":
                case "FA":
                case "FAL":
                case "FALS":
                case "FALSE":
                case "N":
                case "NO":
                case "OF":
                case "OFF":
                default:
                    return PostgresConstant.createFalse();
                }
            case INT:
                try {
                    return PostgresConstant.createIntConstant(Long.parseLong(s));
                } catch (NumberFormatException e) {
                    return PostgresConstant.createIntConstant(-1);
                }
            case TEXT:
                return this;
            case DATE:
                return PostgresConstant.createDateConstant(s);
            case TIME:
                return PostgresConstant.createTimeConstant(s);
            case TIMETZ:
                return PostgresConstant.createTimeWithTimeZoneConstant(s);
            case TIMESTAMP:
                return PostgresConstant.createTimestampConstant(s);
            case TIMESTAMPTZ:
                return PostgresConstant.createTimestampWithTimeZoneConstant(s);
            case INTERVAL:
                return PostgresConstant.createIntervalConstant(s);
            case UUID:
                try {
                    return PostgresConstant.createUUIDConstant(UUID.fromString(s).toString());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            default:
                return null;
            }
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.TEXT;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return value;
        }

    }

    public static class IntConstant extends PostgresConstant {

        private final long val;

        public IntConstant(long val) {
            this.val = val;
        }

        @Override
        public String getTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.INT;
        }

        @Override
        public long asInt() {
            return val;
        }

        @Override
        public boolean isInt() {
            return true;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.isBoolean()) {
                return cast(PostgresDataType.BOOLEAN).isEquals(rightVal);
            } else if (rightVal.isInt()) {
                return PostgresConstant.createBooleanConstant(val == rightVal.asInt());
            } else if (PostgresConstant.isNumericConstant(rightVal)) {
                BigDecimal rightNumeric = PostgresConstant.getNumericValue(rightVal);
                if (rightNumeric == null) {
                    return null;
                }
                return PostgresConstant.createBooleanConstant(BigDecimal.valueOf(val).compareTo(rightNumeric) == 0);
            } else if (rightVal.isString()) {
                return PostgresConstant.createBooleanConstant(val == rightVal.cast(PostgresDataType.INT).asInt());
            } else {
                return null;
            }
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.isInt()) {
                return PostgresConstant.createBooleanConstant(val < rightVal.asInt());
            } else if (PostgresConstant.isNumericConstant(rightVal)) {
                BigDecimal rightNumeric = PostgresConstant.getNumericValue(rightVal);
                if (rightNumeric == null) {
                    return null;
                }
                return PostgresConstant.createBooleanConstant(BigDecimal.valueOf(val).compareTo(rightNumeric) < 0);
            } else if (rightVal.isBoolean()) {
                throw new AssertionError(rightVal);
            } else if (rightVal.isString()) {
                return PostgresConstant.createBooleanConstant(val < rightVal.cast(PostgresDataType.INT).asInt());
            } else {
                return null;
            }

        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            switch (type) {
            case BOOLEAN:
                return PostgresConstant.createBooleanConstant(val != 0);
            case INT:
                return this;
            case TEXT:
                return PostgresConstant.createTextConstant(String.valueOf(val));
            default:
                return null;
            }
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return getTextRepresentation();
        }

    }

    public static PostgresConstant createNullConstant() {
        return new PostgresNullConstant();
    }

    public String asString() {
        throw new UnsupportedOperationException(this.toString());
    }

    public boolean isString() {
        return false;
    }

    public static PostgresConstant createIntConstant(long val) {
        return new IntConstant(val);
    }

    public static PostgresConstant createBooleanConstant(boolean val) {
        return new BooleanConstant(val);
    }

    @Override
    public PostgresConstant getExpectedValue() {
        return this;
    }

    public boolean isNull() {
        return false;
    }

    public boolean asBoolean() {
        throw new UnsupportedOperationException(this.toString());
    }

    public static PostgresConstant createFalse() {
        return createBooleanConstant(false);
    }

    public static PostgresConstant createTrue() {
        return createBooleanConstant(true);
    }

    public long asInt() {
        throw new UnsupportedOperationException(this.toString());
    }

    public boolean isBoolean() {
        return false;
    }

    public abstract PostgresConstant isEquals(PostgresConstant rightVal);

    public boolean isInt() {
        return false;
    }

    protected abstract PostgresConstant isLessThan(PostgresConstant rightVal);

    @Override
    public String toString() {
        return getTextRepresentation();
    }

    public abstract PostgresConstant cast(PostgresDataType type);

    public PostgresConstant cast(PostgresCompoundDataType type) {
        if (type == null) {
            return null;
        }
        if (type.isArray()) {
            return null;
        }
        return cast(type.getDataType());
    }

    public static PostgresConstant createTextConstant(String string) {
        return new StringConstant(string);
    }

    public static class TemporalConstant extends PostgresConstant {

        private final String value;
        private final PostgresDataType type;

        public TemporalConstant(String value, PostgresDataType type) {
            this.value = PostgresTemporalUtil.asText(type, value);
            this.type = type;
        }

        @Override
        public String getTextRepresentation() {
            return String.format("'%s'::%s", value.replace("'", "''"), getCastTypeName(type));
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return value;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal instanceof TemporalConstant && rightVal.getExpressionType() == type) {
                return PostgresConstant
                        .createBooleanConstant(compareInternal(rightVal.getUnquotedTextRepresentation()) == 0);
            } else if (rightVal.isString()) {
                return cast(PostgresDataType.TEXT).isEquals(rightVal.cast(PostgresDataType.TEXT));
            } else {
                throw new IgnoreMeException();
            }
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal instanceof TemporalConstant && rightVal.getExpressionType() == type) {
                return PostgresConstant
                        .createBooleanConstant(compareInternal(rightVal.getUnquotedTextRepresentation()) < 0);
            } else if (rightVal.isString()) {
                return cast(PostgresDataType.TEXT).isLessThan(rightVal.cast(PostgresDataType.TEXT));
            } else {
                throw new IgnoreMeException();
            }
        }

        @Override
        public PostgresConstant cast(PostgresDataType castType) {
            if (castType == type) {
                return this;
            } else if (castType == PostgresDataType.TEXT) {
                return PostgresConstant.createTextConstant(value);
            } else {
                return null;
            }
        }

        @Override
        public PostgresDataType getExpressionType() {
            return type;
        }

        private int compareInternal(String otherValue) {
            return PostgresTemporalUtil.compare(type, value, otherValue);
        }

        private static String getCastTypeName(PostgresDataType type) {
            switch (type) {
            case DATE:
                return "date";
            case TIME:
                return "time";
            case TIMETZ:
                return "timetz";
            case TIMESTAMP:
                return "timestamp";
            case TIMESTAMPTZ:
                return "timestamptz";
            case INTERVAL:
                return "interval";
            default:
                throw new AssertionError(type);
            }
        }
    }

    public static PostgresConstant createDateConstant(String value) {
        return new TemporalConstant(value, PostgresDataType.DATE);
    }

    public static PostgresConstant createTimeConstant(String value) {
        return new TemporalConstant(value, PostgresDataType.TIME);
    }

    public static PostgresConstant createTimeWithTimeZoneConstant(String value) {
        return new TemporalConstant(value, PostgresDataType.TIMETZ);
    }

    public static PostgresConstant createTimestampConstant(String value) {
        return new TemporalConstant(value, PostgresDataType.TIMESTAMP);
    }

    public static PostgresConstant createTimestampWithTimeZoneConstant(String value) {
        return new TemporalConstant(value, PostgresDataType.TIMESTAMPTZ);
    }

    public static PostgresConstant createIntervalConstant(String value) {
        return new TemporalConstant(value, PostgresDataType.INTERVAL);
    }

    public abstract static class PostgresConstantBase extends PostgresConstant {

        @Override
        public String getUnquotedTextRepresentation() {
            return null;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            return null;
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            return null;
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            return null;
        }
    }

    public static class DecimalConstant extends PostgresConstantBase {

        private final BigDecimal val;

        public DecimalConstant(BigDecimal val) {
            this.val = val;
        }

        @Override
        public String getTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.DECIMAL;
        }

    }

    public static class InetConstant extends PostgresConstantBase {

        private final String val;

        public InetConstant(String val) {
            this.val = "'" + val + "'";
        }

        @Override
        public String getTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.INET;
        }

    }

    public static class FloatConstant extends PostgresConstantBase {

        private final float val;

        public FloatConstant(float val) {
            this.val = val;
        }

        @Override
        public String getTextRepresentation() {
            if (Double.isFinite(val)) {
                return String.valueOf(val);
            } else {
                return "'" + val + "'";
            }
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.FLOAT;
        }

    }

    public static class DoubleConstant extends PostgresConstantBase {

        private final double val;

        public DoubleConstant(double val) {
            this.val = val;
        }

        @Override
        public String getTextRepresentation() {
            if (Double.isFinite(val)) {
                return String.valueOf(val);
            } else {
                return "'" + val + "'";
            }
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.FLOAT;
        }

    }

    public static class BitConstant extends PostgresConstantBase {

        private final long val;

        public BitConstant(long val) {
            this.val = val;
        }

        @Override
        public String getTextRepresentation() {
            return String.format("B'%s'", Long.toBinaryString(val));
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.BIT;
        }

    }

    public static class RangeConstant extends PostgresConstantBase {

        private final long left;
        private final boolean leftIsInclusive;
        private final long right;
        private final boolean rightIsInclusive;

        public RangeConstant(long left, boolean leftIsInclusive, long right, boolean rightIsInclusive) {
            this.left = left;
            this.leftIsInclusive = leftIsInclusive;
            this.right = right;
            this.rightIsInclusive = rightIsInclusive;
        }

        @Override
        public String getTextRepresentation() {
            StringBuilder sb = new StringBuilder();
            sb.append("'");
            if (leftIsInclusive) {
                sb.append("[");
            } else {
                sb.append("(");
            }
            sb.append(left);
            sb.append(",");
            sb.append(right);
            if (rightIsInclusive) {
                sb.append("]");
            } else {
                sb.append(")");
            }
            sb.append("'");
            sb.append("::int4range");
            return sb.toString();
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.RANGE;
        }

    }

    public static PostgresConstant createDecimalConstant(BigDecimal bigDecimal) {
        return new DecimalConstant(bigDecimal);
    }

    public static PostgresConstant createFloatConstant(float val) {
        return new FloatConstant(val);
    }

    public static PostgresConstant createDoubleConstant(double val) {
        return new DoubleConstant(val);
    }

    public static PostgresConstant createRange(long left, boolean leftIsInclusive, long right,
            boolean rightIsInclusive) {
        long realLeft;
        long realRight;
        if (left > right) {
            realRight = left;
            realLeft = right;
        } else {
            realLeft = left;
            realRight = right;
        }
        return new RangeConstant(realLeft, leftIsInclusive, realRight, rightIsInclusive);
    }

    public static PostgresExpression createBitConstant(long integer) {
        return new BitConstant(integer);
    }

    public static PostgresExpression createInetConstant(String val) {
        return new InetConstant(val);
    }

    public static class UUIDConstant extends PostgresConstantBase {
        private final UUID uuid;

        public UUIDConstant(String uuid) {
            this.uuid = UUID.fromString(uuid);
        }

        @Override
        public String getTextRepresentation() {
            // Keep it explicit to avoid unknown-type ambiguity.
            return "'" + uuid + "'::uuid";
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.UUID;
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return uuid.toString();
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.getExpressionType() == PostgresDataType.UUID) {
                return PostgresConstant.createBooleanConstant(uuid.equals(UUID.fromString(rightVal.getUnquotedTextRepresentation())));
            } else if (rightVal.isString()) {
                PostgresConstant casted = rightVal.cast(PostgresDataType.UUID);
                if (casted == null) {
                    throw new IgnoreMeException();
                }
                return isEquals(casted);
            } else {
                throw new IgnoreMeException();
            }
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            } else if (rightVal.getExpressionType() == PostgresDataType.UUID) {
                return PostgresConstant
                        .createBooleanConstant(uuid.compareTo(UUID.fromString(rightVal.getUnquotedTextRepresentation())) < 0);
            } else if (rightVal.isString()) {
                PostgresConstant casted = rightVal.cast(PostgresDataType.UUID);
                if (casted == null) {
                    throw new IgnoreMeException();
                }
                return isLessThan(casted);
            } else {
                throw new IgnoreMeException();
            }
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            switch (type) {
            case UUID:
                return this;
            case TEXT:
                return PostgresConstant.createTextConstant(uuid.toString());
            default:
                return null;
            }
        }
    }

    public static class EnumConstant extends PostgresConstantBase {
        private final String typeName;
        private final String label;

        public EnumConstant(String typeName, String label) {
            this.typeName = typeName;
            this.label = label;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + label.replace("'", "''") + "'::" + typeName;
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return label;
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.ENUM;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof EnumConstant && rightVal.getExpressionType() == PostgresDataType.ENUM) {
                EnumConstant other = (EnumConstant) rightVal;
                if (!typeName.equals(other.typeName)) {
                    return null;
                }
                return PostgresConstant.createBooleanConstant(label.equals(other.label));
            }
            if (rightVal.isString()) {
                return PostgresConstant.createBooleanConstant(label.equals(rightVal.asString()));
            }
            return null;
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof EnumConstant && rightVal.getExpressionType() == PostgresDataType.ENUM) {
                EnumConstant other = (EnumConstant) rightVal;
                if (!typeName.equals(other.typeName)) {
                    return null;
                }
                int thisIndex = PostgresProvider.getEnumLabelIndex(typeName, label);
                int otherIndex = PostgresProvider.getEnumLabelIndex(other.typeName, other.label);
                if (thisIndex == -1 || otherIndex == -1) {
                    return null;
                }
                return PostgresConstant.createBooleanConstant(thisIndex < otherIndex);
            }
            if (rightVal.isString()) {
                int thisIndex = PostgresProvider.getEnumLabelIndex(typeName, label);
                int otherIndex = PostgresProvider.getEnumLabelIndex(typeName, rightVal.asString());
                if (thisIndex == -1 || otherIndex == -1) {
                    return null;
                }
                return PostgresConstant.createBooleanConstant(thisIndex < otherIndex);
            }
            return null;
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            switch (type) {
            case ENUM:
                return this;
            case TEXT:
                return PostgresConstant.createTextConstant(label);
            default:
                return null;
            }
        }
    }

    public static class ArrayConstant extends PostgresConstantBase {
        private final PostgresCompoundDataType elementType;
        private final List<PostgresConstant> elements;

        public ArrayConstant(List<PostgresConstant> elements, PostgresCompoundDataType elementType) {
            this.elements = List.copyOf(elements);
            this.elementType = elementType;
        }

        @Override
        public String getTextRepresentation() {
            StringBuilder sb = new StringBuilder();
            sb.append("ARRAY[");
            for (int i = 0; i < elements.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(elements.get(i).getTextRepresentation());
            }
            sb.append("]");
            if (elements.isEmpty()) {
                sb.append("::");
                sb.append(getTypeName(PostgresCompoundDataType.createArray(elementType)));
            }
            return sb.toString();
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.ARRAY;
        }

        @Override
        public PostgresCompoundDataType getExpressionCompoundType() {
            return PostgresCompoundDataType.createArray(elementType);
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            if (type == PostgresDataType.TEXT) {
                return PostgresConstant.createTextConstant(getTextRepresentation());
            }
            if (type == PostgresDataType.ARRAY) {
                return this;
            }
            return null;
        }

        @Override
        public PostgresConstant cast(PostgresCompoundDataType type) {
            if (!type.isArray()) {
                return cast(type.getDataType());
            }
            List<PostgresConstant> castedElements = new ArrayList<>();
            for (PostgresConstant element : elements) {
                PostgresConstant casted = element.cast(type.getElemType());
                if (casted == null) {
                    return null;
                }
                castedElements.add(casted);
            }
            return createArrayConstant(castedElements, type.getElemType());
        }

        public Integer getLength(int dimension) {
            if (dimension <= 0) {
                return null;
            }
            if (dimension == 1) {
                return elements.size();
            }
            if (elements.isEmpty()) {
                return 0;
            }
            PostgresConstant first = elements.get(0);
            if (!(first instanceof ArrayConstant)) {
                return null;
            }
            return ((ArrayConstant) first).getLength(dimension - 1);
        }

        public int getCardinality() {
            int cardinality = elements.size();
            if (elements.isEmpty()) {
                return 0;
            }
            PostgresConstant first = elements.get(0);
            if (first instanceof ArrayConstant) {
                Integer nestedLength = ((ArrayConstant) first).getCardinality();
                if (nestedLength < 0) {
                    return -1;
                }
                cardinality *= nestedLength;
            }
            return cardinality;
        }
    }

    public static PostgresConstant createUUIDConstant(String uuid) {
        return new UUIDConstant(uuid);
    }

    public static class ByteaConstant extends PostgresConstantBase {
        private final String hex;

        public ByteaConstant(String hex) {
            this.hex = hex.toLowerCase(Locale.ROOT);
        }

        @Override
        public String getTextRepresentation() {
            // Stable representation across settings/locales.
            return "decode('" + hex + "','hex')";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return "\\x" + hex;
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.BYTEA;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof ByteaConstant && rightVal.getExpressionType() == PostgresDataType.BYTEA) {
                return PostgresConstant.createBooleanConstant(hex.equals(((ByteaConstant) rightVal).hex));
            }
            if (rightVal.isString()) {
                return PostgresConstant.createBooleanConstant(getUnquotedTextRepresentation().equals(rightVal.asString()));
            }
            return null;
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof ByteaConstant && rightVal.getExpressionType() == PostgresDataType.BYTEA) {
                return PostgresConstant.createBooleanConstant(hex.compareTo(((ByteaConstant) rightVal).hex) < 0);
            }
            if (rightVal.isString()) {
                return PostgresConstant
                        .createBooleanConstant(getUnquotedTextRepresentation().compareTo(rightVal.asString()) < 0);
            }
            return null;
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            switch (type) {
            case BYTEA:
                return this;
            case TEXT:
                return PostgresConstant.createTextConstant(getUnquotedTextRepresentation());
            default:
                return null;
            }
        }
    }

    public static PostgresConstant createByteaConstant(String hex) {
        return new ByteaConstant(hex);
    }

    public static PostgresConstant createEnumConstant(String typeName, String label) {
        return new EnumConstant(typeName, label);
    }

    public static PostgresConstant createArrayConstant(List<PostgresConstant> elements, PostgresCompoundDataType elementType) {
        return new ArrayConstant(elements, elementType);
    }

    public static PostgresConstant createArrayConstant(Array array, PostgresCompoundDataType arrayType)
            throws SQLException {
        return createArrayConstantFromObject(array.getArray(), arrayType);
    }

    private static PostgresConstant createArrayConstantFromObject(Object rawArray, PostgresCompoundDataType arrayType) {
        if (!arrayType.isArray()) {
            throw new AssertionError(arrayType);
        }
        if (rawArray == null) {
            return createNullConstant();
        }
        if (!rawArray.getClass().isArray()) {
            throw new IgnoreMeException();
        }
        int length = java.lang.reflect.Array.getLength(rawArray);
        List<PostgresConstant> constants = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            constants.add(createConstantFromObject(java.lang.reflect.Array.get(rawArray, i), arrayType.getElemType()));
        }
        return createArrayConstant(constants, arrayType.getElemType());
    }

    public static PostgresConstant createConstantFromObject(Object value, PostgresCompoundDataType type) {
        if (value == null) {
            return createNullConstant();
        }
        if (type.isArray()) {
            if (value instanceof Array) {
                try {
                    return createArrayConstant((Array) value, type);
                } catch (SQLException e) {
                    throw new IgnoreMeException();
                }
            }
            return createArrayConstantFromObject(value, type);
        }
        switch (type.getDataType()) {
        case INT:
            return createIntConstant(((Number) value).longValue());
        case BOOLEAN:
            return createBooleanConstant((Boolean) value);
        case TEXT:
        case JSON:
        case JSONB:
        case UUID:
        case BYTEA:
        case ENUM:
            return createTextConstant(String.valueOf(value));
        case DATE:
            return createDateConstant(String.valueOf(value));
        case TIME:
            return createTimeConstant(String.valueOf(value));
        case TIMETZ:
            return createTimeWithTimeZoneConstant(String.valueOf(value));
        case TIMESTAMP:
            return createTimestampConstant(String.valueOf(value));
        case TIMESTAMPTZ:
            return createTimestampWithTimeZoneConstant(String.valueOf(value));
        case INTERVAL:
            return createIntervalConstant(getIntervalTextRepresentation(value));
        default:
            throw new IgnoreMeException();
        }
    }

    private static String getIntervalTextRepresentation(Object value) {
        if (value instanceof PGInterval) {
            PGInterval interval = (PGInterval) value;
            int totalMonths = interval.getYears() * 12 + interval.getMonths();
            int days = interval.getDays();
            double seconds = interval.getSeconds();
            long wholeSeconds = (long) seconds;
            long nanos = Math.round((seconds - wholeSeconds) * 1_000_000_000L);
            long totalNanos = ((interval.getHours() * 60L + interval.getMinutes()) * 60L + wholeSeconds)
                    * 1_000_000_000L + nanos;
            return new IntervalValue(totalMonths, days, totalNanos).toCanonicalString();
        }
        return String.valueOf(value);
    }

    private static String getTypeName(PostgresCompoundDataType type) {
        if (type.isArray()) {
            return getTypeName(type.getElemType()) + "[]";
        }
        switch (type.getDataType()) {
        case BOOLEAN:
            return "BOOLEAN";
        case INT:
            return "INT";
        case TEXT:
            return "TEXT";
        case REAL:
            return "FLOAT";
        case DECIMAL:
            return "DECIMAL";
        case FLOAT:
            return "REAL";
        case RANGE:
            return "int4range";
        case MONEY:
            return "MONEY";
        case INET:
            return "INET";
        case BIT:
            return "BIT";
        case DATE:
            return "DATE";
        case TIME:
            return "TIME";
        case TIMETZ:
            return "TIME WITH TIME ZONE";
        case TIMESTAMP:
            return "TIMESTAMP";
        case TIMESTAMPTZ:
            return "TIMESTAMP WITH TIME ZONE";
        case INTERVAL:
            return "INTERVAL";
        case JSON:
            return "JSON";
        case JSONB:
            return "JSONB";
        case UUID:
            return "UUID";
        case BYTEA:
            return "BYTEA";
        case ENUM:
            return "TEXT";
        default:
            throw new AssertionError(type);
        }
    }

    private static abstract class PostgresTemporalConstant extends PostgresConstant {
        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public PostgresConstant cast(PostgresDataType type) {
            if (type == PostgresDataType.TEXT) {
                return PostgresConstant.createTextConstant(getUnquotedTextRepresentation());
            }
            return null;
        }
    }

    public static class DateConstant extends PostgresTemporalConstant {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
        private final LocalDate date;
        private final long epochDay;

        public DateConstant(LocalDate date) {
            this.date = date;
            this.epochDay = date.toEpochDay();
        }

        @Override
        public String getTextRepresentation() {
            return "'" + FMT.format(date) + "'::date";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return FMT.format(date);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.DATE;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof DateConstant) {
                return PostgresConstant.createBooleanConstant(epochDay == ((DateConstant) rightVal).epochDay);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof DateConstant) {
                return PostgresConstant.createBooleanConstant(epochDay < ((DateConstant) rightVal).epochDay);
            }
            throw new IgnoreMeException();
        }
    }

    public static class TimeConstant extends PostgresTemporalConstant {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
        private final LocalTime time;
        private final int secondOfDay;

        public TimeConstant(LocalTime time) {
            this.time = time.withNano(0);
            this.secondOfDay = this.time.toSecondOfDay();
        }

        @Override
        public String getTextRepresentation() {
            return "'" + FMT.format(time) + "'::time";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return FMT.format(time);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.TIME;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimeConstant) {
                return PostgresConstant.createBooleanConstant(secondOfDay == ((TimeConstant) rightVal).secondOfDay);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimeConstant) {
                return PostgresConstant.createBooleanConstant(secondOfDay < ((TimeConstant) rightVal).secondOfDay);
            }
            throw new IgnoreMeException();
        }
    }

    public static class TimestampConstant extends PostgresTemporalConstant {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private final LocalDateTime timestamp;
        private final long epochSecondUtc;

        public TimestampConstant(LocalDateTime timestamp) {
            this.timestamp = timestamp.withNano(0);
            this.epochSecondUtc = this.timestamp.toEpochSecond(ZoneOffset.UTC);
        }

        @Override
        public String getTextRepresentation() {
            return "'" + FMT.format(timestamp) + "'::timestamp";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return FMT.format(timestamp);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.TIMESTAMP;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimestampConstant) {
                return PostgresConstant
                        .createBooleanConstant(epochSecondUtc == ((TimestampConstant) rightVal).epochSecondUtc);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimestampConstant) {
                return PostgresConstant.createBooleanConstant(epochSecondUtc < ((TimestampConstant) rightVal).epochSecondUtc);
            }
            throw new IgnoreMeException();
        }
    }

    public static class TimestampTZConstant extends PostgresTemporalConstant {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
        private final OffsetDateTime timestamptz;
        private final long epochSecond;

        public TimestampTZConstant(OffsetDateTime timestamptz) {
            this.timestamptz = timestamptz.withNano(0);
            this.epochSecond = this.timestamptz.toInstant().getEpochSecond();
        }

        @Override
        public String getTextRepresentation() {
            return "'" + FMT.format(timestamptz) + "'::timestamptz";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return FMT.format(timestamptz);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.TIMESTAMPTZ;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimestampTZConstant) {
                return PostgresConstant.createBooleanConstant(epochSecond == ((TimestampTZConstant) rightVal).epochSecond);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimestampTZConstant) {
                return PostgresConstant.createBooleanConstant(epochSecond < ((TimestampTZConstant) rightVal).epochSecond);
            }
            throw new IgnoreMeException();
        }
    }

    public static class TimeTZConstant extends PostgresTemporalConstant {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ssXXX");
        private final OffsetTime timetz;

        public TimeTZConstant(OffsetTime timetz) {
            this.timetz = timetz.withNano(0);
        }

        @Override
        public String getTextRepresentation() {
            return "'" + FMT.format(timetz) + "'::timetz";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return FMT.format(timetz);
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.TIMETZ;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimeTZConstant) {
                return PostgresConstant.createBooleanConstant(timetz.equals(((TimeTZConstant) rightVal).timetz));
            }
            throw new IgnoreMeException();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof TimeTZConstant) {
                return PostgresConstant.createBooleanConstant(timetz.compareTo(((TimeTZConstant) rightVal).timetz) < 0);
            }
            throw new IgnoreMeException();
        }
    }

    public static class IntervalConstant extends PostgresTemporalConstant {
        private final long seconds;

        public IntervalConstant(long seconds) {
            this.seconds = seconds;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + seconds + " seconds'::interval";
        }

        @Override
        public String getUnquotedTextRepresentation() {
            return seconds + " seconds";
        }

        @Override
        public PostgresDataType getExpressionType() {
            return PostgresDataType.INTERVAL;
        }

        @Override
        public PostgresConstant isEquals(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof IntervalConstant) {
                return PostgresConstant.createBooleanConstant(seconds == ((IntervalConstant) rightVal).seconds);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected PostgresConstant isLessThan(PostgresConstant rightVal) {
            if (rightVal.isNull()) {
                return PostgresConstant.createNullConstant();
            }
            if (rightVal instanceof IntervalConstant) {
                return PostgresConstant.createBooleanConstant(seconds < ((IntervalConstant) rightVal).seconds);
            }
            throw new IgnoreMeException();
        }
    }

    private static boolean isNumericConstant(PostgresConstant constant) {
        if (constant == null || constant.isInt()) {
            return false;
        }
        PostgresDataType type = constant.getExpressionType();
        return type == PostgresDataType.DECIMAL || type == PostgresDataType.FLOAT || type == PostgresDataType.REAL;
    }

    private static BigDecimal getNumericValue(PostgresConstant constant) {
        if (constant == null) {
            return null;
        }
        if (constant.isInt()) {
            return BigDecimal.valueOf(constant.asInt());
        }
        if (!isNumericConstant(constant)) {
            return null;
        }
        try {
            return new BigDecimal(constant.getUnquotedTextRepresentation());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static PostgresConstant createDateConstant(LocalDate date) {
        return new DateConstant(date);
    }

    public static PostgresConstant createTimeConstant(LocalTime time) {
        return new TimeConstant(time);
    }

    public static PostgresConstant createTimeWithTimeZoneConstant(OffsetTime time) {
        return new TimeTZConstant(time);
    }

    public static PostgresConstant createTimestampConstant(LocalDateTime timestamp) {
        return new TimestampConstant(timestamp);
    }

    public static PostgresConstant createTimestamptzConstant(Instant instant) {
        return new TimestampTZConstant(instant.atOffset(ZoneOffset.UTC));
    }

    public static PostgresConstant createIntervalSecondsConstant(long seconds) {
        return new IntervalConstant(seconds);
    }

}
