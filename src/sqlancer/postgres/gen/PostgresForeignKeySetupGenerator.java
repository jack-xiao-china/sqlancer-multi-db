package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresForeignKeyValuePool;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresProvider;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTable.TableType;

public final class PostgresForeignKeySetupGenerator {

    private PostgresForeignKeySetupGenerator() {
    }

    private enum Topology {
        STAR, CHAIN, SELF_REFERENCE, CYCLE, COMPOSITE, EXISTING_COLUMNS
    }

    private enum ForeignKeyType {
        INT("integer"),
        BOOLEAN("boolean"),
        TEXT("TEXT"),
        VARCHAR("varchar(500)"),
        CHAR("char(500)"),
        NUMERIC("numeric"),
        DOUBLE_PRECISION("double precision"),
        REAL("real"),
        MONEY("money"),
        VARBIT("bit varying(500)"),
        INET("inet"),
        UUID("uuid"),
        DATE("date"),
        TIME("time"),
        TIMETZ("timetz"),
        TIMESTAMP("timestamp"),
        TIMESTAMPTZ("timestamptz"),
        INTERVAL("interval"),
        BYTEA("bytea"),
        INT4RANGE("int4range"),
        ENUM(null);

        private final String sqlTypeName;

        ForeignKeyType(String sqlTypeName) {
            this.sqlTypeName = sqlTypeName;
        }
    }

    private static final class ExistingColumnPair {
        private final PostgresTable referencedTable;
        private final PostgresColumn referencedColumn;
        private final PostgresTable childTable;
        private final PostgresColumn childColumn;

        private ExistingColumnPair(PostgresTable referencedTable, PostgresColumn referencedColumn,
                PostgresTable childTable, PostgresColumn childColumn) {
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.childTable = childTable;
            this.childColumn = childColumn;
        }
    }

    private static final ForeignKeyType[] HIGH_SUCCESS_TYPES = { ForeignKeyType.INT, ForeignKeyType.TEXT,
            ForeignKeyType.VARCHAR, ForeignKeyType.CHAR, ForeignKeyType.UUID, ForeignKeyType.DATE,
            ForeignKeyType.TIMESTAMP, ForeignKeyType.TIMESTAMPTZ };

    private static final ForeignKeyType[] MEDIUM_SUCCESS_TYPES = { ForeignKeyType.NUMERIC,
            ForeignKeyType.DOUBLE_PRECISION, ForeignKeyType.REAL, ForeignKeyType.MONEY, ForeignKeyType.TIME,
            ForeignKeyType.TIMETZ, ForeignKeyType.INTERVAL, ForeignKeyType.ENUM };

    private static final ForeignKeyType[] EXPLORATORY_TYPES = { ForeignKeyType.BOOLEAN, ForeignKeyType.VARBIT,
            ForeignKeyType.INET, ForeignKeyType.BYTEA, ForeignKeyType.INT4RANGE };

    private static final Topology[] TOPOLOGIES = { Topology.STAR, Topology.STAR, Topology.STAR, Topology.STAR,
            Topology.CHAIN, Topology.CHAIN, Topology.SELF_REFERENCE, Topology.CYCLE, Topology.COMPOSITE,
            Topology.EXISTING_COLUMNS };

    public static void setup(PostgresGlobalState globalState) throws Exception {
        if (!globalState.getDbmsSpecificOptions().testForeignKeys()) {
            return;
        }
        if (globalState.getSchema().getDatabaseTables().stream().filter(t -> !t.isView()).count() < 1) {
            return;
        }
        int targetGroups = globalState.getRandomly().getInteger(2, 5);
        int groupId = 0;
        int attempts = targetGroups * 4;
        while (groupId < targetGroups && attempts-- > 0) {
            if (tryCreateTopology(globalState, groupId)) {
                groupId++;
            }
        }
    }

    private static boolean tryCreateTopology(PostgresGlobalState globalState, int groupId) throws Exception {
        Topology topology = Randomly.fromOptions(TOPOLOGIES);
        switch (topology) {
        case STAR:
            return createStarGroup(globalState, groupId);
        case CHAIN:
            return createChainGroup(globalState, groupId);
        case SELF_REFERENCE:
            return createSelfReferenceGroup(globalState, groupId);
        case CYCLE:
            return createCycleGroup(globalState, groupId);
        case COMPOSITE:
            return createCompositeGroup(globalState, groupId);
        case EXISTING_COLUMNS:
            return createExistingColumnGroup(globalState, groupId);
        default:
            throw new AssertionError(topology);
        }
    }

    private static boolean createStarGroup(PostgresGlobalState globalState, int groupId) throws Exception {
        List<PostgresTable> tables = getCompatibleTables(globalState);
        if (tables.size() < 2) {
            return false;
        }
        PostgresTable referencedTable = chooseReferencedTable(tables);
        if (referencedTable == null) {
            return false;
        }
        List<PostgresTable> childCandidates = tables.stream().filter(t -> t != referencedTable)
                .collect(Collectors.toList());
        if (childCandidates.isEmpty()) {
            return false;
        }
        ForeignKeyType type = getRandomForeignKeyType();
        String typeName = getTypeName(type);
        String referencedColumn = "fk_ref_" + groupId;
        if (!prepareReferencedColumns(globalState, referencedTable, List.of(referencedColumn), List.of(typeName))) {
            return false;
        }
        int childCount = Math.min(childCandidates.size(), globalState.getRandomly().getInteger(1, 4));
        boolean createdAny = false;
        for (PostgresTable childTable : chooseChildTables(childCandidates, childCount)) {
            String foreignKeyColumn = "fk_" + groupId + "_" + childTable.getName();
            boolean addDefault = shouldAddForeignKeyDefault(List.of(typeName));
            if (addForeignKeyColumns(globalState, childTable, List.of(foreignKeyColumn), List.of(typeName), addDefault)
                    && createForeignKey(globalState, groupId, referencedTable, List.of(referencedColumn), childTable,
                            List.of(foreignKeyColumn), false, addDefault, Topology.STAR)) {
                insertChildSeedRows(globalState, childTable.getName(), List.of(foreignKeyColumn), List.of(typeName));
                createdAny = true;
            }
        }
        return createdAny;
    }

    private static boolean createChainGroup(PostgresGlobalState globalState, int groupId) throws Exception {
        List<PostgresTable> tables = getCompatibleTables(globalState);
        if (tables.size() < 2) {
            return false;
        }
        int chainLength = Math.min(tables.size(), globalState.getRandomly().getInteger(2, 5));
        List<PostgresTable> chain = Randomly.nonEmptySubset(tables, chainLength);
        if (chain.size() < 2) {
            return false;
        }
        boolean createdAny = false;
        for (int i = 0; i < chain.size() - 1; i++) {
            PostgresTable referencedTable = chain.get(i);
            if (referencedTable.isPartitioned()) {
                continue;
            }
            PostgresTable childTable = chain.get(i + 1);
            ForeignKeyType type = getRandomForeignKeyType();
            String typeName = getTypeName(type);
            String referencedColumn = "fk_ref_" + groupId + "_" + i;
            String foreignKeyColumn = "fk_" + groupId + "_" + i;
            boolean addDefault = shouldAddForeignKeyDefault(List.of(typeName));
            if (prepareReferencedColumns(globalState, referencedTable, List.of(referencedColumn), List.of(typeName))
                    && addForeignKeyColumns(globalState, childTable, List.of(foreignKeyColumn),
                            List.of(typeName), addDefault)
                    && createForeignKey(globalState, groupId, referencedTable, List.of(referencedColumn), childTable,
                            List.of(foreignKeyColumn), false, addDefault, Topology.CHAIN)) {
                insertChildSeedRows(globalState, childTable.getName(), List.of(foreignKeyColumn), List.of(typeName));
                createdAny = true;
            }
        }
        return createdAny;
    }

    private static boolean createSelfReferenceGroup(PostgresGlobalState globalState, int groupId) throws Exception {
        List<PostgresTable> candidates = getSelfReferenceCandidates(globalState);
        if (candidates.isEmpty()) {
            return false;
        }
        PostgresTable table = Randomly.fromList(candidates);
        ForeignKeyType type = getRandomForeignKeyType();
        String typeName = getTypeName(type);
        String referencedColumn = "fk_ref_self_" + groupId;
        String foreignKeyColumn = "fk_self_" + groupId;
        boolean addDefault = shouldAddForeignKeyDefault(List.of(typeName));
        boolean created = prepareReferencedColumns(globalState, table, List.of(referencedColumn), List.of(typeName))
                && addForeignKeyColumns(globalState, table, List.of(foreignKeyColumn), List.of(typeName), addDefault)
                && createForeignKey(globalState, groupId, table, List.of(referencedColumn), table,
                        List.of(foreignKeyColumn), true, addDefault, Topology.SELF_REFERENCE);
        if (created) {
            insertChildSeedRows(globalState, table.getName(), List.of(foreignKeyColumn), List.of(typeName));
        }
        return created;
    }

    private static boolean createCycleGroup(PostgresGlobalState globalState, int groupId) throws Exception {
        List<PostgresTable> tables = getCompatibleTables(globalState).stream().filter(t -> !t.isPartitioned())
                .collect(Collectors.toList());
        if (tables.size() < 2) {
            return false;
        }
        List<PostgresTable> pair = Randomly.nonEmptySubset(tables, 2);
        if (pair.size() < 2) {
            return false;
        }
        PostgresTable first = pair.get(0);
        PostgresTable second = pair.get(1);
        ForeignKeyType type = getRandomForeignKeyType();
        String typeName = getTypeName(type);
        String firstRef = "fk_ref_cycle_a_" + groupId;
        String firstFk = "fk_cycle_a_" + groupId;
        String secondRef = "fk_ref_cycle_b_" + groupId;
        String secondFk = "fk_cycle_b_" + groupId;
        boolean addFirstDefault = shouldAddForeignKeyDefault(List.of(typeName));
        boolean addSecondDefault = shouldAddForeignKeyDefault(List.of(typeName));
        boolean firstReady = prepareReferencedColumns(globalState, first, List.of(firstRef), List.of(typeName))
                && addForeignKeyColumns(globalState, first, List.of(firstFk), List.of(typeName), addFirstDefault);
        boolean secondReady = prepareReferencedColumns(globalState, second, List.of(secondRef), List.of(typeName))
                && addForeignKeyColumns(globalState, second, List.of(secondFk), List.of(typeName), addSecondDefault);
        return firstReady && secondReady
                && createForeignKey(globalState, groupId, second, List.of(secondRef), first, List.of(firstFk),
                        true, addFirstDefault, Topology.CYCLE)
                && createForeignKey(globalState, groupId + 1000, first, List.of(firstRef), second, List.of(secondFk),
                        true, addSecondDefault, Topology.CYCLE)
                && seedCycleChildren(globalState, first.getName(), List.of(firstFk), second.getName(),
                        List.of(secondFk), List.of(typeName));
    }

    private static boolean createCompositeGroup(PostgresGlobalState globalState, int groupId) throws Exception {
        List<PostgresTable> tables = getCompatibleTables(globalState);
        if (tables.size() < 2) {
            return false;
        }
        PostgresTable referencedTable = chooseReferencedTable(tables);
        if (referencedTable == null) {
            return false;
        }
        List<PostgresTable> childCandidates = tables.stream().filter(t -> t != referencedTable)
                .collect(Collectors.toList());
        if (childCandidates.isEmpty()) {
            return false;
        }
        PostgresTable childTable = chooseChildTable(childCandidates);
        int columnCount = globalState.getRandomly().getInteger(2, 5);
        List<String> referencedColumns = new ArrayList<>();
        List<String> foreignKeyColumns = new ArrayList<>();
        List<String> typeNames = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            referencedColumns.add("fk_ref_comp_" + groupId + "_" + i);
            foreignKeyColumns.add("fk_comp_" + groupId + "_" + i);
            typeNames.add(getTypeName(getRandomForeignKeyType()));
        }
        boolean addDefault = shouldAddForeignKeyDefault(typeNames);
        boolean created = prepareReferencedColumns(globalState, referencedTable, referencedColumns, typeNames)
                && addForeignKeyColumns(globalState, childTable, foreignKeyColumns, typeNames, addDefault)
                && createForeignKey(globalState, groupId, referencedTable, referencedColumns, childTable,
                        foreignKeyColumns, false, addDefault, Topology.COMPOSITE);
        if (created) {
            insertChildSeedRows(globalState, childTable.getName(), foreignKeyColumns, typeNames);
        }
        return created;
    }

    private static boolean createExistingColumnGroup(PostgresGlobalState globalState, int groupId) throws Exception {
        List<PostgresTable> tables = getCompatibleTables(globalState);
        if (tables.size() < 2) {
            return false;
        }
        List<ExistingColumnPair> candidates = new ArrayList<>();
        for (PostgresTable referencedTable : tables) {
            if (referencedTable.isPartitioned()) {
                continue;
            }
            for (PostgresTable childTable : tables) {
                if (referencedTable == childTable) {
                    continue;
                }
                for (PostgresColumn referencedColumn : referencedTable.getColumns()) {
                    if (PostgresForeignKeyValuePool.getValues(referencedColumn).isEmpty()) {
                        continue;
                    }
                    for (PostgresColumn childColumn : childTable.getColumns()) {
                        if (referencedColumn.getCompoundType().equals(childColumn.getCompoundType())) {
                            candidates.add(new ExistingColumnPair(referencedTable, referencedColumn, childTable,
                                    childColumn));
                        }
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        ExistingColumnPair pair = Randomly.fromList(candidates);
        String uniqueConstraint = "fk_existing_unique_" + pair.referencedTable.getName() + "_"
                + pair.referencedColumn.getName() + "_" + groupId;
        String unique = "ALTER TABLE " + pair.referencedTable.getName() + " ADD CONSTRAINT " + uniqueConstraint
                + " UNIQUE(" + pair.referencedColumn.getName() + ")";
        if (!executeSchemaStatement(globalState, unique, constraintErrors())) {
            return false;
        }
        insertSeedRows(globalState, pair.referencedTable.getName(), List.of(pair.referencedColumn));
        boolean created = createForeignKey(globalState, groupId, pair.referencedTable,
                List.of(pair.referencedColumn.getName()), pair.childTable, List.of(pair.childColumn.getName()), false,
                false, Topology.EXISTING_COLUMNS);
        if (created) {
            insertChildSeedRows(globalState, pair.childTable.getName(), List.of(pair.childColumn));
        }
        return created;
    }

    private static boolean prepareReferencedColumns(PostgresGlobalState globalState, PostgresTable table,
            List<String> columns, List<String> typeNames) throws Exception {
        if (!addForeignKeyColumns(globalState, table, columns, typeNames, false)) {
            return false;
        }
        String uniqueConstraint = "fk_ref_unique_" + table.getName() + "_" + columns.get(0);
        String query = "ALTER TABLE " + table.getName() + " ADD CONSTRAINT " + uniqueConstraint + " UNIQUE("
                + String.join(", ", columns) + ")";
        if (!executeSchemaStatement(globalState, query, constraintErrors())) {
            return false;
        }
        return insertSeedRows(globalState, table.getName(), columns, typeNames);
    }

    private static boolean addForeignKeyColumns(PostgresGlobalState globalState, PostgresTable table,
            List<String> columns, List<String> typeNames, boolean addDefault) throws Exception {
        for (int i = 0; i < columns.size(); i++) {
            String query = "ALTER TABLE " + table.getName() + " ADD COLUMN " + columns.get(i) + " " + typeNames.get(i);
            if (addDefault) {
                query += " DEFAULT " + PostgresForeignKeyValuePool.getValues(typeNames.get(i)).get(0);
            }
            if (!executeSchemaStatement(globalState, query, addColumnErrors())) {
                return false;
            }
        }
        return true;
    }

    private static boolean createForeignKey(PostgresGlobalState globalState, int groupId, PostgresTable referencedTable,
            List<String> referencedColumns, PostgresTable childTable, List<String> foreignKeyColumns,
            boolean forceDeferred, boolean allowSetDefault, Topology topology) throws Exception {
        String onDelete = chooseReferentialAction(allowSetDefault, topology);
        String onUpdate = chooseReferentialAction(allowSetDefault, topology);
        String fkConstraint = "fk_" + childTable.getName() + "_" + groupId + "_" + referencedTable.getName();
        StringBuilder fk = new StringBuilder();
        fk.append("ALTER TABLE ");
        fk.append(childTable.getName());
        fk.append(" ADD CONSTRAINT ");
        fk.append(fkConstraint);
        fk.append(" FOREIGN KEY (");
        fk.append(String.join(", ", foreignKeyColumns));
        fk.append(") REFERENCES ");
        fk.append(referencedTable.getName());
        fk.append("(");
        fk.append(String.join(", ", referencedColumns));
        fk.append(") MATCH ");
        fk.append(chooseMatchMode(topology));
        fk.append(" ON DELETE ");
        fk.append(onDelete);
        fk.append(" ON UPDATE ");
        fk.append(onUpdate);
        if (forceDeferred) {
            fk.append(" DEFERRABLE INITIALLY DEFERRED");
        } else if (Randomly.getBooleanWithRatherLowProbability()) {
            fk.append(" DEFERRABLE INITIALLY DEFERRED");
        } else if (Randomly.getBooleanWithRatherLowProbability()) {
            fk.append(" DEFERRABLE INITIALLY IMMEDIATE");
        }
        boolean notValid = !forceDeferred && Randomly.getBooleanWithRatherLowProbability();
        if (notValid) {
            fk.append(" NOT VALID");
        }
        if (!executeSchemaStatement(globalState, fk.toString(), constraintErrors())) {
            return false;
        }
        if (notValid && Randomly.getBoolean()) {
            String validate = "ALTER TABLE " + childTable.getName() + " VALIDATE CONSTRAINT " + fkConstraint;
            executeSchemaStatement(globalState, validate, constraintErrors());
        }
        return true;
    }

    private static List<PostgresTable> getCompatibleTables(PostgresGlobalState globalState) {
        List<PostgresTable> standardTables = getTablesOfType(globalState, TableType.STANDARD);
        List<PostgresTable> temporaryTables = getTablesOfType(globalState, TableType.TEMPORARY);
        if (standardTables.size() >= 2 && temporaryTables.size() >= 2) {
            return Randomly.getBooleanWithRatherLowProbability() ? temporaryTables : standardTables;
        }
        if (standardTables.size() >= 2) {
            return standardTables;
        }
        return temporaryTables;
    }

    private static List<PostgresTable> getSelfReferenceCandidates(PostgresGlobalState globalState) {
        List<PostgresTable> candidates = new ArrayList<>();
        candidates.addAll(getTablesOfType(globalState, TableType.STANDARD));
        candidates.addAll(getTablesOfType(globalState, TableType.TEMPORARY));
        return candidates.stream().filter(t -> !t.isPartitioned()).collect(Collectors.toList());
    }

    private static List<PostgresTable> getTablesOfType(PostgresGlobalState globalState, TableType type) {
        return globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> !t.isView() && t.getTableType() == type).collect(Collectors.toList());
    }

    private static PostgresTable chooseReferencedTable(List<PostgresTable> candidates) {
        List<PostgresTable> referencedCandidates = candidates.stream().filter(t -> !t.isPartitioned())
                .collect(Collectors.toList());
        if (referencedCandidates.isEmpty()) {
            return null;
        }
        return Randomly.fromList(referencedCandidates);
    }

    private static String getTypeName(ForeignKeyType type) {
        if (type == ForeignKeyType.ENUM) {
            return PostgresProvider.getRandomEnumTypeName();
        }
        return type.sqlTypeName;
    }

    private static ForeignKeyType getRandomForeignKeyType() {
        int tier = (int) Randomly.getNotCachedInteger(0, 10);
        if (tier < 6) {
            return Randomly.fromOptions(HIGH_SUCCESS_TYPES);
        } else if (tier < 9) {
            return Randomly.fromOptions(MEDIUM_SUCCESS_TYPES);
        }
        return Randomly.fromOptions(EXPLORATORY_TYPES);
    }

    private static String chooseReferentialAction(boolean allowSetDefault, Topology topology) {
        int action = (int) Randomly.getNotCachedInteger(0, 10);
        if ((topology == Topology.STAR || topology == Topology.CHAIN) && action < 5) {
            return "CASCADE";
        } else if (action < 4) {
            return "CASCADE";
        } else if (action < 6) {
            return "SET NULL";
        } else if (allowSetDefault && action < 7) {
            return "SET DEFAULT";
        } else if (action < 9) {
            return "RESTRICT";
        }
        return "NO ACTION";
    }

    private static String chooseMatchMode(Topology topology) {
        if (topology == Topology.COMPOSITE && Randomly.getBoolean()) {
            return "FULL";
        }
        return Randomly.getBooleanWithRatherLowProbability() ? "FULL" : "SIMPLE";
    }

    private static boolean shouldAddForeignKeyDefault(List<String> typeNames) {
        return Randomly.getBooleanWithRatherLowProbability()
                && typeNames.stream().allMatch(t -> !PostgresForeignKeyValuePool.getValues(t).isEmpty());
    }

    private static List<PostgresTable> chooseChildTables(List<PostgresTable> childCandidates, int childCount) {
        List<PostgresTable> selected = new ArrayList<>();
        PostgresTable partitionedChild = choosePartitionedChild(childCandidates);
        if (partitionedChild != null) {
            selected.add(partitionedChild);
        }
        for (PostgresTable table : Randomly.nonEmptySubset(childCandidates, childCount)) {
            if (!selected.contains(table)) {
                selected.add(table);
            }
            if (selected.size() >= childCount) {
                break;
            }
        }
        return selected;
    }

    private static PostgresTable chooseChildTable(List<PostgresTable> childCandidates) {
        PostgresTable partitionedChild = choosePartitionedChild(childCandidates);
        if (partitionedChild != null) {
            return partitionedChild;
        }
        return Randomly.fromList(childCandidates);
    }

    private static PostgresTable choosePartitionedChild(List<PostgresTable> childCandidates) {
        List<PostgresTable> partitionedChildren = childCandidates.stream().filter(PostgresTable::isPartitioned)
                .collect(Collectors.toList());
        if (partitionedChildren.isEmpty() || !Randomly.getBooleanWithRatherLowProbability()) {
            return null;
        }
        return Randomly.fromList(partitionedChildren);
    }

    private static boolean executeSchemaStatement(PostgresGlobalState globalState, String query, ExpectedErrors errors)
            throws Exception {
        return globalState.executeStatement(new SQLQueryAdapter(query, errors, true));
    }

    private static boolean insertSeedRows(PostgresGlobalState globalState, String tableName, List<String> columns,
            List<String> typeNames) throws Exception {
        return insertRows(globalState, tableName, columns, typeNames, true);
    }

    private static boolean insertSeedRows(PostgresGlobalState globalState, String tableName,
            List<PostgresColumn> columns) throws Exception {
        return insertRows(globalState, tableName, columns, true);
    }

    private static boolean insertChildSeedRows(PostgresGlobalState globalState, String tableName, List<String> columns,
            List<String> typeNames) throws Exception {
        return insertRows(globalState, tableName, columns, typeNames, false);
    }

    private static boolean insertChildSeedRows(PostgresGlobalState globalState, String tableName,
            List<PostgresColumn> columns) throws Exception {
        return insertRows(globalState, tableName, columns, false);
    }

    private static boolean seedCycleChildren(PostgresGlobalState globalState, String firstTable, List<String> firstColumns,
            String secondTable, List<String> secondColumns, List<String> typeNames) throws Exception {
        insertChildSeedRows(globalState, firstTable, firstColumns, typeNames);
        insertChildSeedRows(globalState, secondTable, secondColumns, typeNames);
        return true;
    }

    private static boolean insertRows(PostgresGlobalState globalState, String tableName, List<String> columns,
            List<String> typeNames, boolean onConflictDoNothing) throws Exception {
        List<List<String>> valuesByColumn = typeNames.stream().map(PostgresForeignKeyValuePool::getValues)
                .collect(Collectors.toList());
        int rowCount = valuesByColumn.stream().mapToInt(List::size).min().orElse(0);
        if (rowCount == 0) {
            return false;
        }
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            List<String> rowValues = new ArrayList<>();
            for (List<String> columnValues : valuesByColumn) {
                rowValues.add(columnValues.get(row));
            }
            rows.add("(" + String.join(", ", rowValues) + ")");
        }
        String values = String.join(", ", rows);
        String query = "INSERT INTO " + tableName + "(" + String.join(", ", columns) + ") VALUES " + values;
        if (onConflictDoNothing) {
            query += " ON CONFLICT (" + String.join(", ", columns) + ") DO NOTHING";
        }
        return globalState.executeStatement(new SQLQueryAdapter(query, seedErrors()));
    }

    private static boolean insertRows(PostgresGlobalState globalState, String tableName, List<PostgresColumn> columns,
            boolean onConflictDoNothing) throws Exception {
        List<List<String>> valuesByColumn = columns.stream().map(PostgresForeignKeyValuePool::getValues)
                .collect(Collectors.toList());
        int rowCount = valuesByColumn.stream().mapToInt(List::size).min().orElse(0);
        if (rowCount == 0) {
            return false;
        }
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            List<String> rowValues = new ArrayList<>();
            for (List<String> columnValues : valuesByColumn) {
                rowValues.add(columnValues.get(row));
            }
            rows.add("(" + String.join(", ", rowValues) + ")");
        }
        String columnNames = columns.stream().map(PostgresColumn::getName).collect(Collectors.joining(", "));
        String query = "INSERT INTO " + tableName + "(" + columnNames + ") VALUES " + String.join(", ", rows);
        if (onConflictDoNothing) {
            query += " ON CONFLICT (" + columnNames + ") DO NOTHING";
        }
        return globalState.executeStatement(new SQLQueryAdapter(query, seedErrors()));
    }

    private static ExpectedErrors addColumnErrors() {
        return ExpectedErrors.from("already exists", "cannot add column", "cannot add a column to a partition",
                "does not exist", "does not accept data type", "cannot be cast automatically");
    }

    private static ExpectedErrors constraintErrors() {
        return ExpectedErrors.from("already exists", "could not create unique index",
                "duplicate key value violates unique constraint", "cannot reference partitioned table",
                "there is no unique constraint matching given keys for referenced table",
                "constraints on temporary tables may reference only temporary tables",
                "constraints on permanent tables may reference only permanent tables",
                "constraints on unlogged tables may reference only permanent or unlogged tables",
                "violates foreign key constraint", "is violated by some row", "cannot add constraint",
                "cannot add UNIQUE constraint to partitioned table",
                "cannot add NOT VALID foreign key on partitioned table",
                "cannot validate constraint",
                "is being used by active queries in this session");
    }

    private static ExpectedErrors seedErrors() {
        ExpectedErrors errors = ExpectedErrors.from("duplicate key value violates unique constraint",
                "violates not-null constraint", "violates check constraint", "violates foreign key constraint",
                "cannot insert into column", "identity column defined as GENERATED ALWAYS",
                "there is no unique or exclusion constraint matching the ON CONFLICT specification");
        PostgresCommon.addCommonInsertUpdateErrors(errors);
        PostgresCommon.addCommonExpressionErrors(errors);
        return errors;
    }
}
