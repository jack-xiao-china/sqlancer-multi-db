package sqlancer.mysql.ast;

import java.math.BigInteger;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.ast.MySQLCastOperation.CastType;

public abstract class MySQLConstant implements MySQLExpression {

    public boolean isInt() {
        return false;
    }

    public boolean isNull() {
        return false;
    }

    public abstract static class MySQLNoPQSConstant extends MySQLConstant {

        @Override
        public boolean asBooleanNotNull() {
            throw throwException();
        }

        private RuntimeException throwException() {
            throw new UnsupportedOperationException("not applicable for PQS evaluation!");
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            // Return null constant instead of null pointer to avoid NPE propagation
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            // For non-null values, this constant type cannot do proper comparison
            // Throw IgnoreMeException to skip this comparison path
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            throw throwException();
        }

        @Override
        public String castAsString() {
            throw throwException();

        }

        @Override
        public MySQLDataType getType() {
            throw throwException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            throw throwException();
        }

    }

    public static class MySQLDoubleConstant extends MySQLNoPQSConstant {

        private final double val;

        public MySQLDoubleConstant(double val) {
            this.val = val;
            if (Double.isInfinite(val) || Double.isNaN(val)) {
                // seems to not be supported by MySQL
                throw new IgnoreMeException();
            }
        }

        @Override
        public boolean asBooleanNotNull() {
            // In MySQL, a double is truthy if it's non-zero
            return val != 0;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.isInt()) {
                return MySQLConstant.createBoolean(val == rightVal.getInt());
            }
            // For other types, throw IgnoreMeException
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            if (type == CastType.SIGNED || type == CastType.UNSIGNED) {
                return MySQLConstant.createIntConstant((long) val, type == CastType.SIGNED);
            }
            throw new IgnoreMeException();
        }

        @Override
        public String castAsString() {
            return String.valueOf(val);
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.DOUBLE;
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.isInt()) {
                return MySQLConstant.createBoolean(val < rightVal.getInt());
            }
            throw new IgnoreMeException();
        }

        @Override
        public String getTextRepresentation() {
            return String.valueOf(val);
        }

    }

    public static class MySQLTextConstant extends MySQLConstant {

        private final String value;
        private final boolean singleQuotes;

        public MySQLTextConstant(String value) {
            this.value = value;
            singleQuotes = Randomly.getBoolean();

        }

        private void checkIfSmallFloatingPointText() {
            boolean isSmallFloatingPointText = isString() && asBooleanNotNull()
                    && castAs(CastType.SIGNED).getInt() == 0;
            if (isSmallFloatingPointText) {
                throw new IgnoreMeException();
            }
        }

        @Override
        public boolean asBooleanNotNull() {
            // TODO implement as cast
            for (int i = value.length(); i >= 0; i--) {
                try {
                    String substring = value.substring(0, i);
                    Double val = Double.valueOf(substring);
                    return val != 0 && !Double.isNaN(val);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            return false;
            // return castAs(CastType.SIGNED).getInt() != 0;
        }

        @Override
        public String getTextRepresentation() {
            StringBuilder sb = new StringBuilder();
            String quotes = singleQuotes ? "'" : "\"";
            sb.append(quotes);
            String text = value.replace(quotes, quotes + quotes).replace("\\", "\\\\");
            sb.append(text);
            sb.append(quotes);
            return sb.toString();
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            } else if (rightVal.isInt()) {
                checkIfSmallFloatingPointText();
                if (asBooleanNotNull()) {
                    // TODO support SELECT .123 = '.123'; by converting to floating point
                    throw new IgnoreMeException();
                }
                return castAs(CastType.SIGNED).isEquals(rightVal);
            } else if (rightVal.isString()) {
                return MySQLConstant.createBoolean(value.equalsIgnoreCase(rightVal.getString()));
            } else {
                // 日期/时间等类型的比较，忽略而不是抛出 AssertionError
                throw new IgnoreMeException();
            }
        }

        @Override
        public String getString() {
            return value;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            if (type == CastType.SIGNED || type == CastType.UNSIGNED) {
                String value = this.value;
                while (value.startsWith(" ") || value.startsWith("\t") || value.startsWith("\n")) {
                    if (value.startsWith("\n")) {
                        /* workaround for https://bugs.mysql.com/bug.php?id=96294 */
                        throw new IgnoreMeException();
                    }
                    value = value.substring(1);
                }
                for (int i = value.length(); i >= 0; i--) {
                    try {
                        String substring = value.substring(0, i);
                        long val = Long.parseLong(substring);
                        return MySQLConstant.createIntConstant(val, type == CastType.SIGNED);
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                return MySQLConstant.createIntConstant(0, type == CastType.SIGNED);
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public String castAsString() {
            return value;
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.VARCHAR;
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            } else if (rightVal.isInt()) {
                if (asBooleanNotNull()) {
                    // TODO uspport floating point
                    throw new IgnoreMeException();
                }
                checkIfSmallFloatingPointText();
                return castAs(rightVal.isSigned() ? CastType.SIGNED : CastType.UNSIGNED).isLessThan(rightVal);
            } else if (rightVal.isString()) {
                // unexpected result for '-' < "!";
                // return
                // MySQLConstant.createBoolean(value.compareToIgnoreCase(rightVal.getString()) <
                // 0);
                throw new IgnoreMeException();
            } else {
                throw new AssertionError(rightVal);
            }
        }

    }

    public static class MySQLIntConstant extends MySQLConstant {

        private final long value;
        private final String stringRepresentation;
        private final boolean isSigned;

        public MySQLIntConstant(long value, boolean isSigned) {
            this.value = value;
            this.isSigned = isSigned;
            if (isSigned) {
                stringRepresentation = String.valueOf(value);
            } else {
                stringRepresentation = Long.toUnsignedString(value);
            }
        }

        public MySQLIntConstant(long value, String stringRepresentation) {
            this.value = value;
            this.stringRepresentation = stringRepresentation;
            isSigned = true;
        }

        @Override
        public boolean isInt() {
            return true;
        }

        @Override
        public long getInt() {
            return value;
        }

        @Override
        public boolean asBooleanNotNull() {
            return value != 0;
        }

        @Override
        public String getTextRepresentation() {
            return stringRepresentation;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal.isInt()) {
                return MySQLConstant.createBoolean(new BigInteger(getStringRepr())
                        .compareTo(new BigInteger(((MySQLIntConstant) rightVal).getStringRepr())) == 0);
            } else if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            } else if (rightVal.isString()) {
                if (rightVal.asBooleanNotNull()) {
                    // TODO support SELECT .123 = '.123'; by converting to floating point
                    throw new IgnoreMeException();
                }
                return isEquals(rightVal.castAs(CastType.SIGNED));
            } else {
                throw new AssertionError(rightVal);
            }
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            if (type == CastType.SIGNED) {
                return new MySQLIntConstant(value, true);
            } else if (type == CastType.UNSIGNED) {
                return new MySQLIntConstant(value, false);
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public String castAsString() {
            if (isSigned) {
                return String.valueOf(value);
            } else {
                return Long.toUnsignedString(value);
            }
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.INT;
        }

        @Override
        public boolean isSigned() {
            return isSigned;
        }

        private String getStringRepr() {
            if (isSigned) {
                return String.valueOf(value);
            } else {
                return Long.toUnsignedString(value);
            }
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal.isInt()) {
                long intVal = rightVal.getInt();
                if (isSigned && rightVal.isSigned()) {
                    return MySQLConstant.createBoolean(value < intVal);
                } else {
                    return MySQLConstant.createBoolean(new BigInteger(getStringRepr())
                            .compareTo(new BigInteger(((MySQLIntConstant) rightVal).getStringRepr())) < 0);
                    // return MySQLConstant.createBoolean(Long.compareUnsigned(value, intVal) < 0);
                }
            } else if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            } else if (rightVal.isString()) {
                if (rightVal.asBooleanNotNull()) {
                    // TODO support float
                    throw new IgnoreMeException();
                }
                return isLessThan(rightVal.castAs(isSigned ? CastType.SIGNED : CastType.UNSIGNED));
            } else {
                throw new AssertionError(rightVal);
            }
        }

    }

    public static class MySQLNullConstant extends MySQLConstant {

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public boolean asBooleanNotNull() {
            throw new UnsupportedOperationException(this.toString());
        }

        @Override
        public String getTextRepresentation() {
            return "NULL";
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            return MySQLConstant.createNullConstant();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            return this;
        }

        @Override
        public String castAsString() {
            return "NULL";
        }

        @Override
        public MySQLDataType getType() {
            return null;
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            return this;
        }

    }

    private static String pad2(int value) {
        return String.format("%02d", value);
    }

    private static String pad4(int value) {
        return String.format("%04d", value);
    }

    private static String padFraction(int fraction, int fsp) {
        if (fsp <= 0) {
            return "";
        }
        return String.format("%0" + fsp + "d", fraction);
    }

    public static class MySQLDateConstant extends MySQLConstant {

        private final int year;
        private final int month;
        private final int day;

        public MySQLDateConstant(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + castAsString() + "'";
        }

        @Override
        public String castAsString() {
            return pad4(year) + "-" + pad2(month) + "-" + pad2(day);
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.DATE;
        }

        @Override
        public boolean asBooleanNotNull() {
            // Temporal literals are non-NULL and (for our purposes) always truthy.
            return true;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().equals(rightVal.castAsString()));
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // Simplification: casting DATE/TIME/TIMESTAMP/DATETIME literals to numeric is not implemented.
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            // Fixed-width representation makes lexicographic order match chronological order.
            return MySQLConstant.createBoolean(castAsString().compareTo(rightVal.castAsString()) < 0);
        }
    }

    public static class MySQLTimeConstant extends MySQLConstant {

        private final int hour;
        private final int minute;
        private final int second;
        private final int fraction;
        private final int fsp;

        public MySQLTimeConstant(int hour, int minute, int second) {
            this(hour, minute, second, 0, 0);
        }

        public MySQLTimeConstant(int hour, int minute, int second, int fraction, int fsp) {
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.fraction = fraction;
            this.fsp = fsp;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + castAsString() + "'";
        }

        @Override
        public String castAsString() {
            String base = pad2(hour) + ":" + pad2(minute) + ":" + pad2(second);
            if (fsp > 0) {
                // TIME fsp=6 with fraction=0 must render as ".000000"
                return base + "." + padFraction(fraction, fsp);
            }
            return base;
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.TIME;
        }

        @Override
        public boolean asBooleanNotNull() {
            return true;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().equals(rightVal.castAsString()));
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // Simplification: casting TIME literals to numeric is not implemented.
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().compareTo(rightVal.castAsString()) < 0);
        }
    }

    public static class MySQLDateTimeConstant extends MySQLConstant {

        private final int year;
        private final int month;
        private final int day;
        private final int hour;
        private final int minute;
        private final int second;
        private final int fraction;
        private final int fsp;

        public MySQLDateTimeConstant(int year, int month, int day, int hour, int minute, int second) {
            this(year, month, day, hour, minute, second, 0, 0);
        }

        public MySQLDateTimeConstant(int year, int month, int day, int hour, int minute, int second, int fraction,
                int fsp) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.fraction = fraction;
            this.fsp = fsp;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + castAsString() + "'";
        }

        @Override
        public String castAsString() {
            String base = pad4(year) + "-" + pad2(month) + "-" + pad2(day) + " " + pad2(hour) + ":"
                    + pad2(minute) + ":" + pad2(second);
            if (fsp > 0) {
                // DATETIME/TIMESTAMP fsp=6 with fraction=0 must render as ".000000"
                return base + "." + padFraction(fraction, fsp);
            }
            return base;
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.DATETIME;
        }

        @Override
        public boolean asBooleanNotNull() {
            return true;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().equals(rightVal.castAsString()));
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // Simplification: casting DATETIME literals to numeric is not implemented.
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().compareTo(rightVal.castAsString()) < 0);
        }
    }

    public static class MySQLTimestampConstant extends MySQLConstant {

        private final int year;
        private final int month;
        private final int day;
        private final int hour;
        private final int minute;
        private final int second;
        private final int fraction;
        private final int fsp;

        public MySQLTimestampConstant(int year, int month, int day, int hour, int minute, int second) {
            this(year, month, day, hour, minute, second, 0, 0);
        }

        public MySQLTimestampConstant(int year, int month, int day, int hour, int minute, int second, int fraction,
                int fsp) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.fraction = fraction;
            this.fsp = fsp;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + castAsString() + "'";
        }

        @Override
        public String castAsString() {
            String base = pad4(year) + "-" + pad2(month) + "-" + pad2(day) + " " + pad2(hour) + ":"
                    + pad2(minute) + ":" + pad2(second);
            if (fsp > 0) {
                // DATETIME/TIMESTAMP fsp=6 with fraction=0 must render as ".000000"
                return base + "." + padFraction(fraction, fsp);
            }
            return base;
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.TIMESTAMP;
        }

        @Override
        public boolean asBooleanNotNull() {
            return true;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().equals(rightVal.castAsString()));
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // Simplification: casting TIMESTAMP literals to numeric is not implemented.
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().compareTo(rightVal.castAsString()) < 0);
        }
    }

    public static class MySQLYearConstant extends MySQLConstant {

        private final int year;

        public MySQLYearConstant(int year) {
            this.year = year;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + castAsString() + "'";
        }

        @Override
        public String castAsString() {
            return String.valueOf(year);
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.YEAR;
        }

        @Override
        public boolean asBooleanNotNull() {
            return true;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(castAsString().equals(rightVal.castAsString()));
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // Simplification: casting YEAR literals to numeric is not implemented.
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal.getType() != getType()) {
                throw new IgnoreMeException();
            }
            return MySQLConstant.createBoolean(Integer.compare(year, ((MySQLYearConstant) rightVal).year) < 0);
        }
    }

    public static class MySQLBitConstant extends MySQLConstant {

        private final long value;  // 位值（使用 long 存储，最多支持64位）
        private final int width;   // 位宽度

        public MySQLBitConstant(long value, int width) {
            this.value = value;
            this.width = width;
        }

        @Override
        public String getTextRepresentation() {
            // MySQL BIT 类型使用 b'...' 格式
            String binaryStr = Long.toBinaryString(value);
            // 补齐到指定宽度
            while (binaryStr.length() < width) {
                binaryStr = "0" + binaryStr;
            }
            return "b'" + binaryStr + "'";
        }

        @Override
        public String castAsString() {
            return getTextRepresentation();
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.BIT;
        }

        @Override
        public boolean asBooleanNotNull() {
            return value != 0;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLBitConstant) {
                return MySQLConstant.createBoolean(value == ((MySQLBitConstant) rightVal).value);
            }
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            if (type == CastType.SIGNED) {
                return MySQLConstant.createIntConstant(value, true);
            } else if (type == CastType.UNSIGNED) {
                return MySQLConstant.createIntConstant(value, false);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLBitConstant) {
                return MySQLConstant.createBoolean(value < ((MySQLBitConstant) rightVal).value);
            }
            throw new IgnoreMeException();
        }

        public long getBitValue() {
            return value;
        }

        public int getWidth() {
            return width;
        }
    }

    public static class MySQLEnumConstant extends MySQLConstant {

        private final String value;
        private final int index;  // ENUM 索引值（MySQL 内部存储为整数，从1开始）

        public MySQLEnumConstant(String value, int index) {
            this.value = value;
            this.index = index;
        }

        @Override
        public String getTextRepresentation() {
            return "'" + value + "'";
        }

        @Override
        public String castAsString() {
            return value;
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.ENUM;
        }

        @Override
        public boolean asBooleanNotNull() {
            // ENUM 值作为布尔值时，索引值非0为true
            return index != 0;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLEnumConstant) {
                return MySQLConstant.createBoolean(value.equals(((MySQLEnumConstant) rightVal).value));
            }
            // 与字符串比较
            if (rightVal.isString()) {
                return MySQLConstant.createBoolean(value.equals(rightVal.getString()));
            }
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            if (type == CastType.SIGNED) {
                return MySQLConstant.createIntConstant(index, true);
            } else if (type == CastType.UNSIGNED) {
                return MySQLConstant.createIntConstant(index, false);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLEnumConstant) {
                // ENUM 比较基于索引值
                return MySQLConstant.createBoolean(index < ((MySQLEnumConstant) rightVal).index);
            }
            throw new IgnoreMeException();
        }

        public String getEnumValue() {
            return value;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String getString() {
            return value;
        }
    }

    public static class MySQLSetConstant extends MySQLConstant {

        private final java.util.Set<String> values;  // SET 值集合
        private final long bitmap;         // MySQL 内部存储为位图

        public MySQLSetConstant(java.util.Set<String> values, long bitmap) {
            this.values = values;
            this.bitmap = bitmap;
        }

        @Override
        public String getTextRepresentation() {
            // SET 值用逗号分隔
            return "'" + values.stream().collect(java.util.stream.Collectors.joining(",")) + "'";
        }

        @Override
        public String castAsString() {
            return values.stream().collect(java.util.stream.Collectors.joining(","));
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.SET;
        }

        @Override
        public boolean asBooleanNotNull() {
            // SET 值作为布尔值时，位图非0为true
            return bitmap != 0;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLSetConstant) {
                return MySQLConstant.createBoolean(values.equals(((MySQLSetConstant) rightVal).values));
            }
            // 与字符串比较（字符串形式为逗号分隔的值列表）
            if (rightVal.isString()) {
                return MySQLConstant.createBoolean(castAsString().equals(rightVal.getString()));
            }
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            if (type == CastType.SIGNED) {
                return MySQLConstant.createIntConstant(bitmap, true);
            } else if (type == CastType.UNSIGNED) {
                return MySQLConstant.createIntConstant(bitmap, false);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLSetConstant) {
                // SET 比较基于位图值
                return MySQLConstant.createBoolean(bitmap < ((MySQLSetConstant) rightVal).bitmap);
            }
            throw new IgnoreMeException();
        }

        public java.util.Set<String> getSetValues() {
            return values;
        }

        public long getBitmap() {
            return bitmap;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String getString() {
            return castAsString();
        }
    }

    public static class MySQLJSONConstant extends MySQLConstant {

        private final String jsonValue;  // JSON 值的字符串表示

        public MySQLJSONConstant(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        @Override
        public String getTextRepresentation() {
            // MySQL JSON 值使用单引号，需要转义内部单引号
            // MySQL escapes ' with \' and \ with \\
            String escaped = jsonValue.replace("\\", "\\\\").replace("'", "\\'");
            return "'" + escaped + "'";
        }

        @Override
        public String castAsString() {
            return jsonValue;
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.JSON;
        }

        @Override
        public boolean asBooleanNotNull() {
            // JSON 值作为布尔值时，非空为 true
            return jsonValue != null && !jsonValue.isEmpty();
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLJSONConstant) {
                // JSON 比较基于字符串内容（简单实现）
                return MySQLConstant.createBoolean(jsonValue.equals(((MySQLJSONConstant) rightVal).jsonValue));
            }
            // 与字符串比较
            if (rightVal.isString()) {
                return MySQLConstant.createBoolean(jsonValue.equals(rightVal.getString()));
            }
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // JSON 类型不能直接转换为数值类型
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLJSONConstant) {
                // JSON 比较基于字符串排序
                return MySQLConstant.createBoolean(jsonValue.compareTo(((MySQLJSONConstant) rightVal).jsonValue) < 0);
            }
            throw new IgnoreMeException();
        }

        public String getJsonValue() {
            return jsonValue;
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String getString() {
            return jsonValue;
        }
    }

    public static class MySQLBinaryConstant extends MySQLConstant {

        private final byte[] value;
        private final int length;  // 用于 VARBINARY/BINARY 的长度定义

        public MySQLBinaryConstant(byte[] value) {
            this.value = value;
            this.length = value.length;
        }

        public MySQLBinaryConstant(byte[] value, int length) {
            this.value = value;
            this.length = length;
        }

        @Override
        public String getTextRepresentation() {
            // MySQL 二进制值使用 X'hex' 格式或 _binary'...' 格式
            // 使用十六进制格式更安全
            StringBuilder hex = new StringBuilder("X'");
            for (byte b : value) {
                hex.append(String.format("%02X", b));
            }
            hex.append("'");
            return hex.toString();
        }

        @Override
        public String castAsString() {
            // 转换为字符串形式
            StringBuilder sb = new StringBuilder();
            for (byte b : value) {
                if (b >= 32 && b < 127) {
                    sb.append((char) b);
                } else {
                    sb.append(String.format("\\x%02X", b));
                }
            }
            return sb.toString();
        }

        @Override
        public MySQLDataType getType() {
            // 根据长度判断类型，这里简化处理
            return MySQLDataType.BLOB;
        }

        @Override
        public boolean asBooleanNotNull() {
            // 二进制值作为布尔值时，非空为 true
            return value != null && value.length > 0 && value[0] != 0;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLBinaryConstant) {
                byte[] otherValue = ((MySQLBinaryConstant) rightVal).value;
                if (value.length != otherValue.length) {
                    return MySQLConstant.createFalse();
                }
                for (int i = 0; i < value.length; i++) {
                    if (value[i] != otherValue[i]) {
                        return MySQLConstant.createFalse();
                    }
                }
                return MySQLConstant.createTrue();
            }
            // 与字符串比较（需要特殊处理）
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(CastType type) {
            // 二进制类型不能直接转换为数值类型
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLBinaryConstant) {
                byte[] otherValue = ((MySQLBinaryConstant) rightVal).value;
                // 比较字节序列
                int minLen = Math.min(value.length, otherValue.length);
                for (int i = 0; i < minLen; i++) {
                    if (value[i] < otherValue[i]) {
                        return MySQLConstant.createTrue();
                    } else if (value[i] > otherValue[i]) {
                        return MySQLConstant.createFalse();
                    }
                }
                // 相同长度的前缀部分，比较长度
                return MySQLConstant.createBoolean(value.length < otherValue.length);
            }
            throw new IgnoreMeException();
        }

        public byte[] getBinaryValue() {
            return value;
        }

        public int getLength() {
            return length;
        }
    }

    public long getInt() {
        throw new UnsupportedOperationException();
    }

    public boolean isSigned() {
        return false;
    }

    public String getString() {
        throw new UnsupportedOperationException();
    }

    public boolean isString() {
        return false;
    }

    public static MySQLConstant createNullConstant() {
        return new MySQLNullConstant();
    }

    public static MySQLConstant createIntConstant(long value) {
        return new MySQLIntConstant(value, true);
    }

    public static MySQLConstant createIntConstant(long value, boolean signed) {
        return new MySQLIntConstant(value, signed);
    }

    public static MySQLConstant createUnsignedIntConstant(long value) {
        return new MySQLIntConstant(value, false);
    }

    public static MySQLConstant createIntConstantNotAsBoolean(long value) {
        return new MySQLIntConstant(value, String.valueOf(value));
    }

    @Override
    public MySQLConstant getExpectedValue() {
        return this;
    }

    public abstract boolean asBooleanNotNull();

    public abstract String getTextRepresentation();

    public static MySQLConstant createFalse() {
        return MySQLConstant.createIntConstant(0);
    }

    public static MySQLConstant createBoolean(boolean isTrue) {
        return MySQLConstant.createIntConstant(isTrue ? 1 : 0);
    }

    public static MySQLConstant createTrue() {
        return MySQLConstant.createIntConstant(1);
    }

    @Override
    public String toString() {
        return getTextRepresentation();
    }

    public abstract MySQLConstant isEquals(MySQLConstant rightVal);

    /**
     * NULL-safe equality comparison (<=> operator).
     * Unlike regular equality, NULL <=> NULL returns TRUE, and NULL <=> non-NULL returns FALSE.
     */
    public MySQLConstant isEqualsNullSafe(MySQLConstant rightVal) {
        if (this.isNull()) {
            return MySQLConstant.createBoolean(rightVal.isNull());
        }
        if (rightVal == null || rightVal.isNull()) {
            return MySQLConstant.createFalse();
        }
        return this.isEquals(rightVal);
    }

    public abstract MySQLConstant castAs(CastType type);

    public abstract String castAsString();

    public static MySQLConstant createStringConstant(String string) {
        return new MySQLTextConstant(string);
    }

    public static MySQLConstant createBitConstant(long value, int width) {
        return new MySQLBitConstant(value, width);
    }

    public static MySQLConstant createEnumConstant(String value, int index) {
        return new MySQLEnumConstant(value, index);
    }

    public static MySQLConstant createSetConstant(java.util.Set<String> values, long bitmap) {
        return new MySQLSetConstant(values, bitmap);
    }

    public static MySQLConstant createJSONConstant(String jsonValue) {
        return new MySQLJSONConstant(jsonValue);
    }

    public static MySQLConstant createBinaryConstant(byte[] value) {
        return new MySQLBinaryConstant(value);
    }

    public static MySQLConstant createBinaryConstant(byte[] value, int length) {
        return new MySQLBinaryConstant(value, length);
    }

    public static MySQLConstant createIntervalConstant(long value, String unit) {
        return new MySQLIntervalConstant(value, unit);
    }

    /**
     * Represents a MySQL INTERVAL value.
     * Used in DATE_ADD, DATE_SUB, and other temporal functions.
     */
    public static class MySQLIntervalConstant extends MySQLConstant {

        private final long value;
        private final String unit;

        public MySQLIntervalConstant(long value, String unit) {
            this.value = value;
            this.unit = unit;
        }

        @Override
        public String getTextRepresentation() {
            return "INTERVAL " + value + " " + unit;
        }

        @Override
        public String castAsString() {
            return getTextRepresentation();
        }

        @Override
        public MySQLDataType getType() {
            return MySQLDataType.INT; // INTERVAL is internally treated as numeric
        }

        @Override
        public boolean asBooleanNotNull() {
            return value != 0;
        }

        @Override
        public MySQLConstant isEquals(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLIntervalConstant) {
                MySQLIntervalConstant other = (MySQLIntervalConstant) rightVal;
                return MySQLConstant.createBoolean(value == other.value && unit.equals(other.unit));
            }
            throw new IgnoreMeException();
        }

        @Override
        public MySQLConstant castAs(MySQLCastOperation.CastType type) {
            if (type == MySQLCastOperation.CastType.SIGNED) {
                return MySQLConstant.createIntConstant(value, true);
            } else if (type == MySQLCastOperation.CastType.UNSIGNED) {
                return MySQLConstant.createIntConstant(value, false);
            }
            throw new IgnoreMeException();
        }

        @Override
        protected MySQLConstant isLessThan(MySQLConstant rightVal) {
            if (rightVal == null || rightVal.isNull()) {
                return MySQLConstant.createNullConstant();
            }
            if (rightVal instanceof MySQLIntervalConstant) {
                MySQLIntervalConstant other = (MySQLIntervalConstant) rightVal;
                if (!unit.equals(other.unit)) {
                    throw new IgnoreMeException(); // Cannot compare different interval units
                }
                return MySQLConstant.createBoolean(value < other.value);
            }
            throw new IgnoreMeException();
        }

        public long getIntervalValue() {
            return value;
        }

        public String getIntervalUnit() {
            return unit;
        }
    }

    public abstract MySQLDataType getType();

    protected abstract MySQLConstant isLessThan(MySQLConstant rightVal);

}
