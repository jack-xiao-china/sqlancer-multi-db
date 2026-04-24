package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;

public final class PostgresMergeGenerator {

    private PostgresMergeGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        PostgresTable table = globalState.getSchema()
                .getRandomTableOrBailout(t -> t.isInsertable() && !t.getColumns().isEmpty());
        List<PostgresColumn> columns = new ArrayList<>(table.getRandomNonEmptyColumnSubset());
        StringBuilder sb = new StringBuilder("MERGE INTO ");
        sb.append(table.getName());
        sb.append(" AS target USING (VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            PostgresExpression constant = PostgresExpressionGenerator.generateConstant(globalState.getRandomly(),
                    columns.get(i).getCompoundType());
            sb.append(PostgresVisitor.asString(constant));
        }
        sb.append(")) AS source(");
        sb.append(columns.stream().map(PostgresColumn::getName).collect(Collectors.joining(", ")));
        sb.append(") ON target.");
        PostgresColumn matchColumn = Randomly.fromList(columns);
        sb.append(matchColumn.getName());
        sb.append(" IS NOT DISTINCT FROM source.");
        sb.append(matchColumn.getName());
        if (Randomly.getBoolean()) {
            sb.append(" WHEN MATCHED THEN UPDATE SET ");
            sb.append(columns.stream().map(c -> c.getName() + " = source." + c.getName())
                    .collect(Collectors.joining(", ")));
        } else {
            sb.append(" WHEN MATCHED THEN DELETE");
        }
        sb.append(" WHEN NOT MATCHED THEN INSERT (");
        sb.append(columns.stream().map(PostgresColumn::getName).collect(Collectors.joining(", ")));
        sb.append(") VALUES (");
        sb.append(columns.stream().map(c -> "source." + c.getName()).collect(Collectors.joining(", ")));
        sb.append(")");
        ExpectedErrors errors = new ExpectedErrors();
        PostgresCommon.addCommonExpressionErrors(errors);
        PostgresCommon.addCommonInsertUpdateErrors(errors);
        errors.add("duplicate key value violates unique constraint");
        errors.add("violates check constraint");
        errors.add("violates foreign key constraint");
        errors.add("violates not-null constraint");
        errors.add("no partition of relation");
        errors.add("MERGE command cannot affect row a second time");
        errors.add("cannot execute MERGE in a read-only transaction");
        errors.add("can only be updated to DEFAULT");
        errors.add("identity column defined as GENERATED ALWAYS");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
