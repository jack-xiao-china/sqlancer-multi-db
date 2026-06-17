package sqlancer.mysql.ast;

/**
 * Represents the wildcard (*) in a SELECT fetch column list. Renders as literal "*" in SQL output.
 * Used by JIR Oracle for SELECT * fetch column generation, matching the original
 * GeneralJIROracle.generateFetchColumns() pattern.
 */
public class MySQLWildcard implements MySQLExpression {

    @Override
    public MySQLConstant getExpectedValue() {
        return null;
    }

}
