package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.ast.PostgresConcatOperation;
import sqlancer.postgres.ast.PostgresConstant;

class PostgresConcatOperationTest {

    @Test
    void concatReturnsNullInsteadOfThrowingWhenTextCastIsUnsupported() {
        PostgresConcatOperation concat = new PostgresConcatOperation(PostgresConstant.createRange(1, true, 2, false),
                PostgresConstant.createTextConstant("x"));

        assertNull(concat.getExpectedValue());
    }
}
