package sqlancer.gaussdba;

import java.util.ArrayList;
import java.util.List;

import sqlancer.OracleFactory;
import sqlancer.common.oracle.CompositeTestOracle;
import sqlancer.common.oracle.NoRECOracle;
import sqlancer.common.oracle.TLPWhereOracle;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;
import sqlancer.gaussdba.oracle.GaussDBACERTOracle;
import sqlancer.gaussdba.oracle.GaussDBACODDTestOracle;
import sqlancer.gaussdba.oracle.GaussDBADQEOracle;
import sqlancer.gaussdba.oracle.GaussDBADQPOracle;
import sqlancer.gaussdba.oracle.GaussDBAFuzzer;
import sqlancer.gaussdba.oracle.edcdata.GaussDBAEDCDataOracle;
import sqlancer.gaussdba.oracle.GaussDBAPivotedQuerySynthesisOracle;
import sqlancer.gaussdba.oracle.eet.GaussDBAEETOracle;
import sqlancer.gaussdba.oracle.eet.GaussDBAEETInsertSelectOracle;
import sqlancer.gaussdba.oracle.eet.GaussDBAEETUpdateOracle;
import sqlancer.gaussdba.oracle.eet.GaussDBAEETDeleteOracle;
import sqlancer.gaussdba.oracle.ext.GaussDBATLPDistinctOracle;
import sqlancer.gaussdba.oracle.ext.GaussDBATLPGroupByOracle;
import sqlancer.gaussdba.oracle.tlp.GaussDBATLPAggregateOracle;
import sqlancer.gaussdba.oracle.tlp.GaussDBATLPHavingOracle;
import sqlancer.gaussdba.oracle.transaction.GaussDBAWriteCheckOracle;
import sqlancer.gaussdba.oracle.transaction.GaussDBAWriteCheckReproduceOracle;
import sqlancer.gaussdba.oracle.transaction.GaussDBAFucciOracle;
import sqlancer.gaussdba.oracle.transaction.GaussDBATxInferOracle;
import sqlancer.gaussdba.oracle.GaussDBAJIRTransformer;

public enum GaussDBAOracleFactory implements OracleFactory<GaussDBAGlobalState> {

    TLP_WHERE {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState);
            ExpectedErrors expectedErrors = ExpectedErrors.newErrors()
                    .with(GaussDBAErrors.getExpressionErrorStrings()).build();
            return new TLPWhereOracle<>(globalState, gen, expectedErrors);
        }
    },

    NOREC {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState);
            ExpectedErrors errors = ExpectedErrors.newErrors()
                    .with(GaussDBAErrors.getExpressionErrorStrings()).build();
            return new NoRECOracle<>(globalState, gen, errors);
        }
    },

    QUERY_PARTITIONING {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            List<TestOracle<GaussDBAGlobalState>> oracles = new ArrayList<>();
            oracles.add(TLP_WHERE.create(globalState));
            oracles.add(HAVING.create(globalState));
            oracles.add(AGGREGATE.create(globalState));
            return new CompositeTestOracle<>(oracles, globalState);
        }
    },

    HAVING {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBATLPHavingOracle(globalState);
        }
    },

    AGGREGATE {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBATLPAggregateOracle(globalState);
        }
    },

    DISTINCT {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBATLPDistinctOracle(globalState);
        }
    },

    GROUP_BY {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBATLPGroupByOracle(globalState);
        }
    },

    PQS {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAPivotedQuerySynthesisOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },

    CERT {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBACERTOracle(globalState);
        }
    },

    DQP {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBADQPOracle(globalState);
        }
    },

    DQE {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBADQEOracle(globalState);
        }
    },

    EET {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAEETOracle(globalState);
        }
    },
    EET_INSERT_SELECT {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAEETInsertSelectOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    EET_UPDATE {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAEETUpdateOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    EET_DELETE {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAEETDeleteOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },

    FUZZER {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAFuzzer(globalState);
        }
    },
    /**
     * EDC_DATA (Equivalent Data Construction - Data Operation) Oracle.
     * Tests data operation implementation bugs by comparing expressions with precomputed values.
     * Based on SIGMOD 2026 EDC paper methodology.
     */
    EDC_DATA {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAEDCDataOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return false; // EDC_DATA creates its own tables
        }
    },
    /**
     * WriteCheck Oracle for transaction isolation level testing.
     * Detects bugs in transaction execution by comparing schedules.
     */
    WRITE_CHECK {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAWriteCheckOracle(globalState);
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
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAWriteCheckReproduceOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * TX_INFER Oracle for GaussDB-A.
     * Uses auxiliary version tables to infer MVCC visibility and detect isolation bugs.
     */
    TX_INFER {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBATxInferOracle(globalState);
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
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBAFucciOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true; // Fucci needs tables with data for transaction testing
        }
    },
    /**
     * JIR (Join Implication Reasoning) Oracle for GaussDB-A.
     * Detects JOIN optimizer bugs using semantic implication rules (SIGMOD 2026).
     * Supports all 6 rules (Oracle compat has FULL OUTER JOIN and NATURAL JOIN).
     */
    JIR {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new sqlancer.common.oracle.jir.JIROracle<>(globalState, new GaussDBAJIRTransformer(),
                    sqlancer.common.oracle.jir.JIRRule.forGaussDBA());
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return false; // JIR works with empty tables (LEFT JOIN produces valid SQL)
        }
    },
    /**
     * EDC_RADAR (Equivalent Database Construction - RADAR) Oracle.
     * Detects optimizer bugs by comparing query results between original schema (with constraints)
     * and raw schema (without constraints).
     */
    EDC_RADAR {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new sqlancer.gaussdba.oracle.GaussDBAEDCRadarOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * SONAR (Select Optimization N-gram Analysis Runtime) Oracle.
     * Detects optimizer bugs by comparing optimized vs unoptimized query execution.
     */
    SONAR {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new sqlancer.gaussdba.oracle.GaussDBASonarOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * CODDTEST Oracle for GaussDB-A.
     * Tests query optimization correctness by comparing folded/unfolded query results.
     * Supports EXPRESSION, SUBQUERY, and CORRELATED_SUBQUERY modes.
     */
    CODDTEST {
        @Override
        public TestOracle<GaussDBAGlobalState> create(GaussDBAGlobalState globalState) throws Exception {
            return new GaussDBACODDTestOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    };
}