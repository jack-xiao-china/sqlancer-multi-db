package sqlancer.qpg.gaussdbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import sqlancer.Main;
import sqlancer.dbms.TestConfig;

public class TestGaussDBMQPG {

    @Test
    public void testGaussDBMQPG() {
        String gaussdbm = System.getenv("GAUSSDBM_AVAILABLE");
        boolean gaussdbmIsAvailable = gaussdbm != null && gaussdbm.equalsIgnoreCase("true");
        assumeTrue(gaussdbmIsAvailable);
        assertEquals(0,
                Main.executeMain(new String[] { "--random-seed", "0", "--timeout-seconds", TestConfig.SECONDS,
                        "--num-threads", "4", "--qpg-enable", "true", "--num-queries", TestConfig.NUM_QUERIES,
                        "--username", "sqlbuilder1", "--password", "huawei@123",
                        "--host", "121.37.186.131", "--port", "19995",
                        "gaussdb-m", "--oracle", "NOREC" }));
    }
}