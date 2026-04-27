package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.gen.AbstractUpdateGenerator;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresForeignKeyValuePool;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;

public final class PostgresUpdateGenerator extends AbstractUpdateGenerator<PostgresColumn> {

    private final PostgresGlobalState globalState;
    private PostgresTable randomTable;
    private PostgresColumn partitionKeyColumn;
    private String partitionRoutingValue;

    private PostgresUpdateGenerator(PostgresGlobalState globalState) {
        this.globalState = globalState;
        errors.addAll(Arrays.asList("conflicting key value violates exclusion constraint",
                "reached maximum value of sequence", "violates foreign key constraint", "violates not-null constraint",
                "violates unique constraint", "out of range", "cannot cast", "must be type boolean", "is not unique",
                " bit string too long", "can only be updated to DEFAULT", "division by zero",
                "You might need to add explicit type casts.", "invalid regular expression",
                "View columns that are not columns of their base relation are not updatable",
                "duplicate key value violates unique constraint",
                "update or delete on table"));
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        return new PostgresUpdateGenerator(globalState).generate();
    }

    private SQLQueryAdapter generate() {
        randomTable = globalState.getSchema().getRandomTable(t -> t.isInsertable());
        configurePartitionKeyMovement();
        List<PostgresColumn> columns = new ArrayList<>(randomTable.getRandomNonEmptyColumnSubset());
        if (partitionKeyColumn != null && !columns.contains(partitionKeyColumn)) {
            columns.add(partitionKeyColumn);
        }
        sb.append("UPDATE ");
        sb.append(randomTable.getName());
        sb.append(" SET ");
        errors.add("multiple assignments to same column"); // view whose columns refer to a column in the referenced
                                                           // table multiple times
        errors.add("new row violates check option for view");
        PostgresCommon.addCommonInsertUpdateErrors(errors);
        updateColumns(columns);
        errors.add("invalid input syntax for ");
        errors.add("operator does not exist: text = boolean");
        errors.add("violates check constraint");
        errors.add("no partition of relation");
        errors.add("invalid input syntax");
        errors.add("cannot move row");
        errors.add("tuple to be locked was already moved to another partition");
        errors.add("could not determine which collation to use for string comparison");
        errors.add("but expression is of type");
        PostgresCommon.addCommonExpressionErrors(errors);
        if (!Randomly.getBooleanWithSmallProbability()) {
            sb.append(" WHERE ");
            PostgresExpression where = PostgresExpressionGenerator.generateExpression(globalState,
                    randomTable.getColumns(), PostgresDataType.BOOLEAN);
            sb.append(PostgresVisitor.asString(where));
        }

        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    @Override
    protected void updateValue(PostgresColumn column) {
        if (column == partitionKeyColumn && partitionRoutingValue != null) {
            sb.append(partitionRoutingValue);
            return;
        }
        if (isForeignKeySetupColumn(column) && appendForeignKeySetupUpdateValue(column)) {
            return;
        }
        if (!Randomly.getBoolean()) {
            PostgresExpression constant = PostgresExpressionGenerator.generateConstant(globalState.getRandomly(),
                    column.getCompoundType());
            sb.append(PostgresVisitor.asString(constant));
        } else if (Randomly.getBoolean()) {
            sb.append("DEFAULT");
        } else {
            sb.append("(");
            PostgresExpression expr = PostgresExpressionGenerator.generateExpression(globalState,
                    randomTable.getColumns(), column.getCompoundType());
            // caused by casts
            sb.append(PostgresVisitor.asString(expr));
            sb.append(")");
        }
    }

    private boolean appendForeignKeySetupUpdateValue(PostgresColumn column) {
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
        }
        sb.append("DEFAULT");
        return true;
    }

    private static boolean isForeignKeySetupColumn(PostgresColumn column) {
        return column.getName().startsWith("fk_");
    }

    private void configurePartitionKeyMovement() {
        if (!randomTable.isPartitioned() || !Randomly.getBoolean()) {
            return;
        }
        if (!PostgresPartitionGenerator.canGeneratePartitionRoutingValue(globalState, randomTable)) {
            return;
        }
        partitionKeyColumn = PostgresPartitionGenerator.getSimplePartitionKeyColumn(randomTable);
        partitionRoutingValue = PostgresPartitionGenerator.getPartitionRoutingValue(globalState, randomTable);
    }

}
