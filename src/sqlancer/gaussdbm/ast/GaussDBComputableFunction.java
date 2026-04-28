package sqlancer.gaussdbm.ast;

import java.util.Arrays;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;

/**
 * Represents a computable function in GaussDB-M (MySQL compatibility mode). This class supports calculation of expected
 * values for PQS (Partial Query Specification) testing to detect function optimization bugs.
 */
public class GaussDBComputableFunction implements GaussDBExpression {

    private final GaussDBFunction func;
    private final List<GaussDBExpression> args;

    public GaussDBComputableFunction(GaussDBFunction func, List<GaussDBExpression> args) {
        this.func = func;
        this.args = args;
    }

    public GaussDBComputableFunction(GaussDBFunction func, GaussDBExpression... args) {
        this.func = func;
        this.args = Arrays.asList(args);
    }

    public GaussDBFunction getFunction() {
        return func;
    }

    public List<GaussDBExpression> getArguments() {
        return args;
    }

    /**
     * Enumeration of supported computable functions in GaussDB-M. Each function defines how to compute its expected
     * value given evaluated arguments.
     */
    public enum GaussDBFunction {

        // ========== 数学函数 (Math Functions) ==========

        /**
         * ABS - Returns the absolute value of a number.
         */
        ABS("ABS", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long val = arg.asIntNotNull();
                return GaussDBConstant.createIntConstant(Math.abs(val));
            }
        },

        /**
         * CEIL - Returns the smallest integer greater than or equal to a number.
         */
        CEIL("CEIL", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                if (arg.isInt()) {
                    return arg; // CEIL of integer returns the same value
                }
                if (arg.isString()) {
                    try {
                        double val = Double.parseDouble(((GaussDBConstant.GaussDBStringConstant) arg).getValue());
                        return GaussDBConstant.createIntConstant((long) Math.ceil(val));
                    } catch (NumberFormatException e) {
                        throw new IgnoreMeException();
                    }
                }
                throw new IgnoreMeException();
            }
        },

        /**
         * FLOOR - Returns the largest integer less than or equal to a number.
         */
        FLOOR("FLOOR", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                if (arg.isInt()) {
                    return arg; // FLOOR of integer returns the same value
                }
                if (arg.isString()) {
                    try {
                        double val = Double.parseDouble(((GaussDBConstant.GaussDBStringConstant) arg).getValue());
                        return GaussDBConstant.createIntConstant((long) Math.floor(val));
                    } catch (NumberFormatException e) {
                        throw new IgnoreMeException();
                    }
                }
                throw new IgnoreMeException();
            }
        },

        /**
         * ROUND - Rounds a number to the nearest integer.
         */
        ROUND("ROUND", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                if (arg.isInt()) {
                    return arg;
                }
                if (arg.isString()) {
                    try {
                        double val = Double.parseDouble(((GaussDBConstant.GaussDBStringConstant) arg).getValue());
                        return GaussDBConstant.createIntConstant((long) Math.round(val));
                    } catch (NumberFormatException e) {
                        throw new IgnoreMeException();
                    }
                }
                throw new IgnoreMeException();
            }
        },

        /**
         * MOD - Returns the remainder of dividing one number by another.
         */
        MOD("MOD", 2) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg1 = evaluatedArgs.get(0);
                GaussDBConstant arg2 = evaluatedArgs.get(1);
                if (arg1.isNull() || arg2.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long val1 = arg1.asIntNotNull();
                long val2 = arg2.asIntNotNull();
                if (val2 == 0) {
                    return GaussDBConstant.createNullConstant(); // Division by zero returns NULL
                }
                return GaussDBConstant.createIntConstant(val1 % val2);
            }
        },

        /**
         * SIGN - Returns the sign of a number (-1, 0, or 1).
         */
        SIGN("SIGN", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long val = arg.asIntNotNull();
                if (val < 0) {
                    return GaussDBConstant.createIntConstant(-1);
                }
                if (val == 0) {
                    return GaussDBConstant.createIntConstant(0);
                }
                return GaussDBConstant.createIntConstant(1);
            }
        },

        // ========== 字符串函数 (String Functions) ==========

        /**
         * CONCAT - Concatenates strings. Returns NULL if any argument is NULL.
         */
        CONCAT("CONCAT", true) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                StringBuilder sb = new StringBuilder();
                for (GaussDBConstant arg : evaluatedArgs) {
                    if (arg.isNull()) {
                        return GaussDBConstant.createNullConstant(); // CONCAT returns NULL if any arg is NULL
                    }
                    sb.append(getStringValue(arg));
                }
                return GaussDBConstant.createStringConstant(sb.toString());
            }
        },

        /**
         * LENGTH - Returns the length of a string in bytes.
         */
        LENGTH("LENGTH", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                String str = getStringValue(arg);
                return GaussDBConstant.createIntConstant(str.length());
            }
        },

        /**
         * UPPER - Converts a string to uppercase.
         */
        UPPER("UPPER", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                String str = getStringValue(arg);
                return GaussDBConstant.createStringConstant(str.toUpperCase());
            }
        },

        /**
         * LOWER - Converts a string to lowercase.
         */
        LOWER("LOWER", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                String str = getStringValue(arg);
                return GaussDBConstant.createStringConstant(str.toLowerCase());
            }
        },

        /**
         * TRIM - Removes leading and trailing spaces from a string.
         */
        TRIM("TRIM", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                String str = getStringValue(arg);
                return GaussDBConstant.createStringConstant(str.trim());
            }
        },

        // ========== 控制流函数 (Control Flow Functions) ==========

        /**
         * COALESCE - Returns the first non-NULL value from a list of arguments.
         */
        COALESCE("COALESCE", true) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                for (GaussDBConstant arg : evaluatedArgs) {
                    if (!arg.isNull()) {
                        return arg;
                    }
                }
                return GaussDBConstant.createNullConstant(); // All arguments were NULL
            }
        },

        /**
         * IFNULL - Returns the first argument if it is not NULL, otherwise returns the second argument.
         */
        IFNULL("IFNULL", 2) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg1 = evaluatedArgs.get(0);
                GaussDBConstant arg2 = evaluatedArgs.get(1);
                if (arg1.isNull()) {
                    return arg2;
                }
                return arg1;
            }
        };

        private final String functionName;
        private final int minNrArgs;
        private final boolean variadic;

        /**
         * Constructor for non-variadic functions.
         */
        GaussDBFunction(String functionName, int nrArgs) {
            this.functionName = functionName;
            this.minNrArgs = nrArgs;
            this.variadic = false;
        }

        /**
         * Constructor for variadic functions (can accept variable number of arguments).
         */
        GaussDBFunction(String functionName, boolean variadic) {
            this.functionName = functionName;
            this.minNrArgs = 2; // Minimum 2 args for variadic functions
            this.variadic = variadic;
        }

        /**
         * Gets the function name for SQL generation.
         */
        public String getName() {
            return functionName;
        }

        /**
         * Gets the minimum number of arguments required for this function.
         */
        public int getMinNrArgs() {
            return minNrArgs;
        }

        /**
         * Returns true if this function accepts a variable number of arguments.
         */
        public boolean isVariadic() {
            return variadic;
        }

        /**
         * Abstract method to compute the expected value of the function. Each function type implements its own
         * calculation logic.
         *
         * @param evaluatedArgs
         *            the evaluated constant values of the arguments
         * @param origArgs
         *            the original expression arguments (for type inference)
         *
         * @return the computed expected value as a GaussDBConstant
         */
        public abstract GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs);

        /**
         * Helper method to get string value from a constant.
         */
        protected static String getStringValue(GaussDBConstant constant) {
            if (constant.isString()) {
                return ((GaussDBConstant.GaussDBStringConstant) constant).getValue();
            } else if (constant.isInt()) {
                return String.valueOf(constant.asIntNotNull());
            } else if (constant.isBoolean()) {
                return constant.asBooleanNotNull() ? "1" : "0";
            }
            return constant.getTextRepresentation();
        }

        /**
         * Gets a random function from the available functions.
         */
        public static GaussDBFunction getRandomFunction() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String toString() {
            return functionName;
        }
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        List<GaussDBConstant> constants = new java.util.ArrayList<>();
        for (GaussDBExpression arg : args) {
            GaussDBConstant constant = arg.getExpectedValue();
            if (constant == null) {
                return null; // Cannot compute expected value if any arg has null expected value
            }
            constants.add(constant);
        }
        return func.apply(constants, args);
    }
}