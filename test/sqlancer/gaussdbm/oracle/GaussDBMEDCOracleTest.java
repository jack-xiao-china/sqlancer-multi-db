package sqlancer.gaussdbm.oracle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBMOracleFactory;

/**
 * Unit tests for GaussDB-M EDC Oracle implementation.
 */
public class GaussDBMEDCOracleTest {

    /**
     * Test EDC Oracle factory registration.
     */
    @Test
    public void testEDCOracleFactoryExists() {
        boolean found = false;
        for (GaussDBMOracleFactory factory : GaussDBMOracleFactory.values()) {
            if (factory == GaussDBMOracleFactory.EDC) {
                found = true;
                break;
            }
        }
        assertTrue(found, "EDC Oracle should be registered in GaussDBMOracleFactory");
    }

    /**
     * Test EDC Oracle requires tables to contain rows.
     */
    @Test
    public void testEDCRequiresAllTablesToContainRows() {
        assertTrue(GaussDBMOracleFactory.EDC.requiresAllTablesToContainRows(),
                "EDC Oracle should require all tables to contain rows for testing");
    }

    /**
     * Test EDC Oracle count in factory.
     */
    @Test
    public void testOracleCount() {
        int count = GaussDBMOracleFactory.values().length;
        // Expected: NOREC, TLP_WHERE, PQS, CERT, FUZZER, DQP, DQE, EET, CODDTEST, SONAR, EDC,
        // AGGREGATE, HAVING, GROUP_BY, DISTINCT, QUERY_PARTITIONING
        assertEquals(16, count, "GaussDB-M should have 16 oracles registered (including EDC)");
    }

    /**
     * Test EDC Oracle name.
     */
    @Test
    public void testEDCOracleName() {
        assertEquals("EDC", GaussDBMOracleFactory.EDC.name(), "EDC Oracle should be named 'EDC'");
    }
}