package sqlancer.qpg.gaussdba;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import sqlancer.Main;
import sqlancer.dbms.TestConfig;

public class TestGaussDBAQPG {

    @Test
    public void testGaussDBAQPG() {
        String gaussdba = System.getenv("GAUSSDBA_AVAILABLE");
        boolean gaussdbaIsAvailable = gaussdba != null && gaussdba.equalsIgnoreCase("true");
        assumeTrue(gaussdbaIsAvailable);
        assertEquals(0,
                Main.executeMain(new String[] { "--random-seed", "0", "--timeout-seconds", TestConfig.SECONDS,
                        "--num-threads", "4", "--qpg-enable", "true", "--num-queries", TestConfig.NUM_QUERIES,
                        "--username", "tpcc", "--password", "Taurus@123",
                        "--host", "192.168.95.195", "--port", "8000",
                        "gaussdb-a", "--target-database", "gaussdb_a_test", "--oracle", "NOREC" }));
    }
}