package sqlancer.qpg.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import sqlancer.Main;
import sqlancer.dbms.TestConfig;

public class TestMySQLQPG {

    @Test
    public void testMySQLQPG() {
        String mysql = System.getenv("MYSQL_AVAILABLE");
        boolean mysqlIsAvailable = mysql != null && mysql.equalsIgnoreCase("true");
        assumeTrue(mysqlIsAvailable);
        assertEquals(0,
                Main.executeMain(new String[] { "--random-seed", "0", "--timeout-seconds", TestConfig.SECONDS,
                        "--num-threads", "4", "--qpg-enable", "true", "--num-queries", TestConfig.NUM_QUERIES,
                        "--username", "tpcc", "--password", "Taurus@123", "mysql", "--oracle", "NOREC" }));
    }
}