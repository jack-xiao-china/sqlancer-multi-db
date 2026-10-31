package sqlancer.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.beust.jcommander.Parameter;

class MySQLOptionsHelpVisibilityTest {

    @Test
    void onlyEffectiveOptionsAreShownInHelp() {
        Set<String> visibleParameterNames = new TreeSet<>();
        for (Field f : MySQLOptions.class.getDeclaredFields()) {
            Parameter p = f.getAnnotation(Parameter.class);
            if (p == null || p.hidden()) {
                continue;
            }
            visibleParameterNames.addAll(Arrays.asList(p.names()));
        }
        // Kept in sync with MySQLOptions: the FUCCI parameters are exposed since the TX oracle
        // integration.
        assertEquals(Set.of("--engines", "--fucci-isolation-level", "--fucci-oracle-type",
                "--fucci-schedule-count", "--oracle"), visibleParameterNames);
    }
}

