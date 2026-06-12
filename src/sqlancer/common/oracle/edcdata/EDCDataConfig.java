package sqlancer.common.oracle.edcdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration and seed file loader for EDC_DATA Oracle.
 * Loads operation definitions and data types from seed files at initialization.
 */
public class EDCDataConfig {
    private final List<EDCDataOperationDefinition> functions;
    private final List<EDCDataOperationDefinition> aggregates;
    private final List<EDCDataOperationDefinition> predicates;
    private final List<String> dataTypes;

    // Configuration parameters (from EDC Python config.py)
    private final int maxLoop = 10000000;
    private final int testColumnCount = 2;
    private final int otherColumnCount = 2;
    private final int selectCount = 50;

    public EDCDataConfig(List<EDCDataOperationDefinition> functions,
                         List<EDCDataOperationDefinition> aggregates,
                         List<EDCDataOperationDefinition> predicates,
                         List<String> dataTypes) {
        this.functions = Collections.unmodifiableList(new ArrayList<>(functions));
        this.aggregates = Collections.unmodifiableList(new ArrayList<>(aggregates));
        this.predicates = Collections.unmodifiableList(new ArrayList<>(predicates));
        this.dataTypes = Collections.unmodifiableList(new ArrayList<>(dataTypes));
    }

    /**
     * Load configuration for a specific DBMS from seed files.
     *
     * @param dbmsName DBMS name (e.g., "mysql", "postgres", "gaussdbm", "gaussdba")
     * @return Loaded configuration
     */
    public static EDCDataConfig loadForDBMS(String dbmsName) {
        String basePath = "/edc-data-seeds/" + dbmsName + "/";

        List<EDCDataOperationDefinition> functions = EDCDataOperationDefinition.loadFromSeedFile(
                basePath + "func.txt", EDCDataOperationType.FUNCTION);
        List<EDCDataOperationDefinition> aggregates = EDCDataOperationDefinition.loadFromSeedFile(
                basePath + "agg.txt", EDCDataOperationType.AGGREGATE);
        List<EDCDataOperationDefinition> predicates = EDCDataOperationDefinition.loadFromSeedFile(
                basePath + "pred.txt", EDCDataOperationType.PREDICATE);
        List<String> dataTypes = loadDataTypes(basePath + "type.txt");

        return new EDCDataConfig(functions, aggregates, predicates, dataTypes);
    }

    /**
     * Load data types from a seed file.
     */
    private static List<String> loadDataTypes(String resourcePath) {
        List<String> types = new ArrayList<>();
        InputStream is = EDCDataConfig.class.getResourceAsStream(resourcePath);

        if (is == null) {
            throw new IllegalArgumentException("Seed file not found: " + resourcePath);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    types.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read seed file: " + resourcePath, e);
        }

        return types;
    }

    public List<EDCDataOperationDefinition> getFunctions() {
        return functions;
    }

    public List<EDCDataOperationDefinition> getAggregates() {
        return aggregates;
    }

    public List<EDCDataOperationDefinition> getPredicates() {
        return predicates;
    }

    public List<String> getDataTypes() {
        return dataTypes;
    }

    public int getMaxLoop() {
        return maxLoop;
    }

    public int getTestColumnCount() {
        return testColumnCount;
    }

    public int getOtherColumnCount() {
        return otherColumnCount;
    }

    public int getSelectCount() {
        return selectCount;
    }
}
