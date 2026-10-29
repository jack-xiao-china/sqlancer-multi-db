package sqlancer.gaussdbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.beust.jcommander.Parameter;

import sqlancer.common.oracle.CERTOracle;
import sqlancer.common.oracle.NoRECOracle;
import sqlancer.common.oracle.TLPWhereOracle;
import sqlancer.gaussdbm.oracle.GaussDBCODDTestOracle;
import sqlancer.gaussdbm.oracle.GaussDBMDQEOracle;
import sqlancer.gaussdbm.oracle.GaussDBMDQPOracle;
import sqlancer.gaussdbm.oracle.GaussDBMFuzzer;
import sqlancer.gaussdbm.oracle.GaussDBMPivotedQuerySynthesisOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPAggregateOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPDistinctOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPGroupByOracle;
import sqlancer.gaussdbm.oracle.GaussDBMTLPHavingOracle;
import sqlancer.gaussdbm.oracle.GaussDBMSonarOracle;
import sqlancer.gaussdbm.oracle.eet.GaussDBMEETOracle;

class GaussDBMOracleIsolationTest {

    private static final Set<String> EXPECTED_ORACLE_NAMES = Set.of("AGGREGATE", "CERT", "CODDTEST", "DISTINCT", "DQE",
            "DQP", "EDC_DATA", "EDC_RADAR", "EET", "EET_DELETE", "EET_INSERT_SELECT", "EET_UPDATE", "FUCCI", "FUZZER",
            "GROUP_BY", "HAVING", "JIR", "NOREC", "PQS", "QUERY_PARTITIONING", "SONAR", "TLP_WHERE", "TX_INFER",
            "WRITE_CHECK", "WRITE_CHECK_REPRODUCE");

    /**
     * Expected concrete oracle implementation per factory constant (kept in sync with {@link GaussDBMOracleFactory}).
     * {@code QUERY_PARTITIONING} is composite and has no single class here.
     */
    private static Class<?> expectedImplementationClass(GaussDBMOracleFactory o) {
        switch (o) {
        case AGGREGATE:
            return GaussDBMTLPAggregateOracle.class;
        case HAVING:
            return GaussDBMTLPHavingOracle.class;
        case GROUP_BY:
            return GaussDBMTLPGroupByOracle.class;
        case DISTINCT:
            return GaussDBMTLPDistinctOracle.class;
        case NOREC:
            return NoRECOracle.class;
        case TLP_WHERE:
            return TLPWhereOracle.class;
        case PQS:
            return GaussDBMPivotedQuerySynthesisOracle.class;
        case CERT:
            return CERTOracle.class;
        case FUZZER:
            return GaussDBMFuzzer.class;
        case DQP:
            return GaussDBMDQPOracle.class;
        case DQE:
            return GaussDBMDQEOracle.class;
        case EET:
            return GaussDBMEETOracle.class;
        case CODDTEST:
            return GaussDBCODDTestOracle.class;
        case SONAR:
            return GaussDBMSonarOracle.class;
        case EET_DELETE:
            return sqlancer.gaussdbm.oracle.eet.GaussDBMEETDeleteOracle.class;
        case EET_UPDATE:
            return sqlancer.gaussdbm.oracle.eet.GaussDBMEETUpdateOracle.class;
        case EET_INSERT_SELECT:
            return sqlancer.gaussdbm.oracle.eet.GaussDBMEETInsertSelectOracle.class;
        case QUERY_PARTITIONING:
        case JIR:
        case TX_INFER:
        case WRITE_CHECK:
        case WRITE_CHECK_REPRODUCE:
        case FUCCI:
        case EDC_RADAR:
        case EDC_DATA:
            // composite / common-delegated oracles without a single GaussDBM impl class
            return null;
        default:
            throw new AssertionError(o);
        }
    }

    @Test
    void onlyGaussdbMOptionsAreShownInHelp() {
        Set<String> visibleParameterNames = new TreeSet<>();
        for (Field f : GaussDBMOptions.class.getDeclaredFields()) {
            Parameter p = f.getAnnotation(Parameter.class);
            if (p == null || p.hidden()) {
                continue;
            }
            visibleParameterNames.addAll(Arrays.asList(p.names()));
        }
        assertEquals(Set.of("--fucci-isolation-level", "--fucci-oracle-type", "--fucci-schedule-count", "--oracle",
                "--target-database"), visibleParameterNames);
    }

    @Test
    void oracleEnumIsGaussdbmSpecific() {
        GaussDBMOptions opt = new GaussDBMOptions();
        assertTrue(opt.getTestOracleFactory().get(0) instanceof GaussDBMOracleFactory);
    }

    @Test
    void oracleFactoryExposesAllRegisteredOracles() {
        GaussDBMOracleFactory[] values = GaussDBMOracleFactory.values();
        assertEquals(25, values.length);
        Set<String> names = new TreeSet<>();
        for (GaussDBMOracleFactory v : values) {
            names.add(v.name());
        }
        assertEquals(EXPECTED_ORACLE_NAMES, names);
    }

    @Test
    void pqsCertAndCoddRequireRowsLikeMysql() {
        assertTrue(GaussDBMOracleFactory.PQS.requiresAllTablesToContainRows());
        assertTrue(GaussDBMOracleFactory.CERT.requiresAllTablesToContainRows());
        assertTrue(GaussDBMOracleFactory.CODDTEST.requiresAllTablesToContainRows());
        // Kept in sync with GaussDBMOracleFactory.requiresAllTablesToContainRows().
        EnumSet<GaussDBMOracleFactory> needRows = EnumSet.of(GaussDBMOracleFactory.PQS, GaussDBMOracleFactory.CERT,
                GaussDBMOracleFactory.CODDTEST, GaussDBMOracleFactory.EET_INSERT_SELECT,
                GaussDBMOracleFactory.EET_UPDATE, GaussDBMOracleFactory.EET_DELETE, GaussDBMOracleFactory.EDC_RADAR,
                GaussDBMOracleFactory.WRITE_CHECK, GaussDBMOracleFactory.WRITE_CHECK_REPRODUCE,
                GaussDBMOracleFactory.FUCCI, GaussDBMOracleFactory.TX_INFER);
        for (GaussDBMOracleFactory v : GaussDBMOracleFactory.values()) {
            if (needRows.contains(v)) {
                continue;
            }
            assertFalse(v.requiresAllTablesToContainRows(), () -> "unexpected requiresAllTablesToContainRows: " + v);
        }
    }

    @Test
    void oracleImplementationsAreNotAllDelegatedToTlpWhere() {
        List<Class<?>> impls = Arrays.stream(GaussDBMOracleFactory.values())
                .map(GaussDBMOracleIsolationTest::expectedImplementationClass).filter(Objects::nonNull)
                .collect(Collectors.toList());
        assertTrue(impls.stream().anyMatch(c -> !TLPWhereOracle.class.isAssignableFrom(c)));
        long tlpWhereAssignable = impls.stream().filter(TLPWhereOracle.class::isAssignableFrom).count();
        assertEquals(1, tlpWhereAssignable);
    }
}
