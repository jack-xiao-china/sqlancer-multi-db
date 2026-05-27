package sqlancer.gaussdba;

import java.util.ArrayList;
import java.util.List;

import sqlancer.common.query.ExpectedErrors;

public final class GaussDBAErrors {

    private GaussDBAErrors() {
        // Utility class
    }

    public static ExpectedErrors getExpressionErrors() {
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("syntax error");
        errors.add("invalid number");       // Oracle风格错误
        errors.add("division by zero");
        errors.add("value too large");
        errors.add("ORA-01722");             // Oracle错误码风格: invalid number
        errors.add("ORA-01858");             // invalid date format
        errors.add("ORA-01861");             // literal does not match format string (datetime)
        errors.add("invalid input syntax");
        errors.add("invalid date format");
        errors.add("invalid month format");
        errors.add("data exception");
        errors.add("numeric field overflow");
        // GaussDB-A Oracle mode datetime errors
        errors.add("ERROR: invalid datetime value");
        errors.add("ERROR: invalid date value");
        errors.add("ERROR: invalid time value");
        errors.add("ERROR: Incorrect datetime value");
        errors.add("ERROR: Incorrect date value");
        errors.add("ERROR: Incorrect time value");
        return errors;
    }

    public static ExpectedErrors getGroupingErrors() {
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("must appear in the GROUP BY clause");
        errors.add("nonaggregated column");
        errors.add("not a GROUP BY expression");  // Oracle风格
        return errors;
    }

    public static ExpectedErrors getInsertUpdateErrors() {
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("violates not-null constraint");
        errors.add("violates unique constraint");
        errors.add("duplicate key value");
        errors.add("null value in column");
        errors.add("value too long for type");
        errors.add("unique constraint violated");  // Oracle风格
        return errors;
    }

    public static void addInsertUpdateErrors(ExpectedErrors errors) {
        errors.add("violates not-null constraint");
        errors.add("violates unique constraint");
        errors.add("duplicate key value");
        errors.add("null value in column");
        errors.add("value too long for type");
        errors.add("unique constraint violated");
        errors.add("ORA-00001");  // unique constraint violated
        errors.add("ORA-01400");  // cannot insert NULL
    }

    public static ExpectedErrors getFetchErrors() {
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("invalid input syntax");
        errors.add("division by zero");
        errors.add("invalid number");
        return errors;
    }

    public static List<String> getExpressionErrorStrings() {
        List<String> errors = new ArrayList<>();
        errors.add("syntax error");
        errors.add("invalid number");
        errors.add("division by zero");
        errors.add("value too large");
        errors.add("ORA-01722");
        errors.add("ORA-01858");
        errors.add("ORA-01861");  // literal does not match format string
        errors.add("invalid input syntax");
        errors.add("invalid date format");
        errors.add("data exception");
        // GaussDB-A Oracle mode datetime errors
        errors.add("ERROR: invalid datetime value");
        errors.add("ERROR: invalid date value");
        errors.add("ERROR: invalid time value");
        errors.add("ERROR: Incorrect datetime value");
        errors.add("ERROR: Incorrect date value");
        errors.add("ERROR: Incorrect time value");
        return errors;
    }

    public static void addExpressionErrors(ExpectedErrors errors) {
        errors.addAll(getExpressionErrorStrings());
    }

    public static void addFetchErrors(ExpectedErrors errors) {
        errors.add("invalid input syntax");
        errors.add("division by zero");
        errors.add("invalid number");
    }

    public static List<String> getPlanErrorStrings() {
        List<String> errors = new ArrayList<>();
        // EXPLAIN相关的错误
        errors.add("cannot explain");
        errors.add("plan not available");
        errors.add("explain error");
        errors.add("ORA-02404");  // Oracle风格explain错误
        return errors;
    }

    public static void addPlanErrors(ExpectedErrors errors) {
        errors.addAll(getPlanErrorStrings());
    }
}