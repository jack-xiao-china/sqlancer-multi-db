package sqlancer.gaussdbm.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataOracleBase;
import sqlancer.common.oracle.edcdata.EDCDataQueryBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.gaussdbm.GaussDBMGlobalState;

public class GaussDBMEDCDataOracle extends EDCDataOracleBase<GaussDBMGlobalState> {

    public GaussDBMEDCDataOracle(GaussDBMGlobalState state) {
        super(state, EDCDataConfig.loadForDBMS("gaussdbm"));
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
    protected EDCDataTableBuilder<GaussDBMGlobalState> createTableBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        return new GaussDBMEDCDataTableBuilder(state, config, exprBuilder);
    }

    @Override
    protected void addCommonExpectedErrors() {
        super.addCommonExpectedErrors();
        // GaussDB-M specific errors (MySQL-compatible)
        errors.add("Data too long for column");
        errors.add("Truncated incorrect");
        errors.add("Out of range value");
        errors.add("Invalid use of group function");
        errors.add("Incorrect integer value");
        errors.add("Incorrect decimal value");
        errors.add("Incorrect datetime value");
        errors.add("Incorrect date value");
        errors.add("Duplicate entry");
        errors.add("Illegal parameter data type");
        errors.add("Incorrect parameter count");
        errors.add("mix of collations");
        errors.add("Illegal mix of collations");
        errors.add("value is out of range");
        errors.add("is rejected");
        errors.add("Unsupported");
        errors.add("does not support");
        errors.add("not supported");
        errors.add("type mismatch");
        errors.add("invalid input syntax");
        errors.add("cannot cast");
        errors.add("Invalid regular expression");
        errors.add("invalid escape");
    }
}
