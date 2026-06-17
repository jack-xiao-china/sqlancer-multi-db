package sqlancer.mysql;

import sqlancer.mysql.ast.MySQLAggregate;
import sqlancer.mysql.ast.MySQLAnyAllSubquery;
import sqlancer.mysql.ast.MySQLBetweenOperation;
import sqlancer.mysql.ast.MySQLBinaryArithmeticOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryOperation;
import sqlancer.mysql.ast.MySQLCaseOperator;
import sqlancer.mysql.ast.MySQLCastOperation;
import sqlancer.mysql.ast.MySQLCollate;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLComputableFunction;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExists;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLInOperation;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLOrderByTerm;
import sqlancer.mysql.ast.MySQLScalarSubquery;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLStringExpression;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.ast.MySQLPrintedExpression;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation;
import sqlancer.mysql.ast.MySQLUnionSelect;
import sqlancer.mysql.ast.MySQLWithSelect;
import sqlancer.mysql.ast.MySQLDerivedTable;
import sqlancer.mysql.ast.MySQLCteTableReference;
import sqlancer.mysql.ast.MySQLOracleExpressionBag;
import sqlancer.mysql.ast.MySQLValues;
import sqlancer.mysql.ast.MySQLResultMap;
import sqlancer.mysql.ast.MySQLOracleAlias;
import sqlancer.mysql.ast.MySQLTypeof;
import sqlancer.mysql.ast.MySQLWildcard;
import sqlancer.mysql.ast.MySQLTableAndColumnRef;
import sqlancer.mysql.ast.MySQLTemporalFunction;

public interface MySQLVisitor {

    void visit(MySQLUnionSelect unionSelect);

    void visit(MySQLWithSelect withSelect);

    void visit(MySQLDerivedTable derivedTable);

    void visit(MySQLCteTableReference cteTableRef);

    void visit(MySQLPrintedExpression printed);

    void visit(MySQLTableReference ref);

    void visit(MySQLConstant constant);

    void visit(MySQLColumnReference column);

    void visit(MySQLUnaryPostfixOperation column);

    void visit(MySQLComputableFunction f);

    void visit(MySQLBinaryLogicalOperation op);

    void visit(MySQLSelect select);

    void visit(MySQLBinaryComparisonOperation op);

    void visit(MySQLCastOperation op);

    void visit(MySQLInOperation op);

    void visit(MySQLBinaryOperation op);

    void visit(MySQLBinaryArithmeticOperation op);

    void visit(MySQLOrderByTerm op);

    void visit(MySQLExists op);

    void visit(MySQLAnyAllSubquery op);

    void visit(MySQLScalarSubquery op);

    void visit(MySQLStringExpression op);

    void visit(MySQLBetweenOperation op);

    void visit(MySQLCollate collate);

    void visit(MySQLJoin join);

    void visit(MySQLText text);

    void visit(MySQLAggregate aggregate);

    void visit(MySQLCaseOperator caseOp);

    void visit(MySQLOracleExpressionBag bag);

    void visit(MySQLValues values);

    void visit(MySQLResultMap resultMap);

    void visit(MySQLOracleAlias alias);

    void visit(MySQLTypeof typeOf);

    void visit(MySQLTableAndColumnRef ref);

    void visit(MySQLTemporalFunction func);

    void visit(MySQLWildcard wildcard);

    default void visit(MySQLExpression expr) {
        if (expr instanceof MySQLConstant) {
            visit((MySQLConstant) expr);
        } else if (expr instanceof MySQLColumnReference) {
            visit((MySQLColumnReference) expr);
        } else if (expr instanceof MySQLUnaryPostfixOperation) {
            visit((MySQLUnaryPostfixOperation) expr);
        } else if (expr instanceof MySQLComputableFunction) {
            visit((MySQLComputableFunction) expr);
        } else if (expr instanceof MySQLBinaryLogicalOperation) {
            visit((MySQLBinaryLogicalOperation) expr);
        } else if (expr instanceof MySQLSelect) {
            visit((MySQLSelect) expr);
        } else if (expr instanceof MySQLBinaryComparisonOperation) {
            visit((MySQLBinaryComparisonOperation) expr);
        } else if (expr instanceof MySQLCastOperation) {
            visit((MySQLCastOperation) expr);
        } else if (expr instanceof MySQLInOperation) {
            visit((MySQLInOperation) expr);
        } else if (expr instanceof MySQLBinaryOperation) {
            visit((MySQLBinaryOperation) expr);
        } else if (expr instanceof MySQLBinaryArithmeticOperation) {
            visit((MySQLBinaryArithmeticOperation) expr);
        } else if (expr instanceof MySQLOrderByTerm) {
            visit((MySQLOrderByTerm) expr);
        } else if (expr instanceof MySQLExists) {
            visit((MySQLExists) expr);
        } else if (expr instanceof MySQLAnyAllSubquery) {
            visit((MySQLAnyAllSubquery) expr);
        } else if (expr instanceof MySQLScalarSubquery) {
            visit((MySQLScalarSubquery) expr);
        } else if (expr instanceof MySQLJoin) {
            visit((MySQLJoin) expr);
        } else if (expr instanceof MySQLStringExpression) {
            visit((MySQLStringExpression) expr);
        } else if (expr instanceof MySQLBetweenOperation) {
            visit((MySQLBetweenOperation) expr);
        } else if (expr instanceof MySQLTableReference) {
            visit((MySQLTableReference) expr);
        } else if (expr instanceof MySQLCollate) {
            visit((MySQLCollate) expr);
        } else if (expr instanceof MySQLText) {
            visit((MySQLText) expr);
        } else if (expr instanceof MySQLAggregate) {
            visit((MySQLAggregate) expr);
        } else if (expr instanceof MySQLCaseOperator) {
            visit((MySQLCaseOperator) expr);
        } else if (expr instanceof MySQLPrintedExpression) {
            visit((MySQLPrintedExpression) expr);
        } else if (expr instanceof MySQLUnionSelect) {
            visit((MySQLUnionSelect) expr);
        } else if (expr instanceof MySQLWithSelect) {
            visit((MySQLWithSelect) expr);
        } else if (expr instanceof MySQLDerivedTable) {
            visit((MySQLDerivedTable) expr);
        } else if (expr instanceof MySQLCteTableReference) {
            visit((MySQLCteTableReference) expr);
        } else if (expr instanceof MySQLOracleExpressionBag) {
            visit((MySQLOracleExpressionBag) expr);
        } else if (expr instanceof MySQLValues) {
            visit((MySQLValues) expr);
        } else if (expr instanceof MySQLResultMap) {
            visit((MySQLResultMap) expr);
        } else if (expr instanceof MySQLOracleAlias) {
            visit((MySQLOracleAlias) expr);
        } else if (expr instanceof MySQLTypeof) {
            visit((MySQLTypeof) expr);
        } else if (expr instanceof MySQLTableAndColumnRef) {
            visit((MySQLTableAndColumnRef) expr);
        } else if (expr instanceof MySQLTemporalFunction) {
            visit((MySQLTemporalFunction) expr);
        } else if (expr instanceof MySQLWildcard) {
            visit((MySQLWildcard) expr);
        } else if (expr instanceof MySQLConstant.MySQLIntervalConstant) {
            visit((MySQLConstant) expr);
        } else {
            throw new AssertionError(expr);
        }
    }

    static String asString(MySQLExpression expr) {
        MySQLToStringVisitor visitor = new MySQLToStringVisitor();
        visitor.visit(expr);
        return visitor.get();
    }

    static String asExpectedValues(MySQLExpression expr) {
        MySQLExpectedValueVisitor visitor = new MySQLExpectedValueVisitor();
        visitor.visit(expr);
        return visitor.get();
    }

}
