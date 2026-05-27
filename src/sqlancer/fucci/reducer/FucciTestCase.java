package sqlancer.fucci.reducer;

import java.util.ArrayList;
import java.util.List;

import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;

/**
 * Fucci测试用例封装。
 */
public class FucciTestCase {

    private String createStatement;
    private List<String> prepareStatements;
    private List<TxStatement> submittedOrder;
    private Transaction transaction1;
    private Transaction transaction2;

    public FucciTestCase() {
        this.prepareStatements = new ArrayList<>();
        this.submittedOrder = new ArrayList<>();
    }

    public String getCreateStatement() { return createStatement; }
    public void setCreateStatement(String createStatement) { this.createStatement = createStatement; }
    public List<String> getPrepareStatements() { return prepareStatements; }
    public void addPrepareStatement(String stmt) { prepareStatements.add(stmt); }
    public List<TxStatement> getSubmittedOrder() { return submittedOrder; }
    public void addToSubmittedOrder(TxStatement stmt) { submittedOrder.add(stmt); }
    public void setSubmittedOrder(List<TxStatement> order) { this.submittedOrder = order; }
    public int getLength() { return toString().length(); }
    public Transaction getTransaction1() { return transaction1; }
    public void setTransaction1(Transaction tx1) { this.transaction1 = tx1; }
    public Transaction getTransaction2() { return transaction2; }
    public void setTransaction2(Transaction tx2) { this.transaction2 = tx2; }

    public FucciTestCase copy() {
        FucciTestCase copy = new FucciTestCase();
        copy.setCreateStatement(createStatement);
        for (String stmt : prepareStatements) {
            copy.addPrepareStatement(stmt);
        }
        copy.setSubmittedOrder(new ArrayList<>(submittedOrder));
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Fucci TestCase ===\n");
        sb.append("CREATE:\n").append(createStatement).append("\n");
        sb.append("PREPARE:\n");
        for (String stmt : prepareStatements) {
            sb.append(stmt).append("\n");
        }
        sb.append("\nSCHEDULE: ");
        for (TxStatement stmt : submittedOrder) {
            sb.append(stmt.getTransaction().getId()).append("-");
        }
        if (!submittedOrder.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("\nEND\n");
        return sb.toString();
    }

    public String toSimpleString() {
        return String.format("TestCase{prep=%d, schedule=%d}", prepareStatements.size(), submittedOrder.size());
    }
}