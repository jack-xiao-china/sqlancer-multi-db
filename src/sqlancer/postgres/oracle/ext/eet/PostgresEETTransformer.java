package sqlancer.postgres.oracle.ext.eet;

import sqlancer.common.oracle.eet.EETBetweenToComparisonRule;
import sqlancer.common.oracle.eet.EETCoalesceToCaseRule;
import sqlancer.common.oracle.eet.EETDeMorganRule;
import sqlancer.common.oracle.eet.EETExceptToNotExistsRule;
import sqlancer.common.oracle.eet.EETExistsToInRule;
import sqlancer.common.oracle.eet.EETInToExistsRule;
import sqlancer.common.oracle.eet.EETIntersectToExistsRule;
import sqlancer.common.oracle.eet.EETNullifToCaseRule;
import sqlancer.common.oracle.eet.EETTransformerBase;
import sqlancer.common.schema.AbstractTables;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL EET equivalent transformations. Delegates to {@link EETTransformerBase}
 * with a {@link PostgresEETTransformAdapter} for dialect-specific AST node creation.
 *
 * Registers PostgreSQL-supported semantic rewrite rules:
 * - De Morgan's Law
 * - BETWEEN → Comparison
 * - EXISTS → IN (pending PostgresExists AST node, Phase 4)
 * - IN → EXISTS (pending PostgresExists AST node, Phase 4)
 * - INTERSECT → EXISTS (pending INTERSECT AST node, Phase 4)
 * - EXCEPT → NOT EXISTS (pending EXCEPT AST node, Phase 4)
 */
public final class PostgresEETTransformer {

    private final EETTransformerBase<PostgresExpression> base;

    public PostgresEETTransformer(PostgresExpressionGenerator gen) {
        this(gen, null);
    }

    public PostgresEETTransformer(PostgresExpressionGenerator gen,
            AbstractTables<PostgresTable, PostgresColumn> targetTables) {
        PostgresEETTransformAdapter adapter = new PostgresEETTransformAdapter(gen, targetTables);
        this.base = new EETTransformerBase<>(adapter);
        // Register semantic rewrite rules (PostgreSQL-supported)
        base.registerRule(new EETDeMorganRule<>());
        base.registerRule(new EETBetweenToComparisonRule<>());
        base.registerRule(new EETExistsToInRule<>());
        base.registerRule(new EETInToExistsRule<>());
        base.registerRule(new EETIntersectToExistsRule<>());
        base.registerRule(new EETExceptToNotExistsRule<>());
        base.registerRule(new EETCoalesceToCaseRule<>());
        base.registerRule(new EETNullifToCaseRule<>());
    }

    public PostgresExpression transformExpression(PostgresExpression expr) {
        return base.transformExpression(expr);
    }

    /** Get the underlying base transformer (for rule registration and testing). */
    public EETTransformerBase<PostgresExpression> getBase() {
        return base;
    }

    /** Get the adapter (for dialect-specific operations). */
    public PostgresEETTransformAdapter getAdapter() {
        return (PostgresEETTransformAdapter) base.getAdapter();
    }
}