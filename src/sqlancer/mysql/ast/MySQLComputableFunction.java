package sqlancer.mysql.ast;

import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.ast.MySQLCastOperation.CastType;

public class MySQLComputableFunction implements MySQLExpression {

    private final MySQLFunction func;
    private final MySQLExpression[] args;

    public MySQLComputableFunction(MySQLFunction func, MySQLExpression... args) {
        this.func = func;
        this.args = args.clone();
    }

    public MySQLFunction getFunction() {
        return func;
    }

    public MySQLExpression[] getArguments() {
        return args.clone();
    }

    public enum MySQLFunction {

        // ABS(1, "ABS") {
        // @Override
        // public MySQLConstant apply(MySQLConstant[] args, MySQLExpression[] origArgs) {
        // if (args[0].isNull()) {
        // return MySQLConstant.createNullConstant();
        // }
        // MySQLConstant intVal = args[0].castAs(CastType.SIGNED);
        // return MySQLConstant.createIntConstant(Math.abs(intVal.getInt()));
        // }
        // },
        /**
         * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/bit-functions.html#function_bit-count">Bit Functions
         *      and Operators</a>
         */
        BIT_COUNT(1, "BIT_COUNT") {

            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                } else {
                    long val = arg.castAs(CastType.SIGNED).getInt();
                    return MySQLConstant.createIntConstant(Long.bitCount(val));
                }
            }

        },
        // BENCHMARK(2, "BENCHMARK") {
        //
        // @Override
        // public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression[] args) {
        // if (evaluatedArgs[0].isNull()) {
        // return MySQLConstant.createNullConstant();
        // }
        // if (evaluatedArgs[0].castAs(CastType.SIGNED).getInt() < 0) {
        // return MySQLConstant.createNullConstant();
        // }
        // if (Math.abs(evaluatedArgs[0].castAs(CastType.SIGNED).getInt()) > 10) {
        // throw new IgnoreMeException();
        // }
        // return MySQLConstant.createIntConstant(0);
        // }
        //
        // },
        COALESCE(2, "COALESCE") {

            @Override
            public MySQLConstant apply(MySQLConstant[] args, MySQLExpression... origArgs) {
                MySQLConstant result = MySQLConstant.createNullConstant();
                for (MySQLConstant arg : args) {
                    if (!arg.isNull()) {
                        result = MySQLConstant.createStringConstant(arg.castAsString());
                        break;
                    }
                }
                return castToMostGeneralType(result, origArgs);
            }

            @Override
            public boolean isVariadic() {
                return true;
            }

        },
        /**
         * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/control-flow-functions.html#function_if">Flow Control
         *      Functions</a>
         */
        IF(3, "IF") {

            @Override
            public MySQLConstant apply(MySQLConstant[] args, MySQLExpression... origArgs) {
                MySQLConstant cond = args[0];
                MySQLConstant left = args[1];
                MySQLConstant right = args[2];
                MySQLConstant result;
                if (cond.isNull() || !cond.asBooleanNotNull()) {
                    result = right;
                } else {
                    result = left;
                }
                return castToMostGeneralType(result, new MySQLExpression[] { origArgs[1], origArgs[2] });

            }

        },
        /**
         * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/control-flow-functions.html#function_ifnull">IFNULL</a>
         */
        IFNULL(2, "IFNULL") {

            @Override
            public MySQLConstant apply(MySQLConstant[] args, MySQLExpression... origArgs) {
                MySQLConstant result;
                if (args[0].isNull()) {
                    result = args[1];
                } else {
                    result = args[0];
                }
                return castToMostGeneralType(result, origArgs);
            }

        },
        LEAST(2, "LEAST", true) {

            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                return aggregate(evaluatedArgs, (min, cur) -> cur.isLessThan(min).asBooleanNotNull() ? cur : min);
            }

        },
        GREATEST(2, "GREATEST", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                return aggregate(evaluatedArgs, (max, cur) -> cur.isLessThan(max).asBooleanNotNull() ? max : cur);
            }
        },
        // ========== 数学函数 ==========
        ABS(1, "ABS") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                long val = arg.castAs(CastType.SIGNED).getInt();
                return MySQLConstant.createIntConstant(Math.abs(val));
            }
        },
        CEIL(1, "CEIL") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                // 对于整数类型，CEIL 返回原值
                if (arg.isInt()) {
                    return arg;
                }
                // 对于字符串，尝试转换为数值后向上取整
                try {
                    String str = arg.castAsString();
                    double val = Double.parseDouble(str);
                    return MySQLConstant.createIntConstant((long) Math.ceil(val));
                } catch (NumberFormatException e) {
                    throw new IgnoreMeException();
                }
            }
        },
        FLOOR(1, "FLOOR") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                if (arg.isInt()) {
                    return arg;
                }
                try {
                    String str = arg.castAsString();
                    double val = Double.parseDouble(str);
                    return MySQLConstant.createIntConstant((long) Math.floor(val));
                } catch (NumberFormatException e) {
                    throw new IgnoreMeException();
                }
            }
        },
        ROUND(1, "ROUND") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                long val = arg.castAs(CastType.SIGNED).getInt();
                return MySQLConstant.createIntConstant(val);
            }
        },
        MOD(2, "MOD") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg1 = evaluatedArgs[0];
                MySQLConstant arg2 = evaluatedArgs[1];
                if (arg1.isNull() || arg2.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                long val1 = arg1.castAs(CastType.SIGNED).getInt();
                long val2 = arg2.castAs(CastType.SIGNED).getInt();
                if (val2 == 0) {
                    return MySQLConstant.createNullConstant();  // 除数为 0 返回 NULL
                }
                return MySQLConstant.createIntConstant(val1 % val2);
            }
        },
        SIGN(1, "SIGN") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                long val = arg.castAs(CastType.SIGNED).getInt();
                if (val < 0) return MySQLConstant.createIntConstant(-1);
                if (val == 0) return MySQLConstant.createIntConstant(0);
                return MySQLConstant.createIntConstant(1);
            }
        },
        // ========== 字符串函数 ==========
        CONCAT(2, "CONCAT", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                StringBuilder sb = new StringBuilder();
                for (MySQLConstant arg : evaluatedArgs) {
                    if (arg.isNull()) {
                        return MySQLConstant.createNullConstant();  // CONCAT 中任意 NULL 返回 NULL
                    }
                    sb.append(arg.castAsString());
                }
                return MySQLConstant.createStringConstant(sb.toString());
            }
        },
        LENGTH(1, "LENGTH") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String str = arg.castAsString();
                return MySQLConstant.createIntConstant(str.length());
            }
        },
        UPPER(1, "UPPER") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                return MySQLConstant.createStringConstant(arg.castAsString().toUpperCase());
            }
        },
        LOWER(1, "LOWER") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                return MySQLConstant.createStringConstant(arg.castAsString().toLowerCase());
            }
        },
        TRIM(1, "TRIM") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                return MySQLConstant.createStringConstant(arg.castAsString().trim());
            }
        },
        LEFT(2, "LEFT") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant lenArg = evaluatedArgs[1];
                if (strArg.isNull() || lenArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String str = strArg.castAsString();
                int len = (int) lenArg.castAs(CastType.SIGNED).getInt();
                if (len < 0) {
                    return MySQLConstant.createStringConstant("");
                }
                if (len > str.length()) {
                    len = str.length();
                }
                return MySQLConstant.createStringConstant(str.substring(0, len));
            }
        },
        RIGHT(2, "RIGHT") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant lenArg = evaluatedArgs[1];
                if (strArg.isNull() || lenArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String str = strArg.castAsString();
                int len = (int) lenArg.castAs(CastType.SIGNED).getInt();
                if (len < 0) {
                    return MySQLConstant.createStringConstant("");
                }
                if (len > str.length()) {
                    len = str.length();
                }
                return MySQLConstant.createStringConstant(str.substring(str.length() - len));
            }
        },
        // ========== JSON 函数 ==========
        JSON_TYPE(1, "JSON_TYPE") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                // 简化实现：返回 JSON 值的类型
                String json = arg.castAsString();
                if (json.startsWith("{")) return MySQLConstant.createStringConstant("OBJECT");
                if (json.startsWith("[")) return MySQLConstant.createStringConstant("ARRAY");
                if (json.equals("null")) return MySQLConstant.createStringConstant("NULL");
                if (json.equals("true") || json.equals("false")) return MySQLConstant.createStringConstant("BOOLEAN");
                try {
                    Double.parseDouble(json);
                    return MySQLConstant.createStringConstant("INTEGER");
                } catch (NumberFormatException e) {
                    return MySQLConstant.createStringConstant("STRING");
                }
            }
        },
        JSON_VALID(1, "JSON_VALID") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                // 简化实现：假设生成的 JSON 都是有效的
                return MySQLConstant.createIntConstant(1);
            }
        },
        // ========== 时间函数（返回 null，因为依赖当前时间） ==========
        NOW(0, "NOW") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                return null;  // 无法计算期望值
            }
        },
        CURDATE(0, "CURDATE") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                return null;  // 无法计算期望值
            }
        },
        CURTIME(0, "CURTIME") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                return null;  // 无法计算期望值
            }
        },
        // ========== 扩展字符串函数 ==========
        SUBSTRING(2, "SUBSTRING", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant posArg = evaluatedArgs[1];
                MySQLConstant lenArg = evaluatedArgs.length > 2 ? evaluatedArgs[2] : null;

                if (strArg.isNull() || posArg.isNull() || (lenArg != null && lenArg.isNull())) {
                    return MySQLConstant.createNullConstant();
                }

                String str = strArg.castAsString();
                int pos = (int) posArg.castAs(CastType.SIGNED).getInt();

                // MySQL: pos starts from 1, negative pos means from end
                if (pos == 0) {
                    return MySQLConstant.createStringConstant("");
                }

                int startPos;
                if (pos > 0) {
                    startPos = pos - 1; // Convert to 0-based
                } else {
                    startPos = str.length() + pos; // Negative position from end
                    if (startPos < 0) startPos = 0;
                }

                if (lenArg == null) {
                    // SUBSTRING(str, pos) - return from pos to end
                    if (startPos >= str.length()) {
                        return MySQLConstant.createStringConstant("");
                    }
                    return MySQLConstant.createStringConstant(str.substring(startPos));
                } else {
                    // SUBSTRING(str, pos, len)
                    int len = (int) lenArg.castAs(CastType.SIGNED).getInt();
                    if (len <= 0) {
                        return MySQLConstant.createStringConstant("");
                    }
                    int endPos = Math.min(startPos + len, str.length());
                    if (startPos >= str.length()) {
                        return MySQLConstant.createStringConstant("");
                    }
                    return MySQLConstant.createStringConstant(str.substring(startPos, endPos));
                }
            }
        },
        REPLACE(3, "REPLACE") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant fromArg = evaluatedArgs[1];
                MySQLConstant toArg = evaluatedArgs[2];

                if (strArg.isNull() || fromArg.isNull() || toArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String str = strArg.castAsString();
                String fromStr = fromArg.castAsString();
                String toStr = toArg.castAsString();

                if (fromStr.isEmpty()) {
                    return MySQLConstant.createStringConstant(str); // No replacement for empty search string
                }

                return MySQLConstant.createStringConstant(str.replace(fromStr, toStr));
            }
        },
        LOCATE(2, "LOCATE", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant substrArg = evaluatedArgs[0];
                MySQLConstant strArg = evaluatedArgs[1];
                MySQLConstant posArg = evaluatedArgs.length > 2 ? evaluatedArgs[2] : null;

                if (substrArg.isNull() || strArg.isNull() || (posArg != null && posArg.isNull())) {
                    return MySQLConstant.createNullConstant();
                }

                String substr = substrArg.castAsString();
                String str = strArg.castAsString();
                int startPos = 1;

                if (posArg != null) {
                    startPos = (int) posArg.castAs(CastType.SIGNED).getInt();
                    if (startPos <= 0) startPos = 1;
                }

                // MySQL LOCATE is 1-indexed
                if (substr.isEmpty()) {
                    return MySQLConstant.createIntConstant(startPos > str.length() ? 0 : startPos);
                }

                int searchStart = startPos - 1;
                if (searchStart >= str.length()) {
                    return MySQLConstant.createIntConstant(0);
                }

                int index = str.indexOf(substr, searchStart);
                return MySQLConstant.createIntConstant(index >= 0 ? index + 1 : 0);
            }
        },
        INSTR(2, "INSTR") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant substrArg = evaluatedArgs[1];

                if (strArg.isNull() || substrArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String str = strArg.castAsString();
                String substr = substrArg.castAsString();

                if (substr.isEmpty()) {
                    return MySQLConstant.createIntConstant(1);
                }

                int index = str.indexOf(substr);
                return MySQLConstant.createIntConstant(index >= 0 ? index + 1 : 0);
            }
        },
        LPAD(3, "LPAD") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant lenArg = evaluatedArgs[1];
                MySQLConstant padArg = evaluatedArgs[2];

                if (strArg.isNull() || lenArg.isNull() || padArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String str = strArg.castAsString();
                int len = (int) lenArg.castAs(CastType.SIGNED).getInt();
                String pad = padArg.castAsString();

                if (len < 0) {
                    return MySQLConstant.createNullConstant();
                }

                if (str.length() >= len) {
                    return MySQLConstant.createStringConstant(str.substring(0, len));
                }

                if (pad.isEmpty()) {
                    return MySQLConstant.createNullConstant(); // MySQL returns NULL when pad is empty
                }

                int padLen = len - str.length();
                StringBuilder sb = new StringBuilder();
                while (sb.length() < padLen) {
                    sb.append(pad);
                }
                sb.setLength(padLen);
                sb.append(str);
                return MySQLConstant.createStringConstant(sb.toString());
            }
        },
        RPAD(3, "RPAD") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant lenArg = evaluatedArgs[1];
                MySQLConstant padArg = evaluatedArgs[2];

                if (strArg.isNull() || lenArg.isNull() || padArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String str = strArg.castAsString();
                int len = (int) lenArg.castAs(CastType.SIGNED).getInt();
                String pad = padArg.castAsString();

                if (len < 0) {
                    return MySQLConstant.createNullConstant();
                }

                if (str.length() >= len) {
                    return MySQLConstant.createStringConstant(str.substring(0, len));
                }

                if (pad.isEmpty()) {
                    return MySQLConstant.createNullConstant();
                }

                StringBuilder sb = new StringBuilder(str);
                while (sb.length() < len) {
                    sb.append(pad);
                }
                sb.setLength(len);
                return MySQLConstant.createStringConstant(sb.toString());
            }
        },
        REVERSE(1, "REVERSE") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String str = arg.castAsString();
                return MySQLConstant.createStringConstant(new StringBuilder(str).reverse().toString());
            }
        },
        REPEAT(2, "REPEAT") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant strArg = evaluatedArgs[0];
                MySQLConstant countArg = evaluatedArgs[1];

                if (strArg.isNull() || countArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String str = strArg.castAsString();
                int count = (int) countArg.castAs(CastType.SIGNED).getInt();

                if (count < 0) {
                    return MySQLConstant.createNullConstant();
                }

                if (count == 0) {
                    return MySQLConstant.createStringConstant("");
                }

                // Limit count to avoid excessive memory usage
                if (count > 100 || str.length() * count > 10000) {
                    throw new IgnoreMeException();
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    sb.append(str);
                }
                return MySQLConstant.createStringConstant(sb.toString());
            }
        },
        SPACE(1, "SPACE") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant countArg = evaluatedArgs[0];
                if (countArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                int count = (int) countArg.castAs(CastType.SIGNED).getInt();
                if (count < 0) {
                    return MySQLConstant.createNullConstant();
                }

                // Limit count to avoid excessive memory
                if (count > 100) {
                    throw new IgnoreMeException();
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    sb.append(' ');
                }
                return MySQLConstant.createStringConstant(sb.toString());
            }
        },
        ASCII(1, "ASCII") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String str = arg.castAsString();
                if (str.isEmpty()) {
                    return MySQLConstant.createIntConstant(0);
                }

                return MySQLConstant.createIntConstant(str.charAt(0));
            }
        },
        CHAR_LENGTH(1, "CHAR_LENGTH") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                // For CHARACTER_LENGTH, count characters (not bytes)
                return MySQLConstant.createIntConstant(arg.castAsString().length());
            }
        },
        CONCAT_WS(2, "CONCAT_WS", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant sepArg = evaluatedArgs[0];
                if (sepArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String separator = sepArg.castAsString();
                StringBuilder sb = new StringBuilder();
                boolean first = true;

                for (int i = 1; i < evaluatedArgs.length; i++) {
                    MySQLConstant arg = evaluatedArgs[i];
                    if (arg.isNull()) {
                        continue; // NULL values are skipped in CONCAT_WS
                    }
                    if (!first) {
                        sb.append(separator);
                    }
                    sb.append(arg.castAsString());
                    first = false;
                }

                return MySQLConstant.createStringConstant(sb.toString());
            }
        },
        LTRIM(1, "LTRIM") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String str = arg.castAsString();
                // Trim leading spaces
                int start = 0;
                while (start < str.length() && str.charAt(start) == ' ') {
                    start++;
                }
                return MySQLConstant.createStringConstant(str.substring(start));
            }
        },
        RTRIM(1, "RTRIM") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String str = arg.castAsString();
                // Trim trailing spaces
                int end = str.length();
                while (end > 0 && str.charAt(end - 1) == ' ') {
                    end--;
                }
                return MySQLConstant.createStringConstant(str.substring(0, end));
            }
        },
        // ========== 扩展 JSON 函数 ==========
        JSON_EXTRACT(2, "JSON_EXTRACT", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant jsonArg = evaluatedArgs[0];
                if (jsonArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String json = jsonArg.castAsString();
                // Simplified implementation: only support basic paths like '$' or '$.key'
                // For complex paths, throw IgnoreMeException

                if (evaluatedArgs.length < 2) {
                    throw new IgnoreMeException();
                }

                MySQLConstant pathArg = evaluatedArgs[1];
                if (pathArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String path = pathArg.castAsString();

                // Very simplified JSON path extraction
                if (path.equals("$")) {
                    return MySQLConstant.createJSONConstant(json);
                }

                // Support $.key format
                if (path.startsWith("$.")) {
                    String key = path.substring(2);
                    try {
                        // Simplified extraction for single key
                        int keyStart = json.indexOf("\"" + key + "\"");
                        if (keyStart < 0) {
                            return MySQLConstant.createNullConstant();
                        }
                        int valueStart = json.indexOf(":", keyStart) + 1;
                        String remaining = json.substring(valueStart).trim();
                        // Extract value (simplified)
                        if (remaining.startsWith("\"")) {
                            int endQuote = remaining.indexOf("\"", 1);
                            if (endQuote > 0) {
                                return MySQLConstant.createJSONConstant(remaining.substring(0, endQuote + 1));
                            }
                        } else if (remaining.startsWith("{") || remaining.startsWith("[")) {
                            // For nested objects/arrays, throw IgnoreMeException
                            throw new IgnoreMeException();
                        } else {
                            // Number, boolean, null
                            int end = 0;
                            while (end < remaining.length() &&
                                   (Character.isDigit(remaining.charAt(end)) ||
                                    remaining.charAt(end) == '-' ||
                                    remaining.charAt(end) == '.' ||
                                    remaining.charAt(end) == 'e' ||
                                    remaining.charAt(end) == 'E')) {
                                end++;
                            }
                            if (end > 0) {
                                return MySQLConstant.createJSONConstant(remaining.substring(0, end));
                            }
                        }
                    } catch (Exception e) {
                        throw new IgnoreMeException();
                    }
                }

                // Unsupported path format
                throw new IgnoreMeException();
            }
        },
        JSON_ARRAY(0, "JSON_ARRAY", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < evaluatedArgs.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    MySQLConstant arg = evaluatedArgs[i];
                    if (arg.isNull()) {
                        sb.append("null");
                    } else if (arg.isString()) {
                        // Escape string for JSON
                        String str = arg.getString();
                        sb.append("\"");
                        sb.append(str.replace("\\", "\\\\").replace("\"", "\\\""));
                        sb.append("\"");
                    } else if (arg.isInt()) {
                        sb.append(arg.getInt());
                    } else {
                        // For other types, use string representation
                        sb.append("\"");
                        sb.append(arg.castAsString().replace("\\", "\\\\").replace("\"", "\\\""));
                        sb.append("\"");
                    }
                }
                sb.append("]");
                return MySQLConstant.createJSONConstant(sb.toString());
            }
        },
        JSON_OBJECT(0, "JSON_OBJECT", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                // Arguments must be key-value pairs (even number)
                if (evaluatedArgs.length % 2 != 0) {
                    throw new IgnoreMeException();
                }

                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < evaluatedArgs.length; i += 2) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    MySQLConstant keyArg = evaluatedArgs[i];
                    MySQLConstant valueArg = evaluatedArgs[i + 1];

                    // Key must be a string
                    String key;
                    if (keyArg.isNull()) {
                        throw new IgnoreMeException(); // NULL key is invalid
                    } else {
                        key = keyArg.castAsString();
                    }

                    sb.append("\"");
                    sb.append(key.replace("\\", "\\\\").replace("\"", "\\\""));
                    sb.append("\": ");

                    if (valueArg.isNull()) {
                        sb.append("null");
                    } else if (valueArg.isString()) {
                        String value = valueArg.getString();
                        sb.append("\"");
                        sb.append(value.replace("\\", "\\\\").replace("\"", "\\\""));
                        sb.append("\"");
                    } else if (valueArg.isInt()) {
                        sb.append(valueArg.getInt());
                    } else {
                        sb.append("\"");
                        sb.append(valueArg.castAsString().replace("\\", "\\\\").replace("\"", "\\\""));
                        sb.append("\"");
                    }
                }
                sb.append("}");
                return MySQLConstant.createJSONConstant(sb.toString());
            }
        },
        JSON_REMOVE(2, "JSON_REMOVE", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant jsonArg = evaluatedArgs[0];
                if (jsonArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String json = jsonArg.castAsString();

                // Simplified: for one path only, remove the element at $.key
                if (evaluatedArgs.length < 2) {
                    return MySQLConstant.createJSONConstant(json);
                }

                MySQLConstant pathArg = evaluatedArgs[1];
                if (pathArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String path = pathArg.castAsString();

                // Only support $.key removal for simplicity
                if (path.startsWith("$.")) {
                    String key = path.substring(2);
                    try {
                        // Find and remove the key-value pair
                        int keyStart = json.indexOf("\"" + key + "\"");
                        if (keyStart < 0) {
                            return MySQLConstant.createJSONConstant(json); // Key not found, return original
                        }

                        // Find the start of the key's position
                        int searchStart = keyStart;
                        while (searchStart > 0 && json.charAt(searchStart - 1) != ',' && json.charAt(searchStart - 1) != '{') {
                            searchStart--;
                        }

                        // Find the end of the value
                        int valueStart = json.indexOf(":", keyStart) + 1;
                        String remaining = json.substring(valueStart).trim();

                        int valueEnd;
                        if (remaining.startsWith("\"")) {
                            valueEnd = remaining.indexOf("\"", 1) + 1;
                        } else if (remaining.startsWith("{")) {
                            // Count braces for nested object
                            int braceCount = 1;
                            valueEnd = 1;
                            while (valueEnd < remaining.length() && braceCount > 0) {
                                if (remaining.charAt(valueEnd) == '{') braceCount++;
                                if (remaining.charAt(valueEnd) == '}') braceCount--;
                                valueEnd++;
                            }
                        } else if (remaining.startsWith("[")) {
                            // Count brackets for nested array
                            int bracketCount = 1;
                            valueEnd = 1;
                            while (valueEnd < remaining.length() && bracketCount > 0) {
                                if (remaining.charAt(valueEnd) == '[') bracketCount++;
                                if (remaining.charAt(valueEnd) == ']') bracketCount--;
                                valueEnd++;
                            }
                        } else {
                            // Simple value (number, boolean, null)
                            valueEnd = 0;
                            while (valueEnd < remaining.length() &&
                                   remaining.charAt(valueEnd) != ',' &&
                                   remaining.charAt(valueEnd) != '}') {
                                valueEnd++;
                            }
                        }

                        int actualEnd = valueStart + valueEnd;

                        // Handle comma before or after
                        String result;
                        if (searchStart > 0 && json.charAt(searchStart - 1) == ',') {
                            result = json.substring(0, searchStart - 1) + json.substring(actualEnd);
                        } else if (actualEnd < json.length() && json.charAt(actualEnd) == ',') {
                            result = json.substring(0, searchStart) + json.substring(actualEnd + 1);
                        } else {
                            result = json.substring(0, searchStart) + json.substring(actualEnd);
                        }

                        return MySQLConstant.createJSONConstant(result);
                    } catch (Exception e) {
                        throw new IgnoreMeException();
                    }
                }

                throw new IgnoreMeException();
            }
        },
        JSON_CONTAINS(2, "JSON_CONTAINS", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant targetArg = evaluatedArgs[0];
                MySQLConstant candidateArg = evaluatedArgs[1];

                if (targetArg.isNull() || candidateArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String target = targetArg.castAsString();
                String candidate = candidateArg.castAsString();

                // Simplified: check if candidate string is contained in target string
                // This is a naive implementation; proper JSON containment is complex
                if (candidate.equals(target)) {
                    return MySQLConstant.createIntConstant(1);
                }

                // Check if candidate appears as a substring in target (very simplified)
                if (target.contains(candidate)) {
                    return MySQLConstant.createIntConstant(1);
                }

                return MySQLConstant.createIntConstant(0);
            }
        },
        JSON_KEYS(1, "JSON_KEYS", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant jsonArg = evaluatedArgs[0];
                if (jsonArg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String json = jsonArg.castAsString();

                // Only support extracting keys from top-level object
                if (!json.trim().startsWith("{")) {
                    throw new IgnoreMeException(); // Not an object
                }

                // Extract keys (simplified)
                StringBuilder keysArray = new StringBuilder("[");
                int pos = 1; // Skip opening brace
                boolean firstKey = true;

                while (pos < json.length() - 1) {
                    // Find next key
                    int keyStart = json.indexOf("\"", pos);
                    if (keyStart < 0 || keyStart >= json.length() - 1) break;

                    int keyEnd = json.indexOf("\"", keyStart + 1);
                    if (keyEnd < 0) break;

                    String key = json.substring(keyStart, keyEnd + 1);

                    if (!firstKey) {
                        keysArray.append(", ");
                    }
                    keysArray.append(key);
                    firstKey = false;

                    // Move past the value
                    int colonPos = json.indexOf(":", keyEnd);
                    if (colonPos < 0) break;

                    pos = colonPos + 1;
                    // Skip whitespace
                    while (pos < json.length() && (json.charAt(pos) == ' ' || json.charAt(pos) == '\t')) {
                        pos++;
                    }

                    // Skip the value
                    if (pos >= json.length()) break;
                    if (json.charAt(pos) == '"') {
                        // String value
                        int valueEnd = json.indexOf("\"", pos + 1);
                        if (valueEnd < 0) break;
                        pos = valueEnd + 1;
                    } else if (json.charAt(pos) == '{' || json.charAt(pos) == '[') {
                        // Nested structure - skip (complex)
                        throw new IgnoreMeException();
                    } else {
                        // Simple value - find comma or closing brace
                        while (pos < json.length() && json.charAt(pos) != ',' && json.charAt(pos) != '}') {
                            pos++;
                        }
                    }

                    // Skip comma if present
                    if (pos < json.length() && json.charAt(pos) == ',') {
                        pos++;
                    }
                }

                keysArray.append("]");
                return MySQLConstant.createJSONConstant(keysArray.toString());
            }
        },
        // ========== 扩展时间日期函数 ==========
        YEAR(1, "YEAR") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractYear(dateStr));
            }
        },
        MONTH(1, "MONTH") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractMonth(dateStr));
            }
        },
        DAY(1, "DAY") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractDay(dateStr));
            }
        },
        DAYOFWEEK(1, "DAYOFWEEK") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.dayOfWeek(dateStr));
            }
        },
        DAYOFMONTH(1, "DAYOFMONTH") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractDay(dateStr));
            }
        },
        DAYOFYEAR(1, "DAYOFYEAR") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.dayOfYear(dateStr));
            }
        },
        WEEK(1, "WEEK", true) {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                MySQLConstant modeArg = evaluatedArgs.length > 1 ? evaluatedArgs[1] : null;

                if (arg.isNull() || (modeArg != null && modeArg.isNull())) {
                    return MySQLConstant.createNullConstant();
                }

                String dateStr = arg.castAsString();
                int mode = modeArg != null ? (int) modeArg.castAs(CastType.SIGNED).getInt() : 0;

                return MySQLConstant.createIntConstant(MySQLTemporalUtil.weekOfYear(dateStr, mode));
            }
        },
        QUARTER(1, "QUARTER") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String dateStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractQuarter(dateStr));
            }
        },
        HOUR(1, "HOUR") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String timeStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractHour(timeStr));
            }
        },
        MINUTE(1, "MINUTE") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String timeStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractMinute(timeStr));
            }
        },
        SECOND(1, "SECOND") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                String timeStr = arg.castAsString();
                return MySQLConstant.createIntConstant(MySQLTemporalUtil.extractSecond(timeStr));
            }
        },
        DATEDIFF(2, "DATEDIFF") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg1 = evaluatedArgs[0];
                MySQLConstant arg2 = evaluatedArgs[1];

                if (arg1.isNull() || arg2.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String date1 = arg1.castAsString();
                String date2 = arg2.castAsString();

                return MySQLConstant.createIntConstant(MySQLTemporalUtil.dateDiff(date1, date2));
            }
        },
        LAST_DAY(1, "LAST_DAY") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String dateStr = arg.castAsString();
                java.time.LocalDate lastDay = MySQLTemporalUtil.lastDayOfMonth(dateStr);

                return new MySQLConstant.MySQLDateConstant(
                    lastDay.getYear(),
                    lastDay.getMonthValue(),
                    lastDay.getDayOfMonth()
                );
            }
        },
        TO_DAYS(1, "TO_DAYS") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                String dateStr = arg.castAsString();
                java.time.LocalDate date = MySQLTemporalUtil.parseDate(dateStr);

                // TO_DAYS returns the number of days since year 0
                // MySQL uses a different epoch than Java, but we can compute relative to year 0
                // Simplified: use days since year 0 approximation
                long daysSinceYear0 = date.getYear() * 365L + (date.getYear() / 4) - (date.getYear() / 100) + (date.getYear() / 400) + date.getDayOfYear();

                return MySQLConstant.createIntConstant(daysSinceYear0);
            }
        },
        FROM_DAYS(1, "FROM_DAYS") {
            @Override
            public MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args) {
                MySQLConstant arg = evaluatedArgs[0];
                if (arg.isNull()) {
                    return MySQLConstant.createNullConstant();
                }

                long dayNumber = arg.castAs(CastType.SIGNED).getInt();

                // Reverse of TO_DAYS (approximation)
                // MySQL's FROM_DAYS has specific handling; this is simplified
                try {
                    // Estimate year and refine
                    int estimatedYear = (int) (dayNumber / 365);
                    java.time.LocalDate date = java.time.LocalDate.ofYearDay(estimatedYear, 1);
                    long actualDays = date.getYear() * 365L + (date.getYear() / 4) - (date.getYear() / 100) + (date.getYear() / 400) + date.getDayOfYear();

                    // Adjust
                    long diff = dayNumber - actualDays;
                    date = date.plusDays(diff);

                    return new MySQLConstant.MySQLDateConstant(
                        date.getYear(),
                        date.getMonthValue(),
                        date.getDayOfMonth()
                    );
                } catch (Exception e) {
                    throw new IgnoreMeException();
                }
            }
        };

        private String functionName;
        final int nrArgs;
        private final boolean variadic;

        private static MySQLConstant aggregate(MySQLConstant[] evaluatedArgs, BinaryOperator<MySQLConstant> op) {
            boolean containsNull = Stream.of(evaluatedArgs).anyMatch(arg -> arg.isNull());
            if (containsNull) {
                return MySQLConstant.createNullConstant();
            }
            MySQLConstant least = evaluatedArgs[1];
            for (MySQLConstant arg : evaluatedArgs) {
                MySQLConstant left = castToMostGeneralType(least, evaluatedArgs);
                MySQLConstant right = castToMostGeneralType(arg, evaluatedArgs);
                least = op.apply(right, left);
            }
            return castToMostGeneralType(least, evaluatedArgs);
        }

        MySQLFunction(int nrArgs, String functionName) {
            this.nrArgs = nrArgs;
            this.functionName = functionName;
            this.variadic = false;
        }

        MySQLFunction(int nrArgs, String functionName, boolean variadic) {
            this.nrArgs = nrArgs;
            this.functionName = functionName;
            this.variadic = variadic;
        }

        /**
         * Gets the number of arguments if the function is non-variadic. If the function is variadic, the minimum number
         * of arguments is returned.
         *
         * @return the number of arguments
         */
        public int getNrArgs() {
            return nrArgs;
        }

        public abstract MySQLConstant apply(MySQLConstant[] evaluatedArgs, MySQLExpression... args);

        public static MySQLFunction getRandomFunction() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String toString() {
            return functionName;
        }

        public boolean isVariadic() {
            return variadic;
        }

        public String getName() {
            return functionName;
        }
    }

    @Override
    public MySQLConstant getExpectedValue() {
        MySQLConstant[] constants = new MySQLConstant[args.length];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = args[i].getExpectedValue();
            if (constants[i] == null) {
                return null;
            }
        }
        return func.apply(constants, args);
    }

    public static MySQLConstant castToMostGeneralType(MySQLConstant cons, MySQLExpression... typeExpressions) {
        if (cons == null || cons.isNull()) {
            return cons == null ? MySQLConstant.createNullConstant() : cons;
        }
        MySQLDataType type = getMostGeneralType(typeExpressions);
        switch (type) {
        case INT:
            if (cons.isInt()) {
                return cons;
            } else {
                return MySQLConstant.createIntConstant(cons.castAs(CastType.SIGNED).getInt());
            }
        case VARCHAR:
            return MySQLConstant.createStringConstant(cons.castAsString());
        case DATE:
            if (cons.getType() == MySQLDataType.DATE && cons instanceof MySQLConstant.MySQLDateConstant) {
                return cons;
            }
            return parseDate(cons.castAsString());
        case TIME:
            if (cons.getType() == MySQLDataType.TIME && cons instanceof MySQLConstant.MySQLTimeConstant) {
                return cons;
            }
            return parseTime(cons.castAsString());
        case DATETIME:
            if (cons.getType() == MySQLDataType.DATETIME && cons instanceof MySQLConstant.MySQLDateTimeConstant) {
                return cons;
            }
            return parseDateTime(cons.castAsString());
        case TIMESTAMP:
            if (cons.getType() == MySQLDataType.TIMESTAMP && cons instanceof MySQLConstant.MySQLTimestampConstant) {
                return cons;
            }
            return parseTimestamp(cons.castAsString());
        case YEAR:
            if (cons.getType() == MySQLDataType.YEAR && cons instanceof MySQLConstant.MySQLYearConstant) {
                return cons;
            }
            return parseYear(cons.castAsString());
        default:
            throw new IgnoreMeException();
        }
    }

    private static MySQLConstant parseDate(String s) {
        try {
            if (s == null) {
                throw new IgnoreMeException();
            }
            String[] parts = s.split("-");
            if (parts.length != 3) {
                throw new IgnoreMeException();
            }
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                throw new IgnoreMeException();
            }
            return new MySQLConstant.MySQLDateConstant(year, month, day);
        } catch (NumberFormatException e) {
            throw new IgnoreMeException();
        }
    }

    private static MySQLConstant parseTime(String s) {
        try {
            if (s == null) {
                throw new IgnoreMeException();
            }
            String base = s;
            int fsp = 0;
            int fraction = 0;
            int dot = s.indexOf('.');
            if (dot != -1) {
                base = s.substring(0, dot);
                String fracStr = s.substring(dot + 1);
                if (fracStr.isEmpty() || fracStr.length() > 6) {
                    throw new IgnoreMeException();
                }
                fsp = fracStr.length();
                fraction = Integer.parseInt(fracStr);
            }
            String[] parts = base.split(":");
            if (parts.length != 3) {
                throw new IgnoreMeException();
            }
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int second = Integer.parseInt(parts[2]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
                throw new IgnoreMeException();
            }
            if (fsp > 0) {
                return new MySQLConstant.MySQLTimeConstant(hour, minute, second, fraction, fsp);
            }
            return new MySQLConstant.MySQLTimeConstant(hour, minute, second);
        } catch (RuntimeException e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }
            throw new IgnoreMeException();
        }
    }

    private static MySQLConstant parseDateTime(String s) {
        return parseDateTimeLike(s, true);
    }

    private static MySQLConstant parseTimestamp(String s) {
        return parseDateTimeLike(s, false);
    }

    private static MySQLConstant parseDateTimeLike(String s, boolean isDateTime) {
        try {
            if (s == null) {
                throw new IgnoreMeException();
            }
            String[] dtParts = s.split(" ");
            if (dtParts.length != 2) {
                throw new IgnoreMeException();
            }
            String datePart = dtParts[0];
            String timePart = dtParts[1];
            String[] dateParts = datePart.split("-");
            if (dateParts.length != 3) {
                throw new IgnoreMeException();
            }
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                throw new IgnoreMeException();
            }
            String timeBase = timePart;
            int fsp = 0;
            int fraction = 0;
            int dot = timePart.indexOf('.');
            if (dot != -1) {
                timeBase = timePart.substring(0, dot);
                String fracStr = timePart.substring(dot + 1);
                if (fracStr.isEmpty() || fracStr.length() > 6) {
                    throw new IgnoreMeException();
                }
                fsp = fracStr.length();
                fraction = Integer.parseInt(fracStr);
            }
            String[] t = timeBase.split(":");
            if (t.length != 3) {
                throw new IgnoreMeException();
            }
            int hour = Integer.parseInt(t[0]);
            int minute = Integer.parseInt(t[1]);
            int second = Integer.parseInt(t[2]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
                throw new IgnoreMeException();
            }

            if (fsp > 0) {
                if (isDateTime) {
                    return new MySQLConstant.MySQLDateTimeConstant(year, month, day, hour, minute, second, fraction,
                            fsp);
                } else {
                    return new MySQLConstant.MySQLTimestampConstant(year, month, day, hour, minute, second, fraction,
                            fsp);
                }
            }
            if (isDateTime) {
                return new MySQLConstant.MySQLDateTimeConstant(year, month, day, hour, minute, second);
            } else {
                return new MySQLConstant.MySQLTimestampConstant(year, month, day, hour, minute, second);
            }
        } catch (RuntimeException e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }
            throw new IgnoreMeException();
        }
    }

    private static MySQLConstant parseYear(String s) {
        try {
            if (s == null) {
                throw new IgnoreMeException();
            }
            int year = Integer.parseInt(s.trim());
            return new MySQLConstant.MySQLYearConstant(year);
        } catch (NumberFormatException e) {
            throw new IgnoreMeException();
        }
    }

    public static MySQLDataType getMostGeneralType(MySQLExpression... expressions) {
        MySQLDataType type = null;
        for (MySQLExpression expr : expressions) {
            MySQLDataType exprType;
            if (expr instanceof MySQLColumnReference) {
                exprType = ((MySQLColumnReference) expr).getColumn().getType();
            } else {
                exprType = expr.getExpectedValue().getType();
            }
            if (type == null) {
                type = exprType;
            } else if (exprType == MySQLDataType.VARCHAR) {
                type = MySQLDataType.VARCHAR;
            }

        }
        return type;
    }

}
