package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.ast.PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresInOperation;

class PostgresNumericComparisonTest {

    @Test
    void intConstantCanCompareAgainstDoubleConstant() {
        PostgresConstant intValue = PostgresConstant.createIntConstant(1);
        PostgresConstant doubleValue = PostgresConstant.createDoubleConstant(0.5777360464703096d);

        assertNotNull(intValue.isEquals(doubleValue));
        assertFalse(intValue.isEquals(doubleValue).asBoolean());
        assertFalse(PostgresBinaryComparisonOperator.LESS.getExpectedValue(intValue, doubleValue).asBoolean());
    }

    @Test
    void inExpectedValueHandlesMixedNumericTypes() {
        PostgresConstant leftValue = PostgresConstant.createDoubleConstant(0.5777360464703096d);
        PostgresInOperation inOperation = new PostgresInOperation(leftValue,
                List.of(PostgresConstant.createIntConstant(1), PostgresConstant.createDecimalConstant(BigDecimal.ONE)),
                true);

        PostgresConstant expected = inOperation.getExpectedValue();

        assertNotNull(expected);
        assertFalse(expected.asBoolean());
    }

    @Test
    void intAndDecimalWithSameNumericValueCompareEqual() {
        PostgresConstant intValue = PostgresConstant.createIntConstant(1);
        PostgresConstant decimalValue = PostgresConstant.createDecimalConstant(new BigDecimal("1.0"));

        assertTrue(intValue.isEquals(decimalValue).asBoolean());
    }
}
