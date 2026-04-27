package sqlancer.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresFunction.PostgresFunctionWithResult;

class PostgresFunctionTest {

    @Test
    void arrayLengthCastsDimensionBeforeReadingIntValue() {
        PostgresConstant array = PostgresConstant.createArrayConstant(
                List.of(PostgresConstant.createIntConstant(1), PostgresConstant.createIntConstant(2)),
                PostgresCompoundDataType.create(PostgresDataType.INT));

        PostgresConstant result = PostgresFunctionWithResult.ARRAY_LENGTH
                .apply(new PostgresConstant[] { array, PostgresConstant.createTextConstant("1") });

        assertEquals(2, result.asInt());
    }

    @Test
    void arrayLengthReturnsNullForStringDimensionOutsideArrayBounds() {
        PostgresConstant array = PostgresConstant.createArrayConstant(
                List.of(PostgresConstant.createIntConstant(1), PostgresConstant.createIntConstant(2)),
                PostgresCompoundDataType.create(PostgresDataType.INT));

        PostgresConstant result = PostgresFunctionWithResult.ARRAY_LENGTH
                .apply(new PostgresConstant[] { array, PostgresConstant.createTextConstant("-152116979") });

        assertTrue(result.isNull());
    }
}
