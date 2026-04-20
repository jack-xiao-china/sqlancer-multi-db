package sqlancer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBMProvider;

class LoggingRunDirectoryReuseTest {

    @Test
    void executorFactoryReusesOneRunDirectoryPerProcessRun() throws Exception {
        MainOptions options = new MainOptions();
        Main.DBMSExecutorFactory<?, ?, ?> factory = new Main.DBMSExecutorFactory<>(new GaussDBMProvider(), options);

        Object first = factory.getDBMSExecutor("db0", new Randomly(1));
        Object second = factory.getDBMSExecutor("db1", new Randomly(2));

        Field runDirectoryName = first.getClass().getDeclaredField("runDirectoryName");
        runDirectoryName.setAccessible(true);

        assertEquals(runDirectoryName.get(first), runDirectoryName.get(second));
    }
}
