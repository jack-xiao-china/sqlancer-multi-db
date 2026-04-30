package sqlancer.mysql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sqlancer.IgnoreMeException;
import sqlancer.OracleFactory;
import sqlancer.common.oracle.CERTOracle;
import sqlancer.common.oracle.CompositeTestOracle;
import sqlancer.common.oracle.NoRECOracle;
import sqlancer.common.oracle.TLPWhereOracle;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.oracle.MySQLCODDTestOracle;
import sqlancer.mysql.oracle.MySQLEDCOracle;
import sqlancer.mysql.oracle.MySQLSonarOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.mysql.gen.MySQLExpressionGenerator;
import sqlancer.mysql.oracle.MySQLDQEOracle;
import sqlancer.mysql.oracle.MySQLDQPOracle;
import sqlancer.mysql.oracle.MySQLFuzzer;
import sqlancer.mysql.oracle.MySQLPivotedQuerySynthesisOracle;
import sqlancer.mysql.oracle.MySQLTLPAggregateOracle;
import sqlancer.mysql.oracle.MySQLTLPDistinctOracle;
import sqlancer.mysql.oracle.MySQLTLPGroupByOracle;
import sqlancer.mysql.oracle.MySQLTLPHavingOracle;
import sqlancer.mysql.oracle.eet.MySQLEETOracle;

public enum MySQLOracleFactory implements OracleFactory<MySQLGlobalState> {

    AGGREGATE {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLTLPAggregateOracle(globalState);
        }
    },
    HAVING {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLTLPHavingOracle(globalState);
        }
    },
    GROUP_BY {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLTLPGroupByOracle(globalState);
        }
    },
    DISTINCT {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLTLPDistinctOracle(globalState);
        }
    },
    NOREC {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState);
            ExpectedErrors expectedErrors = ExpectedErrors.newErrors().with(MySQLErrors.getExpressionErrors())
                    .withRegex(MySQLErrors.getExpressionRegexErrors()).build();
            return new NoRECOracle<>(globalState, gen, expectedErrors);
        }
    },
    TLP_WHERE {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState);
            ExpectedErrors expectedErrors = ExpectedErrors.newErrors().with(MySQLErrors.getExpressionErrors())
                    .withRegex(MySQLErrors.getExpressionRegexErrors()).build();

            return new TLPWhereOracle<>(globalState, gen, expectedErrors);
        }

    },
    PQS {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLPivotedQuerySynthesisOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }

    },
    CERT {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            MySQLExpressionGenerator gen = new MySQLExpressionGenerator(globalState);
            ExpectedErrors expectedErrors = ExpectedErrors.newErrors().with(MySQLErrors.getExpressionErrors())
                    .withRegex(MySQLErrors.getExpressionRegexErrors()).build();
            CERTOracle.CheckedFunction<SQLancerResultSet, Optional<Long>> rowCountParser = (rs) -> {
                int rowCount = rs.getInt(10);
                return Optional.of((long) rowCount);
            };
            CERTOracle.CheckedFunction<SQLancerResultSet, Optional<String>> queryPlanParser = (rs) -> {
                String operation = rs.getString(2);
                return Optional.of(operation);
            };

            return new CERTOracle<>(globalState, gen, expectedErrors, rowCountParser, queryPlanParser);

        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    FUZZER {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws Exception {
            return new MySQLFuzzer(globalState);
        }

    },
    DQP {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLDQPOracle(globalState);
        }
    },
    DQE {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLDQEOracle(globalState);
        }
    },
    EET {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLEETOracle(globalState);
        }
    },
    CODDTEST {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLCODDTestOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;
        }
    },
    /**
     * EDC (Equivalent Database Construction) Oracle.
     * Detects optimizer bugs by comparing query results between:
     * - Original DB: with constraints (NOT NULL, UNIQUE, FK, CHECK, GENERATED)
     * - Raw DB: without constraints (pure data copy)
     */
    EDC {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLEDCOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true; // EDC needs tables with data
        }
    },
    /**
     * SONAR (Select Optimization N-gram Analysis Runtime) Oracle.
     * Detects optimizer bugs by comparing optimized vs unoptimized query execution.
     * Uses flag-based filtering in outer query instead of WHERE clause.
     */
    SONAR {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLSonarOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true; // SONAR needs tables with data
        }
    },
    QUERY_PARTITIONING {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws Exception {
            List<TestOracle<MySQLGlobalState>> oracles = new ArrayList<>();
            oracles.add(TLP_WHERE.create(globalState));
            oracles.add(HAVING.create(globalState));
            oracles.add(GROUP_BY.create(globalState));
            oracles.add(AGGREGATE.create(globalState));
            oracles.add(DISTINCT.create(globalState));
            oracles.add(NOREC.create(globalState));
            TestOracle<MySQLGlobalState> inner = new CompositeTestOracle<>(oracles, globalState);
            return new TestOracle<MySQLGlobalState>() {
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
                public sqlancer.Reproducer<MySQLGlobalState> getLastReproducer() {
                    return inner.getLastReproducer();
                }
            };
        }
    };
}
