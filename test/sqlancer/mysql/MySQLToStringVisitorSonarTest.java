package sqlancer.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.ast.MySQLAggregateFunction;
import sqlancer.mysql.ast.MySQLAggregateFunction.MySQLFunction;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLManuelPredicate;
import sqlancer.mysql.ast.MySQLPostfixText;
import sqlancer.mysql.ast.MySQLWindowFunction;

/**
 * Tests for MySQLToStringVisitor with SonarOracle AST nodes. Tests MySQLVisitor.asString() for new AST nodes:
 * MySQLPostfixText, MySQLManuelPredicate, MySQLWindowFunction, MySQLAggregateFunction.
 */
public class MySQLToStringVisitorSonarTest {

    @Test
    void testVisitPostfixText() {
        // Create a column reference for the inner expression
        MySQLColumn aCol = new MySQLColumn("a", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("t1", List.of(aCol), Collections.emptyList(), MySQLTable.MySQLEngine.INNO_DB);
        aCol.setTable(t1);
        MySQLColumnReference aRef = new MySQLColumnReference(aCol, MySQLConstant.createNullConstant());

        // Test MySQLPostfixText: (expr) AS alias
        MySQLPostfixText postfixText = new MySQLPostfixText(aRef, "col_alias");
        assertEquals("(t1.a) AS col_alias", MySQLVisitor.asString(postfixText));
    }

    @Test
    void testVisitPostfixTextWithConstant() {
        // Test with a constant expression
        MySQLConstant constant = MySQLConstant.createIntConstant(42);
        MySQLPostfixText postfixText = new MySQLPostfixText(constant, "value_alias");
        assertEquals("(42) AS value_alias", MySQLVisitor.asString(postfixText));
    }

    @Test
    void testVisitManuelPredicate() {
        // Test MySQLManuelPredicate: raw SQL fragment output
        MySQLManuelPredicate predicate = new MySQLManuelPredicate("1 = 1");
        assertEquals("1 = 1", MySQLVisitor.asString(predicate));
    }

    @Test
    void testVisitManuelPredicateWithComplexFragment() {
        // Test with a more complex SQL fragment
        MySQLManuelPredicate predicate = new MySQLManuelPredicate("col1 > col2 AND col3 IS NOT NULL");
        assertEquals("col1 > col2 AND col3 IS NOT NULL", MySQLVisitor.asString(predicate));
    }

    @Test
    void testVisitWindowFunction() {
        // Create a column for partition by
        MySQLColumn partitionCol = new MySQLColumn("partition_col", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("t1", List.of(partitionCol), Collections.emptyList(),
                MySQLTable.MySQLEngine.INNO_DB);
        partitionCol.setTable(t1);
        MySQLColumnReference partitionRef = new MySQLColumnReference(partitionCol, MySQLConstant.createNullConstant());

        // Test zero-argument window function: ROW_NUMBER() OVER (PARTITION BY col)
        MySQLWindowFunction rowNumber = new MySQLWindowFunction(MySQLWindowFunction.MySQLFunction.ROW_NUMBER, null,
                partitionRef);
        assertEquals("ROW_NUMBER() OVER (PARTITION BY t1.partition_col)", MySQLVisitor.asString(rowNumber));
    }

    @Test
    void testVisitWindowFunctionWithArgument() {
        // Create columns for expression and partition by
        MySQLColumn sumCol = new MySQLColumn("sum_col", MySQLDataType.INT, false, 0);
        MySQLColumn partitionCol = new MySQLColumn("partition_col", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("t1", List.of(sumCol, partitionCol), Collections.emptyList(),
                MySQLTable.MySQLEngine.INNO_DB);
        sumCol.setTable(t1);
        partitionCol.setTable(t1);
        MySQLColumnReference sumRef = new MySQLColumnReference(sumCol, MySQLConstant.createNullConstant());
        MySQLColumnReference partitionRef = new MySQLColumnReference(partitionCol, MySQLConstant.createNullConstant());

        // Test one-argument window function: SUM(expr) OVER (PARTITION BY col)
        MySQLWindowFunction sumWindow = new MySQLWindowFunction(MySQLWindowFunction.MySQLFunction.SUM, sumRef,
                partitionRef);
        assertEquals("SUM(t1.sum_col) OVER (PARTITION BY t1.partition_col)", MySQLVisitor.asString(sumWindow));
    }

    @Test
    void testVisitWindowFunctionRank() {
        // Create a column for partition by
        MySQLColumn partitionCol = new MySQLColumn("dept", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("employees", List.of(partitionCol), Collections.emptyList(),
                MySQLTable.MySQLEngine.INNO_DB);
        partitionCol.setTable(t1);
        MySQLColumnReference partitionRef = new MySQLColumnReference(partitionCol, MySQLConstant.createNullConstant());

        // Test RANK() window function
        MySQLWindowFunction rankWindow = new MySQLWindowFunction(MySQLWindowFunction.MySQLFunction.RANK, null,
                partitionRef);
        assertEquals("RANK() OVER (PARTITION BY employees.dept)", MySQLVisitor.asString(rankWindow));
    }

    @Test
    void testVisitAggregateFunction() {
        // Create a column for the aggregate expression
        MySQLColumn aCol = new MySQLColumn("a", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("t1", List.of(aCol), Collections.emptyList(), MySQLTable.MySQLEngine.INNO_DB);
        aCol.setTable(t1);
        MySQLColumnReference aRef = new MySQLColumnReference(aCol, MySQLConstant.createNullConstant());

        // Test MySQLAggregateFunction: SUM(expr)
        MySQLAggregateFunction sumAggr = new MySQLAggregateFunction(MySQLFunction.SUM, aRef);
        assertEquals("SUM(t1.a)", MySQLVisitor.asString(sumAggr));
    }

    @Test
    void testVisitAggregateFunctionCount() {
        // Create a column for the aggregate expression
        MySQLColumn countCol = new MySQLColumn("id", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("users", List.of(countCol), Collections.emptyList(),
                MySQLTable.MySQLEngine.INNO_DB);
        countCol.setTable(t1);
        MySQLColumnReference countRef = new MySQLColumnReference(countCol, MySQLConstant.createNullConstant());

        // Test COUNT aggregate function
        MySQLAggregateFunction countAggr = new MySQLAggregateFunction(MySQLFunction.COUNT, countRef);
        assertEquals("COUNT(users.id)", MySQLVisitor.asString(countAggr));
    }

    @Test
    void testVisitAggregateFunctionAvg() {
        // Create a column for the aggregate expression
        MySQLColumn priceCol = new MySQLColumn("price", MySQLDataType.INT, false, 0);
        MySQLTable products = new MySQLTable("products", List.of(priceCol), Collections.emptyList(),
                MySQLTable.MySQLEngine.INNO_DB);
        priceCol.setTable(products);
        MySQLColumnReference priceRef = new MySQLColumnReference(priceCol, MySQLConstant.createNullConstant());

        // Test AVG aggregate function
        MySQLAggregateFunction avgAggr = new MySQLAggregateFunction(MySQLFunction.AVG, priceRef);
        assertEquals("AVG(products.price)", MySQLVisitor.asString(avgAggr));
    }

    @Test
    void testAsString() {
        // Verify MySQLVisitor.asString() works correctly for all new AST node types

        // MySQLPostfixText
        MySQLConstant constant = MySQLConstant.createIntConstant(100);
        MySQLPostfixText postfix = new MySQLPostfixText(constant, "result");
        assertEquals("(100) AS result", MySQLVisitor.asString(postfix));

        // MySQLManuelPredicate
        MySQLManuelPredicate predicate = new MySQLManuelPredicate("x > 5");
        assertEquals("x > 5", MySQLVisitor.asString(predicate));

        // MySQLAggregateFunction with constant
        MySQLAggregateFunction maxAggr = new MySQLAggregateFunction(MySQLFunction.MAX, constant);
        assertEquals("MAX(100)", MySQLVisitor.asString(maxAggr));
    }

    @Test
    void testAsStringWithNullExpression() {
        // Test window function with null expression (zero-argument functions)
        MySQLColumn partitionCol = new MySQLColumn("grp", MySQLDataType.INT, false, 0);
        MySQLTable t1 = new MySQLTable("data", List.of(partitionCol), Collections.emptyList(),
                MySQLTable.MySQLEngine.INNO_DB);
        partitionCol.setTable(t1);
        MySQLColumnReference partitionRef = new MySQLColumnReference(partitionCol, MySQLConstant.createNullConstant());

        // DENSE_RANK() has no argument (null expr)
        MySQLWindowFunction denseRank = new MySQLWindowFunction(MySQLWindowFunction.MySQLFunction.DENSE_RANK, null,
                partitionRef);
        assertEquals("DENSE_RANK() OVER (PARTITION BY data.grp)", MySQLVisitor.asString(denseRank));
    }
}