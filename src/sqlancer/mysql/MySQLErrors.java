package sqlancer.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import sqlancer.common.query.ExpectedErrors;

public final class MySQLErrors {

    private MySQLErrors() {
    }

    public static List<String> getExpressionErrors() {
        ArrayList<String> errors = new ArrayList<>();

        errors.add("BIGINT value is out of range"); // e.g., CAST(-('-1e500') AS SIGNED)
        errors.add("is not valid for CHARACTER SET");
        // Temporal conversions can legitimately fail when random strings are compared/cast to temporal types.
        errors.add("Incorrect TIMESTAMP value");
        errors.add("Incorrect DATETIME value");
        errors.add("Incorrect DATE value");
        errors.add("Incorrect TIME value");
        // JSON function errors
        errors.add("Incorrect parameter count in the call to native function 'JSON_KEYS'");
        errors.add("Incorrect parameter count in the call to native function 'LOCATE'");
        errors.add("Invalid JSON path expression");
        errors.add("Incorrect parameter count in the call to native function");
        // Packet size errors
        errors.add("Result of repeat() was larger than max_allowed_packet");
        errors.add("Packet too large");
        // 视图和 JOIN 相关错误
        errors.add("Not unique table/alias");
        errors.add("VIEW contains invalid column(s)");
        errors.add("Unknown view");
        errors.add("reference to VIEW");
        // 二进制类型与字符串比较的字符集转换错误
        errors.add("Cannot convert string");
        errors.add("Illegal mix of collations");
        // SQL 语法错误（CODDTEST 等复杂查询可能产生的语法问题）
        errors.add("You have an error in your SQL syntax");

        if (MySQLBugs.bug111471) {
            errors.add("Memory capacity exceeded");
        }

        return errors;
    }

    public static List<Pattern> getExpressionRegexErrors() {
        ArrayList<Pattern> errors = new ArrayList<>();

        if (MySQLBugs.bug114533) {
            errors.add(Pattern.compile("For input string: \"0+-0\"")); // match: For input string:
                                                                       // "00000000000000000000-0"
        }

        errors.add(Pattern.compile("Unknown column '.*' in 'order clause'"));
        errors.add(Pattern.compile("Unknown column '.*' in '.*'")); // General unknown column error

        return errors;
    }

    public static void addExpressionErrors(ExpectedErrors errors) {
        errors.addAll(getExpressionErrors());
        errors.addAllRegexes(getExpressionRegexErrors());
    }

    /**
     * Expected errors for HAVING clause expressions (MySQL syntax compatible).
     */
    public static List<String> getExpressionHavingErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.add("is not in GROUP BY clause");
        errors.add("contains nonaggregated column");
        errors.add("Unknown column");
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
        errors.add("Data truncated for functional index");
        errors.add("cannot be null");
        errors.add("Incorrect decimal value");
        errors.add("The value specified for generated column");
        // 二进制值插入字符串列的字符集错误
        errors.add("Incorrect string value");
        // Temporal value errors during INSERT/UPDATE
        errors.add("Incorrect DATE value");
        errors.add("Incorrect TIME value");
        errors.add("Incorrect DATETIME value");
        errors.add("Incorrect TIMESTAMP value");

        return errors;
    }

    public static void addInsertUpdateErrors(ExpectedErrors errors) {
        errors.addAll(getInsertUpdateErrors());
    }

}
