package sqlancer.common.oracle.eet;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.ast.GaussDBComputableFunction;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction.GaussDBFunction;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.oracle.eet.GaussDBMEETTransformAdapter;

public class EETCoalesceToCaseRuleTest {

    @Test
    public void coalesceWithThreeArgsDoesNotThrow() {
        // Regression test: the >=3-arg branch used (E[]) new Object[...], whose Object[] runtime
        // type hit the bridge-method cast in adapters with a concrete array parameter
        // (e.g. GaussDBMEETTransformAdapter.createCoalesce(GaussDBExpression[])) and crashed
        // with ClassCastException during EET runs.
        GaussDBMEETTransformAdapter adapter = new GaussDBMEETTransformAdapter(null, null);
        GaussDBExpression coalesce = new GaussDBComputableFunction(GaussDBFunction.COALESCE,
                GaussDBConstant.createIntConstant(1), GaussDBConstant.createIntConstant(2),
                GaussDBConstant.createIntConstant(3));
        EETCoalesceToCaseRule<GaussDBExpression> rule = new EETCoalesceToCaseRule<>();
        GaussDBExpression result = rule.apply(coalesce, adapter);
        assertNotNull(result);
    }
}
