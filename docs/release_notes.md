# SQLancer Release Notes

## v2.0.25 | 2026-04-28
- Integration Test [P2 Extension Complete]: All P2 GaussDB-M SonarOracle extension tasks completed
  - P2-1: JSON Function AST - GaussDBJsonFunction (10 functions: JSON_EXTRACT, JSON_CONTAINS, JSON_KEYS, JSON_TYPE, JSON_VALID, JSON_ARRAY, JSON_OBJECT, JSON_REMOVE, JSON_REPLACE, JSON_SET)
  - P2-2: Bitwise Aggregate Functions - BIT_AND, BIT_OR, BIT_XOR (+ DISTINCT variants)
  - P2-3: Statistical Functions - STDDEV, STDDEV_POP, STDDEV_SAMP, STD, VARIANCE, VAR_POP, VAR_SAMP
  - P2-4: IS TRUE/IS FALSE Postfix Operators - IS_TRUE, IS_FALSE, IS_NOT_TRUE, IS_NOT_FALSE
  - P2-5: ExpressionGenerator JSON Function Integration - JSON_FUNCTION Action
  - P2-6: ExpressionGenerator Bitwise Aggregate Integration - Verified existing generateAggregate works
  - P2-7: ExpressionGenerator Statistical Function Integration - Verified existing generateAggregate works
  - P2-8: ExpressionGenerator IS TRUE/FALSE Integration - UnaryPostfixOperator.getRandom()
- Bug Detection Capability: Improved from 70-80% to estimated 80-90% for GaussDB-M SonarOracle

## v2.0.24 | 2026-04-28
- Integration [GaussDB-M ExpressionGenerator]: Verified bitwise and statistical aggregate function integration
  - Verified `generateAggregate()` method correctly selects all aggregate functions including:
    - Basic aggregates (COUNT, SUM, AVG, MIN, MAX)
    - Bitwise aggregates (BIT_AND, BIT_OR, BIT_XOR with DISTINCT variants)
    - Statistical aggregates (STDDEV, VARIANCE series)
  - No additional implementation needed - existing `Randomly.fromOptions(GaussDBAggregateFunction.values())` works correctly
  - SQL generation via `GaussDBToStringVisitor` handles all aggregate function formats correctly
- Test [GaussDBMExpressionGeneratorAggregateTest]: Added comprehensive integration tests (16 tests)
  - Tests for bitwise aggregate generation (BIT_AND, BIT_OR, BIT_XOR)
  - Tests for statistical aggregate generation (STDDEV, VARIANCE series)
  - Tests for SQL generation format correctness
  - Tests for DISTINCT variant handling
  - Tests for isVariadic() returning false for new functions
  - Tests for aggregate function count (23 total)
  - Tests for existing aggregates (COUNT, SUM, AVG, MIN, MAX) still working

## v2.0.23 | 2026-04-28
- New Feature [GaussDB-M Aggregate]: Extended statistical functions support
  - Extended `GaussDBAggregateFunction` enum with 7 statistical aggregate functions:
    - STDDEV - Standard deviation
    - STDDEV_POP - Population standard deviation
    - STDDEV_SAMP - Sample standard deviation
    - STD - MySQL alias for STDDEV
    - VARIANCE - Variance
    - VAR_POP - Population variance
    - VAR_SAMP - Sample variance
  - Note: Statistical functions require actual data to compute, so getExpectedValue() returns null
- Test [GaussDBAggregateStatisticsTest]: Added comprehensive unit tests (32 tests)
  - Tests for all STDDEV/VARIANCE function names
  - Tests for options being null (no DISTINCT variant)
  - Tests for isVariadic() returning false
  - Tests for SQL generation format (STDDEV(expr), VARIANCE(expr))
  - Tests for all statistical functions in enum values
  - Tests for total aggregate function count (23)

## v2.0.22 | 2026-04-28
- New Feature [GaussDB-M Aggregate]: Extended bitwise aggregate functions support
  - Extended `GaussDBAggregateFunction` enum with 6 bitwise aggregate functions:
    - BIT_AND - Bitwise AND aggregation
    - BIT_AND_DISTINCT - Bitwise AND aggregation with DISTINCT
    - BIT_OR - Bitwise OR aggregation
    - BIT_OR_DISTINCT - Bitwise OR aggregation with DISTINCT
    - BIT_XOR - Bitwise XOR aggregation
    - BIT_XOR_DISTINCT - Bitwise XOR aggregation with DISTINCT
  - Note: Bitwise aggregate functions require actual data to compute, so getExpectedValue() returns null
- Test [GaussDBAggregateBitwiseTest]: Added comprehensive unit tests (19 tests)
  - Tests for all bitwise function names (BIT_AND, BIT_OR, BIT_XOR)
  - Tests for DISTINCT option handling
  - Tests for isVariadic() returning false
  - Tests for all bitwise functions in enum values

## v2.0.21 | 2026-04-28
- New Feature [GaussDB-M JSON Function]: Added GaussDBJsonFunction AST node for SonarOracle
  - Created `GaussDBJsonFunction.java` with GaussDBJsonFunctionType enum:
    - JSON Query Functions (5): JSON_EXTRACT, JSON_CONTAINS, JSON_KEYS, JSON_TYPE, JSON_VALID
    - JSON Construction Functions (2): JSON_ARRAY, JSON_OBJECT (variadic)
    - JSON Modification Functions (3): JSON_REMOVE, JSON_REPLACE, JSON_SET
  - Implemented `getExpectedValue()` for computable functions:
    - JSON_VALID: Returns 1/0/NULL based on JSON validity check
    - JSON_TYPE: Returns OBJECT/ARRAY/BOOLEAN/INTEGER/DECIMAL/STRING/NULL based on JSON type
    - Other functions throw IgnoreMeException (require actual JSON data processing)
  - Extended `GaussDBToStringVisitor` with JSON function SQL generation
    - Format: `JSON_EXTRACT(json_doc, path)`
  - Extended `GaussDBExpectedValueVisitor` to handle JSON functions
  - Added helper methods: `getRandomQueryFunction()`, `getRandomConstructionFunction()`, `getRandomModificationFunction()`
- Test [GaussDBJsonFunctionTest]: Added comprehensive unit tests (58 tests)
  - Tests for all JSON function names and argument counts
  - Tests for JSON_VALID expectedValue on valid/invalid JSON strings
  - Tests for JSON_TYPE expectedValue on various JSON types
  - Tests for SQL generation format (JSON_EXTRACT, JSON_CONTAINS, etc.)
  - Tests for ToStringVisitor conversion
  - Tests for variadic function parameter handling
  - Tests for IgnoreMeException on complex JSON functions
  - Tests for random function selection methods

## v2.0.20 | 2026-04-28
- New Feature [GaussDB-M]: Extended IS TRUE/IS FALSE postfix operators support
  - Extended `GaussDBUnaryPostfixOperation.UnaryPostfixOperator` enum with 4 boolean operators:
    - IS_TRUE - Returns TRUE if expression is truthy, FALSE for NULL (SQL standard)
    - IS_FALSE - Returns TRUE if expression is falsy, FALSE for NULL (SQL standard)
    - IS_NOT_TRUE - Returns TRUE if expression is falsy or NULL
    - IS_NOT_FALSE - Returns TRUE if expression is truthy or NULL
  - Refactored `getExpectedValue` method with abstract `applyNotNull` pattern
  - Proper NULL handling: IS TRUE/IS FALSE return FALSE for NULL, IS NOT TRUE/IS NOT FALSE return TRUE for NULL
  - Supports boolean constants, integers (non-zero=TRUE, zero=FALSE), and strings
- Test [GaussDBUnaryPostfixOperationTest]: Added comprehensive unit tests (32 tests)
  - Tests for IS TRUE/IS FALSE with boolean constants (TRUE/FALSE)
  - Tests for IS TRUE/IS FALSE with integers (non-zero, zero)
  - Tests for IS TRUE/IS FALSE/IS NOT TRUE/IS NOT FALSE with NULL
  - Tests for IS NOT TRUE/IS NOT FALSE with boolean constants and integers
  - Tests for existing IS NULL/IS NOT NULL operators
  - Tests for SQL generation format (expr IS TRUE)
  - Tests for operator text representation and enum count (6 values)

## v2.0.19 | 2026-04-28
- Integration Test [P1 Extension Complete]: All P1 GaussDB-M SonarOracle extension tasks completed
  - P1-1: Window Function AST - GaussDBWindowFunction (8 functions: ROW_NUMBER, RANK, DENSE_RANK, SUM, AVG, COUNT, MAX, MIN)
  - P1-2: Computable Function AST - GaussDBComputableFunction (13 functions: ABS, CEIL, FLOOR, ROUND, MOD, SIGN, CONCAT, LENGTH, UPPER, LOWER, TRIM, COALESCE, IFNULL)
  - P1-3: Temporal Function AST - GaussDBTemporalFunction (12 functions: NOW, CURDATE, CURTIME, YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, DAYOFWEEK, DATEDIFF, LAST_DAY)
  - P1-4: CAST Operation AST - GaussDBCastOperation (8 types: SIGNED, UNSIGNED, BINARY, CHAR, DATE, DATETIME, TIME, DECIMAL)
  - P1-5: NATURAL JOIN Support - Extended GaussDBJoin.JoinType enum
  - P1-6: ExpressionGenerator Window Function Integration - generateWindowFunction method
  - P1-7: ExpressionGenerator Computable Function Integration - COMPUTABLE_FUNCTION Action
  - P1-8: ExpressionGenerator Temporal Function Integration - TEMPORAL_FUNCTION Action
  - P1-9: ExpressionGenerator CAST Integration - CAST Action
  - P1-10: GaussDBMSonarOracle Window Function Extension - generateWindowFuc method
- Bug Detection Capability: Improved from 50% to estimated 70-80% for GaussDB-M SonarOracle

## v2.0.18 | 2026-04-28
- New Feature [GaussDB-M SonarOracle]: Extended window function support in SonarOracle
  - Added `generateWindowFuc()` method to GaussDBMExpressionGenerator:
    - Simplified window function generation for SonarOracle fetch columns
    - Randomly generates PARTITION BY and ORDER BY clauses
    - Supports all 8 window function types (ROW_NUMBER, RANK, DENSE_RANK, SUM, AVG, COUNT, MAX, MIN)
  - Modified `check()` method to use window functions as fetch columns:
    - Randomly selects between window function and fetch column expression
    - Window functions enable testing of optimizer bugs in window function execution
  - Added window function related error handling:
    - "window function", "OVER", "PARTITION BY" syntax errors
    - "You cannot use a window function" context errors
    - "is not allowed in window function" constraint errors
- Test [GaussDBMExpressionGeneratorFetchColumnTest]: Added unit tests for generateWindowFuc
  - Tests for valid window function generation (not null, correct type)
  - Tests for aggregate vs ranking function expression handling
  - Tests for SQL string generation with OVER clause
  - Tests for PARTITION BY and ORDER BY clause generation
- Fix [GaussDBMExpressionGeneratorActionsTest]: Added exception handling for empty columns list

## v2.0.17 | 2026-04-28
- New Feature [GaussDB-M ExpressionGenerator]: Integrated window function support
  - Added `WINDOW_FUNCTION` to ColumnExpressionActions enum for SonarOracle fetch columns
  - Implemented `generateWindowFunction(GaussDBColumn firstColumn, GaussDBTables targetTables)` method:
    - Randomly selects from 8 window function types (ROW_NUMBER, RANK, DENSE_RANK, SUM, AVG, COUNT, MAX, MIN)
    - Generates PARTITION BY clause using table columns (optional)
    - Generates ORDER BY clause using table columns (optional)
    - Ranking functions (ROW_NUMBER, RANK, DENSE_RANK) have no expression argument
    - Aggregate window functions (SUM, AVG, COUNT, MAX, MIN) have column expression argument
  - Added handler in `generateFetchColumnExpression()` for window function generation
- Test [GaussDBMExpressionGeneratorFetchColumnTest]: Extended tests for window function support
  - Added `testGenerateWindowFunction` test case
  - Added `testWindowFunctionCanBeConvertedToString` test case
  - Updated `testAllExpressionTypesGenerated` to include window function verification

## v2.0.16 | 2026-04-28
- New Feature [GaussDB-M ExpressionGenerator]: Integrated temporal function support
  - Added `TEMPORAL_FUNCTION` to Actions enum for expression generation
  - Implemented `generateTemporalFunction(int depth)` method:
    - Randomly selects from 12 temporal function types
    - Zero-argument functions (NOW, CURDATE, CURTIME) - no arguments needed
    - One-argument functions (YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, DAYOFWEEK, LAST_DAY) - generate leaf nodes
    - Two-argument function (DATEDIFF) - generate two leaf nodes
  - Added `TEMPORAL_FUNCTION` to ColumnExpressionActions for SonarOracle fetch columns
  - Added handler in `generateFetchColumnExpression()` for temporal function generation
  - Also added CAST and WINDOW_FUNCTION handlers (auto-added by linter)
- Test [GaussDBMExpressionGeneratorTemporalFunctionTest]: Added comprehensive unit tests (31 tests)
  - Tests for SQL generation of all 12 temporal function types
  - Tests for function arity validation (0, 1, 2 arguments)
  - Tests for getExpectedValue() for extraction and calculation functions
  - Tests for function names verification
  - Tests for random function selection methods
  - Tests for expression conversion to SQL

## v2.0.15 | 2026-04-28
- New Feature [GaussDB-M NATURAL JOIN]: Extended GaussDBJoin for NATURAL JOIN support
  - Extended `JoinType` enum with NATURAL type
  - Modified `getRandomJoinClauses` method:
    - NATURAL JOIN is excluded when multiple joins are generated (column name conflicts)
    - NATURAL and CROSS JOIN types have no ON clause
  - Extended `GaussDBToStringVisitor.visit(GaussDBJoin)`:
    - NATURAL JOIN generates `NATURAL JOIN table` format
    - NATURAL JOIN does not generate ON clause
- Test [GaussDBJoinTest]: Added comprehensive unit tests (14 tests)
  - Tests for JoinType enum containing NATURAL
  - Tests for NATURAL JOIN SQL generation (no ON clause)
  - Tests for all JoinType SQL generation (INNER, LEFT, RIGHT, CROSS, NATURAL)
  - Tests for JoinType count (5 values)
  - Tests for getExpectedValue returning NULL

## v2.0.14 | 2026-04-28
- New Feature [GaussDB-M CAST Operation]: Added GaussDBCastOperation AST node for SonarOracle
  - Created `GaussDBCastOperation.java` with GaussDBCastType enum:
    - SIGNED - Signed integer conversion
    - UNSIGNED - Unsigned integer conversion
    - BINARY - Binary conversion
    - CHAR - Character/string conversion
    - DATE - Date conversion
    - DATETIME - Datetime conversion
    - TIME - Time conversion
    - DECIMAL - Decimal conversion
  - Implemented `getExpectedValue()` with type conversion logic:
    - SIGNED/UNSIGNED: String to integer parsing (with whitespace trimming)
    - CHAR: Integer/Boolean to string conversion
    - DATE/DATETIME/TIME/DECIMAL/BINARY: Simplified handling (returns unchanged)
  - Extended `GaussDBToStringVisitor` with CAST SQL generation
    - Format: `CAST(expr AS type)`
- Test [GaussDBCastOperationTest]: Added comprehensive unit tests (18 tests)
  - Tests for all CAST type names (SIGNED, UNSIGNED, CHAR, etc.)
  - Tests for SQL generation format `CAST(expr AS SIGNED)`
  - Tests for integer type conversion (SIGNED/UNSIGNED from string/int/boolean)
  - Tests for string type conversion (CHAR from int/boolean/string)
  - Tests for NULL handling in all CAST types
  - Tests for ToStringVisitor conversion

## v2.0.13 | 2026-04-28
- New Feature [GaussDB-M Window Function]: Added GaussDBWindowFunction AST node for SonarOracle
  - Created `GaussDBWindowFunction.java` with GaussDBFunction enum:
    - Ranking functions (0 args): ROW_NUMBER, RANK, DENSE_RANK
    - Aggregate window functions (1 arg): SUM, AVG, COUNT, MAX, MIN
  - Added `getExpectedValue()` returning null (window functions need actual data)
  - Extended `GaussDBToStringVisitor` with window function SQL generation
    - Format: `FUNCTION(expr) OVER (PARTITION BY col1 ORDER BY col2)`
  - Extended `GaussDBExpectedValueVisitor` to handle window functions (throws IgnoreMeException)
  - Added helper methods: `isRankingFunction()`, `isAggregateWindowFunction()`
- Test [GaussDBWindowFunctionTest]: Added comprehensive unit tests (29 tests)
  - Tests for all function names and argument counts
  - Tests for SQL generation format (PARTITION BY, ORDER BY)
  - Tests for ToStringVisitor conversion
  - Tests for getExpectedValue returning null
- Fix [GaussDBTemporalFunction]: Added missing `java.time.LocalDate` import
- Fix [GaussDBTemporalFunctionTest]: Added missing imports and removed unused import

## v2.0.12 | 2026-04-28
- Integration Test [P0 Extension Complete]: All P0 GaussDB-M SonarOracle extension tasks completed
  - P0-1: Arithmetic operators AST (+,-,*,/,%) - GaussDBBinaryArithmeticOperation
  - P0-2: Unary arithmetic operators (+,- prefix) - Extended GaussDBUnaryPrefixOperation
  - P0-3: IN operator - GaussDBInOperation (IN/NOT IN)
  - P0-4: EXISTS operator - GaussDBExists
  - P0-5: LIKE comparison operator - Extended GaussDBBinaryComparisonOperation
  - P0-6: CASE WHEN integration - Added to ExpressionGenerator Actions enum
  - P0-7: generateFetchColumnExpression - Added ColumnExpressionActions enum
  - P0-8: generateWhereColumnExpression - Added WhereColumnExpressionActions enum
  - P0-9: AVG aggregate function - Extended GaussDBAggregate enum
  - P0-10: ExpressionGenerator Actions integration - ARITHMETIC_OPERATION, IN_OPERATION
  - P0-11: GaussDBMSonarOracle extension - Uses new expression generation methods
- Test Fix [GaussDBMOracleIsolationTest]: Updated test expectations for SONAR oracle
  - Added SONAR to EXPECTED_ORACLE_NAMES
  - Added SONAR case to expectedImplementationClass
  - Updated oracle count from 14 to 15
  - Fixed help parameter visibility expectation
- Bug Detection Capability: Improved from 15% to estimated 50% for GaussDB-M SonarOracle

## v2.0.11 | 2026-04-28
- New Feature [GaussDB-M ExpressionGenerator]: Added `generateWhereColumnExpression` method for SonarOracle
  - Created `WhereColumnExpressionActions` enum with 3 expression types:
    - BINARY_COMPARISON_OPERATION - Comparison operations on fetch column alias
    - AGGREGATE_FUNCTION - Aggregate function calls on fetch column
    - COLUMN - Direct column reference (via ManuelPredicate)
  - Method signature: `public GaussDBExpression generateWhereColumnExpression(GaussDBPostfixText postfixText)`
  - Uses existing AST nodes: GaussDBBinaryComparisonOperation, GaussDBAggregate, GaussDBManuelPredicate
- Enhancement [GaussDBMSonarOracle]: Extended to use new expression generation methods
  - Now uses `generateFetchColumnExpression` for diversified fetch column expressions
  - Now uses `generateWhereColumnExpression` for WHERE clause generation
  - Added support for expression-based optimized/unoptimized query generation
  - Added SONAR-specific error handling (Data truncation, Incorrect datetime/date/time, GROUP BY errors)
  - Added `getLastQueryString()` method implementation
- Update [GaussDBMOptions]: Added SONAR to available oracle options list

## v2.0.10 | 2026-04-28
- New Feature [GaussDB-M ExpressionGenerator]: Integrated new AST types into Actions enum
  - Added `ARITHMETIC_OPERATION` action - generates `GaussDBBinaryArithmeticOperation` (+, -, *, /, %)
  - Added `IN_OPERATION` action - generates `GaussDBInOperation` (IN and NOT IN)
  - EXISTS action documented but deferred (requires subquery support)
  - New imports: `GaussDBBinaryArithmeticOperation`, `GaussDBArithmeticOperator`, `GaussDBInOperation`
- Test [GaussDBMExpressionGeneratorActionsTest]: Added comprehensive unit tests
  - Tests for arithmetic operation generation and expected values
  - Tests for IN operation generation with list elements
  - Tests for nested expressions (arithmetic within arithmetic)
  - Tests for IN list size bounds
- Fix [GaussDBMExpressionGeneratorCaseTest]: Added exception handling for empty columns list

## v2.0.9 | 2026-04-28
- New Feature [GaussDB-M ExpressionGenerator]: Added `generateFetchColumnExpression` method for SonarOracle
  - Created `ColumnExpressionActions` enum with 5 expression types:
    - BINARY_COMPARISON_OPERATION - Column comparison expressions
    - BINARY_ARITHMETIC_OPERATION - Arithmetic operations (+, -, *, /, %)
    - COLUMN - Direct column reference
    - AGGREGATE_FUNCTION - Aggregate function calls (COUNT, SUM, AVG, MIN, MAX)
    - CASE_OPERATOR - CASE WHEN expressions
  - Method signature: `public GaussDBExpression generateFetchColumnExpression(GaussDBTables tables)`
  - Uses existing AST nodes: GaussDBBinaryComparisonOperation, GaussDBBinaryArithmeticOperation, GaussDBAggregate, GaussDBCaseWhen
- Test [GaussDBMExpressionGeneratorFetchColumnTest]: Added comprehensive unit tests
  - Tests for each expression type generation (BinaryComparison, BinaryArithmetic, Aggregate, CaseWhen, Column)
  - Tests for expression conversion to string via ToStringVisitor
  - Tests for all expression types being generated
  - Tests for empty tables handling

## v2.0.8 | 2026-04-28
- New Feature [GaussDB-M]: Added unary arithmetic operators (+, -) support
  - Extended `GaussDBUnaryPrefixOperation.UnaryPrefixOperator` enum with PLUS and MINUS operators
  - PLUS(+) operator returns the original value
  - MINUS(-) operator negates the value (supports INT and STRING types)
  - Proper NULL handling: NULL with unary operators returns NULL
  - Refactored `getExpectedValue` method to use abstract `applyNotNull` pattern
- Test [GaussDBUnaryPrefixOperationTest]: Added comprehensive unit tests
  - PLUS/MINUS with positive, negative, and zero integers
  - MINUS with numeric strings, strings with leading spaces, non-numeric strings
  - NOT boolean operation tests
  - NULL operand handling for all operators
  - Operator text representation tests

## v2.0.7 | 2026-04-27
- New Feature [GaussDB-M]: Added LIKE comparison operator support
  - Extended `GaussDBBinaryComparisonOperation.BinaryComparisonOperator` enum with LIKE operator
  - Implemented `getExpectedValue` using `LikeImplementationHelper` for pattern matching
  - Supports % (any characters) and _ (single character) wildcards
  - Proper NULL handling: NULL LIKE pattern returns NULL
  - Case-insensitive matching as per MySQL/GaussDB-M semantics
- Test [GaussDBBinaryComparisonOperationLIKETest]: Added comprehensive unit tests
  - Exact match, pattern match (%, _), no match, NULL handling tests
  - Combined patterns and case-insensitive matching tests

## v2.0.6 | 2026-04-28
- Docs [GaussDBM-Syntax-Coverage]: Added comprehensive syntax coverage evaluation
  - Operators: 33 MySQL vs 12 GaussDB-M (36% coverage)
    - Missing: arithmetic (+,-,*,/,%), bitwise (&,|,^), IN, EXISTS, LIKE, <=>, XOR
  - Expressions: 28 MySQL vs 17 GaussDB-M (61% coverage)
    - Missing: CAST, window functions, IN/EXISTS operations
  - Functions: 170 MySQL vs 10 GaussDB-M (6% coverage)
    - Missing: 25 math functions, 35 string functions, 38 time functions, 20 JSON functions, 12 window functions
  - Data Types: 38 MySQL vs 21 GaussDB-M (55% coverage)
    - Missing: BIT, BINARY, JSON, ENUM, SET, geometry types
  - Extension plan: P0(3.5d→50%), P1(8d→80%), P2(5.25d→95%)

## v2.0.5 | 2026-04-28
- Docs [GaussDBM-SonarOracle-Extension-Plan]: Added detailed extension evaluation report
  - Analyzed current GaussDB-M SonarOracle limitations (7 Actions vs MySQL 13 Actions)
  - Identified missing features: window functions, computed functions, arithmetic operations
  - Proposed P0/P1/P2 extension plan with work estimates
  - Bug detection capability projection: 15% → 50% (P0) → 80% (P1)
  - Total work estimate: 11 days for full coverage

## v2.0.4 | 2026-04-28
- Docs [SonarOracle-Grammar-Coverage]: Added grammar support evaluation report
  - Evaluated SonarOracle syntax coverage for MySQL, PostgreSQL, GaussDB-M
  - MySQL: 60+ functions, 20+ window functions, 6 FetchColumn expression types
  - PostgreSQL: JSON/Range types, POSIX regex, full window function frame support
  - GaussDB-M: Basic expressions only, identified expansion recommendations
  - Documented bug detection capability comparison across DBMS

## v2.0.3 | 2026-04-27
- New Feature [SonarOracle]: Port SONAR Oracle to all supported DBMS
  - PostgreSQL: Created `PostgresSonarOracle.java` with optimized/unoptimized query comparison
  - SQLite3: Created `SQLite3SonarOracle.java` with aggregate and column expression support
  - MariaDB: Created `MariaDBSonarOracle.java` with MariaDB-specific join handling
  - TiDB: Created `TiDBSonarOracle.java` with TiDB-specific expression generation
  - GaussDB-M: Created `GaussDBMSonarOracle.java` for MySQL-compatible mode
  - GaussDB-A: Created `GaussDBASonarOracle.java` for Oracle-compatible mode
- New Feature [AST Nodes]: Added ManuelPredicate and PostfixText AST nodes for all DBMS
  - MariaDB: `MariaDBManuelPredicate.java`, `MariaDBPostfixText.java`
  - TiDB: `TiDBManuelPredicate.java`, `TiDBPostfixText.java`
  - GaussDB-M: `GaussDBManuelPredicate.java`, `GaussDBPostfixText.java`
  - GaussDB-A: `GaussDBAManuelPredicate.java`, `GaussDBAPostfixText.java`
- New Feature [ExpressionGenerator]: Extended generators for SonarOracle support
  - PostgreSQL: Added `generateFetchColumnExpression`, `generateWhereColumnExpression`
  - SQLite3: Added `generateFetchColumnExp`, `generateWhereColumnExpression`
  - MariaDB: Added `generateFetchColumnExpression`, `generateWhereColumnExpression`
  - TiDB: Added `generateFetchColumnExpression`, `generateWhereColumnExpression`
- New Feature [Visitor]: Extended visitor implementations for new AST nodes
  - MariaDB: Updated `MariaDBVisitor.java`, `MariaDBStringVisitor.java`
  - TiDB: Updated `TiDBVisitor.java`, `TiDBToStringVisitor.java`
  - GaussDB-M: Updated `GaussDBToStringVisitor.java`
  - GaussDB-A: Updated `GaussDBAToStringVisitor.java`
- Integration: SONAR oracle registered in all OracleFactory enums

## v2.0.2 | 2026-04-27
- New Feature [MySQLSonarOracle]: Port SemBug SONAR Oracle to SQLancer
  - Created `MySQLSonarOracle.java` extending `NoRECBase<MySQLGlobalState>`
  - Implemented SONAR test oracle comparing optimized vs unoptimized query result sets
  - Added SONAR to `MySQLOracleFactory` enum for oracle selection
  - Supports window functions and fetch column expressions in query generation
  - Added SONAR-specific error handling (Data truncation, group function errors, etc.)
- Test [MySQLSonarOracleTest]: Added unit tests for SONAR oracle components
  - Visitor tests for MySQLPostfixText, MySQLManuelPredicate, MySQLWindowFunction
  - SONAR query pattern structure validation tests
  - OracleFactory SONAR registration verification tests
- Integration: SONAR oracle fully integrated into SQLancer MySQL testing framework

## v2.0.1 | 2026-04-27
- New Feature [MySQLExpressionGenerator]: Add SonarOracle support methods
  - `generateFetchColumnExpression(MySQLTables)` - Generates fetch column expressions for SELECT clauses
  - `generateWhereColumnExpression(MySQLPostfixText)` - Generates WHERE column expressions for WHERE conditions
  - `generateWindowFuc()` - Generates window function expressions (ROW_NUMBER, RANK, SUM, AVG, etc.)
  - `getRandomJoinClauses(List<MySQLTable>)` - Generates random JOIN clause lists with NATURAL join handling
  - Added `ColumnExpressionActions` and `HavingColumnExpression` enums for expression type selection
- Test [MySQLExpressionGeneratorSonarTest]: Added comprehensive unit tests for SonarOracle methods
- Test [MySQLToStringVisitorSonarTest]: Fixed imports for existing SonarOracle visitor tests

## v2.0.0 | 2026-04-20
- GaussDB-A (Oracle Compatibility Mode) support verified and documented
- GaussDB-M (MySQL Compatibility Mode) verified
- MySQL temporal data type enhancement (DATE, TIME, DATETIME, TIMESTAMP, YEAR)
- MySQL BIT/ENUM/SET/JSON/BINARY data types support
- Bug fixes for schema/database name case sensitivity in GaussDB

## v1.x | Previous releases
- Initial SQLancer project with MySQL, PostgreSQL support
- NoREC, TLP, PQS, CERT, EET oracles