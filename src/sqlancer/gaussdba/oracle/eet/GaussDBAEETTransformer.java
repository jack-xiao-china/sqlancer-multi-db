package sqlancer.gaussdba.oracle.eet;

import sqlancer.common.oracle.eet.EETBetweenToComparisonRule;
import sqlancer.common.oracle.eet.EETCoalesceToCaseRule;
import sqlancer.common.oracle.eet.EETDeMorganRule;
import sqlancer.common.oracle.eet.EETExceptToNotExistsRule;
import sqlancer.common.oracle.eet.EETExistsToInRule;
import sqlancer.common.oracle.eet.EETInToExistsRule;
import sqlancer.common.oracle.eet.EETNullifToCaseRule;
import sqlancer.common.oracle.eet.EETTransformerBase;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * Thin wrapper around EETTransformerBase + GaussDBAEETTransformAdapter.
 * Registers GaussDB-A supported semantic rules (DeMorgan, BetweenToComparison).
 */
public class GaussDBAEETTransformer {

    private final EETTransformerBase<GaussDBAExpression> base;

    public GaussDBAEETTransformer(GaussDBAExpressionGenerator gen,
            AbstractTables<GaussDBATable, GaussDBAColumn> targetTables) {
        GaussDBAEETTransformAdapter adapter = new GaussDBAEETTransformAdapter(gen, targetTables);
        this.base = new EETTransformerBase<>(adapter);
        // Register GaussDB-A supported semantic rewrite rules
        base.registerRule(new EETDeMorganRule<>());
        base.registerRule(new EETBetweenToComparisonRule<>());
        base.registerRule(new EETExistsToInRule<>());
        base.registerRule(new EETInToExistsRule<>());
        base.registerRule(new EETCoalesceToCaseRule<>());
        base.registerRule(new EETNullifToCaseRule<>());
        base.registerRule(new EETExceptToNotExistsRule<>()); // MINUS → NOT EXISTS
        // Register wrapping rules (Rules 1-6, matching MySQL/PG coverage)
        // BoolTransform (1-2), ValueTransform (3-6), ConstBoolTransform are default rules
        // already in EETTransformerBase, so no explicit registration needed.
    }

    public GaussDBAExpression transformExpression(GaussDBAExpression expr) {
        return base.transformExpression(expr);
    }
}