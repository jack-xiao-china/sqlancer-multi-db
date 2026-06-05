package sqlancer.fucci.mvcc;

import java.util.List;
import java.util.Map;

import sqlancer.common.transaction.Transaction;
import sqlancer.fucci.FucciColumn;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciSchema;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.transaction.FucciTransaction;
import sqlancer.fucci.transaction.FucciTxStatement;
import sqlancer.fucci.transaction.FucciTxStatement.FucciStatementType;

/**
 * Fucci MVCC分析器。
 * 包装MVCCSimulator，在每条语句执行时实时更新版本链和构建视图。
 * 参照原生Fucci的FucciChecker.inferOracleMVCC()设计。
 */
public class FucciMVCCAnalyzer {

    private final MVCCSimulator simulator;
    private final FucciIsolationLevel isoLevel;
    private final FucciSchema schema;

    public FucciMVCCAnalyzer(MVCCSimulator simulator, FucciIsolationLevel isoLevel,
            String dbmsType, FucciSchema schema) {
        this.simulator = simulator;
        this.isoLevel = isoLevel;
        this.schema = schema;
    }

    public FucciMVCCAnalyzer(MVCCSimulator simulator, FucciIsolationLevel isoLevel, String dbmsType) {
        this(simulator, isoLevel, dbmsType, null);
    }

    /**
     * 处理一条语句的MVCC状态更新。
     *
     * @param stmt Fucci语句
     */
    public void processStatement(FucciTxStatement stmt) {
        Transaction tx = stmt.getTransaction();
        if (!(tx instanceof FucciTransaction)) {
            return;
        }
        FucciTransaction fucciTx = (FucciTransaction) tx;

        if (!(stmt.getType() instanceof FucciStatementType)) {
            return;
        }
        FucciStatementType type = (FucciStatementType) stmt.getType();
        VisibilityRule rule = VisibilityRule.fromIsolationLevel(isoLevel.getName());

        switch (type) {
            case SELECT:
            case SELECT_FOR_SHARE:
            case SELECT_FOR_UPDATE:
                processSelect(fucciTx, stmt, rule);
                break;
            case UPDATE:
            case DELETE:
            case INSERT:
            case REPLACE:
                processWrite(fucciTx, stmt, type);
                break;
            case COMMIT:
                processCommit(fucciTx);
                break;
            case ROLLBACK:
                processRollback(fucciTx);
                break;
            default:
                break;
        }
    }

    private void processSelect(FucciTransaction tx, FucciTxStatement stmt, VisibilityRule rule) {
        // RR隔离: 首次SELECT时创建快照
        if (rule == VisibilityRule.SNAPSHOT && !simulator.hasSnapshot(tx)) {
            simulator.createSnapshot(tx);
        }

        // 构建当前可见视图（保留rowId映射）
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String targetTable = extractTableName(sql);
        Map<String, Map<Integer, Object[]>> viewData = simulator.buildViewWithRowIds(tx, rule);
        String[] colNames = resolveColumnNames(targetTable);
        View beforeView = convertViewToView(viewData, targetTable, colNames);
        stmt.setBeforeView(beforeView);
        stmt.setAfterView(beforeView.copy());
    }

    private void processWrite(FucciTransaction tx, FucciTxStatement stmt, FucciStatementType type) {
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String tableName = extractTableName(sql);
        int[] rowIds = stmt.getInvolvedRowIds();
        boolean isDelete = (type == FucciStatementType.DELETE);

        if (rowIds != null) {
            for (int rowId : rowIds) {
                Version newVersion = new Version(null, tx, isDelete);
                simulator.addVersion(tableName, rowId, newVersion);
            }
        } else {
            // 无法确定行ID时，为rowId=0创建版本（保守处理）
            Version newVersion = new Version(null, tx, isDelete);
            simulator.addVersion(tableName, 0, newVersion);
        }

        // 记录写入后视图
        VisibilityRule rule = VisibilityRule.fromIsolationLevel(isoLevel.getName());
        Map<String, Map<Integer, Object[]>> afterViewData = simulator.buildViewWithRowIds(tx, rule);
        String[] colNames = resolveColumnNames(tableName);
        stmt.setAfterView(convertViewToView(afterViewData, tableName, colNames));
    }

    private void processCommit(FucciTransaction tx) {
        simulator.commitTransaction(tx);
    }

    private void processRollback(FucciTransaction tx) {
        simulator.rollbackTransaction(tx);
    }

    /**
     * 获取模拟器实例（供外部获取最终状态）。
     */
    public MVCCSimulator getSimulator() {
        return simulator;
    }

    /**
     * 转换视图数据为 View 对象（单表，带列名元数据）。
     */
    private View convertViewToView(Map<String, Map<Integer, Object[]>> viewData,
            String targetTableName, String[] columnNames) {
        View view = new View(targetTableName, columnNames);
        Map<Integer, Object[]> tableRows = viewData.get(targetTableName);
        if (tableRows != null) {
            for (Map.Entry<Integer, Object[]> entry : tableRows.entrySet()) {
                view.putRow(entry.getKey(), entry.getValue());
            }
        }
        return view;
    }

    /**
     * 从 schema 获取指定表的列名数组。
     */
    private String[] resolveColumnNames(String tableName) {
        if (schema == null || tableName == null) {
            return null;
        }
        FucciTable table = schema.getTable(tableName);
        if (table == null) {
            return null;
        }
        List<FucciColumn> cols = table.getColumns();
        String[] names = new String[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            names[i] = cols.get(i).getName();
        }
        return names;
    }

    private String extractTableName(String sql) {
        if (sql == null) {
            return "unknown";
        }
        String upper = sql.toUpperCase();
        String[] keywords = {"FROM ", "INTO ", "UPDATE ", "JOIN "};
        for (String kw : keywords) {
            int idx = upper.indexOf(kw);
            if (idx != -1) {
                String rest = upper.substring(idx + kw.length()).trim();
                int end = rest.indexOf(' ');
                if (end == -1) {
                    end = rest.length();
                }
                return rest.substring(0, end).toLowerCase();
            }
        }
        return "unknown";
    }
}
