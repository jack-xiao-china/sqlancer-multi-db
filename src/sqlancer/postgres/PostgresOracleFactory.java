package sqlancer.postgres;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sqlancer.OracleFactory;
import sqlancer.common.oracle.CERTOracle;
import sqlancer.common.oracle.CompositeTestOracle;
import sqlancer.common.oracle.NoRECOracle;
import sqlancer.common.oracle.TLPWhereOracle;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.PostgresExpressionGenerator;
import sqlancer.postgres.ast.PostgresSelect.LockingClauseContext;
import sqlancer.postgres.oracle.PostgresEDCOracle;
import sqlancer.postgres.oracle.PostgresFuzzer;
import sqlancer.postgres.oracle.PostgresPivotedQuerySynthesisOracle;
import sqlancer.postgres.oracle.ext.PostgresCODDTestOracle;
import sqlancer.postgres.oracle.ext.PostgresDQPOracle;
import sqlancer.postgres.oracle.ext.PostgresDQEOracle;
import sqlancer.postgres.oracle.ext.PostgresTLPDistinctOracle;
import sqlancer.postgres.oracle.ext.PostgresTLPGroupByOracle;
import sqlancer.postgres.oracle.tlp.PostgresTLPAggregateOracle;
import sqlancer.postgres.oracle.tlp.PostgresTLPHavingOracle;
import sqlancer.postgres.oracle.ext.eet.PostgresEETOracle;
import sqlancer.postgres.oracle.ext.eet.PostgresEETUpdateOracle;
import sqlancer.postgres.oracle.ext.eet.PostgresEETDeleteOracle;
import sqlancer.postgres.oracle.ext.eet.PostgresEETInsertSelectOracle;
import sqlancer.postgres.oracle.PostgresSonarOracle;
import sqlancer.postgres.oracle.transaction.PostgresWriteCheckOracle;
import sqlancer.postgres.oracle.transaction.PostgresWriteCheckReproduceOracle;
import sqlancer.postgres.oracle.transaction.PostgresFucciOracle;
import sqlancer.postgres.oracle.transaction.PostgresTxInferOracle;

public enum PostgresOracleFactory implements OracleFactory<PostgresGlobalState> {
    NOREC {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState)
                    .setLockingClauseContext(LockingClauseContext.ORACLE_SCALAR);
            ExpectedErrors errors = ExpectedErrors.newErrors().with(PostgresCommon.getCommonExpressionErrors())
                    .with(PostgresCommon.getCommonFetchErrors())
                    .withRegex(PostgresCommon.getCommonExpressionRegexErrors()).build();
            return new NoRECOracle<>(globalState, gen, errors);
        }
    },
    PQS {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresPivotedQuerySynthesisOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    TLP_WHERE {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState);
            ExpectedErrors expectedErrors = ExpectedErrors.newErrors().with(PostgresCommon.getCommonExpressionErrors())
                    .with(PostgresCommon.getCommonFetchErrors())
                    .withRegex(PostgresCommon.getCommonExpressionRegexErrors()).build();
            return new TLPWhereOracle<>(globalState, gen, expectedErrors);
        }
    },
    HAVING {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresTLPHavingOracle(globalState);
        }

    },
    AGGREGATE {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresTLPAggregateOracle(globalState);
        }
    },
    DISTINCT {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresTLPDistinctOracle(globalState);
        }
    },
    GROUP_BY {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresTLPGroupByOracle(globalState);
        }
    },
    QUERY_PARTITIONING {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            List<TestOracle<PostgresGlobalState>> oracles = new ArrayList<>();
            oracles.add(TLP_WHERE.create(globalState));
            oracles.add(HAVING.create(globalState));
            oracles.add(AGGREGATE.create(globalState));
            return new CompositeTestOracle<PostgresGlobalState>(oracles, globalState);
        }
    },
    CERT {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState)
                    .setLockingClauseContext(LockingClauseContext.EXPLAIN_PLAN);
            ExpectedErrors errors = ExpectedErrors.newErrors().with(PostgresCommon.getCommonExpressionErrors())
                    .withRegex(PostgresCommon.getCommonExpressionRegexErrors())
                    .with(PostgresCommon.getCommonFetchErrors()).with(PostgresCommon.getCommonInsertUpdateErrors())
                    .with(PostgresCommon.getGroupingErrors()).with(PostgresCommon.getCommonInsertUpdateErrors())
                    .with(PostgresCommon.getCommonRangeExpressionErrors()).build();
            CERTOracle.CheckedFunction<SQLancerResultSet, Optional<Long>> rowCountParser = (rs) -> {
                String content = rs.getString(1).trim();
                if (content.contains("Result") && content.contains("rows=")) {
                    try {
                        int ind = content.indexOf("rows=");
                        long number = Long.parseLong(content.substring(ind + 5).split(" ")[0]);
                        return Optional.of(number);
                    } catch (Exception e) {
                    }
                }
                return Optional.empty();
            };
            CERTOracle.CheckedFunction<SQLancerResultSet, Optional<String>> queryPlanParser = (rs) -> {
                String content = rs.getString(1).trim();
                String[] planPart = content.split("-> ");
                String plan = planPart[planPart.length - 1];
                return Optional.of(plan.split("  ")[0].trim());
            };
            return new CERTOracle<>(globalState, gen, errors, rowCountParser, queryPlanParser);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    FUZZER {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresFuzzer(globalState);
        }

    },
    DQP {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresDQPOracle(globalState);
        }
    },
    DQE {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresDQEOracle(globalState);
        }
    },
    EET {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresEETOracle(globalState);
        }
    },
    EET_UPDATE {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresEETUpdateOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    EET_DELETE {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresEETDeleteOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    EET_INSERT_SELECT {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresEETInsertSelectOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    CODDTEST {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresCODDTestOracle(globalState);
        }
    },
    /**
     * SONAR (Select Optimization N-gram Analysis Runtime) Oracle.
     * Detects optimizer bugs by comparing optimized vs unoptimized query execution.
     */
    SONAR {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresSonarOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * EDC (Equivalent Database Construction) Oracle.
     * Detects optimizer bugs by comparing query results between original DB (with constraints)
     * and raw DB (without constraints).
     */
    EDC {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresEDCOracle(globalState);
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
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresWriteCheckOracle(globalState);
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
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresWriteCheckReproduceOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * TX_INFER Oracle for PostgreSQL.
     * Uses auxiliary version tables to infer MVCC visibility and detect isolation bugs.
     */
    TX_INFER {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresTxInferOracle(globalState);
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
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresFucciOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true; // Fucci needs tables with data for transaction testing
        }
    },
    /**
     * JIR (Join Implication Reasoning) Oracle.
     * Detects JOIN optimizer bugs by comparing results across different JOIN types
     * using semantic implication rules (SIGMOD 2026).
     */
    JIR {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new sqlancer.common.oracle.jir.JIROracle<>(globalState,
                    new sqlancer.postgres.oracle.ext.PostgresJIRTransformer(),
                    sqlancer.common.oracle.jir.JIRRule.forPostgres());
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return false; // JIR works with empty tables (LEFT JOIN produces valid SQL)
        }
    };

}
