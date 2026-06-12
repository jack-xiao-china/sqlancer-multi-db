package sqlancer.postgres.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTestScenario;
import sqlancer.postgres.PostgresGlobalState;

import java.util.List;

/**
 * PostgreSQL-specific table builder for EDC_DATA test scenarios.
 */
public class PostgresEDCDataTableBuilder extends EDCDataTableBuilder<PostgresGlobalState> {

    public PostgresEDCDataTableBuilder(PostgresGlobalState state, EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
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
        // PostgreSQL requires CREATE TABLE AS with WITH NO DATA, then INSERT
        return "CREATE TABLE " + scenario.getDerivedTableName() + " AS " +
                "(SELECT (" + testExpr + ") AS " + derivedColumn +
                otherColumnsWithAlias + " FROM " + scenario.getBaseTableName() +
                " " + groupByClause + ") WITH NO DATA";
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
        // PostgreSQL needs schema name
        return "SELECT data_type FROM information_schema.columns WHERE " +
                "table_schema = 'public' AND " +
                "table_name = '" + scenario.getDerivedTableName() + "' AND " +
                "column_name = '" + scenario.getDerivedColumnName() + "'";
    }

    @Override
    protected boolean requiresSeparateInsert() {
        return true; // PostgreSQL uses WITH NO DATA, requires separate INSERT
    }

    public String generateDerivedInsertSQL(EDCDataTestScenario scenario, String testExpr,
                                            String otherColumnsWithAlias, String groupByClause) {
        return "INSERT INTO " + scenario.getDerivedTableName() + " " +
                "SELECT (" + testExpr + ") AS " + scenario.getDerivedColumnName() +
                otherColumnsWithAlias + " FROM " + scenario.getBaseTableName() +
                " " + groupByClause;
    }
}
