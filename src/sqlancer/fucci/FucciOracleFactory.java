package sqlancer.fucci;

import sqlancer.OracleFactory;
import sqlancer.common.oracle.TestOracle;

/**
 * Fucci Oracle Factory枚举。
 */
public enum FucciOracleFactory implements OracleFactory<FucciGlobalState> {

    FUCCI_DT {
        @Override
        public TestOracle<FucciGlobalState> create(FucciGlobalState globalState) throws Exception {
            return new sqlancer.fucci.oracle.FucciDTOracle(globalState);
        }
    },
    FUCCI_MT {
        @Override
        public TestOracle<FucciGlobalState> create(FucciGlobalState globalState) throws Exception {
            return new sqlancer.fucci.oracle.FucciMTOracle(globalState);
        }
    },
    FUCCI_CS {
        @Override
        public TestOracle<FucciGlobalState> create(FucciGlobalState globalState) throws Exception {
            return new sqlancer.fucci.oracle.FucciCSOracle(globalState);
        }
    },
    FUCCI_ALL {
        @Override
        public TestOracle<FucciGlobalState> create(FucciGlobalState globalState) throws Exception {
            return new sqlancer.fucci.oracle.FucciCompositeOracle(globalState);
        }
    }
}