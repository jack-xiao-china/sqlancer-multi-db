package sqlancer.postgres.oracle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.PostgresOracleFactory;

/**
 * Unit tests for PostgreSQL EDC_RADAR Oracle implementation.
 */
public class PostgresEDCRadarOracleTest {

    /**
     * Test EDC_RADAR Oracle factory registration.
     */
    @Test
    public void testEDCRadarOracleFactoryExists() {
        boolean found = false;
        for (PostgresOracleFactory factory : PostgresOracleFactory.values()) {
            if (factory == PostgresOracleFactory.EDC_RADAR) {
                found = true;
                break;
            }
        }
        assertTrue(found, "EDC_RADAR Oracle should be registered in PostgresOracleFactory");
    }

    /**
     * Test EDC_RADAR Oracle requires tables to contain rows.
     */
    @Test
    public void testEDCRadarRequiresAllTablesToContainRows() {
        assertTrue(PostgresOracleFactory.EDC_RADAR.requiresAllTablesToContainRows(),
                "EDC_RADAR Oracle should require all tables to contain rows for testing");
    }

    /**
     * Test EDC_RADAR Oracle count in factory.
     */
    @Test
    public void testOracleCount() {
        int count = PostgresOracleFactory.values().length;
        // Expected: NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY,
        // QUERY_PARTITIONING, CERT, FUZZER, DQP, DQE, EET, CODDTEST, SONAR, EDC_RADAR
        assertEquals(16, count, "PostgreSQL should have 16 oracles registered (including EDC_RADAR)");
    }

    /**
     * Test EDC_RADAR Oracle name.
     */
    @Test
    public void testEDCRadarOracleName() {
        assertEquals("EDC_RADAR", PostgresOracleFactory.EDC_RADAR.name(), "EDC_RADAR Oracle should be named 'EDC_RADAR'");
    }
}
