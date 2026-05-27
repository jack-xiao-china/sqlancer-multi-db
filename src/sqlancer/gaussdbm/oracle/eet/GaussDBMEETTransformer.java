package sqlancer.gaussdbm.oracle.eet;

import sqlancer.common.oracle.eet.EETBetweenToComparisonRule;
import sqlancer.common.oracle.eet.EETCoalesceToCaseRule;
import sqlancer.common.oracle.eet.EETDeMorganRule;
import sqlancer.common.oracle.eet.EETExistsToInRule;
import sqlancer.common.oracle.eet.EETInToExistsRule;
import sqlancer.common.oracle.eet.EETNullifToCaseRule;
import sqlancer.common.oracle.eet.EETTransformerBase;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * Thin wrapper around EETTransformerBase + GaussDBMEETTransformAdapter.
 * Registers GaussDB-M supported semantic rules (DeMorgan, Between, ExistsToIn, InToExists).
 */
public class GaussDBMEETTransformer {

    private final EETTransformerBase<GaussDBExpression> base;

    public GaussDBMEETTransformer(GaussDBMExpressionGenerator gen,
            AbstractTables<GaussDBTable, GaussDBColumn> targetTables) {
        GaussDBMEETTransformAdapter adapter = new GaussDBMEETTransformAdapter(gen, targetTables);
        this.base = new EETTransformerBase<>(adapter);
        // Register GaussDB-M supported semantic rewrite rules
        base.registerRule(new EETDeMorganRule<>());
        base.registerRule(new EETBetweenToComparisonRule<>());
        base.registerRule(new EETExistsToInRule<>());
        base.registerRule(new EETInToExistsRule<>());
        base.registerRule(new EETCoalesceToCaseRule<>());
        base.registerRule(new EETNullifToCaseRule<>());
    }

    public GaussDBExpression transformExpression(GaussDBExpression expr) {
        return base.transformExpression(expr);
    }
}