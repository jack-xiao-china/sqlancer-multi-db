package sqlancer.gaussdbm.ast;

import sqlancer.Randomly;

public abstract class GaussDBConstant implements GaussDBExpression {

    public abstract String getTextRepresentation();

    public boolean isNull() {
        return false;
    }

    public boolean isInt() {
        return false;
    }

    public boolean isString() {
        return false;
    }

    public boolean isBoolean() {
        return false;
    }

    public long asIntNotNull() {
        throw new UnsupportedOperationException();
    }

    /** SQL truth value for use in PQS / logical composition (MySQL-style 0/1 ints included). */
    public boolean asBooleanNotNull() {
        throw new UnsupportedOperationException();
    }

    /** Three-valued = ; returns TRUE/FALSE/NULL constants. */
    public GaussDBConstant sqlEquals(GaussDBConstant right) {
        throw new UnsupportedOperationException();
    }

    /** Three-valued &lt; ; returns TRUE/FALSE/NULL constants. */
    public GaussDBConstant sqlLessThan(GaussDBConstant right) {
        throw new UnsupportedOperationException();
    }

    public static GaussDBConstant createNullConstant() {
        return new GaussDBNullConstant();
    }

    public static GaussDBConstant createIntConstant(long val) {
        return new GaussDBIntConstant(val);
    }

    public static GaussDBConstant createStringConstant(String val) {
        return new GaussDBStringConstant(val);
    }

    public static GaussDBConstant createBooleanConstant(boolean val) {
        return new GaussDBBooleanConstant(val);
    }

    public static GaussDBConstant createRandomConstant() {
        switch (Randomly.fromOptions(0, 1, 2)) {
        case 0:
            return createIntConstant(Randomly.getNotCachedInteger(-100, 100));
        case 1: {
            String s = new Randomly().getString();
            return createStringConstant(s.substring(0, Math.min(10, s.length())));
        }
        case 2:
            return createNullConstant();
        default:
            throw new AssertionError();
        }
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        return this;
    }

    public static final class GaussDBNullConstant extends GaussDBConstant {
        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public String getTextRepresentation() {
            return "NULL";
        }

        @Override
        public GaussDBConstant sqlEquals(GaussDBConstant right) {
            return createNullConstant();
        }

        @Override
        public GaussDBConstant sqlLessThan(GaussDBConstant right) {
            return createNullConstant();
        }
    }

    public static final class GaussDBIntConstant extends GaussDBConstant {
        private final long val;

        GaussDBIntConstant(long val) {
            this.val = val;
        }

        @Override
        public boolean isInt() {
            return true;
        }

        @Override
        public long asIntNotNull() {
            return val;
        }

        @Override
        public boolean asBooleanNotNull() {
            return val != 0;
        }

        @Override
        public String getTextRepresentation() {
            return String.valueOf(val);
        }

        @Override
        public GaussDBConstant sqlEquals(GaussDBConstant right) {
            if (right.isNull()) {
                return createNullConstant();
            }
            if (right.isInt()) {
                return createBooleanConstant(val == right.asIntNotNull());
            }
            if (right.isString()) {
                return createBooleanConstant(val == ((GaussDBStringConstant) right).asLongLenient());
            }
            if (right.isBoolean()) {
                return createBooleanConstant(asBooleanNotNull() == right.asBooleanNotNull());
            }
            throw new AssertionError(right);
        }

        @Override
        public GaussDBConstant sqlLessThan(GaussDBConstant right) {
            if (right.isNull()) {
                return createNullConstant();
            }
            if (right.isInt()) {
                return createBooleanConstant(val < right.asIntNotNull());
            }
            if (right.isString()) {
                return createBooleanConstant(val < ((GaussDBStringConstant) right).asLongLenient());
            }
            throw new AssertionError(right);
        }
    }

    public static final class GaussDBStringConstant extends GaussDBConstant {
        private final String val;

        GaussDBStringConstant(String val) {
            this.val = val;
        }

        public String getValue() {
            return val;
        }

        @Override
        public boolean isString() {
            return true;
        }

        long asLongLenient() {
            for (int i = val.length(); i >= 0; i--) {
                try {
                    String sub = val.substring(0, i).trim();
                    if (sub.isEmpty()) {
                        continue;
                    }
                    return Long.parseLong(sub);
                } catch (NumberFormatException e) {
                    // try shorter prefix
                }
            }
            return 0;
        }

        @Override
        public boolean asBooleanNotNull() {
            for (int i = val.length(); i >= 0; i--) {
                try {
                    String sub = val.substring(0, i);
                    double d = Double.parseDouble(sub);
                    return d != 0 && !Double.isNaN(d);
                } catch (NumberFormatException e) {
                    // continue
                }
            }
            return false;
        }

        @Override
        public String getTextRepresentation() {
            String escaped = val.replace("\\", "\\\\").replace("'", "''");
            return "'" + escaped + "'";
        }

        @Override
        public GaussDBConstant sqlEquals(GaussDBConstant right) {
            if (right.isNull()) {
                return createNullConstant();
            }
            if (right.isString()) {
                return createBooleanConstant(val.equalsIgnoreCase(((GaussDBStringConstant) right).val));
            }
            if (right.isInt()) {
                return createBooleanConstant(asLongLenient() == right.asIntNotNull());
            }
            throw new AssertionError(right);
        }

        @Override
        public GaussDBConstant sqlLessThan(GaussDBConstant right) {
            if (right.isNull()) {
                return createNullConstant();
            }
            if (right.isString()) {
                return createBooleanConstant(val.compareTo(((GaussDBStringConstant) right).val) < 0);
            }
            if (right.isInt()) {
                return createBooleanConstant(asLongLenient() < right.asIntNotNull());
            }
            throw new AssertionError(right);
        }
    }

    public static final class GaussDBBooleanConstant extends GaussDBConstant {
        private final boolean val;

        GaussDBBooleanConstant(boolean val) {
            this.val = val;
        }

        @Override
        public boolean isBoolean() {
            return true;
        }

        @Override
        public boolean asBooleanNotNull() {
            return val;
        }

        @Override
        public String getTextRepresentation() {
            return val ? "TRUE" : "FALSE";
        }

        @Override
        public GaussDBConstant sqlEquals(GaussDBConstant right) {
            if (right.isNull()) {
                return createNullConstant();
            }
            if (right.isBoolean()) {
                return createBooleanConstant(val == right.asBooleanNotNull());
            }
            if (right.isInt()) {
                return createBooleanConstant((val ? 1 : 0) == right.asIntNotNull());
            }
            throw new AssertionError(right);
        }

        @Override
        public GaussDBConstant sqlLessThan(GaussDBConstant right) {
            if (right.isNull()) {
                return createNullConstant();
            }
            int l = val ? 1 : 0;
            if (right.isInt()) {
                return createBooleanConstant(l < right.asIntNotNull());
            }
            if (right.isBoolean()) {
                int r = right.asBooleanNotNull() ? 1 : 0;
                return createBooleanConstant(l < r);
            }
            throw new AssertionError(right);
        }
    }
}
