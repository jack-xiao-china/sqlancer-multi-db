package sqlancer.postgres.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.beust.jcommander.Strings;

import sqlancer.Randomly;
import sqlancer.common.oracle.EDCBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.postgres.PostgresEDC;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresColumnReference;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL EDC (Equivalent Database Construction) Oracle Implementation.
 *
 * Detects PostgreSQL optimizer bugs by comparing query results between:
 * - Original Database: Has constraints (NOT NULL, UNIQUE, FK, CHECK, GENERATED)
 *   that affect query optimization
 * - Raw Database: No constraints, pure data copy (non-optimized execution)
 *
 * If the same query produces different results on original vs raw database,
 * it indicates an optimizer bug where constraints were incorrectly handled
 * in the query plan.
 *
 * Bug categories detected:
 * - Constraint optimization bugs (NOT NULL, UNIQUE incorrectly optimized)
 * - Foreign key handling bugs
 * - Generated column evaluation bugs
 * - CHECK constraint bypass bugs
 */
public class PostgresEDCOracle extends EDCBase<PostgresGlobalState> implements TestOracle<PostgresGlobalState> {

    public PostgresEDCOracle(PostgresGlobalState originalState) {
        super(originalState);
        PostgresCommon.addCommonExpressionErrors(errors);
        PostgresCommon.addCommonFetchErrors(errors);
    }

    /**
     * Obtain table schemas with constraint metadata for diversity checking.
     *
     * Parses PostgreSQL system catalogs to extract:
     * - Column metadata (type, NOT NULL, DEFAULT, GENERATED)
     * - Table constraints (UNIQUE, PRIMARY KEY, FOREIGN KEY, CHECK)
     *
     * @param state PostgreSQL global state
     * @return Map of table name to column/constraint metadata
     */
    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(PostgresGlobalState state) throws SQLException {
        Map<String, Map<String, List<String>>> tableSchema = new HashMap<>();
        List<String> foreignKeyList = new ArrayList<>();

        for (PostgresTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            String tableName = table.getName();
            if (table.isPartition() || table.isPartitioned()) {
                continue; // Skip partition tables
            }

            try (Statement statement = state.getConnection().createStatement()) {
                // Query column information
                ResultSet columnRs = statement.executeQuery(
                        "SELECT column_name, data_type, is_nullable, column_default, " +
                        "pg_catalog.format_type(atttypid, atttypmod) as formatted_type " +
                        "FROM information_schema.columns " +
                        "JOIN pg_catalog.pg_attribute ON attname = column_name " +
                        "JOIN pg_catalog.pg_class ON pg_class.oid = attrelid " +
                        "JOIN pg_catalog.pg_namespace ON pg_namespace.oid = pg_class.relnamespace " +
                        "WHERE table_schema = 'public' AND pg_class.relname = '" + tableName + "' " +
                        "AND attnum > 0 ORDER BY ordinal_position");

                tableSchema.put(tableName, new HashMap<>());

                while (columnRs.next()) {
                    String columnName = columnRs.getString("column_name");
                    String dataType = columnRs.getString("data_type");
                    String isNullable = columnRs.getString("is_nullable");
                    String columnDefault = columnRs.getString("column_default");
                    String formattedType = columnRs.getString("formatted_type");

                    List<String> metaElements = new ArrayList<>();
                    metaElements.add(formattedType != null ? formattedType : dataType);

                    if ("NO".equalsIgnoreCase(isNullable)) {
                        metaElements.add("NOT NULL");
                    }

                    if (columnDefault != null) {
                        if (columnDefault.contains("GENERATED")) {
                            metaElements.add("GENERATED");
                        } else {
                            metaElements.add("DEFAULT " + columnDefault);
                        }
                    }

                    String columnRef = "\"" + columnName + "\"";
                    tableSchema.get(tableName).put(columnRef, metaElements);
                }
                columnRs.close();

                // Query constraints
                ResultSet constraintRs = statement.executeQuery(
                        "SELECT conname, contype, pg_get_constraintdef(oid) as condef " +
                        "FROM pg_constraint " +
                        "JOIN pg_class ON pg_class.oid = conrelid " +
                        "JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace " +
                        "WHERE pg_class.relname = '" + tableName + "' AND nspname = 'public'");

                while (constraintRs.next()) {
                    String constraintName = constraintRs.getString("conname");
                    String constraintType = constraintRs.getString("contype");
                    String constraintDef = constraintRs.getString("condef");

                    List<String> constraintMeta = new ArrayList<>();

                    switch (constraintType) {
                    case "p": // PRIMARY KEY
                        constraintMeta.add("PRIMARY KEY");
                        parseConstraintColumns(constraintDef, tableName, tableSchema, constraintMeta);
                        break;
                    case "u": // UNIQUE
                        constraintMeta.add("UNIQUE");
                        parseConstraintColumns(constraintDef, tableName, tableSchema, constraintMeta);
                        break;
                    case "f": // FOREIGN KEY
                        constraintMeta.add("FOREIGN KEY");
                        parseForeignKeyColumns(constraintDef, foreignKeyList, tableName);
                        continue; // Handle separately
                    case "c": // CHECK
                        constraintMeta.add("CHECK");
                        parseCheckConstraintColumns(constraintDef, tableName, tableSchema, constraintMeta);
                        break;
                    default:
                        continue;
                    }

                    tableSchema.get(tableName).put(constraintType + "_" + constraintName, constraintMeta);
                }
                constraintRs.close();
            }
        }

        // Handle foreign key relationships
        if (!foreignKeyList.isEmpty()) {
            for (int i = 0; i < foreignKeyList.size() / 4; i++) {
                List<String> foreignExpression = new ArrayList<>();
                foreignExpression.add("FOREIGN KEY");
                String sourceTable = foreignKeyList.get(i * 4);
                String sourceColumn = foreignKeyList.get(i * 4 + 1);
                String targetTable = foreignKeyList.get(i * 4 + 2);
                String targetColumn = foreignKeyList.get(i * 4 + 3);

                if (tableSchema.containsKey(sourceTable) && tableSchema.containsKey(targetTable)) {
                    String sourceMeta = Strings.join(" ", tableSchema.get(sourceTable).getOrDefault(sourceColumn, List.of()));
                    String targetMeta = Strings.join(" ", tableSchema.get(targetTable).getOrDefault(targetColumn, List.of()));
                    foreignExpression.add(sourceMeta);
                    foreignExpression.add(targetMeta);
                    tableSchema.get(sourceTable).put("FOREIGN KEY" + i, foreignExpression);
                }
            }
        }

        return tableSchema;
    }

    private void parseConstraintColumns(String constraintDef, String tableName,
            Map<String, Map<String, List<String>>> tableSchema, List<String> constraintMeta) {
        Pattern pattern = Pattern.compile("\\((.*?)\\)");
        Matcher matcher = pattern.matcher(constraintDef);
        if (matcher.find()) {
            String columnsStr = matcher.group(1);
            for (String col : columnsStr.split(",")) {
                String columnName = col.trim().replace("\"", "");
                String columnRef = "\"" + columnName + "\"";
                if (tableSchema.get(tableName).containsKey(columnRef)) {
                    constraintMeta.addAll(tableSchema.get(tableName).get(columnRef));
                }
            }
        }
    }

    private void parseForeignKeyColumns(String constraintDef, List<String> foreignKeyList, String tableName) {
        Pattern pattern = Pattern.compile("FOREIGN KEY \\((.*?)\\) REFERENCES (\\w+)\\((.*?)\\)");
        Matcher matcher = pattern.matcher(constraintDef);
        if (matcher.find()) {
            String sourceColumn = "\"" + matcher.group(1).trim().replace("\"", "") + "\"";
            String targetTable = matcher.group(2);
            String targetColumn = "\"" + matcher.group(3).trim().replace("\"", "") + "\"";

            foreignKeyList.add(tableName);
            foreignKeyList.add(sourceColumn);
            foreignKeyList.add(targetTable);
            foreignKeyList.add(targetColumn);
        }
    }

    private void parseCheckConstraintColumns(String constraintDef, String tableName,
            Map<String, Map<String, List<String>>> tableSchema, List<String> constraintMeta) {
        Pattern pattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(constraintDef);
        while (matcher.find()) {
            String columnName = matcher.group(1);
            String columnRef = "\"" + columnName + "\"";
            if (tableSchema.get(tableName).containsKey(columnRef)) {
                constraintMeta.addAll(tableSchema.get(tableName).get(columnRef));
            }
        }
    }

    /**
     * Construct equivalent state (raw database without constraints).
     */
    @Override
    public PostgresGlobalState constructEquivalentState(PostgresGlobalState state) {
        try {
            PostgresEDC edc = new PostgresEDC(state);
            return edc.createRawDB();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate random SELECT query for testing.
     *
     * Uses SQLancer's PostgresExpressionGenerator (richer than RADAR's original)
     * to generate random WHERE conditions.
     */
    @Override
    public String generateQueryString(PostgresGlobalState state) {
        // Get random non-empty tables
        PostgresSchema.PostgresTables randomTables = state.getSchema().getRandomTableNonEmptyTables();
        List<PostgresColumn> columns = randomTables.getColumns();

        // Use SQLancer's ExpressionGenerator (richer with more Actions)
        PostgresExpressionGenerator generator = new PostgresExpressionGenerator(state).setColumns(columns);
        PostgresExpression randomWhereCondition = generator.generateExpression(0);

        // Build SELECT query
        List<PostgresExpression> tableRefs = randomTables.getTables().stream()
                .map(PostgresTableReference::new)
                .collect(Collectors.toList());

        PostgresSelect select = new PostgresSelect();
        select.setSelectOption(PostgresSelect.SelectType.ALL);
        select.setFetchColumns(
                Randomly.nonEmptySubset(randomTables.getColumns()).stream()
                        .map(PostgresColumnReference::new)
                        .collect(Collectors.toList()));
        select.setFromList(tableRefs);
        select.setWhereClause(randomWhereCondition);

        // Convert to SQL string
        return PostgresVisitor.asString(select);
    }
}