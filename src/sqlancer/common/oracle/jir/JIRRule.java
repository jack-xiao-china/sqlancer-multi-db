package sqlancer.common.oracle.jir;

import java.util.Arrays;

/**
 * JIR (Join Implication Reasoning) rules from SIGMOD 2026. Each rule defines a semantic relationship between
 * different JOIN types that can be used to detect JOIN optimizer bugs.
 */
public enum JIRRule {

    /** Rule 1: LEFT JOIN = INNER JOIN UNION ALL anti-join (NOT EXISTS) */
    LEFT_JOIN_DECOMPOSITION(true, true, true, true),

    /** Rule 2: A LEFT JOIN B = B RIGHT JOIN A (with column reorder) */
    LEFT_RIGHT_SYMMETRY(true, true, true, true),

    /** Rule 3: L = SEMI(L) UNION ALL ANTI(L) via EXISTS + NOT EXISTS */
    SEMI_ANTI_COMPLEMENT(true, true, true, true),

    /** Rule 4: FULL JOIN = INNER UNION ALL left-anti(NULL) UNION ALL right-anti(NULL) */
    FULL_JOIN_DECOMPOSITION(false, true, false, true),

    /** Rule 5: CROSS JOIN = any JOIN ON TRUE (when both tables non-empty) */
    CROSS_JOIN_EQUIVALENCE(true, true, true, true),

    /** Rule 6: NATURAL JOIN = INNER JOIN with explicit equality conditions */
    NATURAL_JOIN_EXPLICATION(true, true, true, true);

    private final boolean mysqlSupported;
    private final boolean postgresSupported;
    private final boolean gaussdbmSupported;
    private final boolean gaussdbaSupported;

    JIRRule(boolean mysqlSupported, boolean postgresSupported, boolean gaussdbmSupported,
            boolean gaussdbaSupported) {
        this.mysqlSupported = mysqlSupported;
        this.postgresSupported = postgresSupported;
        this.gaussdbmSupported = gaussdbmSupported;
        this.gaussdbaSupported = gaussdbaSupported;
    }

    public boolean isMysqlSupported() {
        return mysqlSupported;
    }

    public boolean isPostgresSupported() {
        return postgresSupported;
    }

    public boolean isGaussdbmSupported() {
        return gaussdbmSupported;
    }

    public boolean isGaussdbaSupported() {
        return gaussdbaSupported;
    }

    /**
     * Returns all rules supported by MySQL (excludes FULL_JOIN_DECOMPOSITION).
     */
    public static JIRRule[] forMySQL() {
        return Arrays.stream(values()).filter(JIRRule::isMysqlSupported).toArray(JIRRule[]::new);
    }

    /**
     * Returns all rules supported by PostgreSQL (all 6 rules).
     */
    public static JIRRule[] forPostgres() {
        return Arrays.stream(values()).filter(JIRRule::isPostgresSupported).toArray(JIRRule[]::new);
    }

    /**
     * Returns all rules supported by GaussDB-M (MySQL compatibility mode, excludes FULL_JOIN_DECOMPOSITION).
     */
    public static JIRRule[] forGaussDBM() {
        return Arrays.stream(values()).filter(JIRRule::isGaussdbmSupported).toArray(JIRRule[]::new);
    }

    /**
     * Returns all rules supported by GaussDB-A (Oracle compatibility mode, all 6 rules including FULL_JOIN_DECOMPOSITION).
     */
    public static JIRRule[] forGaussDBA() {
        return Arrays.stream(values()).filter(JIRRule::isGaussdbaSupported).toArray(JIRRule[]::new);
    }
}