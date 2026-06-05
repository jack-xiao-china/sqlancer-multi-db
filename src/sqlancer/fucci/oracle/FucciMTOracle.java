package sqlancer.fucci.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.eval.ColumnResolver;
import sqlancer.fucci.eval.PredicateEvaluator;
import sqlancer.fucci.mvcc.FucciMVCCAnalyzer;
import sqlancer.fucci.mvcc.MVCCSimulator;
import sqlancer.fucci.mvcc.Version;
import sqlancer.fucci.mvcc.View;
import sqlancer.fucci.mvcc.VisibilityRule;
import sqlancer.fucci.transaction.FucciTransaction;
import sqlancer.fucci.transaction.FucciTxStatement;
import sqlancer.fucci.transaction.FucciTxTestExecutor;
import sqlancer.fucci.transaction.FucciTxTestGenerator;

/**
 * Fucci MT Oracle (变形测试Oracle)。
 * 使用语句级MVCC模拟预测每条SELECT的预期结果，与实际执行结果比较。
 */
public class FucciMTOracle extends TxBase<FucciGlobalState> {

    private Map<String, Map<Integer, List<Version>>> versionChains;
    private List<FucciTransaction> activeTransactions;

    public FucciMTOracle(FucciGlobalState state) {
        super(state);
        this.versionChains = new HashMap<>();
        this.activeTransactions = new ArrayList<>();
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Fucci MT Oracle Check =================");
        initializeVersionChains();

        try {
            FucciTxTestGenerator txTestGenerator = new FucciTxTestGenerator(state);
            List<Transaction> transactions = txTestGenerator.generateTransactions();

            for (Transaction tx : transactions) {
                if (tx instanceof FucciTransaction) {
                    activeTransactions.add((FucciTransaction) tx);
                }
                logger.writeCurrent(tx.toString());
            }

            List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId)
                        .collect(Collectors.joining(", ", "[", "]")));

                FucciIsolationLevel isoLevel = selectIsolationLevel();
                logger.writeCurrent("Isolation level: " + isoLevel);

                FucciTxTestExecutor testExecutor = new FucciTxTestExecutor(state, transactions, schedule, isoLevel);
                TxTestExecutionResult testResult = testExecutor.execute();

                TxTestExecutionResult simulatedResult = simulateMVCCExecution(transactions, schedule, isoLevel);
                String compareResult = compareWithSimulation(testResult, simulatedResult, isoLevel);

                if (!compareResult.isEmpty()) {
                    reportBug(transactions, schedule, compareResult, testResult, simulatedResult);
                    throw new AssertionError("MT Oracle detected MVCC visibility violation");
                } else {
                    logger.writeCurrent("============ MT Oracle: MVCC Simulation Matches =============");
                }

                reproduceDatabase(state.getState().getStatements());
                resetVersionChains();
            }
        } finally {
            for (Transaction tx : new ArrayList<Transaction>(activeTransactions)) {
                tx.closeConnection();
            }
            activeTransactions.clear();
        }
    }

    private void initializeVersionChains() throws SQLException {
        versionChains.clear();
        List<FucciTable> tables = state.getSchema().getDatabaseTables();
        for (FucciTable table : tables) {
            String tableName = table.getName();
            Map<Integer, List<Version>> tableVersions = new HashMap<>();

            String query = "SELECT * FROM " + tableName;
            SQLQueryAdapter sql = new SQLQueryAdapter(query);
            SQLancerResultSet rs = sql.executeAndGet(state);

            if (rs != null) {
                int rowId = 0;
                while (rs.next()) {
                    int columnCount = rs.getMetaData().getColumnCount();
                    Object[] rowData = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        rowData[i] = rs.getObject(i + 1);
                    }

                    Version initVersion = new Version(rowData, "initial", false);
                    List<Version> versions = new ArrayList<>();
                    versions.add(initVersion);
                    tableVersions.put(rowId, versions);
                    rowId++;
                }
                rs.close();
            }
            versionChains.put(tableName, tableVersions);
        }
    }

    private void resetVersionChains() throws SQLException {
        initializeVersionChains();
    }

    private FucciIsolationLevel selectIsolationLevel() {
        String isolationStr = state.getFucciOptions().getIsolationLevel();
        if (isolationStr == null || isolationStr.equalsIgnoreCase("RANDOM")) {
            return Randomly.fromList(state.getSupportedIsolationLevels());
        }
        return FucciIsolationLevel.fromString(isolationStr);
    }

    /**
     * 使用FucciMVCCAnalyzer逐语句模拟MVCC执行。
     * 为每条SELECT生成预测结果（从beforeView转换），最终状态从simulator获取。
     */
    private TxTestExecutionResult simulateMVCCExecution(List<Transaction> transactions,
            List<TxStatement> schedule, FucciIsolationLevel isoLevel) {
        TxTestExecutionResult simulatedResult = new TxTestExecutionResult();
        simulatedResult.setIsolationLevel(isoLevel);

        // 创建独立的模拟用版本链（不影响原始版本链）
        Map<String, Map<Integer, List<Version>>> simChains = deepCopyVersionChains();
        MVCCSimulator simSimulator = new MVCCSimulator(simChains);
        FucciMVCCAnalyzer analyzer = new FucciMVCCAnalyzer(simSimulator, isoLevel,
                state.getDbmsType(), state.getSchema());

        for (TxStatement stmt : schedule) {
            if (stmt instanceof FucciTxStatement) {
                FucciTxStatement fucciStmt = (FucciTxStatement) stmt;
                analyzer.processStatement(fucciStmt);

                TxStatementExecutionResult stmtResult = new TxStatementExecutionResult(stmt);
                // 从beforeView提取SELECT预测结果（带WHERE过滤和列投影）
                if (fucciStmt.isSelectType() && fucciStmt.getBeforeView() != null) {
                    List<Object> predictedData = buildPredictedData(fucciStmt);
                    stmtResult.setResult(predictedData);
                }
                simulatedResult.getStatementExecutionResults().add(stmtResult);
            }
        }

        // 获取模拟的最终状态
        Map<String, List<Object>> finalStates = simSimulator.computeFinalStates();
        simulatedResult.setDbFinalStates(finalStates);

        return simulatedResult;
    }

    /**
     * 构建预测的 SELECT 结果：WHERE 过滤 + 列投影。
     * 失败时回退到全行全列（保守策略）。
     */
    private List<Object> buildPredictedData(FucciTxStatement stmt) {
        View view = stmt.getBeforeView();
        List<Object> predictedData = new ArrayList<>();

        // Step 1: WHERE 过滤
        Set<Integer> matchingRows = filterByPredicate(stmt, view);

        // Step 2: 列投影
        int[] projectedCols = resolveProjectedColumns(stmt, view);

        // Step 3: 构建结果
        for (int rowId : matchingRows) {
            Object[] rowData = view.getRow(rowId);
            if (rowData == null || view.isDeleted(rowId)) {
                continue;
            }
            if (projectedCols != null) {
                for (int colIdx : projectedCols) {
                    if (colIdx >= 0 && colIdx < rowData.length) {
                        predictedData.add(rowData[colIdx]);
                    }
                }
            } else {
                for (Object val : rowData) {
                    predictedData.add(val);
                }
            }
        }

        return predictedData;
    }

    /**
     * 用 PredicateEvaluator 过滤视图行。
     * 无谓词或无列名时返回所有非删除行。
     */
    private Set<Integer> filterByPredicate(FucciTxStatement stmt, View view) {
        String predicate = stmt.getPredicate();
        String[] columnNames = view.getColumnNames();

        Set<Integer> allNonDeleted = new HashSet<>();
        for (int rowId : view.getRowIds()) {
            if (!view.isDeleted(rowId)) {
                allNonDeleted.add(rowId);
            }
        }

        if (predicate == null || columnNames == null) {
            return allNonDeleted;
        }

        ColumnResolver resolver = new ColumnResolver(
                view.getTableName() != null ? view.getTableName() : "unknown",
                Arrays.asList(columnNames));

        return PredicateEvaluator.evaluate(predicate, resolver, view.getData());
    }

    /**
     * 解析 SELECT 列列表，返回列索引数组。
     * SELECT * 或解析失败时返回 null（回退到全部列）。
     */
    private int[] resolveProjectedColumns(FucciTxStatement stmt, View view) {
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String[] columnNames = view.getColumnNames();
        if (columnNames == null) {
            return null;
        }

        try {
            net.sf.jsqlparser.statement.Statement parsed = CCJSqlParserUtil.parse(sql);
            if (parsed instanceof Select) {
                Select select = (Select) parsed;
                SelectBody body = select.getSelectBody();
                if (body instanceof PlainSelect) {
                    PlainSelect plain = (PlainSelect) body;
                    List<SelectItem> items = plain.getSelectItems();
                    if (items == null || items.isEmpty()) {
                        return null;
                    }
                    if (items.size() == 1 && items.get(0) instanceof AllColumns) {
                        return null;
                    }

                    ColumnResolver resolver = new ColumnResolver(
                            view.getTableName() != null ? view.getTableName() : "unknown",
                            Arrays.asList(columnNames));

                    List<Integer> indices = new ArrayList<>();
                    for (SelectItem item : items) {
                        if (item instanceof SelectExpressionItem) {
                            Expression expr = ((SelectExpressionItem) item).getExpression();
                            if (expr instanceof Column) {
                                int idx = resolver.resolve(((Column) expr).getColumnName());
                                if (idx >= 0) {
                                    indices.add(idx);
                                } else {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                    return indices.stream().mapToInt(Integer::intValue).toArray();
                }
            }
        } catch (Exception e) {
            // 解析失败: 回退到全部列
        }
        return null;
    }

    private String compareWithSimulation(TxTestExecutionResult testResult,
            TxTestExecutionResult simulatedResult, FucciIsolationLevel isoLevel) {
        // SER级别比较所有结果（包括SELECT），其他级别仅比较写操作结果
        VisibilityRule rule = VisibilityRule.fromIsolationLevel(
                isoLevel != null ? isoLevel.getName() : null);
        if (rule == VisibilityRule.COMMITTED_WITH_LOCK) {
            return compareAllResults(testResult, simulatedResult);
        } else {
            return compareWriteTxResults(testResult, simulatedResult);
        }
    }

    /**
     * 深拷贝版本链（用于独立模拟）。
     */
    private Map<String, Map<Integer, List<Version>>> deepCopyVersionChains() {
        Map<String, Map<Integer, List<Version>>> copy = new HashMap<>();
        for (Map.Entry<String, Map<Integer, List<Version>>> tableEntry : versionChains.entrySet()) {
            Map<Integer, List<Version>> tableCopy = new HashMap<>();
            for (Map.Entry<Integer, List<Version>> rowEntry : tableEntry.getValue().entrySet()) {
                List<Version> versionsCopy = new ArrayList<>();
                for (Version v : rowEntry.getValue()) {
                    versionsCopy.add(v.copy());
                }
                tableCopy.put(rowEntry.getKey(), versionsCopy);
            }
            copy.put(tableEntry.getKey(), tableCopy);
        }
        return copy;
    }

    private void reportBug(List<Transaction> transactions, List<TxStatement> schedule,
            String compareResult, TxTestExecutionResult testResult, TxTestExecutionResult simulatedResult) {
        state.getState().getLocalState().log("============ Fucci MT Oracle Bug Report =============");
        for (Transaction tx : transactions) {
            state.getState().getLocalState().log(tx.toString());
        }
        state.getState().getLocalState().log("Schedule: " + schedule.stream().map(TxStatement::getStmtId)
                .collect(Collectors.joining(", ", "[", "]")));
        state.getState().getLocalState().log(compareResult);
    }
}
