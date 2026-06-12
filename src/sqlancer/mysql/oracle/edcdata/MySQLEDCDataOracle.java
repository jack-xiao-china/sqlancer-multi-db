package sqlancer.mysql.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataOracleBase;
import sqlancer.common.oracle.edcdata.EDCDataQueryBuilder;
import sqlancer.mysql.MySQLGlobalState;

/**
 * MySQL-specific EDC_DATA Oracle implementation.
 */
public class MySQLEDCDataOracle extends EDCDataOracleBase<MySQLGlobalState> {

    public MySQLEDCDataOracle(MySQLGlobalState state) {
        super(state, loadConfig(state));
    }

    private static EDCDataConfig loadConfig(MySQLGlobalState state) {
        return EDCDataConfig.loadForDBMS("mysql");
    }

    @Override
    protected EDCDataExpressionBuilder createExpressionBuilder(EDCDataConfig config) {
        return new EDCDataExpressionBuilder(config, state.getRandomly());
    }

    @Override
    protected EDCDataQueryBuilder createQueryBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        return new EDCDataQueryBuilder(config, exprBuilder, state.getRandomly());
    }

    @Override
    protected MySQLEDCDataTableBuilder createTableBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        return new MySQLEDCDataTableBuilder(state, config, exprBuilder);
    }

    @Override
    protected void addCommonExpectedErrors() {
        super.addCommonExpectedErrors();
        errors.add("Data too long for column");
        errors.add("Truncated incorrect DOUBLE value");
        errors.add("Out of range value for column");
        errors.add("Incorrect integer value");
        errors.add("Incorrect decimal value");
        errors.add("Invalid JSON text");
        errors.add("Duplicate entry");
        errors.add("Access to native function");
        errors.add("is rejected");
        errors.add("Illegal parameter data type");
        errors.add("Incorrect parameter count");
        errors.add("Invalid use of group function");
        errors.add("mix of collations");
        errors.add("Illegal mix of collations");
        errors.add("Collation");
        errors.add("Character set");
        errors.add("BIGINT UNSIGNED value is out of range");
        errors.add("DOUBLE value is out of range");
        errors.add("value is out of range");
    }
}
