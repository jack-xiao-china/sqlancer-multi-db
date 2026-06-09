package sqlancer.gaussdba;

import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;
import sqlancer.fucci.FucciOptions.FucciOracleType;

@Parameters(separators = "=", commandDescription = "GaussDB A Compatibility Mode (Oracle Style)")
public class GaussDBAOptions implements DBMSSpecificOptions<GaussDBAOracleFactory> {

    @Parameter(names = { "--help", "-h" }, description = "Lists all supported options for GaussDB A", help = true, hidden = true)
    public boolean help;

    @Parameter(names = "--oracle", description = "Specifies which test oracle should be used, Options: [AGGREGATE, CERT, DISTINCT, DQE, DQP, EET, FUCCI, FUZZER, GROUP_BY, HAVING, JIR, NOREC, PQS, QUERY_PARTITIONING, TLP_WHERE, TX_INFER, WRITE_CHECK, WRITE_CHECK_REPRODUCE]")
    public List<GaussDBAOracleFactory> oracles = Arrays.asList(GaussDBAOracleFactory.QUERY_PARTITIONING);

    @Parameter(names = "--enable-clob-blob", description = "Enable CLOB/BLOB types")
    public boolean enableClobBlob = false;

    @Parameter(names = "--target-database", description = "A-compatible database to connect (REQUIRED). Must be created with 'CREATE DATABASE xxx WITH dbcompatibility A'", required = true)
    public String targetDatabase = null;

    // Fucci Oracle parameters
    @Parameter(names = "--fucci-oracle-type", description = "Fucci Oracle type: DT/MT/CS/ALL (default: ALL)")
    public FucciOracleType fucciOracleType = FucciOracleType.ALL;

    @Parameter(names = "--fucci-isolation-level", description = "Test isolation level: RANDOM/READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE (default: RANDOM)")
    public String fucciIsolationLevel = "RANDOM";

    @Parameter(names = "--fucci-schedule-count", description = "Number of schedules to test (default: 10)")
    public int fucciScheduleCount = 10;

    @Override
    public List<GaussDBAOracleFactory> getTestOracleFactory() {
        return oracles;
    }

    public boolean isHelp() {
        return help;
    }
}