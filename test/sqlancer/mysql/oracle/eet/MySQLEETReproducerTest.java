package sqlancer.mysql.oracle.eet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.mysql.MySQLGlobalState;

class MySQLEETReproducerTest {

    @Test
    void bugStillTriggers_whenMismatch() throws SQLException {
        EETQueryExecutor ex = new EETQueryExecutor() {
            private int n;

            @Override
            public List<List<String>> executeQuery(String sql) {
                n++;
                if (n % 2 == 1) {
                    return List.of(List.of("1"));
                } else {
                    return List.of(List.of("2"));
                }
            }
        };
        MySQLEETReproducer r = new MySQLEETReproducer("SELECT 1", "SELECT 2", ex);
        assertTrue(r.bugStillTriggers(new MySQLGlobalState()));
    }

    @Test
    void bugStillTriggers_whenMatch() throws SQLException {
        EETQueryExecutor ex = sql -> List.of(List.of("x"));
        MySQLEETReproducer r = new MySQLEETReproducer("SELECT 1", "SELECT 1", ex);
        assertFalse(r.bugStillTriggers(new MySQLGlobalState()));
    }
}
