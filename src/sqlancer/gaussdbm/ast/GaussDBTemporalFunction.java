package sqlancer.gaussdbm.ast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;

/**
 * Represents GaussDB-M temporal functions (MySQL compatibility mode). Includes current time functions, extraction
 * functions, and calculation functions.
 */
public final class GaussDBTemporalFunction implements GaussDBExpression {

    /**
     * Enum for GaussDB-M temporal function types.
     */
    public enum GaussDBTemporalFunctionType {
        // Current time functions (0 args)
        NOW("NOW", 0), CURDATE("CURDATE", 0), CURTIME("CURTIME", 0),

        // Extraction functions (1 arg)
        YEAR("YEAR", 1), MONTH("MONTH", 1), DAY("DAY", 1), HOUR("HOUR", 1), MINUTE("MINUTE", 1), SECOND("SECOND", 1),
        DAYOFWEEK("DAYOFWEEK", 1),

        // Calculation functions
        DATEDIFF("DATEDIFF", 2), LAST_DAY("LAST_DAY", 1);

        private final String functionName;
        private final int arity;

        GaussDBTemporalFunctionType(String functionName, int arity) {
            this.functionName = functionName;
            this.arity = arity;
        }

        public String getFunctionName() {
            return functionName;
        }

        public int getArity() {
            return arity;
        }

        /**
         * Get a random temporal function type.
         */
        public static GaussDBTemporalFunctionType getRandom() {
            return Randomly.fromOptions(values());
        }

        /**
         * Get a random current time function (NOW, CURDATE, CURTIME).
         */
        public static GaussDBTemporalFunctionType getRandomCurrentTimeFunction() {
            return Randomly.fromOptions(NOW, CURDATE, CURTIME);
        }

        /**
         * Get a random extraction function.
         */
        public static GaussDBTemporalFunctionType getRandomExtractionFunction() {
            return Randomly.fromOptions(YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, DAYOFWEEK);
        }

        /**
         * Get a random calculation function.
         */
        public static GaussDBTemporalFunctionType getRandomCalculationFunction() {
            return Randomly.fromOptions(DATEDIFF, LAST_DAY);
        }
    }

    private final GaussDBTemporalFunctionType func;
    private final List<GaussDBExpression> args;

    /**
     * Create a temporal function with the given type and arguments.
     */
    public GaussDBTemporalFunction(GaussDBTemporalFunctionType func, List<GaussDBExpression> args) {
        if (args.size() != func.getArity()) {
            throw new IllegalArgumentException("Function " + func.getFunctionName() + " requires " + func.getArity()
                    + " arguments, got " + args.size());
        }
        this.func = func;
        this.args = args;
    }

    /**
     * Create a temporal function with the given type and no arguments (for current time functions).
     */
    public GaussDBTemporalFunction(GaussDBTemporalFunctionType func) {
        if (func.getArity() != 0) {
            throw new IllegalArgumentException(
                    "Function " + func.getFunctionName() + " requires " + func.getArity() + " arguments");
        }
        this.func = func;
        this.args = new ArrayList<>();
    }

    public GaussDBTemporalFunctionType getFunc() {
        return func;
    }

    public List<GaussDBExpression> getArgs() {
        return args;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        // Current time functions return null (cannot be statically computed)
        if (func == GaussDBTemporalFunctionType.NOW || func == GaussDBTemporalFunctionType.CURDATE
                || func == GaussDBTemporalFunctionType.CURTIME) {
            return null;
        }

        // All other functions require arguments
        if (args.isEmpty()) {
            throw new IgnoreMeException();
        }

        // Get the first argument's expected value
        GaussDBConstant arg0 = args.get(0).getExpectedValue();
        if (arg0 == null || arg0.isNull()) {
            return GaussDBConstant.createNullConstant();
        }

        // Get the string value of the argument
        String temporalStr;
        if (arg0.isString()) {
            temporalStr = ((GaussDBConstant.GaussDBStringConstant) arg0).getValue();
        } else {
            // Try to convert to string
            temporalStr = arg0.getTextRepresentation();
            // Remove quotes if present
            if (temporalStr.startsWith("'") && temporalStr.endsWith("'")) {
                temporalStr = temporalStr.substring(1, temporalStr.length() - 1);
            }
        }

        try {
            switch (func) {
            case YEAR:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.extractYear(temporalStr));

            case MONTH:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.extractMonth(temporalStr));

            case DAY:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.extractDay(temporalStr));

            case HOUR:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.extractHour(temporalStr));

            case MINUTE:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.extractMinute(temporalStr));

            case SECOND:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.extractSecond(temporalStr));

            case DAYOFWEEK:
                return GaussDBConstant.createIntConstant(GaussDBTemporalUtil.dayOfWeek(temporalStr));

            case LAST_DAY:
                LocalDate lastDay = GaussDBTemporalUtil.lastDayOfMonth(temporalStr);
                return GaussDBConstant.createStringConstant(GaussDBTemporalUtil.formatDate(lastDay));

            case DATEDIFF:
                if (args.size() < 2) {
                    throw new IgnoreMeException();
                }
                GaussDBConstant arg1 = args.get(1).getExpectedValue();
                if (arg1 == null || arg1.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                String temporalStr2;
                if (arg1.isString()) {
                    temporalStr2 = ((GaussDBConstant.GaussDBStringConstant) arg1).getValue();
                } else {
                    temporalStr2 = arg1.getTextRepresentation();
                    if (temporalStr2.startsWith("'") && temporalStr2.endsWith("'")) {
                        temporalStr2 = temporalStr2.substring(1, temporalStr2.length() - 1);
                    }
                }
                long diff = GaussDBTemporalUtil.dateDiff(temporalStr, temporalStr2);
                return GaussDBConstant.createIntConstant(diff);

            default:
                throw new IgnoreMeException();
            }
        } catch (IgnoreMeException e) {
            throw e;
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
    }
}