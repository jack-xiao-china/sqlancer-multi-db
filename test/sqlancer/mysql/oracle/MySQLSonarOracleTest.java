package sqlancer.mysql.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sqlancer.Randomly;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLManuelPredicate;
import sqlancer.mysql.ast.MySQLPostfixText;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLWindowFunction;

/**
 * Unit tests for MySQLSonarOracle.
 *
 * Tests verify: 1. AST node rendering for SONAR expressions (PostfixText, ManuelPredicate, WindowFunction) 2. Visitor
 * handles SONAR-specific AST nodes 3. OracleFactory SONAR registration 4. Expected errors configuration 5. SONAR query
 * pattern structures
 *
 * Note: ExpressionGenerator tests are in MySQLExpressionGeneratorSonarTest. MySQLSonarOracle constructor requires a
 * database connection, so we test the components it uses without instantiating the oracle.
 */
class MySQLSonarOracleTest {

    private MySQLColumn col;
    private MySQLTable table;

    @BeforeEach
    void setUp() {
        // Initialize Randomly with a fixed seed for reproducible tests
        new Randomly(42L);

        // Create mock column and table (no database connection needed)
        col = new MySQLColumn("c", MySQLDataType.INT, false, 0);
        table = new MySQLTable("t", List.of(col), List.of(), MySQLTable.MySQLEngine.INNO_DB);
        col.setTable(table);
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testErrorHandling_containsSonarSpecificErrors() {
        // Verify SONAR-specific expected errors by checking MySQLErrors
        List<String> expressionErrors = MySQLErrors.getExpressionErrors();

        // SONAR oracle uses all expression errors plus additional errors
        assertTrue(expressionErrors.contains("Incorrect DATETIME value"));
        assertTrue(expressionErrors.contains("Incorrect DATE value"));
        assertTrue(expressionErrors.contains("Incorrect TIME value"));
        assertTrue(expressionErrors.contains("Cannot convert string"));
    }

    @Test
    void testErrorHandling_sonarAdditionalErrors() {
        // SONAR oracle adds these additional errors in constructor:
        // - "Data truncation"
        // - "Incorrect DATETIME value" (also in MySQLErrors)
        // - "Incorrect DATE value" (also in MySQLErrors)
        // - "Invalid use of group function"
        // - "incompatible with sql_mode=only_full_group_by"
        // - "Cannot convert string" (also in MySQLErrors)

        // Verify the base errors list contains most SONAR errors
        assertTrue(MySQLErrors.getExpressionErrors().contains("Incorrect DATETIME value"));
        assertTrue(MySQLErrors.getExpressionErrors().contains("Incorrect DATE value"));
        assertTrue(MySQLErrors.getExpressionErrors().contains("Cannot convert string"));
    }

    // ==================== Visitor Tests ====================

    @Test
    void testVisitor_handlesPostfixText() {
        // Test MySQLPostfixText rendering (used for fetch column aliases in SONAR)
        MySQLPostfixText postfix = new MySQLPostfixText(MySQLConstant.createIntConstant(1), "alias");
        String postfixStr = MySQLVisitor.asString(postfix);

        assertNotNull(postfixStr);
        assertTrue(postfixStr.contains("AS"));
        assertTrue(postfixStr.contains("alias"));
    }

    @Test
    void testVisitor_handlesManuelPredicate() {
        // Test MySQLManuelPredicate rendering (used for flag expressions in SONAR)
        MySQLManuelPredicate predicate = new MySQLManuelPredicate("flag=1");
        String predicateStr = MySQLVisitor.asString(predicate);

        assertNotNull(predicateStr);
        assertEquals("flag=1", predicateStr);
    }

    @Test
    void testVisitor_handlesManuelPredicateComplexExpression() {
        // Test complex flag expression like "(expr) IS TRUE AS flag"
        MySQLManuelPredicate complexPredicate = new MySQLManuelPredicate("(1 > 0) IS TRUE AS flag");
        String complexStr = MySQLVisitor.asString(complexPredicate);

        assertNotNull(complexStr);
        assertTrue(complexStr.contains("IS TRUE"));
        assertTrue(complexStr.contains("flag"));
    }

    @Test
    void testVisitor_handlesWindowFunction() {
        // Test window function rendering (used in SONAR fetch columns)
        MySQLWindowFunction window = new MySQLWindowFunction(MySQLWindowFunction.MySQLFunction.ROW_NUMBER, null, // zero-arg
                                                                                                                 // function
                MySQLColumnReference.create(col, null));

        String windowStr = MySQLVisitor.asString(window);

        assertNotNull(windowStr);
        assertTrue(windowStr.contains("ROW_NUMBER"));
        assertTrue(windowStr.contains("OVER"));
        assertTrue(windowStr.contains("PARTITION BY"));
    }

    @Test
    void testWindowFunctionNames_correct() {
        // Verify window function names are correct
        assertEquals("ROW_NUMBER", MySQLWindowFunction.MySQLFunction.ROW_NUMBER.getName());
        assertEquals("RANK", MySQLWindowFunction.MySQLFunction.RANK.getName());
        assertEquals("SUM", MySQLWindowFunction.MySQLFunction.SUM.getName());
        assertEquals("AVG", MySQLWindowFunction.MySQLFunction.AVG.getName());
    }

    // ==================== AST Structure Tests ====================

    @Test
    void testSelect_astRendering() {
        // Test SELECT AST rendering with SONAR components
        MySQLSelect select = new MySQLSelect();
        MySQLPostfixText fetchCol = new MySQLPostfixText(MySQLConstant.createIntConstant(1), "f1");
        select.setFetchColumns(List.of(fetchCol));
        select.setFromList(List.of(new MySQLTableReference(table)));
        select.setJoinList(List.of());
        select.setWhereClause(new MySQLManuelPredicate("f1 > 0"));

        String selectStr = MySQLVisitor.asString(select);

        assertNotNull(selectStr);
        assertTrue(selectStr.contains("SELECT"));
        assertTrue(selectStr.contains("FROM"));
        assertTrue(selectStr.contains("WHERE"));
    }

    @Test
    void testSelect_nestedQueryStructure() {
        // Test nested SELECT structure (as used in SONAR's optimized query)
        MySQLSelect innerSelect = new MySQLSelect();
        MySQLPostfixText innerCol = new MySQLPostfixText(MySQLConstant.createIntConstant(1), "f1");
        innerSelect.setFetchColumns(List.of(innerCol));
        innerSelect.setFromList(List.of(new MySQLTableReference(table)));
        innerSelect.setJoinList(List.of());

        MySQLSelect outerSelect = new MySQLSelect();
        outerSelect.setFetchColumns(List.of(new MySQLManuelPredicate("f1")));
        outerSelect.setFromList(List.of(innerSelect));
        outerSelect.setWhereClause(new MySQLManuelPredicate("f1 > 0"));

        String nestedStr = MySQLVisitor.asString(outerSelect);

        assertNotNull(nestedStr);
        // Outer query should wrap inner query
        assertTrue(nestedStr.contains("SELECT"));
        assertTrue(nestedStr.contains("FROM"));
    }

    @Test
    void testFlagExpressionGeneration() {
        // Test the pattern of flag expression used in SONAR unoptimized query
        MySQLExpression innerExpr = MySQLConstant.createIntConstant(1);
        String flagPattern = "(" + MySQLVisitor.asString(innerExpr) + ") IS TRUE AS flag";

        MySQLManuelPredicate flagPredicate = new MySQLManuelPredicate(flagPattern);
        String flagStr = MySQLVisitor.asString(flagPredicate);

        assertTrue(flagStr.contains("IS TRUE"));
        assertTrue(flagStr.contains("flag"));
    }

    @Test
    void testMultipleFetchColumns_withFlag() {
        // Test multiple fetch columns with flag column (SONAR pattern)
        MySQLSelect select = new MySQLSelect();
        MySQLPostfixText mainCol = new MySQLPostfixText(MySQLConstant.createIntConstant(1), "f1");
        MySQLManuelPredicate flagCol = new MySQLManuelPredicate("(f1 > 0) IS TRUE AS flag");
        select.setFetchColumns(Arrays.asList(mainCol, flagCol));
        select.setFromList(List.of(new MySQLTableReference(table)));
        select.setJoinList(List.of());

        String selectStr = MySQLVisitor.asString(select);

        assertTrue(selectStr.contains("f1"));
        assertTrue(selectStr.contains("flag"));
    }

    // ==================== OracleFactory Tests ====================

    @Test
    void testOracleFactory_sonarExists() {
        // Verify SONAR is available in MySQLOracleFactory
        sqlancer.mysql.MySQLOracleFactory[] factories = sqlancer.mysql.MySQLOracleFactory.values();

        boolean hasSonar = Arrays.stream(factories).anyMatch(f -> f.name().equals("SONAR"));

        assertTrue(hasSonar, "SONAR oracle should be registered in MySQLOracleFactory");
    }

    @Test
    void testOracleFactory_sonarNameCorrect() {
        // Verify SONAR factory enum name is correct
        sqlancer.mysql.MySQLOracleFactory sonarFactory = sqlancer.mysql.MySQLOracleFactory.valueOf("SONAR");

        assertNotNull(sonarFactory);
        assertEquals("SONAR", sonarFactory.name());
    }

    // ==================== Integration Pattern Tests ====================

    @Test
    void testSonarPattern_optimizedQueryStructure() {
        // Verify the optimized query structure used by SONAR:
        // SELECT f1 FROM (SELECT ... AS f1 FROM table) WHERE f1 > condition

        MySQLSelect inner = new MySQLSelect();
        inner.setFetchColumns(List.of(new MySQLPostfixText(MySQLConstant.createIntConstant(1), "f1")));
        inner.setFromList(List.of(new MySQLTableReference(table)));
        inner.setJoinList(List.of());

        MySQLSelect outer = new MySQLSelect();
        outer.setFetchColumns(List.of(new MySQLManuelPredicate("f1")));
        outer.setFromList(List.of(inner));
        outer.setWhereClause(new MySQLManuelPredicate("f1 > 0"));

        String result = MySQLVisitor.asString(outer);

        // Verify structure
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("FROM"));
        assertTrue(result.contains("WHERE"));
        assertTrue(result.contains("f1"));
    }

    @Test
    void testSonarPattern_unoptimizedQueryStructure() {
        // Verify the unoptimized query structure used by SONAR:
        // SELECT f1 FROM (SELECT ..., condition IS TRUE AS flag FROM table) WHERE flag=1

        MySQLSelect inner = new MySQLSelect();
        MySQLPostfixText mainCol = new MySQLPostfixText(MySQLConstant.createIntConstant(1), "f1");
        MySQLManuelPredicate flagCol = new MySQLManuelPredicate("(1 > 0) IS TRUE AS flag");
        inner.setFetchColumns(Arrays.asList(mainCol, flagCol));
        inner.setFromList(List.of(new MySQLTableReference(table)));
        inner.setJoinList(List.of());
        inner.setWhereClause(null); // No WHERE in inner

        MySQLSelect outer = new MySQLSelect();
        outer.setFetchColumns(List.of(new MySQLManuelPredicate("f1")));
        outer.setFromList(List.of(inner));
        outer.setWhereClause(new MySQLManuelPredicate("flag=1"));

        String result = MySQLVisitor.asString(outer);

        // Verify structure
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("FROM"));
        assertTrue(result.contains("WHERE"));
        assertTrue(result.contains("flag=1"));
        assertTrue(result.contains("IS TRUE"));
    }

    @Test
    void testWindowFunction_getArgs() {
        // Verify window function argument handling
        assertEquals(0, MySQLWindowFunction.MySQLFunction.ROW_NUMBER.getArgs());
        assertEquals(0, MySQLWindowFunction.MySQLFunction.RANK.getArgs());
        assertEquals(1, MySQLWindowFunction.MySQLFunction.SUM.getArgs());
        assertEquals(1, MySQLWindowFunction.MySQLFunction.AVG.getArgs());
    }
}