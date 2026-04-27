package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresForeignKeyValuePool;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;

public final class PostgresInsertGenerator {

    private PostgresInsertGenerator() {
    }

    public static SQLQueryAdapter insert(PostgresGlobalState globalState) {
        PostgresTable table = globalState.getSchema().getRandomTable(t -> t.isInsertable());
        return insertRows(globalState, table);
    }

    public static SQLQueryAdapter insertRows(PostgresGlobalState globalState, PostgresSchema.PostgresTable table) {
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("cannot insert into column");
        PostgresCommon.addCommonExpressionErrors(errors);
        PostgresCommon.addCommonInsertUpdateErrors(errors);
        PostgresCommon.addCommonExpressionErrors(errors);
        errors.add("multiple assignments to same column");
        errors.add("violates foreign key constraint");
        errors.add("value too long for type character varying");
        errors.add("conflicting key value violates exclusion constraint");
        errors.add("violates not-null constraint");
        errors.add("current transaction is aborted");
        errors.add("bit string too long");
        errors.add("new row violates check option for view");
        errors.add("reached maximum value of sequence");
        errors.add("but expression is of type");
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(table.getName());
        PostgresColumn partitionKeyColumn = getPartitionKeyColumnForRouting(globalState, table);
        String partitionRoutingValue = getPartitionRoutingValue(globalState, table, partitionKeyColumn);
        List<PostgresColumn> columns = new ArrayList<>(table.getRandomNonEmptyColumnSubset());
        if (partitionKeyColumn != null && !columns.contains(partitionKeyColumn)) {
            columns.add(partitionKeyColumn);
        }
        sb.append("(");
        sb.append(columns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
        sb.append(")");
        if (Randomly.getBooleanWithRatherLowProbability()) {
            sb.append(" OVERRIDING");
            sb.append(" ");
            sb.append(Randomly.fromOptions("SYSTEM", "USER"));
            sb.append(" VALUE");
        }
        sb.append(" VALUES");

        if (globalState.getDbmsSpecificOptions().allowBulkInsert && Randomly.getBooleanWithSmallProbability()) {
            StringBuilder sbRowValue = new StringBuilder();
            sbRowValue.append("(");
            for (int i = 0; i < columns.size(); i++) {
                if (i != 0) {
                    sbRowValue.append(", ");
                }
                appendInsertValue(globalState, sbRowValue, columns.get(i), partitionKeyColumn, partitionRoutingValue,
                        false);
            }
            sbRowValue.append(")");

            int n = (int) Randomly.getNotCachedInteger(100, 1000);
            for (int i = 0; i < n; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(sbRowValue);
            }
        } else {
            int n = globalState.getRandomly().getInteger(1,
                    Math.max(1, globalState.getDbmsSpecificOptions().getPgGenerateRowsPerInsert()));
            for (int i = 0; i < n; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                insertRow(globalState, sb, columns, n == 1, partitionKeyColumn, partitionRoutingValue);
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            sb.append(" ON CONFLICT ");
            if (Randomly.getBoolean()) {
                sb.append("(");
                sb.append(table.getRandomColumn().getName());
                sb.append(")");
                errors.add("there is no unique or exclusion constraint matching the ON CONFLICT specification");
            }
            sb.append(" DO NOTHING");
        }
        errors.add("duplicate key value violates unique constraint");
        errors.add("identity column defined as GENERATED ALWAYS");
        errors.add("out of range");
        errors.add("violates check constraint");
        errors.add("no partition of relation");
        errors.add("invalid input syntax");
        errors.add("division by zero");
        errors.add("violates foreign key constraint");
        errors.add("data type unknown");
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    private static void insertRow(PostgresGlobalState globalState, StringBuilder sb, List<PostgresColumn> columns,
            boolean canBeDefault, PostgresColumn partitionKeyColumn, String partitionRoutingValue) {
        sb.append("(");
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            appendInsertValue(globalState, sb, columns.get(i), partitionKeyColumn, partitionRoutingValue, canBeDefault);
        }
        sb.append(")");
    }

    private static void appendInsertValue(PostgresGlobalState globalState, StringBuilder sb, PostgresColumn column,
            PostgresColumn partitionKeyColumn, String partitionRoutingValue, boolean canBeDefault) {
        if (column == partitionKeyColumn && partitionRoutingValue != null) {
            sb.append(partitionRoutingValue);
            return;
        }
        if (isForeignKeySetupColumn(column) && appendForeignKeySetupValue(sb, column, canBeDefault)) {
            return;
        }
        if (Randomly.getBooleanWithSmallProbability() && canBeDefault) {
            sb.append("DEFAULT");
            return;
        }
        PostgresExpression generateConstant;
        if (Randomly.getBoolean()) {
            generateConstant = PostgresExpressionGenerator.generateConstant(globalState.getRandomly(),
                    column.getCompoundType());
        } else {
            generateConstant = new PostgresExpressionGenerator(globalState).generateExpression(column.getCompoundType());
        }
        sb.append(PostgresVisitor.asString(generateConstant));
    }

    private static boolean appendForeignKeySetupValue(StringBuilder sb, PostgresColumn column, boolean canBeDefault) {
        List<String> values = PostgresForeignKeyValuePool.getValues(column);
        if (values.isEmpty()) {
            return false;
        }
        int choice = (int) Randomly.getNotCachedInteger(0, 10);
        if (choice < 7) {
            sb.append(Randomly.fromList(values));
            return true;
        } else if (choice < 9) {
            sb.append("NULL");
            return true;
        } else if (canBeDefault && Randomly.getBooleanWithRatherLowProbability()) {
            sb.append("DEFAULT");
            return true;
        }
        return false;
    }

    private static boolean isForeignKeySetupColumn(PostgresColumn column) {
        return column.getName().startsWith("fk_");
    }

    private static PostgresColumn getPartitionKeyColumnForRouting(PostgresGlobalState globalState, PostgresTable table) {
        if (!table.isPartitioned() || !Randomly.getBoolean()) {
            return null;
        }
        if (!PostgresPartitionGenerator.canGeneratePartitionRoutingValue(globalState, table)) {
            return null;
        }
        return PostgresPartitionGenerator.getSimplePartitionKeyColumn(table);
    }

    private static String getPartitionRoutingValue(PostgresGlobalState globalState, PostgresTable table,
            PostgresColumn partitionKeyColumn) {
        if (partitionKeyColumn == null) {
            return null;
        }
        return PostgresPartitionGenerator.getPartitionRoutingValue(globalState, table);
    }

}
