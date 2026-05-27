package sqlancer.gaussdba.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.gen.CERTGenerator;
import sqlancer.common.gen.ExpressionGenerator;
import sqlancer.common.gen.NoRECGenerator;
import sqlancer.common.gen.TLPWhereGenerator;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBARowValue;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBABetweenOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryArithmeticOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryArithmeticOperation.GaussDBAArithmeticOperator;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation;
import sqlancer.gaussdba.ast.GaussDBACastOperation;
import sqlancer.gaussdba.ast.GaussDBACaseWhen;
import sqlancer.gaussdba.ast.GaussDBALikeOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation.GaussDBABinaryComparisonOperator;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation.GaussDBABinaryLogicalOperator;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAColumnValue;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBADataType;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAInOperation;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBAJoin.GaussDBAJoinType;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdba.ast.GaussDBAAggregate;
import sqlancer.gaussdba.ast.GaussDBAAggregate.GaussDBAAggregateFunction;

public class GaussDBAExpressionGenerator
        implements ExpressionGenerator<GaussDBAExpression>,
        TLPWhereGenerator<GaussDBASelect, GaussDBAJoin, GaussDBAExpression, GaussDBATable, GaussDBAColumn>,
        NoRECGenerator<GaussDBASelect, GaussDBAJoin, GaussDBAExpression, GaussDBATable, GaussDBAColumn>,
        CERTGenerator<GaussDBASelect, GaussDBAJoin, GaussDBAExpression, GaussDBATable, GaussDBAColumn> {

    private final GaussDBAGlobalState state;
    private List<GaussDBATable> tables = new ArrayList<>();
    private AbstractTables<GaussDBATable, GaussDBAColumn> targetTables;
    private List<GaussDBAColumn> columns;
    private GaussDBARowValue rowValue;

    public GaussDBAExpressionGenerator(GaussDBAGlobalState state) {
        this.state = state;
    }

    public GaussDBAExpressionGenerator setColumns(List<GaussDBAColumn> columns) {
        this.columns = columns;
        return this;
    }

    public GaussDBAExpressionGenerator setRowValue(GaussDBARowValue rowValue) {
        this.rowValue = rowValue;
        return this;
    }

    // ExpressionGenerator interface method
    @Override
    public GaussDBAExpression generatePredicate() {
        return generateBooleanExpression();
    }

    public GaussDBAExpression generateExpressionWithExpectedResult(GaussDBADataType type) {
        GaussDBAExpression expr = generateExpression(0);
        return expr;
    }

    private enum Action {
        COLUMN, LITERAL, BINARY_LOGICAL_OPERATOR, BINARY_COMPARISON_OPERATION, BINARY_ARITHMETIC_OPERATION,
        BETWEEN_OPERATOR, IN_OPERATOR, UNARY_OPERATOR, UNARY_POSTFIX_OPERATOR, CASE_OPERATOR,
        AGGREGATE_FUNCTION, CAST_OPERATOR, LIKE_OPERATOR
    }

    public GaussDBAExpression generateExpression(int depth) {
        if (depth >= state.getOptions().getMaxExpressionDepth()) {
            return generateLeafNode();
        }
        switch (Randomly.fromOptions(Action.values())) {
        case COLUMN:
            return generateColumn();
        case LITERAL:
            return generateConstant();
        case BINARY_LOGICAL_OPERATOR:
            return new GaussDBABinaryLogicalOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    GaussDBABinaryLogicalOperator.getRandom());
        case BINARY_COMPARISON_OPERATION:
            return new GaussDBABinaryComparisonOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    GaussDBABinaryComparisonOperator.getRandom());
        case BINARY_ARITHMETIC_OPERATION:
            return new GaussDBABinaryArithmeticOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                    GaussDBAArithmeticOperator.getRandom());
        case BETWEEN_OPERATOR:
            GaussDBAExpression a = generateExpression(depth + 1);
            GaussDBAExpression x = generateLeafNode();
            GaussDBAExpression y = generateLeafNode();
            return new GaussDBABetweenOperation(a, x, y, Randomly.getBoolean());
        case IN_OPERATOR:
            return GaussDBAInOperation.create(generateLeafNode(), state.getRandomly(), Randomly.smallNumber() + 1);
        case UNARY_OPERATOR:
            return new GaussDBAUnaryPrefixOperation(generateExpression(depth + 1),
                    Randomly.getBoolean() ? UnaryPrefixOperator.NOT : Randomly.fromOptions(UnaryPrefixOperator.UNARY_PLUS, UnaryPrefixOperator.UNARY_MINUS));
        case UNARY_POSTFIX_OPERATOR:
            return new GaussDBAUnaryPostfixOperation(generateExpression(depth + 1),
                    Randomly.fromOptions(UnaryPostfixOperator.IS_NULL, UnaryPostfixOperator.IS_NOT_NULL));
        case CASE_OPERATOR:
            int nrCases = Randomly.smallNumber() + 1;
            List<GaussDBAExpression> conditions = new ArrayList<>();
            List<GaussDBAExpression> thenExprs = new ArrayList<>();
            for (int i = 0; i < nrCases; i++) {
                conditions.add(generateExpression(depth + 1));
                thenExprs.add(generateLeafNode());
            }
            return new GaussDBACaseWhen(conditions, thenExprs, generateLeafNode());
        case AGGREGATE_FUNCTION:
            GaussDBAAggregateFunction func = Randomly.fromOptions(GaussDBAAggregateFunction.values());
            return new GaussDBAAggregate(List.of(generateLeafNode()), func);
        case CAST_OPERATOR:
            return new GaussDBACastOperation(generateExpression(depth + 1),
                    Randomly.fromOptions(GaussDBADataType.NUMBER, GaussDBADataType.VARCHAR2, GaussDBADataType.DATE));
        case LIKE_OPERATOR:
            return new GaussDBALikeOperation(generateExpression(depth + 1),
                    GaussDBAConstant.createVarchar2Constant(generateLikePattern()), Randomly.getBoolean());
        default:
            throw new AssertionError();
        }
    }

    private String generateLikePattern() {
        StringBuilder sb = new StringBuilder();
        int len = Randomly.smallNumber() + 1;
        for (int i = 0; i < len; i++) {
            if (Randomly.getBooleanWithSmallProbability()) {
                sb.append(Randomly.fromOptions('%', '_'));
            } else {
                sb.append((char) ('a' + state.getRandomly().getInteger(0, 26)));
            }
        }
        return sb.toString();
    }

    private GaussDBAExpression generateLeafNode() {
        List<GaussDBAColumn> availableColumns = columns != null ? columns
                : (targetTables != null ? targetTables.getColumns() : null);
        if (Randomly.getBoolean() && availableColumns != null && !availableColumns.isEmpty()) {
            return generateColumn();
        }
        return generateConstant();
    }

    private GaussDBAExpression generateColumn() {
        List<GaussDBAColumn> availableColumns = columns != null ? columns
                : (targetTables != null ? targetTables.getColumns() : null);
        if (availableColumns == null || availableColumns.isEmpty()) {
            return generateConstant();
        }
        GaussDBAColumn c = Randomly.fromList(availableColumns);
        if (rowValue != null && rowValue.getValues().containsKey(c)) {
            return GaussDBAColumnValue.create(c, rowValue.getValues().get(c));
        }
        return GaussDBAColumnReference.create(c, null);
    }

    public GaussDBAConstant generateConstant() {
        return GaussDBAConstant.createRandomConstant(state.getRandomly());
    }

    public GaussDBAConstant generateConstant(GaussDBADataType type) {
        switch (type) {
        case NUMBER:
            return GaussDBAConstant.createNumberConstant(state.getRandomly().getInteger());
        case VARCHAR2:
            return GaussDBAConstant.createVarchar2Constant(state.getRandomly().getString());
        case DATE:
            return GaussDBAConstant.createDateConstant(java.time.LocalDate.now().plusDays(state.getRandomly().getInteger()));
        case TIMESTAMP:
            return GaussDBAConstant.createTimestampConstant(java.time.LocalDateTime.now().plusDays(state.getRandomly().getInteger()));
        default:
            return GaussDBAConstant.createRandomConstant(state.getRandomly());
        }
    }

    @Override
    public GaussDBAExpression generateBooleanExpression() {
        GaussDBAExpression expr = generateExpression(0);
        if (expr instanceof GaussDBABinaryLogicalOperation || expr instanceof GaussDBABinaryComparisonOperation
                || expr instanceof GaussDBABetweenOperation || expr instanceof GaussDBAInOperation
                || expr instanceof GaussDBAUnaryPostfixOperation) {
            return expr;
        }
        // A模式无BOOLEAN，用比较操作生成数值结果
        return new GaussDBABinaryComparisonOperation(expr, generateLeafNode(),
                GaussDBABinaryComparisonOperator.EQUALS);
    }

    @Override
    public GaussDBAExpression negatePredicate(GaussDBAExpression predicate) {
        return new GaussDBAUnaryPrefixOperation(predicate, UnaryPrefixOperator.NOT);
    }

    @Override
    public GaussDBAExpression isNull(GaussDBAExpression expr) {
        return new GaussDBAUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
    }

    @Override
    public GaussDBASelect generateSelect() {
        GaussDBASelect select = new GaussDBASelect();
        return select;
    }

    @Override
    public List<GaussDBAJoin> getRandomJoinClauses() {
        List<GaussDBAJoin> joinStatements = new ArrayList<>();
        for (int i = 1; i < tables.size(); i++) {
            GaussDBAExpression joinClause = generateExpression(0);
            GaussDBATable table = Randomly.fromList(tables);
            tables.remove(table);
            GaussDBAJoinType joinType = GaussDBAJoinType.getRandom();
            GaussDBAJoin j = new GaussDBAJoin(GaussDBATableReference.create(table), joinClause, joinType);
            joinStatements.add(j);
        }
        return joinStatements;
    }

    @Override
    public List<GaussDBAExpression> getTableRefs() {
        return tables.stream().map(GaussDBATableReference::create).collect(Collectors.toList());
    }

    @Override
    public List<GaussDBAExpression> generateFetchColumns(boolean shouldCreateDummy) {
        if (shouldCreateDummy && Randomly.getBooleanWithSmallProbability()) {
            return List.of(GaussDBAColumnReference.create(GaussDBAColumn.createDummy("*"), null));
        }
        List<GaussDBAExpression> fetchColumns = new ArrayList<>();
        List<GaussDBAColumn> targetColumns = Randomly.nonEmptySubset(columns);
        for (GaussDBAColumn c : targetColumns) {
            fetchColumns.add(new GaussDBAColumnReference(c, null));
        }
        return fetchColumns;
    }

    @Override
    public List<GaussDBAExpression> generateOrderBys() {
        return List.of();
    }

    @Override
    public GaussDBAExpressionGenerator setTablesAndColumns(AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        this.targetTables = tables;
        this.tables = tables.getTables();
        this.columns = tables.getColumns();
        return this;
    }

    // ==================== NoRECGenerator interface ====================

    @Override
    public String generateOptimizedQueryString(GaussDBASelect select, GaussDBAExpression whereCondition,
            boolean shouldUseAggregate) {
        GaussDBAColumnReference allColumns = GaussDBAColumnReference.create(GaussDBAColumn.createDummy("*"), null);
        if (shouldUseAggregate) {
            select.setFetchColumns(
                    List.of(new GaussDBAAggregate(List.of(allColumns), GaussDBAAggregateFunction.COUNT)));
        } else {
            select.setFetchColumns(List.of(allColumns));
        }
        select.setWhereClause(whereCondition);
        select.setSelectType(GaussDBASelect.GaussDBASelectType.ALL);
        return GaussDBAToStringVisitor.asString(select);
    }

    @Override
    public String generateUnoptimizedQueryString(GaussDBASelect select, GaussDBAExpression whereCondition) {
        // A模式使用CASE WHEN（GaussDB A兼容支持CASE语法）
        GaussDBACaseWhen caseExpr = GaussDBACaseWhen.create(
                whereCondition,
                GaussDBAConstant.createNumberConstant(1),
                GaussDBAConstant.createNumberConstant(0));
        select.setFetchColumns(List.of(caseExpr));
        select.setWhereClause(null);
        select.setOrderByClauses(List.of());
        select.setSelectType(GaussDBASelect.GaussDBASelectType.ALL);
        return "SELECT SUM(ref0) FROM (" + GaussDBAToStringVisitor.asString(select) + ") AS res";
    }

    // ==================== CERTGenerator interface ====================

    @Override
    public String generateExplainQuery(GaussDBASelect select) {
        // GaussDB A兼容模式使用Oracle风格的EXPLAIN
        // EXPLAIN PLAN FOR ... 然后查询 plan_table
        return "EXPLAIN " + GaussDBAToStringVisitor.asString(select);
    }

    @Override
    public boolean mutate(GaussDBASelect select) {
        List<java.util.function.Function<GaussDBASelect, Boolean>> mutators = new ArrayList<>();
        mutators.add(this::mutateJoin);
        mutators.add(this::mutateWhere);
        mutators.add(this::mutateGroupBy);
        mutators.add(this::mutateHaving);
        mutators.add(this::mutateDistinct);
        mutators.add(this::mutateAnd);
        mutators.add(this::mutateOr);

        java.util.function.Function<GaussDBASelect, Boolean> mutator = Randomly.fromList(mutators);
        return mutator.apply(select);
    }

    private boolean mutateJoin(GaussDBASelect select) {
        List<GaussDBAJoin> joinClauses = select.getJoinClauses();
        if (joinClauses.isEmpty()) {
            return false;
        }
        GaussDBAJoin join = Randomly.fromList(joinClauses);
        // 改变JOIN类型
        GaussDBAJoinType newType = Randomly.fromOptions(GaussDBAJoinType.values());
        join.setJoinType(newType);
        return newType == GaussDBAJoinType.LEFT || newType == GaussDBAJoinType.RIGHT;
    }

    private boolean mutateWhere(GaussDBASelect select) {
        GaussDBAExpression whereClause = select.getWhereClause();
        if (whereClause == null) {
            select.setWhereClause(generateBooleanExpression());
            return true;
        }
        // 移除WHERE clause
        select.setWhereClause(null);
        return false;
    }

    private boolean mutateGroupBy(GaussDBASelect select) {
        List<GaussDBAExpression> groupBy = select.getGroupByClause();
        if (groupBy.isEmpty()) {
            select.setGroupByClause(select.getFetchColumns());
            return false;
        }
        select.clearGroupByExpressions();
        return true;
    }

    private boolean mutateHaving(GaussDBASelect select) {
        GaussDBAExpression havingClause = select.getHavingClause();
        if (havingClause == null) {
            if (!select.getGroupByClause().isEmpty()) {
                select.setHavingClause(generateBooleanExpression());
                return true;
            }
            return false;
        }
        select.clearHavingClause();
        return false;
    }

    private boolean mutateDistinct(GaussDBASelect select) {
        GaussDBASelect.GaussDBASelectType selectType = select.getSelectType();
        if (selectType == GaussDBASelect.GaussDBASelectType.ALL) {
            select.setSelectType(GaussDBASelect.GaussDBASelectType.DISTINCT);
            return true;
        }
        select.setSelectType(GaussDBASelect.GaussDBASelectType.ALL);
        return false;
    }

    private boolean mutateAnd(GaussDBASelect select) {
        GaussDBAExpression whereClause = select.getWhereClause();
        if (whereClause == null || !(whereClause instanceof GaussDBABinaryLogicalOperation)) {
            return false;
        }
        GaussDBABinaryLogicalOperation binOp = (GaussDBABinaryLogicalOperation) whereClause;
        if (binOp.getOperator() == GaussDBABinaryLogicalOperator.AND) {
            // AND -> OR should increase result count
            binOp.setOperator(GaussDBABinaryLogicalOperator.OR);
            return true;
        }
        return false;
    }

    private boolean mutateOr(GaussDBASelect select) {
        GaussDBAExpression whereClause = select.getWhereClause();
        if (whereClause == null || !(whereClause instanceof GaussDBABinaryLogicalOperation)) {
            return false;
        }
        GaussDBABinaryLogicalOperation binOp = (GaussDBABinaryLogicalOperation) whereClause;
        if (binOp.getOperator() == GaussDBABinaryLogicalOperator.OR) {
            // OR -> AND should decrease result count
            binOp.setOperator(GaussDBABinaryLogicalOperator.AND);
            return false;
        }
        return true;
    }

    private GaussDBAExpression lastGeneratedExpression;

    public GaussDBAExpression getLastGeneratedExpression() {
        return lastGeneratedExpression;
    }

    public void setLastGeneratedExpression(GaussDBAExpression expr) {
        this.lastGeneratedExpression = expr;
    }

    public List<GaussDBAExpression> generateExpressions(int count) {
        List<GaussDBAExpression> expressions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expressions.add(generateExpression(0));
        }
        return expressions;
    }
}