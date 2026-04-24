package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTable.PartitionStrategy;

public final class PostgresPartitionGenerator {

    private static final int HASH_MODULUS = 8;
    private static final String DEFAULT_PARTITION_SUFFIX = "_default";

    private PostgresPartitionGenerator() {
    }

    public static SQLQueryAdapter createPartition(PostgresGlobalState globalState) {
        List<PostgresTable> candidates = globalState.getSchema().getDatabaseTables().stream()
                .filter(PostgresPartitionGenerator::canCreatePartitionFor).collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new IgnoreMeException();
        }
        PostgresTable parent = selectPartitionParent(candidates);
        int partitionIndex = getNextPartitionIndex(globalState, parent);
        if (parent.getPartitionStrategy() == PartitionStrategy.HASH && partitionIndex >= HASH_MODULUS) {
            throw new IgnoreMeException();
        }
        boolean createDefaultPartition = shouldCreateDefaultPartition(globalState, parent);

        ExpectedErrors errors = createPartitionErrors();
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ");
        if (createDefaultPartition) {
            sb.append(createDefaultPartitionName(parent));
        } else {
            sb.append(createPartitionName(globalState, parent, partitionIndex));
        }
        sb.append(" PARTITION OF ");
        sb.append(parent.getName());
        sb.append(" ");
        if (createDefaultPartition) {
            sb.append("DEFAULT");
        } else {
            appendPartitionBound(sb, parent, getPartitionKeyColumn(parent), partitionIndex);
            PartitionStrategy subpartitionStrategy = getSubpartitionStrategy(parent);
            if (subpartitionStrategy != PartitionStrategy.NONE) {
                appendPartitionBy(sb, subpartitionStrategy, getPartitionKeyColumn(parent));
            }
        }
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    public static SQLQueryAdapter detachPartition(PostgresGlobalState globalState) {
        List<PostgresTable> candidates = globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> t.isPartition() && t.getPartitionParent() != null).collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new IgnoreMeException();
        }
        PostgresTable child = Randomly.fromList(candidates);
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("is not a partition of relation");
        errors.add("relation does not exist");
        errors.add("cannot detach partitions concurrently");
        errors.add("cannot run inside a transaction block");
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(child.getPartitionParent());
        sb.append(" DETACH PARTITION ");
        sb.append(child.getName());
        if (Randomly.getBooleanWithRatherLowProbability()) {
            sb.append(Randomly.fromOptions(" CONCURRENTLY", " FINALIZE"));
        }
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    public static SQLQueryAdapter dropPartition(PostgresGlobalState globalState) {
        List<PostgresTable> candidates = globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> t.isPartition() && t.getPartitionParent() != null).collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new IgnoreMeException();
        }
        PostgresTable child = Randomly.fromList(candidates);
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("does not exist");
        errors.add("cannot drop table");
        errors.add("because other objects depend on it");
        errors.add("cannot drop desired object(s) because other objects depend on them");
        StringBuilder sb = new StringBuilder();
        sb.append("DROP TABLE ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }
        sb.append(child.getName());
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("RESTRICT", "CASCADE"));
        }
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    public static SQLQueryAdapter attachPartition(PostgresGlobalState globalState) {
        List<PostgresTable> parents = globalState.getSchema().getDatabaseTables().stream()
                .filter(PostgresPartitionGenerator::canCreatePartitionFor).collect(Collectors.toList());
        if (parents.isEmpty()) {
            throw new IgnoreMeException();
        }
        PostgresTable parent = selectPartitionParent(parents);
        int partitionIndex = getNextPartitionIndex(globalState, parent);
        if (parent.getPartitionStrategy() == PartitionStrategy.HASH && partitionIndex >= HASH_MODULUS) {
            throw new IgnoreMeException();
        }
        List<PostgresTable> children = getAttachablePartitionChildren(globalState, parent);
        if (children.isEmpty()) {
            throw new IgnoreMeException();
        }

        PostgresTable child = Randomly.fromList(children);
        ExpectedErrors errors = createPartitionErrors();
        errors.add("is already a partition");
        errors.add("cannot attach partition");
        errors.add("is not a table");
        errors.add("is a partitioned table");
        errors.add("table contains rows that violate the partition constraint");
        errors.add("updated partition constraint for default partition");
        errors.add("would be violated by some row");
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(parent.getName());
        sb.append(" ATTACH PARTITION ");
        sb.append(child.getName());
        sb.append(" ");
        appendPartitionBound(sb, parent, getPartitionKeyColumn(parent), partitionIndex);
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    private static boolean canCreatePartitionFor(PostgresTable parent) {
        if (!parent.isPartitioned() || !parent.hasSimplePartitionKey()) {
            return false;
        }
        PostgresColumn keyColumn = getPartitionKeyColumn(parent);
        if (keyColumn == null || keyColumn.getCompoundType().isArray()) {
            return false;
        }
        switch (parent.getPartitionStrategy()) {
        case RANGE:
            return isRangePartitionType(keyColumn.getCompoundType().getDataType());
        case LIST:
            return isListPartitionType(keyColumn.getCompoundType().getDataType());
        case HASH:
            return true;
        case NONE:
        default:
            return false;
        }
    }

    public static boolean hasCreatePartitionCandidate(PostgresGlobalState globalState) {
        return globalState.getSchema().getDatabaseTables().stream().anyMatch(PostgresPartitionGenerator::canCreatePartitionFor);
    }

    public static boolean hasAttachPartitionCandidate(PostgresGlobalState globalState) {
        List<PostgresTable> parents = globalState.getSchema().getDatabaseTables().stream()
                .filter(PostgresPartitionGenerator::canCreatePartitionFor).collect(Collectors.toList());
        for (PostgresTable parent : parents) {
            if (!getAttachablePartitionChildren(globalState, parent).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean canGeneratePartitionRoutingValue(PostgresGlobalState globalState, PostgresTable parent) {
        try {
            getPartitionRoutingValue(globalState, parent);
            return true;
        } catch (IgnoreMeException ignored) {
            return false;
        }
    }

    static PostgresColumn getSimplePartitionKeyColumn(PostgresTable parent) {
        return getPartitionKeyColumn(parent);
    }

    static String getPartitionRoutingValue(PostgresGlobalState globalState, PostgresTable parent) {
        if (!canCreatePartitionFor(parent)) {
            throw new IgnoreMeException();
        }
        List<Integer> partitionIndexes = getExistingPartitionIndexes(globalState, parent);
        if (partitionIndexes.isEmpty()) {
            throw new IgnoreMeException();
        }
        int partitionIndex = Randomly.fromList(partitionIndexes);
        PostgresColumn keyColumn = getPartitionKeyColumn(parent);
        switch (parent.getPartitionStrategy()) {
        case RANGE:
            return rangeRoutingLiteral(keyColumn.getCompoundType().getDataType(), partitionIndex);
        case LIST:
            return listLiteral(keyColumn.getCompoundType().getDataType(), partitionIndex);
        case HASH:
        case NONE:
        default:
            throw new IgnoreMeException();
        }
    }

    private static PostgresTable selectPartitionParent(List<PostgresTable> candidates) {
        List<PostgresTable> subpartitionedPartitions = candidates.stream()
                .filter(t -> t.isPartition() && t.isPartitioned()).collect(Collectors.toList());
        if (!subpartitionedPartitions.isEmpty() && Randomly.getBoolean()) {
            return Randomly.fromList(subpartitionedPartitions);
        }
        return Randomly.fromList(candidates);
    }

    private static boolean isRangePartitionType(PostgresDataType dataType) {
        return dataType == PostgresDataType.INT || dataType == PostgresDataType.DATE
                || dataType == PostgresDataType.TIMESTAMP || dataType == PostgresDataType.TIMESTAMPTZ;
    }

    private static boolean isListPartitionType(PostgresDataType dataType) {
        return dataType == PostgresDataType.INT || dataType == PostgresDataType.TEXT
                || dataType == PostgresDataType.ENUM || dataType == PostgresDataType.BOOLEAN;
    }

    private static List<PostgresTable> getAttachablePartitionChildren(PostgresGlobalState globalState,
            PostgresTable parent) {
        PostgresColumn parentKeyColumn = getPartitionKeyColumn(parent);
        if (parentKeyColumn == null) {
            return List.of();
        }
        return globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> canAttachAsPartitionChild(parent, parentKeyColumn, t)).collect(Collectors.toList());
    }

    private static boolean canAttachAsPartitionChild(PostgresTable parent, PostgresColumn parentKeyColumn,
            PostgresTable child) {
        if (child.isView() || child.isPartition() || child.isPartitioned() || child == parent) {
            return false;
        }
        if (child.getTableType() != parent.getTableType()) {
            return false;
        }
        PostgresColumn childKeyColumn = findColumn(child, parentKeyColumn.getName());
        return childKeyColumn != null && childKeyColumn.getCompoundType().equals(parentKeyColumn.getCompoundType());
    }

    private static PostgresColumn findColumn(PostgresTable table, String columnName) {
        for (PostgresColumn column : table.getColumns()) {
            if (column.getName().equals(columnName)) {
                return column;
            }
        }
        return null;
    }

    private static PostgresColumn getPartitionKeyColumn(PostgresTable parent) {
        if (!parent.hasSimplePartitionKey()) {
            return null;
        }
        String keyColumnName = parent.getPartitionKeyColumns().get(0);
        for (PostgresColumn column : parent.getColumns()) {
            if (column.getName().equals(keyColumnName)) {
                return column;
            }
        }
        return null;
    }

    private static int getNextPartitionIndex(PostgresGlobalState globalState, PostgresTable parent) {
        String prefix = parent.getName() + "_p";
        int next = 0;
        for (PostgresTable table : globalState.getSchema().getDatabaseTables()) {
            if (!table.getName().startsWith(prefix)) {
                continue;
            }
            String suffix = table.getName().substring(prefix.length());
            try {
                next = Math.max(next, Integer.parseInt(suffix) + 1);
            } catch (NumberFormatException ignored) {
                next++;
            }
        }
        return next;
    }

    private static List<Integer> getExistingPartitionIndexes(PostgresGlobalState globalState, PostgresTable parent) {
        List<Integer> indexes = new ArrayList<>();
        String prefix = parent.getName() + "_p";
        for (PostgresTable table : globalState.getSchema().getDatabaseTables()) {
            if (!parent.getName().equals(table.getPartitionParent()) || !table.getName().startsWith(prefix)) {
                continue;
            }
            String suffix = table.getName().substring(prefix.length());
            try {
                indexes.add(Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
            }
        }
        return indexes;
    }

    static String createPartitionName(PostgresGlobalState globalState, PostgresTable parent, int partitionIndex) {
        String prefix = parent.getName() + "_p";
        int candidateIndex = partitionIndex;
        while (true) {
            String candidate = prefix + candidateIndex;
            boolean exists = globalState.getSchema().getDatabaseTables().stream()
                    .anyMatch(table -> table.getName().equals(candidate));
            if (!exists) {
                return candidate;
            }
            candidateIndex++;
        }
    }

    private static boolean shouldCreateDefaultPartition(PostgresGlobalState globalState, PostgresTable parent) {
        if (parent.getPartitionStrategy() == PartitionStrategy.HASH || Randomly.getBooleanWithRatherLowProbability()) {
            return false;
        }
        String defaultPartitionName = createDefaultPartitionName(parent);
        return globalState.getSchema().getDatabaseTables().stream()
                .noneMatch(table -> table.getName().equals(defaultPartitionName));
    }

    private static String createDefaultPartitionName(PostgresTable parent) {
        return parent.getName() + DEFAULT_PARTITION_SUFFIX;
    }

    static void appendPartitionBound(StringBuilder sb, PostgresTable parent, PostgresColumn keyColumn,
            int partitionIndex) {
        switch (parent.getPartitionStrategy()) {
        case RANGE:
            sb.append("FOR VALUES FROM (");
            sb.append(rangeLiteral(keyColumn.getCompoundType().getDataType(), partitionIndex));
            sb.append(") TO (");
            sb.append(rangeLiteral(keyColumn.getCompoundType().getDataType(), partitionIndex + 1));
            sb.append(")");
            break;
        case LIST:
            sb.append("FOR VALUES IN (");
            sb.append(listLiteral(keyColumn.getCompoundType().getDataType(), partitionIndex));
            sb.append(")");
            break;
        case HASH:
            sb.append("FOR VALUES WITH (MODULUS ");
            sb.append(HASH_MODULUS);
            sb.append(", REMAINDER ");
            sb.append(partitionIndex);
            sb.append(")");
            break;
        case NONE:
        default:
            throw new IgnoreMeException();
        }
    }

    private static PartitionStrategy getSubpartitionStrategy(PostgresTable parent) {
        List<PartitionStrategy> supportedStrategies = getSupportedSubpartitionStrategies(parent);
        if (supportedStrategies.isEmpty() || !Randomly.getBooleanWithRatherLowProbability()) {
            return PartitionStrategy.NONE;
        }
        return Randomly.fromList(supportedStrategies);
    }

    private static List<PartitionStrategy> getSupportedSubpartitionStrategies(PostgresTable parent) {
        List<PartitionStrategy> strategies = new ArrayList<>();
        if (parent.isPartition() || !parent.hasSimplePartitionKey()) {
            return strategies;
        }
        if (parent.getPartitionStrategy() != PartitionStrategy.RANGE
                && parent.getPartitionStrategy() != PartitionStrategy.LIST) {
            return strategies;
        }
        PostgresColumn keyColumn = getPartitionKeyColumn(parent);
        if (keyColumn == null || keyColumn.getCompoundType().isArray()) {
            return strategies;
        }
        PostgresDataType dataType = keyColumn.getCompoundType().getDataType();
        if (isRangePartitionType(dataType)) {
            strategies.add(PartitionStrategy.RANGE);
        }
        if (isListPartitionType(dataType)) {
            strategies.add(PartitionStrategy.LIST);
        }
        return strategies;
    }

    static void appendPartitionBy(StringBuilder sb, PartitionStrategy strategy, PostgresColumn keyColumn) {
        switch (strategy) {
        case RANGE:
        case LIST:
            sb.append(" PARTITION BY ");
            sb.append(strategy.name());
            sb.append("(");
            sb.append(keyColumn.getName());
            sb.append(")");
            break;
        case HASH:
        case NONE:
        default:
            throw new IgnoreMeException();
        }
    }

    private static String rangeLiteral(PostgresDataType dataType, int partitionIndex) {
        int value = partitionIndex * 100;
        switch (dataType) {
        case INT:
            return String.valueOf(value);
        case DATE:
            return "'2000-01-01'::date + " + value;
        case TIMESTAMP:
            return "'2000-01-01 00:00:00'::timestamp + interval '" + value + " days'";
        case TIMESTAMPTZ:
            return "'2000-01-01 00:00:00+00'::timestamptz + interval '" + value + " days'";
        default:
            throw new IgnoreMeException();
        }
    }

    private static String rangeRoutingLiteral(PostgresDataType dataType, int partitionIndex) {
        int value = partitionIndex * 100 + 1;
        switch (dataType) {
        case INT:
            return String.valueOf(value);
        case DATE:
            return "'2000-01-01'::date + " + value;
        case TIMESTAMP:
            return "'2000-01-01 00:00:00'::timestamp + interval '" + value + " days'";
        case TIMESTAMPTZ:
            return "'2000-01-01 00:00:00+00'::timestamptz + interval '" + value + " days'";
        default:
            throw new IgnoreMeException();
        }
    }

    private static String listLiteral(PostgresDataType dataType, int partitionIndex) {
        switch (dataType) {
        case INT:
            return String.valueOf(partitionIndex);
        case TEXT:
            return "'" + escapeSql("sqlancer_partition_" + partitionIndex) + "'";
        case ENUM:
            String[] labels = { "a", "b", "c", "d" };
            if (partitionIndex >= labels.length) {
                throw new IgnoreMeException();
            }
            return "'" + escapeSql(labels[partitionIndex]) + "'";
        case BOOLEAN:
            if (partitionIndex > 1) {
                throw new IgnoreMeException();
            }
            return partitionIndex == 0 ? "false" : "true";
        default:
            throw new IgnoreMeException();
        }
    }

    private static String escapeSql(String text) {
        return text.replace("'", "''");
    }

    private static ExpectedErrors createPartitionErrors() {
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("would overlap partition");
        errors.add("cannot create partition");
        errors.add("contains a whole-row variable");
        errors.add("cannot use column reference in partition bound expression");
        errors.add("specified more than once");
        errors.add("remainder for hash partition must be less than modulus");
        errors.add("partition would overlap");
        errors.add("relation already exists");
        errors.add("a hash-partitioned table may not have a default partition");
        errors.add("no partition of relation");
        errors.add("invalid input syntax");
        return errors;
    }
}
