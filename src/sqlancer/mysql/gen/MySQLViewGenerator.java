package sqlancer.mysql.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLSelect;

public final class MySQLViewGenerator {

    private MySQLViewGenerator() {
    }

    public static SQLQueryAdapter create(MySQLGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder("CREATE");

        // MySQL 支持 CREATE OR REPLACE VIEW
        if (Randomly.getBoolean()) {
            sb.append(" OR REPLACE");
        }

        sb.append(" VIEW ");

        // 生成唯一的视图名
        String viewName;
        int i = 0;
        while (true) {
            String candidateName = "v" + i++;
            if (globalState.getSchema().getDatabaseTables().stream()
                    .noneMatch(tab -> tab.getName().contentEquals(candidateName))) {
                viewName = candidateName;
                break;
            }
        }
        sb.append(viewName);

        // 可选的列名列表 - 不生成显式列名，让 MySQL 自动从 SELECT 推导
        // 避免 "SELECT list and column names list have different column counts" 错误

        sb.append(" AS ");

        // 生成随机 SELECT 语句
        MySQLSelect select = MySQLRandomQuerySynthesizer.generate(globalState, Randomly.smallNumber() + 1);
        sb.append(MySQLVisitor.asString(select));

        // 添加预期错误
        errors.add("already exists");
        errors.add("cannot drop columns from view");
        errors.add("non-integer constant in ORDER BY");
        errors.add("for SELECT DISTINCT, ORDER BY expressions must appear in select list");
        errors.add("cannot change data type of view column");
        errors.add("specified more than once");
        errors.add("VIEW contains invalid column(s)");
        errors.add("Unknown table");
        errors.add("reference to VIEW");
        MySQLErrors.addExpressionErrors(errors);

        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}