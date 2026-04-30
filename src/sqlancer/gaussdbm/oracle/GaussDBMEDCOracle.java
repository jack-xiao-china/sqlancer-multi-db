package sqlancer.gaussdbm.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
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
import sqlancer.gaussdbm.GaussDBMEDC;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * GaussDB-M EDC (Equivalent Database Construction) Oracle Implementation.
 *
 * Detects GaussDB-M optimizer bugs by comparing query results between:
 * - Original Database: Has constraints (NOT NULL, UNIQUE, FK, CHECK, GENERATED)
 *   that affect query optimization
 * - Raw Database: No constraints, pure data copy (non-optimized execution)
 *
 * GaussDB-M uses MySQL compatibility mode, so implementation follows MySQL EDC pattern.
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
public class GaussDBMEDCOracle extends EDCBase<GaussDBMGlobalState> implements TestOracle<GaussDBMGlobalState> {

    public GaussDBMEDCOracle(GaussDBMGlobalState originalState) {
        super(originalState);
        GaussDBMErrors.addExpressionErrors(errors);
    }

    /**
     * Obtain table schemas with constraint metadata for diversity checking.
     *
     * Parses SHOW CREATE TABLE output to extract:
     * - Column metadata (type, NOT NULL, DEFAULT, GENERATED)
     * - Table constraints (UNIQUE, PRIMARY KEY, FOREIGN KEY, CHECK, KEY)
     *
     * @param state GaussDB-M global state
     * @return Map of table name to column/constraint metadata
     */
    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(GaussDBMGlobalState state) throws SQLException {
        Map<String, Map<String, List<String>>> tableSchema = new HashMap<>();
        List<String> foreignKeyList = new ArrayList<>();
        Pattern patternForColumn = Pattern.compile("`([^`]*?)`");
        boolean needComma;

        for (GaussDBTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            String tableName = table.getName();
            try (Statement statement = state.getConnection().createStatement()) {
                ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE " + tableName);
                String createTable = null;
                if (resultSet.next()) {
                    createTable = resultSet.getString("Create Table");
                }
                if (createTable != null) {
                    tableSchema.put(tableName, new HashMap<>());
                    String[] createTableLines = createTable.split("\n");

                    // Handle column-related metadata
                    Map<String, String> generatedColumns = new HashMap<>();
                    String columnName = table.getColumns().get(0).getName();
                    needComma = createTableLines[1].startsWith("  `" + columnName + "`");

                    for (int i = 0; i < table.getColumns().size(); i++) {
                        columnName = table.getColumns().get(i).getName();
                        String columnRef = "`" + columnName + "`";
                        int offset = 5;
                        if (!needComma) {
                            columnRef = columnName;
                            offset = 3;
                        }
                        int start = createTableLines[i + 1].indexOf(columnRef) + offset;
                        String metadataString = createTableLines[i + 1].substring(start,
                                createTableLines[i + 1].length() - 1);

                        if (metadataString.contains("GENERATED")) {
                            generatedColumns.put(columnRef, metadataString);
                        } else {
                            tableSchema.get(tableName).put(columnRef, List.of(metadataString));
                        }
                    }

                    // Handle generated columns - parse their dependencies
                    for (String generatedColumn : generatedColumns.keySet()) {
                        String expression = generatedColumns.get(generatedColumn);
                        Matcher matcher = patternForColumn.matcher(expression);
                        List<String> metaElements = new ArrayList<>();
                        while (matcher.find()) {
                            String columnRef = matcher.group(1);
                            if (needComma) {
                                columnRef = "`" + columnRef + "`";
                            }
                            if (tableSchema.get(tableName).containsKey(columnRef)) {
                                metaElements.add(Strings.join(" ", tableSchema.get(tableName).get(columnRef)));
                            }
                        }
                        Collections.sort(metaElements);
                        metaElements.add(0, "GENERATED");
                        tableSchema.get(tableName).put(generatedColumn, metaElements);
                    }

                    // Handle table-related metadata (constraints)
                    for (int i = table.getColumns().size() + 1; i < createTableLines.length - 1; i++) {
                        if (createTableLines[i].contains("ENGINE")) {
                            tableSchema.get(tableName).put("CONFIGURATION",
                                    List.of(createTableLines[i].substring(1)));
                            break;
                        }
                        parseConstraint(createTableLines[i], tableName, tableSchema, foreignKeyList, needComma);
                    }
                }
            }
        }

        // Handle foreign key relationships
        if (!foreignKeyList.isEmpty()) {
            for (int i = 0; i < foreignKeyList.size() / 4; i++) {
                List<String> foreignExpression = new ArrayList<>();
                foreignExpression.add("FOREIGN KEY");
                String source = Strings.join(" ",
                        tableSchema.get(foreignKeyList.get(i)).get(foreignKeyList.get(i + 1)));
                String target = Strings.join(" ",
                        tableSchema.get(foreignKeyList.get(i + 2)).get(foreignKeyList.get(i + 3)));
                foreignExpression.add(source);
                foreignExpression.add(target);
                tableSchema.get(foreignKeyList.get(i)).put("FOREIGN KEY" + i, foreignExpression);
            }
        }
        return tableSchema;
    }

    /**
     * Parse constraint from CREATE TABLE line.
     *
     * Handles: UNIQUE, PRIMARY KEY, FOREIGN KEY, CHECK, KEY (index)
     */
    private void parseConstraint(String line, String tableName,
            Map<String, Map<String, List<String>>> tableSchema,
            List<String> foreignKeyList, boolean needComma) {
        Pattern patternForColumnSet = Pattern.compile("\\((.*?)\\)");
        Pattern patternForColumn = Pattern.compile("`([^`]*?)`");

        Matcher matcherColumnSet = patternForColumnSet.matcher(line);
        List<String> compositeColumns = new ArrayList<>();
        if (matcherColumnSet.find()) {
            String columns = matcherColumnSet.group(1);
            if (columns.contains(",")) {
                Matcher matcherColumn = patternForColumn.matcher(columns);
                while (matcherColumn.find()) {
                    String columnRef = matcherColumn.group(1);
                    if (needComma) {
                        columnRef = "`" + columnRef + "`";
                    }
                    compositeColumns.add(columnRef);
                }
            } else {
                compositeColumns.add(matcherColumnSet.group(1));
            }
        }

        String metadataType = "";
        if (line.contains("UNIQUE")) {
            metadataType = "UNIQUE";
        } else if (line.contains("PRIMARY")) {
            metadataType = "PRIMARY";
        } else if (line.contains("FOREIGN")) {
            // Handle foreign key - store for cross-table linking
            foreignKeyList.add(tableName);
            foreignKeyList.add(compositeColumns.get(0));
            String references = line.substring(line.indexOf("REFERENCES") + 11);
            Matcher matcher = patternForColumn.matcher(references);
            if (matcher.find()) {
                foreignKeyList.add(matcher.group(1)); // Referenced table
            }
            if (matcher.find()) {
                if (needComma) {
                    foreignKeyList.add("`" + matcher.group(1) + "`"); // Referenced column
                } else {
                    foreignKeyList.add(matcher.group(1));
                }
            }
            return;
        } else if (line.contains("CHECK")) {
            metadataType = "CHECK";
            int start = line.indexOf(metadataType) + 5;
            String metadataString = line.substring(start, line.length() - 1);
            Matcher matcher = patternForColumn.matcher(metadataString);
            List<String> metaElements = new ArrayList<>();
            while (matcher.find()) {
                String columnRef = matcher.group(1);
                if (needComma) {
                    columnRef = "`" + columnRef + "`";
                }
                if (tableSchema.get(tableName).containsKey(columnRef)) {
                    metaElements.add(Strings.join(" ", tableSchema.get(tableName).get(columnRef)));
                }
            }
            Collections.sort(metaElements);
            metaElements.add(0, metadataType);
            tableSchema.get(tableName).put(metadataType + line.hashCode(), metaElements);
            return;
        } else if (line.contains("KEY")) {
            metadataType = "KEY";
        }

        List<String> columnRelatedMetadata = new ArrayList<>();
        for (String columnRef : compositeColumns) {
            if (tableSchema.get(tableName).containsKey(columnRef)) {
                columnRelatedMetadata.addAll(tableSchema.get(tableName).get(columnRef));
            }
        }
        Collections.sort(columnRelatedMetadata);
        columnRelatedMetadata.add(0, metadataType);
        tableSchema.get(tableName).put(metadataType + line.hashCode(), columnRelatedMetadata);
    }

    /**
     * Construct equivalent state (raw database without constraints).
     * Closes previous equivalent state before creating new one.
     */
    @Override
    public GaussDBMGlobalState constructEquivalentState(GaussDBMGlobalState state) {
        try {
            // Close previous equivalent state connection before creating new raw DB
            closeEquState();
            GaussDBMEDC edc = new GaussDBMEDC(state);
            return edc.createRawDB();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate random SELECT query for testing.
     *
     * Uses SQLancer's GaussDBMExpressionGenerator (richer with more Actions)
     * to generate random WHERE conditions.
     */
    @Override
    public String generateQueryString(GaussDBMGlobalState state) {
        // Get random non-empty tables
        GaussDBMSchema.GaussDBTables randomTables = state.getSchema().getRandomTableNonEmptyTables();
        List<GaussDBColumn> columns = randomTables.getColumns();

        // Use SQLancer's ExpressionGenerator
        GaussDBMExpressionGenerator generator = new GaussDBMExpressionGenerator(state);
        generator.setColumns(columns);
        GaussDBExpression randomWhereCondition = generator.generateExpression();

        // Build SELECT query
        List<GaussDBExpression> tableRefs = randomTables.getTables().stream()
                .map(GaussDBTableReference::new)
                .collect(Collectors.toList());

        GaussDBSelect select = new GaussDBSelect();
        select.setSelectType(GaussDBSelect.SelectType.ALL);
        select.setFetchColumns(
                Randomly.nonEmptySubset(randomTables.getColumns()).stream()
                        .map(c -> new GaussDBColumnReference(c, null))
                        .collect(Collectors.toList()));
        select.setFromList(tableRefs);
        select.setWhereClause(randomWhereCondition);

        // Convert to SQL string
        return GaussDBToStringVisitor.asString(select);
    }
}