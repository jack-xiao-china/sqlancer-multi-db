package sqlancer.mysql.oracle.eet;

import sqlancer.common.oracle.eet.EETBetweenToComparisonRule;
import sqlancer.common.oracle.eet.EETCoalesceToCaseRule;
import sqlancer.common.oracle.eet.EETDeMorganRule;
import sqlancer.common.oracle.eet.EETExistsToInRule;
import sqlancer.common.oracle.eet.EETInToExistsRule;
import sqlancer.common.oracle.eet.EETNullifToCaseRule;
import sqlancer.common.oracle.eet.EETTransformerBase;
import sqlancer.common.schema.AbstractTables;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL EET equivalent transformations. Delegates to {@link EETTransformerBase}
 * with a {@link MySQLEETTransformAdapter} for dialect-specific AST node creation.
 *
 * Registers MySQL-supported semantic rewrite rules:
 * - De Morgan's Law (AND/OR → NOT(NOT...OR/AND...NOT))
 * - BETWEEN → Comparison (x BETWEEN a AND b → (x >= a) AND (x <= b))
 * - EXISTS → IN (with NULL-safe CASE wrapping)
 * - IN → EXISTS (with CASE IS NOT NULL wrapping)
 * - INTERSECT → EXISTS: NOT supported (MySQL lacks INTERSECT)
 * - EXCEPT → NOT EXISTS: NOT supported (MySQL lacks EXCEPT)
 */
public class MySQLEETTransformer {

    private final EETTransformerBase<MySQLExpression> base;

    public MySQLEETTransformer(MySQLExpressionGenerator gen) {
        this(gen, null);
    }

    public MySQLEETTransformer(MySQLExpressionGenerator gen,
            AbstractTables<MySQLTable, MySQLColumn> targetTables) {
        MySQLEETTransformAdapter adapter = new MySQLEETTransformAdapter(gen, targetTables);
        this.base = new EETTransformerBase<>(adapter);
        // Register semantic rewrite rules (MySQL-supported only)
        base.registerRule(new EETDeMorganRule<>());
        base.registerRule(new EETBetweenToComparisonRule<>());
        base.registerRule(new EETExistsToInRule<>());
        base.registerRule(new EETInToExistsRule<>());
        base.registerRule(new EETCoalesceToCaseRule<>());
        base.registerRule(new EETNullifToCaseRule<>());
    }

    public MySQLExpression transformExpression(MySQLExpression expr) {
        return base.transformExpression(expr);
    }

    /** Get the underlying base transformer (for rule registration and testing). */
    public EETTransformerBase<MySQLExpression> getBase() {
        return base;
    }

    /** Get the adapter (for dialect-specific operations). */
    public MySQLEETTransformAdapter getAdapter() {
        return (MySQLEETTransformAdapter) base.getAdapter();
    }
}