package sqlancer.gaussdbm.ast;

import sqlancer.Randomly;

/**
 * Represents a CAST operation in GaussDB-M (MySQL compatibility mode). CAST(expr AS type) converts an expression to a
 * specified type.
 */
public class GaussDBCastOperation implements GaussDBExpression {

    private final GaussDBExpression expr;
    private final GaussDBCastType type;

    /**
     * Enum representing the CAST target types supported in GaussDB-M.
     */
    public enum GaussDBCastType {
        SIGNED("SIGNED"), UNSIGNED("UNSIGNED"), BINARY("BINARY"), CHAR("CHAR"), DATE("DATE"), DATETIME("DATETIME"),
        TIME("TIME"), DECIMAL("DECIMAL");

        private final String textRepresentation;

        GaussDBCastType(String textRepresentation) {
            this.textRepresentation = textRepresentation;
        }

        public String getTextRepresentation() {
            return textRepresentation;
        }

        /**
         * Returns a random CAST type. Currently returns SIGNED as the primary type.
         */
        public static GaussDBCastType getRandom() {
            return Randomly.fromOptions(GaussDBCastType.values());
        }
    }

    public GaussDBCastOperation(GaussDBExpression expr, GaussDBCastType type) {
        this.expr = expr;
        this.type = type;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public GaussDBCastType getType() {
        return type;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant expectedValue = expr.getExpectedValue();
        if (expectedValue.isNull()) {
            return GaussDBConstant.createNullConstant();
        }
        return castAs(expectedValue, type);
    }

    /**
     * Casts a constant to the specified type.
     */
    private GaussDBConstant castAs(GaussDBConstant constant, GaussDBCastType type) {
        switch (type) {
        case SIGNED:
            return castAsSigned(constant);
        case UNSIGNED:
            return castAsUnsigned(constant);
        case CHAR:
            return castAsChar(constant);
        case DATE:
        case DATETIME:
        case TIME:
        case DECIMAL:
        case BINARY:
            // For these types, simplified handling - return the constant unchanged
            // as precise type conversion requires more complex semantics
            return constant;
        default:
            throw new AssertionError("Unknown cast type: " + type);
        }
    }

    /**
     * Casts a constant to SIGNED (signed integer).
     */
    private GaussDBConstant castAsSigned(GaussDBConstant constant) {
        if (constant.isInt()) {
            return constant;
        }
        if (constant.isString()) {
            String value = ((GaussDBConstant.GaussDBStringConstant) constant).getValue();
            // Trim leading whitespace
            while (value.startsWith(" ") || value.startsWith("\t") || value.startsWith("\n")) {
                value = value.substring(1);
            }
            // Try to parse as integer
            for (int i = value.length(); i >= 0; i--) {
                try {
                    String substring = value.substring(0, i);
                    long val = Long.parseLong(substring);
                    return GaussDBConstant.createIntConstant(val);
                } catch (NumberFormatException e) {
                    // try shorter prefix
                }
            }
            // Cannot parse - return 0
            return GaussDBConstant.createIntConstant(0);
        }
        if (constant.isBoolean()) {
            return GaussDBConstant.createIntConstant(constant.asBooleanNotNull() ? 1 : 0);
        }
        return constant;
    }

    /**
     * Casts a constant to UNSIGNED (unsigned integer).
     */
    private GaussDBConstant castAsUnsigned(GaussDBConstant constant) {
        if (constant.isInt()) {
            return constant;
        }
        if (constant.isString()) {
            String value = ((GaussDBConstant.GaussDBStringConstant) constant).getValue();
            // Trim leading whitespace
            while (value.startsWith(" ") || value.startsWith("\t") || value.startsWith("\n")) {
                value = value.substring(1);
            }
            // Try to parse as unsigned integer
            for (int i = value.length(); i >= 0; i--) {
                try {
                    String substring = value.substring(0, i);
                    long val = Long.parseLong(substring);
                    return GaussDBConstant.createIntConstant(val);
                } catch (NumberFormatException e) {
                    // try shorter prefix
                }
            }
            // Cannot parse - return 0
            return GaussDBConstant.createIntConstant(0);
        }
        if (constant.isBoolean()) {
            return GaussDBConstant.createIntConstant(constant.asBooleanNotNull() ? 1 : 0);
        }
        return constant;
    }

    /**
     * Casts a constant to CHAR (string).
     */
    private GaussDBConstant castAsChar(GaussDBConstant constant) {
        if (constant.isString()) {
            return constant;
        }
        if (constant.isInt()) {
            return GaussDBConstant.createStringConstant(String.valueOf(constant.asIntNotNull()));
        }
        if (constant.isBoolean()) {
            return GaussDBConstant.createStringConstant(constant.asBooleanNotNull() ? "1" : "0");
        }
        return constant;
    }
}