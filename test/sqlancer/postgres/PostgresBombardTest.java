package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.beust.jcommander.JCommander;

class PostgresBombardTest {

    @Test
    void parsesPostgresBombardOptions() {
        PostgresOptions options = new PostgresOptions();

        JCommander.newBuilder().addObject(options).build().parse("--bombard", "true", "--bombard-workers", "7");

        assertTrue(options.isBombard());
        assertEquals(7, options.getBombardWorkers());
    }

    @Test
    void bombardOptionsHaveSafeDefaults() {
        PostgresOptions options = new PostgresOptions();

        assertFalse(options.isBombard());
        assertEquals(4, options.getBombardWorkers());
    }

    @Test
    void refreshIntervalIsLessAggressiveThanMysqlBaseline() {
        assertEquals(15, PostgresBombard.SCHEMA_REFRESH_INTERVAL);
    }

    @Test
    void excludesHighRiskActionsFromBombardWorkload() {
        assertTrue(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.DISCARD));
        assertTrue(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.CREATE_TABLESPACE));
        assertTrue(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.TRUNCATE));
        assertTrue(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.VACUUM));
        assertTrue(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.CLUSTER));
        assertTrue(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.REINDEX));

        assertFalse(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.INSERT));
        assertFalse(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.UPDATE));
        assertFalse(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.DELETE));
        assertFalse(PostgresProvider.isExcludedBombardAction(PostgresProvider.Action.CREATE_INDEX));
    }

    @Test
    void bombardTableNamesAreWorkerScoped() {
        assertEquals("tb2_17", PostgresProvider.getBombardTableName(2, 17));
    }
}
