package sqlancer.gaussdbm;

import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;
import sqlancer.fucci.FucciOptions.FucciOracleType;

@Parameters(separators = "=", commandDescription = "GaussDB-M (MySQL-compatible mode) - automatically creates M-compatible test databases")
public class GaussDBMOptions implements DBMSSpecificOptions<GaussDBMOracleFactory> {

    @Parameter(names = { "--help",
            "-h" }, description = "Lists all supported options for the GaussDB-M command", help = true, hidden = true)
    public boolean help;

    @Parameter(names = "--oracle", description = "Specifies which test oracle should be used, Options: [AGGREGATE, CERT, CODDTEST, DISTINCT, DQE, DQP, EDC, EET, FUCCI, FUZZER, GROUP_BY, HAVING, NOREC, PQS, QUERY_PARTITIONING, SONAR, TLP_WHERE, TX_INFER, WRITE_CHECK, WRITE_CHECK_REPRODUCE]")
    public List<GaussDBMOracleFactory> oracles = Arrays.asList(GaussDBMOracleFactory.QUERY_PARTITIONING);

    // Fucci Oracle parameters
    @Parameter(names = "--fucci-oracle-type", description = "Fucci Oracle type: DT/MT/CS/ALL (default: ALL)")
    public FucciOracleType fucciOracleType = FucciOracleType.ALL;

    @Parameter(names = "--fucci-isolation-level", description = "Test isolation level: RANDOM/READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE (default: RANDOM)")
    public String fucciIsolationLevel = "RANDOM";

    @Parameter(names = "--fucci-schedule-count", description = "Number of schedules to test (default: 10)")
    public int fucciScheduleCount = 10;

    @Override
    public List<GaussDBMOracleFactory> getTestOracleFactory() {
        return oracles;
    }

    public boolean isHelp() {
        return help;
    }
}
