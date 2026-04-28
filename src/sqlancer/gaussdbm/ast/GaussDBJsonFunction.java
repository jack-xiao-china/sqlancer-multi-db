package sqlancer.gaussdbm.ast;

import java.util.Arrays;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;

/**
 * Represents a JSON function in GaussDB-M (MySQL compatibility mode). This class supports calculation of expected
 * values for PQS (Partial Query Specification) testing to detect JSON function optimization bugs.
 */
public class GaussDBJsonFunction implements GaussDBExpression {

    private final GaussDBJsonFunctionType func;
    private final List<GaussDBExpression> args;

    public GaussDBJsonFunction(GaussDBJsonFunctionType func, List<GaussDBExpression> args) {
        this.func = func;
        this.args = args;
    }

    public GaussDBJsonFunction(GaussDBJsonFunctionType func, GaussDBExpression... args) {
        this.func = func;
        this.args = Arrays.asList(args);
    }

    public GaussDBJsonFunctionType getFunction() {
        return func;
    }

    public List<GaussDBExpression> getArguments() {
        return args;
    }

    /**
     * Enumeration of supported JSON functions in GaussDB-M. Each function defines how to compute its expected value
     * given evaluated arguments.
     */
    public enum GaussDBJsonFunctionType {

        // ========== JSON Query Functions ==========

        /**
         * JSON_EXTRACT - Extracts data from a JSON document using a path. Returns the extracted value or NULL if the
         * path does not exist.
         */
        JSON_EXTRACT("JSON_EXTRACT", 2) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_EXTRACT requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        /**
         * JSON_CONTAINS - Checks if a JSON document contains a specific value at a given path. Returns 1 if found, 0 if
         * not found, or NULL if any argument is NULL.
         */
        JSON_CONTAINS("JSON_CONTAINS", 2) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_CONTAINS requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        /**
         * JSON_KEYS - Returns the keys of a JSON object as a JSON array. Returns NULL if the argument is NULL or not a
         * JSON object.
         */
        JSON_KEYS("JSON_KEYS", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_KEYS requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        /**
         * JSON_TYPE - Returns the type of a JSON value. Returns one of: ARRAY, OBJECT, STRING, INTEGER, DECIMAL,
         * BOOLEAN, NULL.
         */
        JSON_TYPE("JSON_TYPE", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                if (arg.isString()) {
                    String jsonStr = ((GaussDBConstant.GaussDBStringConstant) arg).getValue();
                    String jsonType = determineJsonType(jsonStr);
                    if (jsonType == null) {
                        // Invalid JSON returns NULL
                        return GaussDBConstant.createNullConstant();
                    }
                    return GaussDBConstant.createStringConstant(jsonType);
                }
                // Non-string arguments are treated as invalid JSON
                return GaussDBConstant.createNullConstant();
            }

            private String determineJsonType(String jsonStr) {
                jsonStr = jsonStr.trim();
                if (jsonStr.isEmpty()) {
                    return null;
                }
                if (jsonStr.startsWith("{") && jsonStr.endsWith("}")) {
                    return "OBJECT";
                }
                if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                    return "ARRAY";
                }
                if (jsonStr.equalsIgnoreCase("true") || jsonStr.equalsIgnoreCase("false")) {
                    return "BOOLEAN";
                }
                if (jsonStr.equalsIgnoreCase("null")) {
                    return "NULL";
                }
                try {
                    Long.parseLong(jsonStr);
                    return "INTEGER";
                } catch (NumberFormatException e1) {
                    try {
                        Double.parseDouble(jsonStr);
                        return "DECIMAL";
                    } catch (NumberFormatException e2) {
                        // Check if it's a quoted string
                        if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"") && jsonStr.length() >= 2) {
                            return "STRING";
                        }
                        return null;
                    }
                }
            }
        },

        /**
         * JSON_VALID - Validates if a string is a valid JSON document. Returns 1 if valid, 0 if invalid, or NULL if the
         * argument is NULL.
         */
        JSON_VALID("JSON_VALID", 1) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                GaussDBConstant arg = evaluatedArgs.get(0);
                if (arg.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                if (arg.isString()) {
                    String jsonStr = ((GaussDBConstant.GaussDBStringConstant) arg).getValue();
                    boolean isValid = isValidJson(jsonStr);
                    return GaussDBConstant.createIntConstant(isValid ? 1 : 0);
                }
                // Non-string arguments return 0 (not valid JSON)
                return GaussDBConstant.createIntConstant(0);
            }

            private boolean isValidJson(String jsonStr) {
                jsonStr = jsonStr.trim();
                if (jsonStr.isEmpty()) {
                    return false;
                }
                // Simple JSON validation - check basic structure
                try {
                    return isValidJsonStructure(jsonStr);
                } catch (Exception e) {
                    return false;
                }
            }

            private boolean isValidJsonStructure(String json) {
                json = json.trim();
                if (json.isEmpty()) {
                    return false;
                }
                // Check for JSON object
                if (json.startsWith("{") && json.endsWith("}")) {
                    return isValidJsonObject(json.substring(1, json.length() - 1));
                }
                // Check for JSON array
                if (json.startsWith("[") && json.endsWith("]")) {
                    return isValidJsonArray(json.substring(1, json.length() - 1));
                }
                // Check for JSON primitives
                if (json.equalsIgnoreCase("true") || json.equalsIgnoreCase("false") || json.equalsIgnoreCase("null")) {
                    return true;
                }
                // Check for JSON number
                try {
                    Double.parseDouble(json);
                    return true;
                } catch (NumberFormatException e) {
                    // Check for JSON string (must be quoted)
                    if (json.startsWith("\"") && json.endsWith("\"") && json.length() >= 2) {
                        return !json.substring(1, json.length() - 1).contains("\"");
                    }
                    return false;
                }
            }

            private boolean isValidJsonObject(String content) {
                if (content.isEmpty()) {
                    return true; // Empty object {} is valid
                }
                // Simplified check: look for key:value pairs
                return content.contains(":");
            }

            private boolean isValidJsonArray(String content) {
                if (content.isEmpty()) {
                    return true; // Empty array [] is valid
                }
                // Simplified check: look for elements
                return true;
            }
        },

        // ========== JSON Construction Functions ==========

        /**
         * JSON_ARRAY - Constructs a JSON array from a list of values. Variadic function that accepts any number of
         * arguments.
         */
        JSON_ARRAY("JSON_ARRAY", true) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_ARRAY requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        /**
         * JSON_OBJECT - Constructs a JSON object from key-value pairs. Variadic function that accepts pairs of key and
         * value arguments.
         */
        JSON_OBJECT("JSON_OBJECT", true) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_OBJECT requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        // ========== JSON Modification Functions ==========

        /**
         * JSON_REMOVE - Removes data from a JSON document at specified paths. Returns the modified JSON document or
         * NULL if any argument is NULL.
         */
        JSON_REMOVE("JSON_REMOVE", 2) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_REMOVE requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        /**
         * JSON_REPLACE - Replaces values in a JSON document at specified paths. Returns the modified JSON document or
         * NULL if any argument is NULL.
         */
        JSON_REPLACE("JSON_REPLACE", 3) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_REPLACE requires actual JSON data processing
                throw new IgnoreMeException();
            }
        },

        /**
         * JSON_SET - Sets values in a JSON document at specified paths. Returns the modified JSON document or NULL if
         * any argument is NULL.
         */
        JSON_SET("JSON_SET", 3) {
            @Override
            public GaussDBConstant apply(List<GaussDBConstant> evaluatedArgs, List<GaussDBExpression> origArgs) {
                // JSON_SET requires actual JSON data processing
                throw new IgnoreMeException();
            }
        };

        private final String functionName;
        private final int minNrArgs;
        private final boolean variadic;

        /**
         * Constructor for non-variadic JSON functions.
         */
        GaussDBJsonFunctionType(String functionName, int nrArgs) {
            this.functionName = functionName;
            this.minNrArgs = nrArgs;
            this.variadic = false;
        }

        /**
         * Constructor for variadic JSON functions (can accept variable number of arguments).
         */
        GaussDBJsonFunctionType(String functionName, boolean variadic) {
            this.functionName = functionName;
            this.minNrArgs = 0; // Variadic functions can have 0 or more args
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
         * Abstract method to compute the expected value of the JSON function. Each function type implements its own
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
         * Gets a random JSON function from the available functions.
         */
        public static GaussDBJsonFunctionType getRandomFunction() {
            return Randomly.fromOptions(values());
        }

        /**
         * Gets a random JSON query function.
         */
        public static GaussDBJsonFunctionType getRandomQueryFunction() {
            return Randomly.fromOptions(JSON_EXTRACT, JSON_CONTAINS, JSON_KEYS, JSON_TYPE, JSON_VALID);
        }

        /**
         * Gets a random JSON construction function.
         */
        public static GaussDBJsonFunctionType getRandomConstructionFunction() {
            return Randomly.fromOptions(JSON_ARRAY, JSON_OBJECT);
        }

        /**
         * Gets a random JSON modification function.
         */
        public static GaussDBJsonFunctionType getRandomModificationFunction() {
            return Randomly.fromOptions(JSON_REMOVE, JSON_REPLACE, JSON_SET);
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