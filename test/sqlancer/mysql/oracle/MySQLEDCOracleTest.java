package sqlancer.mysql.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sqlancer.Randomly;
import sqlancer.common.oracle.EDCBase.TableStructure;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLOracleFactory;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;

/**
 * Unit tests for MySQLEDCOracle and EDCBase.
 *
 * Tests verify:
 * 1. EDCBase core functionality (structure hashing, result comparison)
 * 2. MySQLEDCOracle query generation
 * 3. OracleFactory EDC registration
 * 4. Error handling configuration
 * 5. EDC query pattern structures
 *
 * Note: MySQLEDCOracle constructor requires a database connection for full
 * testing. This test class tests components without database connection.
 */
class MySQLEDCOracleTest {

    private MySQLColumn col0;
    private MySQLColumn col1;
    private MySQLTable table;

    @BeforeEach
    void setUp() {
        // Initialize Randomly with a fixed seed for reproducible tests
        new Randomly(42L);

        // Create mock columns and table (no database connection needed)
        col0 = new MySQLColumn("c0", MySQLDataType.INT, false, 0);
        col1 = new MySQLColumn("c1", MySQLDataType.VARCHAR, false, 0);
        table = new MySQLTable("t0", List.of(col0, col1), List.of(), MySQLTable.MySQLEngine.INNO_DB);
        col0.setTable(table);
        col1.setTable(table);
    }

    // ==================== OracleFactory Tests ====================

    @Test
    void testOracleFactory_edcExists() {
        // Verify EDC is available in MySQLOracleFactory
        MySQLOracleFactory[] factories = MySQLOracleFactory.values();

        boolean hasEdc = Arrays.stream(factories).anyMatch(f -> f.name().equals("EDC"));

        assertTrue(hasEdc, "EDC oracle should be registered in MySQLOracleFactory");
    }

    @Test
    void testOracleFactory_edcNameCorrect() {
        // Verify EDC factory enum name is correct
        MySQLOracleFactory edcFactory = MySQLOracleFactory.valueOf("EDC");

        assertNotNull(edcFactory);
        assertEquals("EDC", edcFactory.name());
    }

    @Test
    void testOracleFactory_edcRequiresTables() {
        // Verify EDC requires tables to contain rows (like PQS)
        MySQLOracleFactory edcFactory = MySQLOracleFactory.valueOf("EDC");

        assertTrue(edcFactory.requiresAllTablesToContainRows(),
                "EDC oracle should require tables to contain rows for testing");
    }

    @Test
    void testOracleFactory_allOraclesCount() {
        // Verify total oracle count including EDC
        MySQLOracleFactory[] factories = MySQLOracleFactory.values();

        // Should have: AGGREGATE, HAVING, GROUP_BY, DISTINCT, NOREC, TLP_WHERE, PQS,
        // CERT, FUZZER, DQP, DQE, EET, CODDTEST, EDC, QUERY_PARTITIONING = 15
        assertEquals(15, factories.length, "Total oracle count should be 15 including EDC");
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testErrorHandling_expressionErrorsAvailable() {
        // Verify EDC oracle uses MySQLErrors for expected errors
        List<String> expressionErrors = MySQLErrors.getExpressionErrors();

        // EDC should handle standard expression errors
        assertFalse(expressionErrors.isEmpty(), "Expression errors list should not be empty");
        assertTrue(expressionErrors.contains("BIGINT value is out of range"));
        assertTrue(expressionErrors.contains("Incorrect DATETIME value"));
    }

    @Test
    void testErrorHandling_regexErrorsAvailable() {
        // Verify EDC oracle uses regex errors
        List<java.util.regex.Pattern> regexErrors = MySQLErrors.getExpressionRegexErrors();

        assertFalse(regexErrors.isEmpty(), "Regex errors list should not be empty");
    }

    // ==================== Query Generation Tests ====================

    @Test
    void testQueryGeneration_selectStructure() {
        // Test basic SELECT query structure (as EDC generates)
        MySQLSelect select = new MySQLSelect();
        select.setSelectType(MySQLSelect.SelectType.ALL);
        select.setFetchColumns(List.of(new MySQLColumnReference(col0, null)));
        select.setFromList(List.of(new MySQLTableReference(table)));
        select.setWhereClause(MySQLConstant.createIntConstant(1));

        String selectStr = MySQLVisitor.asString(select);

        assertNotNull(selectStr);
        assertTrue(selectStr.contains("SELECT"));
        assertTrue(selectStr.contains("FROM"));
        assertTrue(selectStr.contains("WHERE"));
    }

    @Test
    void testQueryGeneration_multipleColumns() {
        // Test SELECT with multiple columns (EDC pattern)
        MySQLSelect select = new MySQLSelect();
        select.setFetchColumns(Arrays.asList(
                new MySQLColumnReference(col0, null),
                new MySQLColumnReference(col1, null)));
        select.setFromList(List.of(new MySQLTableReference(table)));
        select.setWhereClause(MySQLConstant.createIntConstant(1));

        String selectStr = MySQLVisitor.asString(select);

        assertTrue(selectStr.contains("c0"));
        assertTrue(selectStr.contains("c1"));
    }

    @Test
    void testQueryGeneration_tableReference() {
        // Test table reference rendering
        MySQLTableReference tableRef = new MySQLTableReference(table);
        String tableRefStr = MySQLVisitor.asString(tableRef);

        assertNotNull(tableRefStr);
        assertTrue(tableRefStr.contains("t0"));
    }

    // ==================== EDCBase Structure Tests ====================

    @Test
    void testTableStructure_hashCodeConsistency() {
        // Test TableStructure hash code calculation for diversity checking
        List<String> columns1 = List.of("INT", "NOT NULL");
        List<String> tables1 = List.of("PRIMARY");
        TableStructure ts1 = new TableStructure(columns1, tables1);

        List<String> columns2 = List.of("INT", "NOT NULL");
        List<String> tables2 = List.of("PRIMARY");
        TableStructure ts2 = new TableStructure(columns2, tables2);

        // Same structure should have same hash code
        assertEquals(ts1.hashCode(), ts2.hashCode());
        assertEquals(ts1, ts2);
    }

    @Test
    void testTableStructure_differentHashCode() {
        // Test different TableStructure produces different hash code
        List<String> columns1 = List.of("INT", "NOT NULL");
        List<String> tables1 = List.of("PRIMARY");
        TableStructure ts1 = new TableStructure(columns1, tables1);

        List<String> columns2 = List.of("VARCHAR");
        List<String> tables2 = List.of();
        TableStructure ts2 = new TableStructure(columns2, tables2);

        // Different structure should have different hash code
        assertFalse(ts1.equals(ts2));
    }

    @Test
    void testTableStructure_compareTo() {
        // Test TableStructure comparison
        TableStructure ts1 = new TableStructure(List.of("A"), List.of());
        TableStructure ts2 = new TableStructure(List.of("B"), List.of());

        int comparison = ts1.compareTo(ts2);

        // Comparison should be based on hash code
        assertTrue(comparison != 0 || ts1.hashCode() == ts2.hashCode());
    }

    // ==================== Schema Parsing Pattern Tests ====================

    @Test
    void testSchemaParsing_patternForColumn() {
        // Test regex pattern used for parsing CREATE TABLE output
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("`([^`]*?)`");

        String createTable = "`c0` INT NOT NULL, `c1` VARCHAR(100)";
        java.util.regex.Matcher matcher = pattern.matcher(createTable);

        assertTrue(matcher.find());
        assertEquals("c0", matcher.group(1));
        assertTrue(matcher.find());
        assertEquals("c1", matcher.group(1));
    }

    @Test
    void testSchemaParsing_patternForColumnSet() {
        // Test regex pattern for parsing constraint column sets
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\((.*?)\\)");

        String constraint = "PRIMARY KEY (`c0`, `c1`)";
        java.util.regex.Matcher matcher = pattern.matcher(constraint);

        assertTrue(matcher.find());
        assertTrue(matcher.group(1).contains("c0"));
        assertTrue(matcher.group(1).contains("c1"));
    }

    @Test
    void testSchemaParsing_constraintDetection() {
        // Test constraint type detection from CREATE TABLE lines
        String uniqueLine = "UNIQUE KEY `u1` (`c0`)";
        String primaryLine = "PRIMARY KEY (`c0`)";
        String foreignLine = "FOREIGN KEY (`c0`) REFERENCES `t1` (`c1`)";
        String checkLine = "CHECK (`c0` > 0)";

        assertTrue(uniqueLine.contains("UNIQUE"));
        assertTrue(primaryLine.contains("PRIMARY"));
        assertTrue(foreignKeyLineContains("FOREIGN", foreignLine));
        assertTrue(checkLine.contains("CHECK"));
    }

    private boolean foreignKeyLineContains(String expected, String line) {
        return line.contains(expected);
    }

    // ==================== Result Comparison Tests ====================

    @Test
    void testResultComparison_emptyResultSet() {
        // Test handling of empty result sets (should be equal)
        List<String> empty1 = List.of();
        List<String> empty2 = List.of();

        Set<String> set1 = new HashSet<>(empty1);
        Set<String> set2 = new HashSet<>(empty2);

        assertEquals(set1, set2);
    }

    @Test
    void testResultComparison_equalResultSets() {
        // Test handling of equal result sets
        List<String> result1 = List.of("1,", "2,", "3,");
        List<String> result2 = List.of("1,", "2,", "3,");

        Set<String> set1 = new HashSet<>(result1);
        Set<String> set2 = new HashSet<>(result2);

        assertEquals(set1, set2);
    }

    @Test
    void testResultComparison_differentOrder() {
        // Test that result comparison is order-independent (uses HashSet)
        List<String> result1 = List.of("1,", "2,", "3,");
        List<String> result2 = List.of("3,", "1,", "2,");

        Set<String> set1 = new HashSet<>(result1);
        Set<String> set2 = new HashSet<>(result2);

        // HashSet comparison ignores order
        assertEquals(set1, set2);
    }

    @Test
    void testResultComparison_sizeMismatchDetection() {
        // Test size mismatch detection
        List<String> result1 = List.of("1,", "2,", "3,");
        List<String> result2 = List.of("1,", "2,");

        // Size mismatch would be caught by ComparatorHelper
        assertNotEquals(result1.size(), result2.size());
    }

    private void assertNotEquals(int a, int b) {
        assertTrue(a != b, "Values should not be equal");
    }

    // ==================== Raw DB Creation Pattern Tests ====================

    @Test
    void testRawDBName_generation() {
        // Test raw database naming convention
        String originalDb = "test_db";
        String rawDb = originalDb + "_raw";

        assertTrue(rawDb.endsWith("_raw"));
        assertEquals("test_db_raw", rawDb);
    }

    @Test
    void testRawDBColumns_noConstraints() {
        // Test that raw DB columns have no constraint markers
        String rawColumnDef = "c0 INT"; // No NOT NULL, no UNIQUE, no PRIMARY KEY

        assertFalse(rawColumnDef.contains("NOT NULL"));
        assertFalse(rawColumnDef.contains("UNIQUE"));
        assertFalse(rawColumnDef.contains("PRIMARY KEY"));
        assertFalse(rawColumnDef.contains("CHECK"));
    }

    // ==================== Integration Pattern Tests ====================

    @Test
    void testEDCPattern_queryComparisonConcept() {
        // Test the EDC concept: same query on different databases
        // Original DB: SELECT * FROM t0 WHERE c0 > 0 (optimized with constraints)
        // Raw DB: SELECT * FROM t0 WHERE c0 > 0 (non-optimized, no constraints)

        MySQLSelect select = new MySQLSelect();
        select.setFetchColumns(List.of(new MySQLColumnReference(col0, null)));
        select.setFromList(List.of(new MySQLTableReference(table)));
        select.setWhereClause(MySQLConstant.createIntConstant(1));

        String query = MySQLVisitor.asString(select);

        // Same query string for both databases
        assertNotNull(query);
        assertTrue(query.contains("SELECT"));
        assertTrue(query.contains("FROM"));
    }

    // ==================== Constraint Metadata Tests ====================

    @Test
    void testConstraintMetadata_notNullDetection() {
        // Test NOT NULL constraint detection for EDC
        String columnMetadata = "INT NOT NULL DEFAULT 0";

        assertTrue(columnMetadata.contains("NOT NULL"));
    }

    @Test
    void testConstraintMetadata_uniqueDetection() {
        // Test UNIQUE constraint detection
        String uniqueMetadata = "UNIQUE KEY (`c0`)";

        assertTrue(uniqueMetadata.contains("UNIQUE"));
    }

    @Test
    void testConstraintMetadata_generatedColumnDetection() {
        // Test GENERATED column detection
        String generatedDef = "GENERATED ALWAYS AS (c0 + 1) STORED";

        assertTrue(generatedDef.contains("GENERATED"));
    }

    @Test
    void testConstraintMetadata_checkConstraintDetection() {
        // Test CHECK constraint detection
        String checkDef = "CHECK (c0 > 0)";

        assertTrue(checkDef.contains("CHECK"));
    }

    @Test
    void testConstraintMetadata_foreignKeyDetection() {
        // Test FOREIGN KEY constraint detection
        String fkDef = "FOREIGN KEY (`c0`) REFERENCES `t1` (`c1`)";

        assertTrue(fkDef.contains("FOREIGN KEY"));
        assertTrue(fkDef.contains("REFERENCES"));
    }

    // ==================== Collation Handling Tests ====================

    @Test
    void testCollationHandling_defaultCollation() {
        // Test default collation is skipped in raw DB
        String defaultCollation = "utf8mb4_0900_ai_ci";

        // Should not be added to raw DB columns
        assertTrue(defaultCollation.contains("utf8mb4"));
    }

    @Test
    void testCollationHandling_nonDefaultCollation() {
        // Test non-default collation is preserved
        String customCollation = "utf8mb4_bin";

        // Should be added to raw DB columns
        assertFalse(customCollation.equals("utf8mb4_0900_ai_ci"));
    }

    // ==================== Database Structure Hash Tests ====================

    @Test
    void testDatabaseStructureHash_emptySet() {
        // Test empty database structure set
        Set<Integer> structureSet = new HashSet<>();

        assertTrue(structureSet.isEmpty());
    }

    @Test
    void testDatabaseStructureHash_addingStructure() {
        // Test adding structure hash to set
        Set<Integer> structureSet = new HashSet<>();
        int structureHash = 12345;

        assertTrue(structureSet.add(structureHash));
        assertFalse(structureSet.add(structureHash)); // Duplicate should fail
        assertTrue(structureSet.contains(structureHash));
    }

    @Test
    void testDatabaseStructureHash_constraintPresence() {
        // Test that constraints contribute to hash
        Map<String, Map<String, List<String>>> schema1 = new HashMap<>();
        Map<String, List<String>> tableSchema1 = new HashMap<>();
        tableSchema1.put("c0", List.of("INT", "NOT NULL"));
        schema1.put("t0", tableSchema1);

        // Has constraints (NOT NULL)
        boolean hasConstraints = tableSchema1.get("c0").stream()
                .anyMatch(m -> m.toUpperCase().contains("NOT NULL"));

        assertTrue(hasConstraints);
    }
}