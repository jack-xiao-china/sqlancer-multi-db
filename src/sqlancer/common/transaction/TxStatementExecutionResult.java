package sqlancer.common.transaction;

import java.util.List;
import java.util.stream.Collectors;

public class TxStatementExecutionResult {

    private final TxStatement statement;
    private boolean blocked;
    /** Set when the oracle's block prediction comes from a conservative heuristic (e.g. InnoDB
     * range conflict) rather than a definite lock-wait: such predictions admit false positives. */
    private boolean heuristicBlocked;
    private String errorInfo;
    private List<Object> warningInfo;
    private List<Object> result;

    public TxStatementExecutionResult(TxStatement statement) {
        this.statement = statement;
    }

    public String getResultAsString() {
        if (result == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        sb.append(result.stream().map(o -> {
            if (o == null) {
                return "NULL";
            } else {
                return o.toString();
            }
        }).collect(Collectors.joining(", ")));
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toString() {
        String str = statement.getStmtId();
        if (blocked) {
            str += ": (Blocked)";
        }
        if (reportWarning()) {
            str += ": (Warning)";
        }
        if (reportError()) {
            str += ": (Error)";
        }
        if (reportDeadlock()) {
            str += ": (Deadlock)";
        }
        return str;
    }

    public TxStatement getStatement() {
        return statement;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isHeuristicBlocked() {
        return heuristicBlocked;
    }

    public void setHeuristicBlocked(boolean heuristicBlocked) {
        this.heuristicBlocked = heuristicBlocked;
    }

    public boolean reportError() {
        if (errorInfo == null) {
            return false;
        } else return errorInfo.length() > 0;
    }

    public String getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(String errorInfo) {
        this.errorInfo = errorInfo;
    }

    public boolean reportDeadlock() {
        if (reportError()) {
            return statement.reportDeadlock(errorInfo);
        } else
            return false;
    }

    public boolean reportRollback() {
        if (reportError()) {
            return statement.reportRollback(errorInfo);
        } else
            return false;
    }

    public boolean reportWarning() {
        if (warningInfo == null) {
            return false;
        } else return warningInfo.size() > 0;
    }

    public List<Object> getWarningInfo() {
        return warningInfo;
    }

    public void setWarningInfo(List<Object> warningInfo) {
        this.warningInfo = warningInfo;
    }

    public List<Object> getResult() {
        return result;
    }

    public void setResult(List<Object> result) {
        this.result = result;
    }
}