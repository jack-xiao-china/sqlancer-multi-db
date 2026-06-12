package sqlancer.postgres.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataOracleBase;
import sqlancer.common.oracle.edcdata.EDCDataQueryBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.postgres.PostgresGlobalState;

public class PostgresEDCDataOracle extends EDCDataOracleBase<PostgresGlobalState> {

    public PostgresEDCDataOracle(PostgresGlobalState state) {
        super(state, EDCDataConfig.loadForDBMS("postgres"));
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
    protected EDCDataTableBuilder<PostgresGlobalState> createTableBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        return new PostgresEDCDataTableBuilder(state, config, exprBuilder);
    }

    @Override
    protected void addCommonExpectedErrors() {
        super.addCommonExpectedErrors();
        errors.add("value too long for type");
        errors.add("division by zero");
        errors.add("invalid input syntax");
        errors.add("operator does not exist");
        errors.add("function does not exist");
        errors.add("aggregate function not allowed");
        errors.add("invalid value");
        errors.add("out of range");
        errors.add("cannot cast");
        errors.add("numeric field overflow");
        errors.add("integer out of range");
    }
}
