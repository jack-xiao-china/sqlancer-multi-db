package sqlancer.fucci.transaction;

import java.util.ArrayList;
import java.util.List;

import sqlancer.SQLConnection;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.lock.Lock;
import sqlancer.fucci.mvcc.View;

/**
 * Fucci扩展事务类。
 * 在SQLancer Transaction基础上添加MVCC和锁分析所需的状态。
 */
public class FucciTransaction extends Transaction {

    private boolean blocked = false;
    private boolean aborted = false;
    private boolean committed = false;
    private boolean finished = false;
    private List<Lock> locks = new ArrayList<>();
    private List<FucciTxStatement> blockedStatements = new ArrayList<>();
    private List<Transaction> snapTxs = new ArrayList<>();
    private View snapView = new View();
    private FucciIsolationLevel isolationLevel;
    private SQLConnection refConnection;
    private int conflictStmtId = -1;

    public FucciTransaction(SQLConnection connection) {
        super(connection);
    }

    public FucciTransaction(SQLConnection connection, FucciIsolationLevel isolationLevel) {
        super(connection);
        this.isolationLevel = isolationLevel;
    }

    public FucciTransaction(SQLConnection connection, FucciIsolationLevel isolationLevel, SQLConnection refConnection) {
        super(connection);
        this.isolationLevel = isolationLevel;
        this.refConnection = refConnection;
    }

    public void clearStates() {
        this.blocked = false;
        this.aborted = false;
        this.committed = false;
        this.finished = false;
        this.locks.clear();
        this.blockedStatements.clear();
        this.snapTxs.clear();
        this.snapView = new View();
    }

    public void addLock(Lock lock) {
        locks.add(lock);
    }

    public void clearLocks() {
        locks.clear();
    }

    public void addBlockedStatement(FucciTxStatement stmt) {
        blockedStatements.add(stmt);
    }

    public void addSnapTx(Transaction tx) {
        snapTxs.add(tx);
    }

    public List<FucciTxStatement> getFucciStatements() {
        List<FucciTxStatement> fucciStmts = new ArrayList<>();
        for (TxStatement stmt : getStatements()) {
            if (stmt instanceof FucciTxStatement) {
                fucciStmts.add((FucciTxStatement) stmt);
            }
        }
        return fucciStmts;
    }

    public boolean isActive() {
        return !finished && !aborted && !committed;
    }

    // Getter methods
    public boolean isBlocked() { return blocked; }
    public boolean isAborted() { return aborted; }
    public boolean isCommitted() { return committed; }
    public boolean isFinished() { return finished; }
    public List<Lock> getLocks() { return locks; }
    public List<FucciTxStatement> getBlockedStatements() { return blockedStatements; }
    public List<Transaction> getSnapTxs() { return snapTxs; }
    public View getSnapView() { return snapView; }
    public FucciIsolationLevel getIsolationLevel() { return isolationLevel; }
    public SQLConnection getRefConnection() { return refConnection; }
    public int getConflictStmtId() { return conflictStmtId; }

    // Setter methods
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public void setAborted(boolean aborted) { this.aborted = aborted; }
    public void setCommitted(boolean committed) { this.committed = committed; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public void setLocks(List<Lock> locks) { this.locks = locks; }
    public void setBlockedStatements(List<FucciTxStatement> blockedStatements) { this.blockedStatements = blockedStatements; }
    public void setSnapTxs(List<Transaction> snapTxs) { this.snapTxs = snapTxs; }
    public void setSnapView(View snapView) { this.snapView = snapView; }
    public void setIsolationLevel(FucciIsolationLevel isolationLevel) { this.isolationLevel = isolationLevel; }
    public void setRefConnection(SQLConnection refConnection) { this.refConnection = refConnection; }
    public void setConflictStmtId(int conflictStmtId) { this.conflictStmtId = conflictStmtId; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FucciTransaction{id=%d, isolation=%s, blocked=%s, aborted=%s, committed=%s}\n",
                getId(), isolationLevel, blocked, aborted, committed));
        sb.append("Statements:\n");
        for (TxStatement stmt : getStatements()) {
            sb.append("  ").append(stmt.toString()).append("\n");
        }
        if (!locks.isEmpty()) {
            sb.append("Locks: ").append(locks.size()).append(" locks held\n");
        }
        if (!blockedStatements.isEmpty()) {
            sb.append("Blocked statements: ").append(blockedStatements.size()).append("\n");
        }
        return sb.toString();
    }
}