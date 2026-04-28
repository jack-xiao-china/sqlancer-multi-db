package sqlancer.gaussdbm.oracle;

import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.gen.GaussDBMRandomQuerySynthesizer;

public class GaussDBMFuzzer implements TestOracle<GaussDBMGlobalState> {

    private final GaussDBMGlobalState globalState;

    public GaussDBMFuzzer(GaussDBMGlobalState globalState) {
        this.globalState = globalState;
    }

    @Override
    public void check() throws Exception {
        String s = GaussDBToStringVisitor
                .asString(GaussDBMRandomQuerySynthesizer.generate(globalState, Randomly.smallNumber() + 1)) + ';';
        try {
            globalState.executeStatement(new SQLQueryAdapter(s));
            globalState.getManager().incrementSelectQueryCount();
        } catch (Error e) {
            // ignore JVM errors from driver
        }
    }
}
