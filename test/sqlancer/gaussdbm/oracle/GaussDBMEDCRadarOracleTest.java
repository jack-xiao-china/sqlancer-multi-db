package sqlancer.gaussdbm.oracle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBMOracleFactory;

/**
 * Unit tests for GaussDB-M EDC_RADAR Oracle implementation.
 */
public class GaussDBMEDCRadarOracleTest {

    /**
     * Test EDC_RADAR Oracle factory registration.
     */
    @Test
    public void testEDCRadarOracleFactoryExists() {
        boolean found = false;
        for (GaussDBMOracleFactory factory : GaussDBMOracleFactory.values()) {
            if (factory == GaussDBMOracleFactory.EDC_RADAR) {
                found = true;
                break;
            }
        }
        assertTrue(found, "EDC_RADAR Oracle should be registered in GaussDBMOracleFactory");
    }

    /**
     * Test EDC_RADAR Oracle requires tables to contain rows.
     */
    @Test
    public void testEDCRadarRequiresAllTablesToContainRows() {
        assertTrue(GaussDBMOracleFactory.EDC_RADAR.requiresAllTablesToContainRows(),
                "EDC_RADAR Oracle should require all tables to contain rows for testing");
    }

    /**
     * Test EDC_RADAR Oracle count in factory.
     */
    @Test
    public void testOracleCount() {
        int count = GaussDBMOracleFactory.values().length;
        // Expected: NOREC, TLP_WHERE, PQS, CERT, FUZZER, DQP, DQE, EET, CODDTEST, SONAR, EDC_RADAR,
        // AGGREGATE, HAVING, GROUP_BY, DISTINCT, QUERY_PARTITIONING
        assertEquals(16, count, "GaussDB-M should have 16 oracles registered (including EDC_RADAR)");
    }

    /**
     * Test EDC_RADAR Oracle name.
     */
    @Test
    public void testEDCRadarOracleName() {
        assertEquals("EDC_RADAR", GaussDBMOracleFactory.EDC_RADAR.name(), "EDC_RADAR Oracle should be named 'EDC_RADAR'");
    }
}
