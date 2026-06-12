package sqlancer.mysql.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTestScenario;
import sqlancer.mysql.MySQLGlobalState;

import java.util.List;

/**
 * MySQL-specific table builder for EDC_DATA test scenarios.
 */
public class MySQLEDCDataTableBuilder extends EDCDataTableBuilder<MySQLGlobalState> {

    public MySQLEDCDataTableBuilder(MySQLGlobalState state, EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        super(state, config, exprBuilder);
    }

    @Override
    protected String generateCreateTableSQL(EDCDataTestScenario scenario) {
        StringBuilder columns = new StringBuilder();
        List<String> columnNames = scenario.getColumnNames();
        List<String> columnTypes = scenario.getColumnTypes();

        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) columns.append(", ");
            columns.append(columnNames.get(i)).append(" ").append(columnTypes.get(i));
        }

        return "CREATE TABLE " + scenario.getBaseTableName() + " (" + columns + ")";
    }

    @Override
    protected String generateDerivedTableSQL(EDCDataTestScenario scenario, String testExpr,
                                              String derivedColumn, String otherColumnsWithAlias,
                                              String groupByClause) {
        // MySQL supports CREATE TABLE AS SELECT directly
        return "CREATE TABLE " + scenario.getDerivedTableName() + " AS " +
                "(SELECT (" + testExpr + ") AS " + derivedColumn +
                otherColumnsWithAlias + " FROM " + scenario.getBaseTableName() +
                " " + groupByClause + ")";
    }

    @Override
    protected String generateInsertSQL(EDCDataTestScenario scenario) {
        List<String> columnNames = scenario.getColumnNames();
        List<String> columnTypes = scenario.getColumnTypes();

        StringBuilder values = new StringBuilder();
        for (int i = 0; i < columnTypes.size(); i++) {
            if (i > 0) values.append(", ");
            values.append(exprBuilder.generateRandomValue(columnTypes.get(i)));
        }

        return "INSERT INTO " + scenario.getBaseTableName() +
                " (" + String.join(", ", columnNames) + ") VALUES (" + values + ")";
    }

    @Override
    protected String generateDerivedTypeQuery(EDCDataTestScenario scenario) {
        return "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE " +
                "TABLE_SCHEMA = DATABASE() AND " +
                "TABLE_NAME = '" + scenario.getDerivedTableName() + "' AND " +
                "COLUMN_NAME = '" + scenario.getDerivedColumnName() + "'";
    }

    @Override
    protected boolean requiresSeparateInsert() {
        return false; // MySQL supports CREATE TABLE AS SELECT
    }
}
