package sqlancer.gaussdba.oracle.edcdata;

import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataOracleBase;
import sqlancer.common.oracle.edcdata.EDCDataQueryBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.gaussdba.GaussDBAGlobalState;

import java.util.Set;

public class GaussDBAEDCDataOracle extends EDCDataOracleBase<GaussDBAGlobalState> {

    public GaussDBAEDCDataOracle(GaussDBAGlobalState state) {
        super(state, EDCDataConfig.loadForDBMS("gaussdba"));
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
    protected EDCDataTableBuilder<GaussDBAGlobalState> createTableBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        return new GaussDBAEDCDataTableBuilder(state, config, exprBuilder);
    }

    @Override
    protected Set<String> getZeroArgFunctions() {
        return Set.of(
                "CURRENT_DATE", "CURRENT_TIMESTAMP", "SYSDATE",
                "NOW", "CLOCK_TIMESTAMP",
                "CURRENT_SCHEMA", "CURRENT_USER", "SESSION_USER",
                "VERSION", "RANDOM"
        );
    }

    @Override
    protected void addCommonExpectedErrors() {
        super.addCommonExpectedErrors();
        // GaussDB-A specific errors (Oracle-compatible)
        errors.add("numeric or value error");
        errors.add("invalid number");
        errors.add("literal does not match format string");
        errors.add("missing expression");
        errors.add("invalid identifier");
        errors.add("division by zero");
        errors.add("cannot insert NULL");
        errors.add("value too large for column");
        errors.add("date format picture");
        errors.add("not supported");
        errors.add("unsupported type");
        errors.add("type mismatch");
        errors.add("invalid input syntax");
        errors.add("cannot cast");
        errors.add("operator does not exist");
        errors.add("function does not exist");
        errors.add("is ambiguous");
    }
}
