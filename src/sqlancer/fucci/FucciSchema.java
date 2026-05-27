package sqlancer.fucci;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTable;

/**
 * Fucci Schema包装类。
 */
public class FucciSchema extends AbstractSchema<FucciGlobalState, FucciTable> {

    private AbstractSchema<?, ?> originalSchema;
    private List<FucciTable> fucciTables;

    public FucciSchema(List<FucciTable> databaseTables) {
        super(databaseTables);
        this.fucciTables = databaseTables;
    }

    public FucciSchema(AbstractSchema<?, ?> originalSchema) {
        super(convertTables(originalSchema));
        this.originalSchema = originalSchema;
        this.fucciTables = convertTables(originalSchema);
    }

    private static List<FucciTable> convertTables(AbstractSchema<?, ?> originalSchema) {
        List<FucciTable> tables = new ArrayList<>();
        if (originalSchema != null) {
            for (AbstractTable<?, ?, ?> table : originalSchema.getDatabaseTables()) {
                tables.add(new FucciTable(table));
            }
        }
        return tables;
    }

    public static FucciSchema fromConnection(sqlancer.SQLConnection con, String databaseName) throws SQLException {
        List<FucciTable> tables = new ArrayList<>();
        return new FucciSchema(tables);
    }

    @Override
    public List<FucciTable> getDatabaseTables() {
        return fucciTables;
    }

    public AbstractSchema<?, ?> getOriginalSchema() { return originalSchema; }
    public void addTable(FucciTable table) { fucciTables.add(table); }
    public int getTableCount() { return fucciTables.size(); }

    public FucciTable getTable(String tableName) {
        for (FucciTable table : fucciTables) {
            if (table.getName().equals(tableName)) {
                return table;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FucciSchema{tables=").append(fucciTables.size()).append("}\n");
        for (FucciTable table : fucciTables) {
            sb.append("  ").append(table.toString()).append("\n");
        }
        return sb.toString();
    }
}