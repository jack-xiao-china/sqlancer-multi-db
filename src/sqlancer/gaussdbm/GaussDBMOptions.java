package sqlancer.gaussdbm;

import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;

@Parameters(separators = "=", commandDescription = "GaussDB-M (MySQL-compatible mode) - automatically creates M-compatible test databases")
public class GaussDBMOptions implements DBMSSpecificOptions<GaussDBMOracleFactory> {

    @Parameter(names = { "--help",
            "-h" }, description = "Lists all supported options for the GaussDB-M command", help = true, hidden = true)
    public boolean help;

    @Parameter(names = "--oracle", description = "Specifies which test oracle should be used, Options: [AGGREGATE, CERT, CODDTEST, DISTINCT, DQE, DQP, EET, FUZZER, GROUP_BY, HAVING, NOREC, PQS, QUERY_PARTITIONING, SONAR, TLP_WHERE]")
    public List<GaussDBMOracleFactory> oracles = Arrays.asList(GaussDBMOracleFactory.QUERY_PARTITIONING);

    @Override
    public List<GaussDBMOracleFactory> getTestOracleFactory() {
        return oracles;
    }

    public boolean isHelp() {
        return help;
    }
}
