package sqlancer.gaussdba.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTestScenario;
import sqlancer.gaussdba.GaussDBAGlobalState;

import java.util.List;

/**
 * GaussDB-A (Oracle-compatible) table builder for EDC_DATA test scenarios.
 */
public class GaussDBAEDCDataTableBuilder extends EDCDataTableBuilder<GaussDBAGlobalState> {

    public GaussDBAEDCDataTableBuilder(GaussDBAGlobalState state, EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
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
        // GaussDB-A (Oracle-compatible) requires CREATE TABLE AS SELECT
        return "CREATE TABLE " + scenario.getDerivedTableName() + " AS " +
                "SELECT (" + testExpr + ") AS " + derivedColumn +
                otherColumnsWithAlias + " FROM " + scenario.getBaseTableName() +
                " " + groupByClause;
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
        // GaussDB-A: all_tab_columns does not exist, use information_schema.columns
        return "SELECT data_type FROM information_schema.columns WHERE " +
                "table_schema = current_schema AND " +
                "table_name = '" + scenario.getDerivedTableName() + "' AND " +
                "column_name = '" + scenario.getDerivedColumnName() + "'";
    }

    @Override
    protected boolean requiresSeparateInsert() {
        return false; // GaussDB-A supports CREATE TABLE AS SELECT
    }
}
