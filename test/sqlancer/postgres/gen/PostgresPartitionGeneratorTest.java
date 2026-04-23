package sqlancer.postgres.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTable.PartitionStrategy;
import sqlancer.postgres.PostgresSchema.PostgresTable.TableType;

class PostgresPartitionGeneratorTest {

    @Test
    void rendersRangeBoundsForSimpleIntegerPartitionKey() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        PostgresTable parent = partitionedTable(column, PartitionStrategy.RANGE);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, parent, column, 2);

        assertEquals("FOR VALUES FROM (200) TO (300)", sb.toString());
    }

    @Test
    void rendersListBoundsForTextPartitionKey() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.TEXT);
        PostgresTable parent = partitionedTable(column, PartitionStrategy.LIST);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, parent, column, 3);

        assertEquals("FOR VALUES IN ('sqlancer_partition_3')", sb.toString());
    }

    @Test
    void rendersHashBoundsWithStableModulus() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        PostgresTable parent = partitionedTable(column, PartitionStrategy.HASH);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, parent, column, 5);

        assertEquals("FOR VALUES WITH (MODULUS 8, REMAINDER 5)", sb.toString());
    }

    @Test
    void rendersRangeRangeSubpartitionClause() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, partitionedTable(column, PartitionStrategy.RANGE), column,
                0);
        PostgresPartitionGenerator.appendPartitionBy(sb, PartitionStrategy.RANGE, column);

        assertEquals("FOR VALUES FROM (0) TO (100) PARTITION BY RANGE(c0)", sb.toString());
    }

    @Test
    void rendersRangeListSubpartitionClause() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, partitionedTable(column, PartitionStrategy.RANGE), column,
                0);
        PostgresPartitionGenerator.appendPartitionBy(sb, PartitionStrategy.LIST, column);

        assertEquals("FOR VALUES FROM (0) TO (100) PARTITION BY LIST(c0)", sb.toString());
    }

    @Test
    void rendersListRangeSubpartitionClause() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, partitionedTable(column, PartitionStrategy.LIST), column,
                0);
        PostgresPartitionGenerator.appendPartitionBy(sb, PartitionStrategy.RANGE, column);

        assertEquals("FOR VALUES IN (0) PARTITION BY RANGE(c0)", sb.toString());
    }

    @Test
    void rendersListListSubpartitionClause() {
        PostgresColumn column = new PostgresColumn("c0", PostgresDataType.INT);
        StringBuilder sb = new StringBuilder();

        PostgresPartitionGenerator.appendPartitionBound(sb, partitionedTable(column, PartitionStrategy.LIST), column,
                0);
        PostgresPartitionGenerator.appendPartitionBy(sb, PartitionStrategy.LIST, column);

        assertEquals("FOR VALUES IN (0) PARTITION BY LIST(c0)", sb.toString());
    }

    private static PostgresTable partitionedTable(PostgresColumn column, PartitionStrategy strategy) {
        PostgresTable table = new PostgresTable("t0", List.of(column), List.of(), TableType.STANDARD, List.of(),
                List.of(), false, true, true, false, null, strategy, List.of(column.getName()));
        column.setTable(table);
        return table;
    }
}
