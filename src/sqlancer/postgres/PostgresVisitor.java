package sqlancer.postgres;

import java.util.List;

import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.ast.PostgresAggregate;
import sqlancer.postgres.ast.PostgresBetweenOperation;
import sqlancer.postgres.ast.PostgresBinaryJsonOperation;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation;
import sqlancer.postgres.ast.PostgresCaseWhen;
import sqlancer.postgres.ast.PostgresCastOperation;
import sqlancer.postgres.ast.PostgresCollate;
import sqlancer.postgres.ast.PostgresColumnReference;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresFunction;
import sqlancer.postgres.ast.PostgresInOperation;
import sqlancer.postgres.ast.PostgresJsonContainOperation;
import sqlancer.postgres.ast.PostgresLikeOperation;
import sqlancer.postgres.ast.PostgresOrderByTerm;
import sqlancer.postgres.ast.PostgresPOSIXRegularExpression;
import sqlancer.postgres.ast.PostgresPostfixOperation;
import sqlancer.postgres.ast.PostgresPostfixText;
import sqlancer.postgres.ast.PostgresPrintedExpression;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresText;
import sqlancer.postgres.ast.PostgresUnionSelect;
import sqlancer.postgres.ast.PostgresWithSelect;
import sqlancer.postgres.ast.PostgresDerivedTable;
import sqlancer.postgres.ast.PostgresCteTableReference;
import sqlancer.postgres.ast.PostgresOracleExpressionBag;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresSelect.PostgresSubquery;
import sqlancer.postgres.ast.PostgresSimilarTo;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.ast.PostgresTemporalBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresTemporalFunction;
import sqlancer.postgres.ast.PostgresWindowFunction;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

public interface PostgresVisitor {

    void visit(PostgresConstant constant);

    void visit(PostgresPostfixOperation op);

    void visit(PostgresColumnValue c);

    void visit(PostgresColumnReference c);

    void visit(PostgresTableReference tb);

    void visit(PostgresPrefixOperation op);

    void visit(PostgresSelect op);

    void visit(PostgresOrderByTerm op);

    void visit(PostgresFunction f);

    void visit(PostgresTemporalFunction function);

    void visit(PostgresTemporalBinaryArithmeticOperation op);

    void visit(PostgresCastOperation cast);

    void visit(PostgresBetweenOperation op);

    void visit(PostgresInOperation op);

    void visit(PostgresPostfixText op);

    void visit(PostgresAggregate op);

    void visit(PostgresSimilarTo op);

    void visit(PostgresCollate op);

    void visit(PostgresPOSIXRegularExpression op);

    void visit(PostgresFromTable from);

    void visit(PostgresSubquery subquery);

    void visit(PostgresBinaryLogicalOperation op);

    void visit(PostgresLikeOperation op);

    void visit(PostgresBinaryJsonOperation op);

    void visit(PostgresJsonContainOperation op);

    void visit(PostgresWindowFunction windowFunction);

    void visit(PostgresCaseWhen caseWhen);

    void visit(PostgresPrintedExpression printedExpression);

    void visit(PostgresText text);

    void visit(PostgresUnionSelect unionSelect);

    void visit(PostgresWithSelect withSelect);

    void visit(PostgresDerivedTable derivedTable);

    void visit(PostgresCteTableReference cteTableReference);

    void visit(PostgresOracleExpressionBag bag);

    default void visit(PostgresExpression expression) {
        if (expression instanceof PostgresConstant) {
            visit((PostgresConstant) expression);
        } else if (expression instanceof PostgresPostfixOperation) {
            visit((PostgresPostfixOperation) expression);
        } else if (expression instanceof PostgresColumnValue) {
            visit((PostgresColumnValue) expression);
        } else if (expression instanceof PostgresPrefixOperation) {
            visit((PostgresPrefixOperation) expression);
        } else if (expression instanceof PostgresSelect) {
            visit((PostgresSelect) expression);
        } else if (expression instanceof PostgresOrderByTerm) {
            visit((PostgresOrderByTerm) expression);
        } else if (expression instanceof PostgresFunction) {
            visit((PostgresFunction) expression);
        } else if (expression instanceof PostgresTemporalFunction) {
            visit((PostgresTemporalFunction) expression);
        } else if (expression instanceof PostgresCastOperation) {
            visit((PostgresCastOperation) expression);
        } else if (expression instanceof PostgresTemporalBinaryArithmeticOperation) {
            visit((PostgresTemporalBinaryArithmeticOperation) expression);
        } else if (expression instanceof PostgresBetweenOperation) {
            visit((PostgresBetweenOperation) expression);
        } else if (expression instanceof PostgresInOperation) {
            visit((PostgresInOperation) expression);
        } else if (expression instanceof PostgresAggregate) {
            visit((PostgresAggregate) expression);
        } else if (expression instanceof PostgresPostfixText) {
            visit((PostgresPostfixText) expression);
        } else if (expression instanceof PostgresSimilarTo) {
            visit((PostgresSimilarTo) expression);
        } else if (expression instanceof PostgresPOSIXRegularExpression) {
            visit((PostgresPOSIXRegularExpression) expression);
        } else if (expression instanceof PostgresCollate) {
            visit((PostgresCollate) expression);
        } else if (expression instanceof PostgresFromTable) {
            visit((PostgresFromTable) expression);
        } else if (expression instanceof PostgresSubquery) {
            visit((PostgresSubquery) expression);
        } else if (expression instanceof PostgresLikeOperation) {
            visit((PostgresLikeOperation) expression);
        } else if (expression instanceof PostgresBinaryJsonOperation) {
            visit((PostgresBinaryJsonOperation) expression);
        } else if (expression instanceof PostgresJsonContainOperation) {
            visit((PostgresJsonContainOperation) expression);
        } else if (expression instanceof PostgresColumnReference) {
            visit((PostgresColumnReference) expression);
        } else if (expression instanceof PostgresTableReference) {
            visit((PostgresTableReference) expression);
        } else if (expression instanceof PostgresWindowFunction) {
            visit((PostgresWindowFunction) expression);
        } else if (expression instanceof PostgresCaseWhen) {
            visit((PostgresCaseWhen) expression);
        } else if (expression instanceof PostgresPrintedExpression) {
            visit((PostgresPrintedExpression) expression);
        } else if (expression instanceof PostgresText) {
            visit((PostgresText) expression);
        } else if (expression instanceof PostgresUnionSelect) {
            visit((PostgresUnionSelect) expression);
        } else if (expression instanceof PostgresWithSelect) {
            visit((PostgresWithSelect) expression);
        } else if (expression instanceof PostgresDerivedTable) {
            visit((PostgresDerivedTable) expression);
        } else if (expression instanceof PostgresCteTableReference) {
            visit((PostgresCteTableReference) expression);
        } else if (expression instanceof PostgresOracleExpressionBag) {
            visit((PostgresOracleExpressionBag) expression);
        } else {
            throw new AssertionError(expression);
        }
    }

    static String asString(PostgresExpression expr) {
        PostgresToStringVisitor visitor = new PostgresToStringVisitor();
        visitor.visit(expr);
        return visitor.get();
    }

    static String asExpectedValues(PostgresExpression expr) {
        PostgresExpectedValueVisitor v = new PostgresExpectedValueVisitor();
        v.visit(expr);
        return v.get();
    }

    static String getExpressionAsString(PostgresGlobalState globalState, PostgresDataType type,
            List<PostgresColumn> columns) {
        PostgresExpression expression = PostgresExpressionGenerator.generateExpression(globalState, columns, type);
        PostgresToStringVisitor visitor = new PostgresToStringVisitor();
        visitor.visit(expression);
        return visitor.get();
    }

}
