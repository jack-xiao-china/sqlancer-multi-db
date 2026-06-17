package sqlancer.gaussdba.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.EDCRadarBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdba.GaussDBAEDCRadar;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * GaussDB-A EDC_RADAR (Equivalent Database Construction - RADAR) Oracle Implementation.
 *
 * Detects GaussDB-A optimizer bugs by comparing query results between:
 * - Original Schema: Has constraints (NOT NULL, UNIQUE, FK, CHECK) that affect query optimization
 * - Raw Schema: No constraints, pure data copy (non-optimized execution)
 *
 * GaussDB-A uses Oracle compatibility mode with schema isolation.
 *
 * If the same query produces different results on original vs raw schema,
 * it indicates an optimizer bug where constraints were incorrectly handled.
 */
public class GaussDBAEDCRadarOracle extends EDCRadarBase<GaussDBAGlobalState>
        implements TestOracle<GaussDBAGlobalState> {

    public GaussDBAEDCRadarOracle(GaussDBAGlobalState originalState) {
        super(originalState);
        GaussDBAErrors.addExpressionErrors(errors);
    }

    /**
     * Obtain table schemas with constraint metadata for diversity checking.
     *
     * Uses information_schema / pg_catalog to extract constraint metadata since
     * GaussDB-A (Oracle mode) does not support SHOW CREATE TABLE.
     */
    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(GaussDBAGlobalState state) throws SQLException {
        Map<String, Map<String, List<String>>> tableSchema = new HashMap<>();
        String schemaName = state.getDatabaseName().toLowerCase();

        for (GaussDBATable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            String tableName = table.getName();
            tableSchema.put(tableName, new HashMap<>());

            // Get column metadata from information_schema
            try (Statement stmt = state.getConnection().createStatement()) {
                String colQuery = "SELECT column_name, is_nullable, data_type FROM information_schema.columns "
                        + "WHERE table_schema = '" + schemaName + "' AND table_name = '" + tableName + "' "
                        + "ORDER BY ordinal_position";
                ResultSet colRs = stmt.executeQuery(colQuery);
                while (colRs.next()) {
                    String colName = colRs.getString("column_name");
                    String isNullable = colRs.getString("is_nullable");
                    String dataType = colRs.getString("data_type");
                    String colRef = "\"" + colName + "\"";

                    List<String> metaElements = new ArrayList<>();
                    metaElements.add(dataType);
                    if ("NO".equalsIgnoreCase(isNullable)) {
                        metaElements.add("NOT NULL");
                    }
                    tableSchema.get(tableName).put(colRef, metaElements);
                }
                colRs.close();
            }

            // Get constraint metadata from pg_catalog
            try (Statement stmt = state.getConnection().createStatement()) {
                String constrQuery = "SELECT con.conname, con.contype, "
                        + "pg_catalog.pg_get_constraintdef(con.oid, true) AS condef "
                        + "FROM pg_catalog.pg_constraint con "
                        + "INNER JOIN pg_catalog.pg_class cls ON con.conrelid = cls.oid "
                        + "INNER JOIN pg_catalog.pg_namespace ns ON cls.relnamespace = ns.oid "
                        + "WHERE ns.nspname = '" + schemaName + "' AND cls.relname = '" + tableName + "'";

                ResultSet conRs = stmt.executeQuery(constrQuery);
                while (conRs.next()) {
                    String conName = conRs.getString("conname");
                    String conType = conRs.getString("contype");
                    String conDef = conRs.getString("condef");

                    String metadataType;
                    switch (conType) {
                    case "p":
                        metadataType = "PRIMARY";
                        break;
                    case "u":
                        metadataType = "UNIQUE";
                        break;
                    case "f":
                        metadataType = "FOREIGN KEY";
                        break;
                    case "c":
                        metadataType = "CHECK";
                        break;
                    default:
                        metadataType = "UNKNOWN";
                        break;
                    }

                    List<String> metaElements = new ArrayList<>();
                    metaElements.add(metadataType);
                    if (conDef != null) {
                        metaElements.add(conDef);
                    }
                    tableSchema.get(tableName).put("CONSTRAINT_" + conName, metaElements);
                }
                conRs.close();
            }
        }
        return tableSchema;
    }

    /**
     * Construct equivalent state (raw schema without constraints).
     */
    @Override
    public GaussDBAGlobalState constructEquivalentState(GaussDBAGlobalState state) {
        try {
            closeEquState();
            GaussDBAEDCRadar edc = new GaussDBAEDCRadar(state);
            return edc.createRawDB();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate random SELECT query for testing.
     */
    @Override
    public String generateQueryString(GaussDBAGlobalState state) {
        GaussDBASchema.GaussDBATables randomTables = state.getSchema().getRandomTableNonEmptyTables();
        List<GaussDBAColumn> columns = randomTables.getColumns();

        GaussDBAExpressionGenerator generator = new GaussDBAExpressionGenerator(state);
        generator.setColumns(columns);
        GaussDBAExpression randomWhereCondition = generator.generateExpression(0);

        List<GaussDBAExpression> tableRefs = randomTables.getTables().stream()
                .map(GaussDBATableReference::new)
                .collect(Collectors.toList());

        GaussDBASelect select = new GaussDBASelect();
        select.setFetchColumns(
                Randomly.nonEmptySubset(randomTables.getColumns()).stream()
                        .map(c -> new GaussDBAColumnReference(c, null))
                        .collect(Collectors.toList()));
        select.setFromList(tableRefs);
        select.setWhereClause(randomWhereCondition);

        return GaussDBAToStringVisitor.asString(select);
    }
}
