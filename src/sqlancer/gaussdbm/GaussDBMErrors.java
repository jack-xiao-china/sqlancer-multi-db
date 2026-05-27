package sqlancer.gaussdbm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import sqlancer.common.query.ExpectedErrors;

public final class GaussDBMErrors {

    private GaussDBMErrors() {
    }

    private static List<String> expressionErrorStrings() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("syntax error");
        errors.add("invalid input syntax");
        errors.add("division by zero");
        errors.add("out of range");
        errors.add("BIGINT value is out of range");
        errors.add("Incorrect TIMESTAMP value");
        errors.add("Incorrect DATETIME value");
        errors.add("Incorrect datetime value"); // GaussDB-M lowercase variant
        errors.add("Incorrect DATE value");
        errors.add("Incorrect TIME value");
        errors.add("Incorrect date value"); // lowercase variant
        errors.add("Incorrect time value"); // lowercase variant
        errors.add("Data truncated");
        errors.add("Duplicate entry");
        errors.add("ERROR: Incorrect datetime value"); // GaussDB-M error prefix
        errors.add("ERROR: Incorrect date value");
        errors.add("ERROR: Incorrect time value");
        errors.add("ERROR: invalid datetime value"); // GaussDB-M variant
        errors.add("ERROR: invalid date value");
        errors.add("ERROR: invalid time value");
        return errors;
    }

    public static List<Pattern> getExpressionRegexErrors() {
        ArrayList<Pattern> patterns = new ArrayList<>();
        // Match datetime/time/date value errors with various characters (including encoding issues)
        patterns.add(Pattern.compile("ERROR: invalid datetime value: '.+'"));
        patterns.add(Pattern.compile("ERROR: invalid date value: '.+'"));
        patterns.add(Pattern.compile("ERROR: invalid time value: '.+'"));
        // Match Incorrect datetime/date/time value errors with specific values
        patterns.add(Pattern.compile("ERROR: Incorrect datetime value: '.+'"));
        patterns.add(Pattern.compile("ERROR: Incorrect date value: '.+'"));
        patterns.add(Pattern.compile("ERROR: Incorrect time value: '.+'"));
        return patterns;
    }

    public static void addExpressionErrors(ExpectedErrors errors) {
        errors.addAll(expressionErrorStrings());
        errors.addAllRegexes(getExpressionRegexErrors());
    }

    public static ExpectedErrors getExpressionErrors() {
        ExpectedErrors errors = new ExpectedErrors();
        addExpressionErrors(errors);
        return errors;
    }

    /**
     * Expected errors for HAVING and similar aggregate-context expressions (MySQL-compatible wording where applicable).
     */
    public static List<String> getExpressionHavingErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("is not in GROUP BY clause");
        errors.add("contains nonaggregated column");
        errors.add("Unknown column");
        errors.add("which is not in GROUP BY");
        return errors;
    }

    public static void addExpressionHavingErrors(ExpectedErrors errors) {
        errors.addAll(getExpressionHavingErrors());
    }

    public static List<String> getInsertUpdateErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("doesn't have a default value");
        errors.add("Data truncation");
        errors.add("Incorrect integer value");
        errors.add("Duplicate entry");
        errors.add("Data truncated for column");
        errors.add("cannot be null");
        errors.add("Incorrect decimal value");
        errors.add("The value specified for generated column");
        // Temporal value errors during INSERT/UPDATE
        errors.add("Incorrect DATE value");
        errors.add("Incorrect TIME value");
        errors.add("Incorrect DATETIME value");
        errors.add("Incorrect TIMESTAMP value");
        errors.add("ERROR: Incorrect date value");
        errors.add("ERROR: Incorrect time value");
        errors.add("ERROR: Incorrect datetime value");
        return errors;
    }

    public static void addInsertUpdateErrors(ExpectedErrors errors) {
        errors.addAll(getInsertUpdateErrors());
        errors.addAllRegexes(getInsertUpdateRegexErrors());
    }

    public static List<Pattern> getInsertUpdateRegexErrors() {
        ArrayList<Pattern> patterns = new ArrayList<>();
        patterns.add(Pattern.compile("ERROR: Incorrect datetime value: '.+'"));
        patterns.add(Pattern.compile("ERROR: Incorrect date value: '.+'"));
        patterns.add(Pattern.compile("ERROR: Incorrect time value: '.+'"));
        patterns.add(Pattern.compile("ERROR: invalid datetime value: '.+'"));
        patterns.add(Pattern.compile("ERROR: invalid date value: '.+'"));
        patterns.add(Pattern.compile("ERROR: invalid time value: '.+'"));
        return patterns;
    }
}
