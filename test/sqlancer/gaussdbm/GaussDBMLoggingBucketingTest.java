package sqlancer.gaussdbm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import sqlancer.Main;
import sqlancer.MainOptions;

class GaussDBMLoggingBucketingTest {

    @Test
    void logDirectoryIsBucketedByDbmsName() throws Exception {
        Path tmp = Files.createTempDirectory("sqlancer-gaussdbm-logs-");
        File base = tmp.toFile();

        new Main.StateLogger("db0", new GaussDBMProvider(), new MainOptions(), base, "unit_test_run");

        File expected = new File(new File(base, "gaussdb-m"), "unit_test_run");
        assertTrue(expected.isDirectory(), "Expected log directory to be created: " + expected);
    }
}
