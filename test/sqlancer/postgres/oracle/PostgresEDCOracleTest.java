package sqlancer.postgres.oracle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.PostgresOracleFactory;

/**
 * Unit tests for PostgreSQL EDC Oracle implementation.
 */
public class PostgresEDCOracleTest {

    /**
     * Test EDC Oracle factory registration.
     */
    @Test
    public void testEDCOracleFactoryExists() {
        boolean found = false;
        for (PostgresOracleFactory factory : PostgresOracleFactory.values()) {
            if (factory == PostgresOracleFactory.EDC) {
                found = true;
                break;
            }
        }
        assertTrue(found, "EDC Oracle should be registered in PostgresOracleFactory");
    }

    /**
     * Test EDC Oracle requires tables to contain rows.
     */
    @Test
    public void testEDCRequiresAllTablesToContainRows() {
        assertTrue(PostgresOracleFactory.EDC.requiresAllTablesToContainRows(),
                "EDC Oracle should require all tables to contain rows for testing");
    }

    /**
     * Test EDC Oracle count in factory.
     */
    @Test
    public void testOracleCount() {
        int count = PostgresOracleFactory.values().length;
        // Expected: NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY,
        // QUERY_PARTITIONING, CERT, FUZZER, DQP, DQE, EET, CODDTEST, SONAR, EDC
        assertEquals(16, count, "PostgreSQL should have 16 oracles registered (including EDC)");
    }

    /**
     * Test EDC Oracle name.
     */
    @Test
    public void testEDCOracleName() {
        assertEquals("EDC", PostgresOracleFactory.EDC.name(), "EDC Oracle should be named 'EDC'");
    }
}