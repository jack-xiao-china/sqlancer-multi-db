package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.ast.PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator;
import sqlancer.postgres.ast.PostgresConstant;

class PostgresBinaryComparisonOperationTest {

    @Test
    void greaterEqualsReturnsNullInsteadOfThrowingWhenEqualityIsUnsupported() {
        PostgresConstant left = PostgresConstant.createRange(1, true, 2, false);
        PostgresConstant right = PostgresConstant.createRange(1, true, 2, false);

        assertNull(PostgresBinaryComparisonOperator.GREATER_EQUALS.getExpectedValue(left, right));
    }

    @Test
    void notEqualsReturnsNullInsteadOfThrowingWhenEqualityIsUnsupported() {
        PostgresConstant left = PostgresConstant.createRange(1, true, 2, false);
        PostgresConstant right = PostgresConstant.createRange(2, true, 3, false);

        assertNull(PostgresBinaryComparisonOperator.NOT_EQUALS.getExpectedValue(left, right));
    }
}
