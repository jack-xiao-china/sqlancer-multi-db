package sqlancer.common.oracle.edcdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for a single operation (function, aggregate, or predicate).
 * Loaded from seed files at initialization.
 */
public class EDCDataOperationDefinition {
    private final String name;
    private final EDCDataOperationType type;

    public EDCDataOperationDefinition(String name, EDCDataOperationType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public EDCDataOperationType getType() {
        return type;
    }

    /**
     * Load operation definitions from a seed file in the classpath.
     *
     * @param resourcePath Path to the seed file (e.g., "/edc-data-seeds/mysql/func.txt")
     * @param type         Operation type to assign to all loaded operations
     * @return List of operation definitions
     */
    public static List<EDCDataOperationDefinition> loadFromSeedFile(String resourcePath, EDCDataOperationType type) {
        List<EDCDataOperationDefinition> operations = new ArrayList<>();
        InputStream is = EDCDataOperationDefinition.class.getResourceAsStream(resourcePath);

        if (is == null) {
            throw new IllegalArgumentException("Seed file not found: " + resourcePath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    operations.add(new EDCDataOperationDefinition(line, type));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read seed file: " + resourcePath, e);
        }

        return operations;
    }

    @Override
    public String toString() {
        return String.format("EDCDataOperationDefinition[%s, %s]", name, type);
    }
}
