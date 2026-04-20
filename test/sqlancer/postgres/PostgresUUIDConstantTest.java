package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.ast.PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator;
import sqlancer.postgres.ast.PostgresConstant;

class PostgresUUIDConstantTest {

    @Test
    void stringLiteralAndUuidConstantCanBeComparedInExpectedValueEvaluation() {
        PostgresConstant left = PostgresConstant.createTextConstant("00000000-0000-0000-0000-000000000001");
        PostgresConstant right = PostgresConstant.createUUIDConstant("00000000-0000-0000-0000-000000000002");

        PostgresConstant less = PostgresBinaryComparisonOperator.LESS.getExpectedValue(left, right);
        PostgresConstant lessEquals = PostgresBinaryComparisonOperator.LESS_EQUALS.getExpectedValue(left, right);
        PostgresConstant greater = PostgresBinaryComparisonOperator.GREATER.getExpectedValue(right, left);

        assertTrue(less.asBoolean());
        assertTrue(lessEquals.asBoolean());
        assertTrue(greater.asBoolean());
    }

    @Test
    void uuidConstantSupportsEqualityAndTextCast() {
        PostgresConstant uuid = PostgresConstant.createUUIDConstant("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("'123e4567-e89b-12d3-a456-426614174000'", uuid.cast(PostgresSchema.PostgresDataType.TEXT).getTextRepresentation());
        assertTrue(uuid.isEquals(PostgresConstant.createTextConstant("123e4567-e89b-12d3-a456-426614174000")).asBoolean());
    }
}
