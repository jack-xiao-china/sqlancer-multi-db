package sqlancer.mysql;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;

@Parameters(separators = "=", commandDescription = "MySQL (default port: " + MySQLOptions.DEFAULT_PORT
        + ", default host: " + MySQLOptions.DEFAULT_HOST + ")")
public class MySQLOptions implements DBMSSpecificOptions<MySQLOracleFactory> {

    @Parameter(names = { "--help", "-h" }, description = "Lists all supported options for the MySQL command", help = true, hidden = true)
    public boolean help;

    public enum CODDTestModel {
        RANDOM, EXPRESSION, SUBQUERY;

        public boolean isRandom() { return this == RANDOM; }
        public boolean isExpression() { return this == EXPRESSION; }
        public boolean isSubquery() { return this == SUBQUERY; }
    }

    @Parameter(names = { "--coddtest-model" }, description = "Apply CODDTest on EXPRESSION, SUBQUERY, or RANDOM", hidden = true)
    public CODDTestModel coddTestModel = CODDTestModel.RANDOM;
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3306;

    @Parameter(names = "--oracle", description = "Specifies which test oracle should be used, Options: [AGGREGATE, CERT, CODDTEST, DISTINCT, DQE, DQP, EDC, EET, FUZZER, GROUP_BY, HAVING, NOREC, PQS, QUERY_PARTITIONING, SONAR, TLP_WHERE]")
    public List<MySQLOracleFactory> oracles = Arrays.asList(MySQLOracleFactory.QUERY_PARTITIONING);

    @Parameter(names = "--engines", description = "Comma-separated storage engine names used in CREATE TABLE ENGINE=... (e.g. InnoDB,MyISAM,MyCustomEngine)")
    public String engines;

    // Test feature flags
    @Parameter(names = { "--test-foreign-keys" }, description = "Test foreign key constraints", arity = 1, hidden = true)
    public boolean testForeignKeys = true;

    @Parameter(names = { "--test-check-constraints" }, description = "Allow CHECK constraints in tables", arity = 1, hidden = true)
    public boolean testCheckConstraints = true;

    @Parameter(names = { "--test-generated-columns" }, description = "Test generated columns", arity = 1, hidden = true)
    public boolean testGeneratedColumns = true;

    @Parameter(names = { "--test-collations" }, description = "Test different collations", arity = 1, hidden = true)
    public boolean testCollations = true;

    @Parameter(names = { "--test-functions" }, description = "Allow the generation of functions in expressions", arity = 1, hidden = true)
    public boolean testFunctions = true;

    @Parameter(names = { "--test-joins" }, description = "Allow the generation of JOIN clauses", arity = 1, hidden = true)
    public boolean testJoins = true;

    @Parameter(names = { "--test-in-operator" }, description = "Allow the generation of the IN operator", arity = 1, hidden = true)
    public boolean testIn = true;

    @Parameter(names = { "--test-nulls-first-last" }, description = "Allow NULLS FIRST/NULLS LAST in ordering terms", arity = 1, hidden = true)
    public boolean testNullsFirstLast = true;

    @Parameter(names = { "--test-temp-tables" }, description = "Generate TEMPORARY tables", arity = 1, hidden = true)
    public boolean testTempTables = true;

    @Parameter(names = { "--test-window-functions" }, description = "Allow window functions in queries", arity = 1, hidden = true)
    public boolean testWindowFunctions = true;

    @Parameter(names = { "--test-common-table-expressions" }, description = "Allow Common Table Expressions (WITH clauses)", arity = 1, hidden = true)
    public boolean testCommonTableExpressions = true;

    @Parameter(names = { "--test-json" }, description = "Allow JSON functions and operators", arity = 1, hidden = true)
    public boolean testJSON = true;

    @Parameter(names = { "--test-spatial" }, description = "Allow spatial functions and types", arity = 1, hidden = true)
    public boolean testSpatial = true;

    @Parameter(names = { "--test-full-text-search" }, description = "Allow full-text search features", arity = 1, hidden = true)
    public boolean testFullTextSearch = true;

    @Parameter(names = { "--test-anonymous-columns" }, description = "Allow anonymous columns in INSERT statements", arity = 1, hidden = true)
    public boolean testAnonymousColumns = true;

    @Parameter(names = { "--test-multi-table-delete" }, description = "Allow multi-table DELETE statements", arity = 1, hidden = true)
    public boolean testMultiTableDelete = true;

    @Parameter(names = { "--test-multi-table-update" }, description = "Allow multi-table UPDATE statements", arity = 1, hidden = true)
    public boolean testMultiTableUpdate = true;

    @Parameter(names = { "--test-for-loop" }, description = "Allow FOR loop constructs", arity = 1, hidden = true)
    public boolean testForLoop = true;

    @Parameter(names = { "--test-if-conditions" }, description = "Allow IF conditions", arity = 1, hidden = true)
    public boolean testIfConditions = true;

    @Parameter(names = { "--test-case-expressions" }, description = "Allow CASE expressions", arity = 1, hidden = true)
    public boolean testCaseExpressions = true;

    @Parameter(names = { "--test-cast" }, description = "Allow CAST operations", arity = 1, hidden = true)
    public boolean testCast = true;

    @Parameter(names = { "--test-concat" }, description = "Allow CONCAT function", arity = 1, hidden = true)
    public boolean testConcat = true;

    @Parameter(names = { "--test-math-functions" }, description = "Allow mathematical functions", arity = 1, hidden = true)
    public boolean testMathFunctions = true;

    @Parameter(names = { "--test-string-functions" }, description = "Allow string functions", arity = 1, hidden = true)
    public boolean testStringFunctions = true;

    @Parameter(names = { "--test-aggregate-functions" }, description = "Allow aggregate functions", arity = 1, hidden = true)
    public boolean testAggregateFunctions = true;

    @Parameter(names = { "--test-group-sets" }, description = "Allow GROUP SETS, ROLLUP, CUBE", arity = 1, hidden = true)
    public boolean testGroupSets = true;

    @Parameter(names = { "--test-having" }, description = "Allow HAVING clauses", arity = 1, hidden = true)
    public boolean testHaving = true;

    @Parameter(names = { "--test-distinct" }, description = "Allow DISTINCT in SELECT", arity = 1, hidden = true)
    public boolean testDistinct = true;

    @Parameter(names = { "--test-limit" }, description = "Allow LIMIT clauses", arity = 1, hidden = true)
    public boolean testLimit = true;

    @Parameter(names = { "--test-order-by" }, description = "Allow ORDER BY clauses", arity = 1, hidden = true)
    public boolean testOrderBy = true;

    @Parameter(names = { "--test-group-by" }, description = "Allow GROUP BY clauses", arity = 1, hidden = true)
    public boolean testGroupBy = true;

    @Parameter(names = { "--test-union" }, description = "Allow UNION statements", arity = 1, hidden = true)
    public boolean testUnion = true;

    @Parameter(names = { "--test-views" }, description = "Allow views creation", arity = 1, hidden = true)
    public boolean testViews = true;

    @Parameter(names = { "--test-triggers" }, description = "Allow triggers creation", arity = 1, hidden = true)
    public boolean testTriggers = true;

    @Parameter(names = { "--test-procedures" }, description = "Allow stored procedures", arity = 1, hidden = true)
    public boolean testProcedures = true;

    @Parameter(names = { "--test-events" }, description = "Allow events", arity = 1, hidden = true)
    public boolean testEvents = true;

    @Parameter(names = { "--test-indexes" }, description = "Allow indexes creation", arity = 1, hidden = true)
    public boolean testIndexes = true;

    @Parameter(names = { "--test-primary-keys" }, description = "Allow primary keys", arity = 1, hidden = true)
    public boolean testPrimaryKeys = true;

    @Parameter(names = { "--test-unique-constraints" }, description = "Allow unique constraints", arity = 1, hidden = true)
    public boolean testUniqueConstraints = true;

    @Parameter(names = { "--test-auto-increment" }, description = "Allow AUTO_INCREMENT columns", arity = 1, hidden = true)
    public boolean testAutoIncrement = true;

    @Parameter(names = { "--test-default-values" }, description = "Allow DEFAULT values", arity = 1, hidden = true)
    public boolean testDefaultValues = true;

    @Parameter(names = { "--test-not-null" }, description = "Allow NOT NULL constraints", arity = 1, hidden = true)
    public boolean testNotNull = true;

    @Parameter(names = { "--test-comments" }, description = "Allow table and column comments", arity = 1, hidden = true)
    public boolean testComments = true;

    @Parameter(names = { "--test-unsigned" }, description = "Allow UNSIGNED integer types", arity = 1, hidden = true)
    public boolean testUnsigned = true;

    @Parameter(names = { "--test-zerofill" }, description = "Allow ZEROFILL integer types", arity = 1, hidden = true)
    public boolean testZerofill = true;

    @Parameter(names = { "--test-binary" }, description = "Allow BINARY data types", arity = 1, hidden = true)
    public boolean testBinary = true;

    @Parameter(names = { "--test-varchar" }, description = "Allow VARCHAR data types", arity = 1, hidden = true)
    public boolean testVarchar = true;

    @Parameter(names = { "--test-text" }, description = "Allow TEXT data types", arity = 1, hidden = true)
    public boolean testText = true;

    @Parameter(names = { "--test-integers" }, description = "Allow integer data types", arity = 1, hidden = true)
    public boolean testIntegers = true;

    @Parameter(names = { "--test-floats" }, description = "Allow floating-point data types", arity = 1, hidden = true)
    public boolean testFloats = true;

    @Parameter(names = { "--test-decimals" }, description = "Allow DECIMAL data types", arity = 1, hidden = true)
    public boolean testDecimals = true;

    @Parameter(names = { "--test-dates" }, description = "Allow date and time data types", arity = 1, hidden = true)
    public boolean testDates = true;

    @Parameter(names = { "--test-enums" }, description = "Allow ENUM data types", arity = 1, hidden = true)
    public boolean testEnums = true;

    @Parameter(names = { "--test-sets" }, description = "Allow SET data types", arity = 1, hidden = true)
    public boolean testSets = true;

    @Parameter(names = { "--test-bit" }, description = "Allow BIT data types", arity = 1, hidden = true)
    public boolean testBit = true;

    @Parameter(names = { "--test-boolean" }, description = "Allow BOOLEAN data types", arity = 1, hidden = true)
    public boolean testBoolean = true;

    @Parameter(names = { "--test-json-data-type" }, description = "Allow JSON data type", arity = 1, hidden = true)
    public boolean testJSONDataType = true;

    @Parameter(names = { "--test-array-data-type" }, description = "Allow ARRAY data type (MySQL 8.0+)", arity = 1, hidden = true)
    public boolean testArrayDataType = true;

    // Table and schema options
    @Parameter(names = { "--max-num-tables" }, description = "The maximum number of tables that can be created", hidden = true)
    public int maxNumTables = 10;

    @Parameter(names = { "--max-num-indexes" }, description = "The maximum number of indexes that can be created", hidden = true)
    public int maxNumIndexes = 20;

    @Parameter(names = { "--max-num-columns" }, description = "The maximum number of columns per table", hidden = true)
    public int maxNumColumns = 20;

    @Parameter(names = { "--max-num-constraints" }, description = "The maximum number of constraints per table", hidden = true)
    public int maxNumConstraints = 10;

    // Query options
    @Parameter(names = { "--max-query-length" }, description = "The maximum length of generated queries in characters", hidden = true)
    public int maxQueryLength = 1000;

    @Parameter(names = { "--max-subquery-depth" }, description = "The maximum depth of nested subqueries", hidden = true)
    public int maxSubqueryDepth = 5;

    @Parameter(names = { "--max-join-clauses" }, description = "The maximum number of JOIN clauses in a query", hidden = true)
    public int maxJoinClauses = 5;

    @Parameter(names = { "--max-group-by-clauses" }, description = "The maximum number of GROUP BY clauses", hidden = true)
    public int maxGroupByClauses = 5;

    @Parameter(names = { "--max-order-by-clauses" }, description = "The maximum number of ORDER BY clauses", hidden = true)
    public int maxOrderByClauses = 5;

    @Parameter(names = { "--max-having-clauses" }, description = "The maximum number of HAVING clauses", hidden = true)
    public int maxHavingClauses = 5;

    @Parameter(names = { "--max-union-clauses" }, description = "The maximum number of UNION clauses", hidden = true)
    public int maxUnionClauses = 3;

    @Parameter(names = { "--max-limit-clauses" }, description = "The maximum number of LIMIT clauses", hidden = true)
    public int maxLimitClauses = 3;

    // Other options
    @Parameter(names = { "--test-log-queries" }, description = "Log all generated queries", arity = 1, hidden = true)
    public boolean testLogQueries = false;

    @Parameter(names = { "--test-random-order" }, description = "Generate tables and columns in random order", arity = 1, hidden = true)
    public boolean testRandomOrder = false;

    @Parameter(names = { "--test-explain" }, description = "Generate EXPLAIN statements", arity = 1, hidden = true)
    public boolean testExplain = false;

    @Parameter(names = { "--test-show" }, description = "Generate SHOW statements", arity = 1, hidden = true)
    public boolean testShow = false;

    @Parameter(names = { "--test-describe" }, description = "Generate DESCRIBE statements", arity = 1, hidden = true)
    public boolean testDescribe = false;

    @Parameter(names = { "--test-use" }, description = "Generate USE statements", arity = 1, hidden = true)
    public boolean testUse = false;

    @Parameter(names = { "--test-create-database" }, description = "Generate CREATE DATABASE statements", arity = 1, hidden = true)
    public boolean testCreateDatabase = false;

    @Parameter(names = { "--test-drop-database" }, description = "Generate DROP DATABASE statements", arity = 1, hidden = true)
    public boolean testDropDatabase = false;

    @Parameter(names = { "--test-create-table" }, description = "Generate CREATE TABLE statements", arity = 1, hidden = true)
    public boolean testCreateTable = true;

    @Parameter(names = { "--test-drop-table" }, description = "Generate DROP TABLE statements", arity = 1, hidden = true)
    public boolean testDropTable = true;

    @Parameter(names = { "--test-alter-table" }, description = "Generate ALTER TABLE statements", arity = 1, hidden = true)
    public boolean testAlterTable = true;

    @Parameter(names = { "--test-truncate-table" }, description = "Generate TRUNCATE TABLE statements", arity = 1, hidden = true)
    public boolean testTruncateTable = true;

    @Parameter(names = { "--test-create-view" }, description = "Generate CREATE VIEW statements", arity = 1, hidden = true)
    public boolean testCreateView = true;

    @Parameter(names = { "--test-drop-view" }, description = "Generate DROP VIEW statements", arity = 1, hidden = true)
    public boolean testDropView = true;

    @Parameter(names = { "--test-create-index" }, description = "Generate CREATE INDEX statements", arity = 1, hidden = true)
    public boolean testCreateIndex = true;

    @Parameter(names = { "--test-drop-index" }, description = "Generate DROP INDEX statements", arity = 1, hidden = true)
    public boolean testDropIndex = true;

    @Parameter(names = { "--test-create-procedure" }, description = "Generate CREATE PROCEDURE statements", arity = 1, hidden = true)
    public boolean testCreateProcedure = true;

    @Parameter(names = { "--test-drop-procedure" }, description = "Generate DROP PROCEDURE statements", arity = 1, hidden = true)
    public boolean testDropProcedure = true;

    @Parameter(names = { "--test-create-trigger" }, description = "Generate CREATE TRIGGER statements", arity = 1, hidden = true)
    public boolean testCreateTrigger = true;

    @Parameter(names = { "--test-drop-trigger" }, description = "Generate DROP TRIGGER statements", arity = 1, hidden = true)
    public boolean testDropTrigger = true;

    @Parameter(names = { "--test-create-event" }, description = "Generate CREATE EVENT statements", arity = 1, hidden = true)
    public boolean testCreateEvent = true;

    @Parameter(names = { "--test-drop-event" }, description = "Generate DROP EVENT statements", arity = 1, hidden = true)
    public boolean testDropEvent = true;

    @Override
    public List<MySQLOracleFactory> getTestOracleFactory() {
        return oracles;
    }

    public boolean isHelp() {
        return help;
    }

    public List<String> getSpecifiedEngines() {
        if (engines == null || engines.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(engines.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    // Helper methods to check if specific features are enabled
    public boolean testForeignKeys() {
        return testForeignKeys;
    }

    public boolean testCheckConstraints() {
        return testCheckConstraints;
    }

    public boolean testGeneratedColumns() {
        return testGeneratedColumns;
    }

    public boolean testCollations() {
        return testCollations;
    }

    public boolean testFunctions() {
        return testFunctions;
    }

    public boolean testJoins() {
        return testJoins;
    }

    public boolean testIn() {
        return testIn;
    }

    public boolean testNullsFirstLast() {
        return testNullsFirstLast;
    }

    public boolean testTempTables() {
        return testTempTables;
    }

    public boolean testWindowFunctions() {
        return testWindowFunctions;
    }

    public boolean testCommonTableExpressions() {
        return testCommonTableExpressions;
    }

    public boolean testJSON() {
        return testJSON;
    }

    public boolean testSpatial() {
        return testSpatial;
    }

    public boolean testFullTextSearch() {
        return testFullTextSearch;
    }

    public boolean testAnonymousColumns() {
        return testAnonymousColumns;
    }

    public boolean testMultiTableDelete() {
        return testMultiTableDelete;
    }

    public boolean testMultiTableUpdate() {
        return testMultiTableUpdate;
    }

    public boolean testForLoop() {
        return testForLoop;
    }

    public boolean testIfConditions() {
        return testIfConditions;
    }

    public boolean testCaseExpressions() {
        return testCaseExpressions;
    }

    public boolean testCast() {
        return testCast;
    }

    public boolean testConcat() {
        return testConcat;
    }

    public boolean testMathFunctions() {
        return testMathFunctions;
    }

    public boolean testStringFunctions() {
        return testStringFunctions;
    }

    public boolean testAggregateFunctions() {
        return testAggregateFunctions;
    }

    public boolean testGroupSets() {
        return testGroupSets;
    }

    public boolean testHaving() {
        return testHaving;
    }

    public boolean testDistinct() {
        return testDistinct;
    }

    public boolean testLimit() {
        return testLimit;
    }

    public boolean testOrderBy() {
        return testOrderBy;
    }

    public boolean testGroupBy() {
        return testGroupBy;
    }

    public boolean testUnion() {
        return testUnion;
    }

    public boolean testViews() {
        return testViews;
    }

    public boolean testTriggers() {
        return testTriggers;
    }

    public boolean testProcedures() {
        return testProcedures;
    }

    public boolean testEvents() {
        return testEvents;
    }

    public boolean testIndexes() {
        return testIndexes;
    }

    public boolean testPrimaryKeys() {
        return testPrimaryKeys;
    }

    public boolean testUniqueConstraints() {
        return testUniqueConstraints;
    }

    public boolean testAutoIncrement() {
        return testAutoIncrement;
    }

    public boolean testDefaultValues() {
        return testDefaultValues;
    }

    public boolean testNotNull() {
        return testNotNull;
    }

    public boolean testComments() {
        return testComments;
    }

    public boolean testUnsigned() {
        return testUnsigned;
    }

    public boolean testZerofill() {
        return testZerofill;
    }

    public boolean testBinary() {
        return testBinary;
    }

    public boolean testVarchar() {
        return testVarchar;
    }

    public boolean testText() {
        return testText;
    }

    public boolean testIntegers() {
        return testIntegers;
    }

    public boolean testFloats() {
        return testFloats;
    }

    public boolean testDecimals() {
        return testDecimals;
    }

    public boolean testDates() {
        return testDates;
    }

    public boolean testEnums() {
        return testEnums;
    }

    public boolean testSets() {
        return testSets;
    }

    public boolean testBit() {
        return testBit;
    }

    public boolean testBoolean() {
        return testBoolean;
    }

    public boolean testJSONDataType() {
        return testJSONDataType;
    }

    public boolean testArrayDataType() {
        return testArrayDataType;
    }

}
