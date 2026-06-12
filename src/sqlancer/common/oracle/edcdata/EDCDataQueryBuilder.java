package sqlancer.common.oracle.edcdata;

import sqlancer.Randomly;

import java.util.ArrayList;
import java.util.List;

/**
 * Query builder for EDC_DATA test scenarios.
 * Generates base and derived SQL queries for each operation type.
 * Equivalent to EDC Python's SQLGenerator.
 */
public class EDCDataQueryBuilder {
    protected final EDCDataConfig config;
    protected final EDCDataExpressionBuilder exprBuilder;
    protected final Randomly r;

    public EDCDataQueryBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder, Randomly r) {
        this.config = config;
        this.exprBuilder = exprBuilder;
        this.r = r;
    }

    /**
     * Generate AGGREGATE SELECT queries (base and derived).
     * Equivalent to EDC Python's generate_agg_select.
     */
    public String[] generateAggSelect(EDCDataTestScenario scenario, String exprType) {
        String baseTable = scenario.getBaseTableName();
        String derivedTable = scenario.getDerivedTableName();
        String testExpr = scenario.getTestExpression();
        String derivedColumn = scenario.getDerivedColumnName();
        List<String> otherColumnNames = scenario.getOtherColumnNames();
        List<String> otherColumnTypes = scenario.getOtherColumnTypes();

        String otherCols = otherColumnNames.isEmpty() ? "" : ", " + String.join(", ", otherColumnNames);
        String groupBy = otherColumnNames.isEmpty() ? "" : "GROUP BY " + String.join(", ", otherColumnNames);

        String baseSelect = "SELECT " + testExpr + otherCols + " FROM " + baseTable;
        String equalSelect = "SELECT " + derivedColumn + otherCols + " FROM " + derivedTable;

        String baseWhereCond = exprBuilder.generateExprOnColumn(baseTable, otherColumnNames, otherColumnTypes,
                r.getInteger(3, 5));
        String equalWhereCond = baseWhereCond.replace(baseTable, derivedTable);

        String havingCond = exprBuilder.generateExprOnColumn(baseTable, List.of(testExpr), List.of(exprType), 2);
        String equalHavingCond = havingCond.replace(baseTable, derivedTable).replace(testExpr, derivedColumn);

        baseSelect += " WHERE " + baseWhereCond + " " + groupBy + " HAVING " + havingCond;
        equalSelect += " WHERE " + equalWhereCond + " AND (" + equalHavingCond + ")";

        return new String[]{baseSelect, equalSelect};
    }

    /**
     * Generate FUNCTION SELECT queries (base and derived).
     * Equivalent to EDC Python's generate_func_select.
     */
    public String[] generateFuncSelect(EDCDataTestScenario scenario, String exprType) {
        String baseTable = scenario.getBaseTableName();
        String derivedTable = scenario.getDerivedTableName();
        String testExpr = scenario.getTestExpression();
        String derivedColumn = scenario.getDerivedColumnName();
        List<String> otherColumnNames = scenario.getOtherColumnNames();
        List<String> otherColumnTypes = scenario.getOtherColumnTypes();

        List<String> allCols = new ArrayList<>(otherColumnNames);
        allCols.add("(" + testExpr + ")");
        List<String> allTypes = new ArrayList<>(otherColumnTypes);
        allTypes.add(exprType);

        // Random subset of columns
        List<String> selectedCols = Randomly.nonEmptySubset(allCols);
        String baseSelect = "SELECT " + String.join(", ", selectedCols) + " FROM " + baseTable;

        String baseWhereCond = exprBuilder.generateExprOnColumn(baseTable, allCols, allTypes,
                r.getInteger(3, 6));
        baseSelect += " WHERE " + baseWhereCond;

        if (Randomly.getBoolean()) {
            List<String> orderCols = Randomly.nonEmptySubset(allCols);
            List<String> orderDirs = new ArrayList<>();
            for (int i = 0; i < orderCols.size(); i++) {
                orderDirs.add(Randomly.fromOptions("ASC", "DESC"));
            }
            String orderClause = " ORDER BY ";
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < orderCols.size(); i++) {
                parts.add(orderCols.get(i) + " " + orderDirs.get(i));
            }
            baseSelect += orderClause + String.join(", ", parts);
        }

        String equalSelect = baseSelect.replace(baseTable, derivedTable).replace(testExpr, derivedColumn);

        return new String[]{baseSelect, equalSelect};
    }

    /**
     * Generate PREDICATE SELECT queries (base and derived).
     * Equivalent to EDC Python's generate_pred_select.
     */
    public String[] generatePredSelect(EDCDataTestScenario scenario, String exprType) {
        String baseTable = scenario.getBaseTableName();
        String derivedTable = scenario.getDerivedTableName();
        String testExpr = scenario.getTestExpression();
        String derivedColumn = scenario.getDerivedColumnName();
        List<String> otherColumnNames = scenario.getOtherColumnNames();
        List<String> otherColumnTypes = scenario.getOtherColumnTypes();

        List<String> selectedCols = Randomly.nonEmptySubset(otherColumnNames);
        String baseSelect = "SELECT " + String.join(", ", selectedCols) + " FROM " + baseTable;

        String baseWhereCond;
        if (Randomly.getPercentage() < 0.3) {
            List<String> allCols = new ArrayList<>(otherColumnNames);
            allCols.add("(" + testExpr + ")");
            List<String> allTypes = new ArrayList<>(otherColumnTypes);
            allTypes.add(exprType);
            baseWhereCond = exprBuilder.generateExprOnColumn(baseTable, allCols, allTypes,
                    r.getInteger(3, 5));
        } else {
            baseWhereCond = exprBuilder.generateExprOnColumn(baseTable, otherColumnNames, otherColumnTypes,
                    r.getInteger(3, 5));
            baseWhereCond = "(" + testExpr + ") " + Randomly.fromOptions("AND", "OR") + " " + baseWhereCond;
        }
        baseSelect += " WHERE " + baseWhereCond;

        if (Randomly.getBoolean()) {
            List<String> orderCols = Randomly.nonEmptySubset(otherColumnNames);
            List<String> orderDirs = new ArrayList<>();
            for (int i = 0; i < orderCols.size(); i++) {
                orderDirs.add(Randomly.fromOptions("ASC", "DESC"));
            }
            String orderClause = " ORDER BY ";
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < orderCols.size(); i++) {
                parts.add(orderCols.get(i) + " " + orderDirs.get(i));
            }
            baseSelect += orderClause + String.join(", ", parts);
        }

        String equalSelect = baseSelect.replace(baseTable, derivedTable).replace(testExpr, derivedColumn);

        return new String[]{baseSelect, equalSelect};
    }
}
