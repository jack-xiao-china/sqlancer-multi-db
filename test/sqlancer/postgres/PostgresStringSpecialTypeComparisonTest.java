package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.ast.PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresInOperation;

class PostgresStringSpecialTypeComparisonTest {

    @Test
    void stringAndByteaCanBeComparedWithoutAssertion() {
        PostgresConstant stringValue = PostgresConstant.createTextConstant("\\xdeadbeef");
        PostgresConstant byteaValue = PostgresConstant.createByteaConstant("DEADBEEF");

        assertTrue(stringValue.isEquals(byteaValue).asBoolean());
        assertFalse(PostgresBinaryComparisonOperator.LESS.getExpectedValue(stringValue, byteaValue).asBoolean());
    }

    @Test
    void stringAndEnumCanBeComparedWithoutAssertion() {
        PostgresConstant stringValue = PostgresConstant.createTextConstant("a");
        PostgresConstant enumValue = PostgresConstant.createEnumConstant("e0", "a");
        PostgresConstant laterEnum = PostgresConstant.createEnumConstant("e0", "c");

        assertTrue(stringValue.isEquals(enumValue).asBoolean());
        assertTrue(PostgresBinaryComparisonOperator.LESS.getExpectedValue(stringValue, laterEnum).asBoolean());
    }

    @Test
    void inExpectedValueHandlesEnumMembers() {
        PostgresInOperation inOperation = new PostgresInOperation(PostgresConstant.createTextConstant("a"),
                List.of(PostgresConstant.createEnumConstant("e0", "b"),
                        PostgresConstant.createEnumConstant("e0", "a")),
                true);

        PostgresConstant expected = inOperation.getExpectedValue();

        assertNotNull(expected);
        assertTrue(expected.asBoolean());
    }
}
