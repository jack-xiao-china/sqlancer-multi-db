package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

class PostgresOracleFactoryTest {

    @Test
    void postgresSupportsTlpWhereButNotLegacyWhereAlias() {
        PostgresOptions validOptions = new PostgresOptions();
        JCommander.newBuilder().addObject(validOptions).build().parse("--oracle", "TLP_WHERE");
        assertEquals(PostgresOracleFactory.TLP_WHERE, validOptions.getTestOracleFactory().get(0));

        PostgresOptions invalidOptions = new PostgresOptions();
        assertThrows(ParameterException.class,
                () -> JCommander.newBuilder().addObject(invalidOptions).build().parse("--oracle", "WHERE"));
    }
}
