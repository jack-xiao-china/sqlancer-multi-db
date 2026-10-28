package sqlancer.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import sqlancer.IgnoreMeException;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.ast.MySQLComputableFunction.MySQLFunction;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLTemporalFunction;
import sqlancer.mysql.ast.MySQLTemporalFunction.TemporalFunctionKind;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;

/**
 * Regression tests for the v2.7.11 MySQL oracle fixes.
 */
public class MySQLOracleRegressionTest {

    @Test
    public void timestampAddRendersMySQLSignature() {
        // MySQL signature is TIMESTAMPADD(unit, interval, datetime_expr); the INTERVAL form is a
        // syntax error and previously crashed CODDTEST rounds.
        MySQLTemporalFunction func = new MySQLTemporalFunction(TemporalFunctionKind.TIMESTAMPADD,
                new MySQLText("'2020-01-01'"), MySQLConstant.createIntConstant(83), "MINUTE", MySQLDataType.DATETIME);
        String sql = MySQLVisitor.asString(func);
        assertEquals("TIMESTAMPADD(MINUTE, 83, '2020-01-01')", sql);
    }

    @Test
    public void unaryMinusOnUnsupportedConstantDeclines() {
        // MINUS over a bit constant previously crashed generation with AssertionError.
        MySQLUnaryPrefixOperation op = new MySQLUnaryPrefixOperation(MySQLConstant.createBitConstant(5, 4),
                MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator.MINUS);
        assertThrows(IgnoreMeException.class, op::getExpectedValue);
    }

    @Test
    public void padFunctionHugeLengthDeclines() {
        // A generated huge LPAD/RPAD length previously ballooned the expected-value builder into
        // an OutOfMemoryError.
        MySQLConstant[] args = { MySQLConstant.createStringConstant("s"), MySQLConstant.createIntConstant(1_000_000),
                MySQLConstant.createStringConstant("p") };
        assertThrows(IgnoreMeException.class, () -> MySQLFunction.LPAD.apply(args));
        assertThrows(IgnoreMeException.class, () -> MySQLFunction.RPAD.apply(args));
    }
}
