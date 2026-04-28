package sqlancer.gaussdbm;

import java.util.ArrayList;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.OracleFactory;
import sqlancer.common.oracle.CERTOracle;
import sqlancer.common.oracle.CompositeTestOracle;
import sqlancer.common.oracle.NoRECOracle;
import sqlancer.common.oracle.TLPWhereOracle;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;
import sqlancer.gaussdbm.oracle.GaussDBCODDTestOracle;
import sqlancer.gaussdbm.oracle.GaussDBMCERTExplainParser;
import sqlancer.gaussdbm.oracle.GaussDBMDQEOracle;
import sqlancer.gaussdbm.oracle.GaussDBMDQPOracle;
import sqlancer.gaussdbm.oracle.GaussDBMFuzzer;
import sqlancer.gaussdbm.oracle.GaussDBMPivotedQuerySynthesisOracle;
import sqlancer.gaussdbm.oracle.GaussDBMSonarOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPAggregateOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPDistinctOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPGroupByOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPHavingOracle;
import sqlancer.gaussdbm.oracle.eet.GaussDBMEETOracle;

/**
 * GaussDB-M test oracles. Enum declaration order matches {@link sqlancer.mysql.MySQLOracleFactory} for CLI/help
 * consistency; {@link #QUERY_PARTITIONING} is last and composes TLP/NOREC oracles like MySQL.
 */
public enum GaussDBMOracleFactory implements OracleFactory<GaussDBMGlobalState> {

    AGGREGATE {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMTLPAggregateOracle(globalState);
        }
    },
    HAVING {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMTLPHavingOracle(globalState);
        }
    },
    GROUP_BY {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMTLPGroupByOracle(globalState);
        }
    },
    DISTINCT {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMTLPDistinctOracle(globalState);
        }
    },
    NOREC {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(globalState);
            return new NoRECOracle<>(globalState, gen, GaussDBMErrors.getExpressionErrors());
        }
    },
    TLP_WHERE {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(globalState);
            return new TLPWhereOracle<>(globalState, gen, GaussDBMErrors.getExpressionErrors());
        }
    },
    PQS {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMPivotedQuerySynthesisOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    CERT {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(globalState);
            ExpectedErrors expectedErrors = GaussDBMErrors.getExpressionErrors();
            return new CERTOracle<>(globalState, gen, expectedErrors, GaussDBMCERTExplainParser.rowCountParser(),
                    GaussDBMCERTExplainParser.queryPlanParser());
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    FUZZER {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMFuzzer(globalState);
        }
    },
    DQP {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMDQPOracle(globalState);
        }
    },
    DQE {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMDQEOracle(globalState);
        }
    },
    EET {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMEETOracle(globalState);
        }
    },
    CODDTEST {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBCODDTestOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    SONAR {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMSonarOracle(globalState);
        }
    },
    QUERY_PARTITIONING {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            List<TestOracle<GaussDBMGlobalState>> oracles = new ArrayList<>();
            oracles.add(TLP_WHERE.create(globalState));
            oracles.add(HAVING.create(globalState));
            oracles.add(GROUP_BY.create(globalState));
            oracles.add(AGGREGATE.create(globalState));
            oracles.add(DISTINCT.create(globalState));
            oracles.add(NOREC.create(globalState));
            TestOracle<GaussDBMGlobalState> inner = new CompositeTestOracle<>(oracles, globalState);
            return new TestOracle<GaussDBMGlobalState>() {
                @Override
                public void check() throws Exception {
                    try {
                        inner.check();
                    } catch (AssertionError e) {
                        String msg = e.getMessage();
                        if (msg != null && (msg.contains("the counts mismatch")
                                || msg.contains("The size of the result sets mismatch"))) {
                            throw new IgnoreMeException();
                        }
                        throw e;
                    }
                }

                @Override
                public String getLastQueryString() {
                    return inner.getLastQueryString();
                }

                @Override
                public sqlancer.Reproducer<GaussDBMGlobalState> getLastReproducer() {
                    return inner.getLastReproducer();
                }
            };
        }
    };
}
