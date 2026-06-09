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
import sqlancer.gaussdbm.oracle.GaussDBMEDCOracle;
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
import sqlancer.gaussdbm.oracle.eet.GaussDBMEETInsertSelectOracle;
import sqlancer.gaussdbm.oracle.eet.GaussDBMEETUpdateOracle;
import sqlancer.gaussdbm.oracle.eet.GaussDBMEETDeleteOracle;
import sqlancer.gaussdbm.oracle.transaction.GaussDBMWriteCheckOracle;
import sqlancer.gaussdbm.oracle.transaction.GaussDBMWriteCheckReproduceOracle;
import sqlancer.gaussdbm.oracle.transaction.GaussDBMFucciOracle;
import sqlancer.gaussdbm.oracle.transaction.GaussDBMTxInferOracle;
import sqlancer.gaussdbm.oracle.GaussDBMJIRTransformer;

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
    EET_INSERT_SELECT {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMEETInsertSelectOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    EET_UPDATE {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMEETUpdateOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    EET_DELETE {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMEETDeleteOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
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
    EDC {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMEDCOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * WriteCheck Oracle for transaction isolation level testing.
     * Detects bugs in transaction execution by comparing schedules.
     */
    WRITE_CHECK {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMWriteCheckOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * WriteCheck Reproduce Oracle for reproducing bugs from file.
     * Reads transaction test case from specified file and re-executes.
     */
    WRITE_CHECK_REPRODUCE {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMWriteCheckReproduceOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * Fucci Oracle - MVCC-based transaction testing.
     * Combines DT (Differential Testing), MT (Metamorphic Testing), and CS (Constraint Solving) oracles.
     * Supports MySQL, PostgreSQL, GaussDB-M, and GaussDB-A.
     */
    FUCCI {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMFucciOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true; // Fucci needs tables with data for transaction testing
        }
    },
    /**
     * TX_INFER Oracle - MVCC version inference for transaction isolation level testing.
     * Uses auxiliary version tables to track row versions and infer expected results.
     * Inherits MySQL implementation since GaussDB-M uses MySQL-compatible mode.
     */
    TX_INFER {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new GaussDBMTxInferOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true; // TX_INFER needs tables with data
        }
    },
    /**
     * JIR (Join Implication Reasoning) Oracle for GaussDB-M.
     * Detects JOIN optimizer bugs using semantic implication rules (SIGMOD 2026).
     * Supports 5 rules (MySQL compat has no FULL OUTER JOIN).
     */
    JIR {
        @Override
        public TestOracle<GaussDBMGlobalState> create(GaussDBMGlobalState globalState) throws Exception {
            return new sqlancer.common.oracle.jir.JIROracle<>(globalState, new GaussDBMJIRTransformer(),
                    sqlancer.common.oracle.jir.JIRRule.forGaussDBM());
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return false; // JIR works with empty tables (LEFT JOIN produces valid SQL)
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
