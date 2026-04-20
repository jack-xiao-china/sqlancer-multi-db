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
    CODDTEST {
        @Override
        public TestOracle<PostgresGlobalState> create(PostgresGlobalState globalState) throws Exception {
            return new PostgresCODDTestOracle(globalState);
        }
    };

}
