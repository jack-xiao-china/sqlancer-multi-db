package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.gen.PostgresCommon;

class PostgresEnumTypeCatalogTest {

    private List<String> originalEnumTypeNames;
    private Map<String, List<String>> originalEnumTypeLabels;

    @BeforeEach
    void saveEnumCatalog() throws Exception {
        originalEnumTypeNames = new ArrayList<>(enumTypeNames());
        originalEnumTypeLabels = new HashMap<>(enumTypeLabels());
    }

    @AfterEach
    void restoreEnumCatalog() throws Exception {
        List<String> enumTypeNames = enumTypeNames();
        enumTypeNames.clear();
        enumTypeNames.addAll(originalEnumTypeNames);

        Map<String, List<String>> enumTypeLabels = enumTypeLabels();
        enumTypeLabels.clear();
        enumTypeLabels.putAll(originalEnumTypeLabels);
    }

    @Test
    void enumTypeFallsBackToTextWhenCatalogIsEmpty() throws Exception {
        enumTypeNames().clear();
        enumTypeLabels().clear();

        StringBuilder sb = new StringBuilder();
        PostgresCommon.appendDataType(PostgresDataType.ENUM, sb, false, List.of());

        assertEquals("text", sb.toString());
        assertEquals("text", PostgresProvider.getRandomEnumTypeName());
    }

    @Test
    void enumTypeNamesAreReturnedAsSnapshot() throws Exception {
        List<String> enumTypeNames = enumTypeNames();
        enumTypeNames.clear();
        enumTypeNames.add("e0");

        Map<String, List<String>> enumTypeLabels = enumTypeLabels();
        enumTypeLabels.clear();
        enumTypeLabels.put("e0", List.of("a", "b"));

        List<String> snapshot = PostgresProvider.getEnumTypeNames();
        snapshot.clear();

        assertEquals(List.of("e0"), PostgresProvider.getEnumTypeNames());
        assertEquals("e0", PostgresProvider.getRandomEnumTypeName());
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumTypeNames() throws Exception {
        Field field = PostgresProvider.class.getDeclaredField("enumTypeNames");
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> enumTypeLabels() throws Exception {
        Field field = PostgresProvider.class.getDeclaredField("enumTypeLabels");
        field.setAccessible(true);
        return (Map<String, List<String>>) field.get(null);
    }
}
