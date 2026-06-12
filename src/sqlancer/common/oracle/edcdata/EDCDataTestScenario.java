package sqlancer.common.oracle.edcdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data class representing a single EDC_DATA test scenario.
 * Encapsulates all information needed for one test iteration.
 */
public class EDCDataTestScenario {
    private final String operationName;
    private final EDCDataOperationType opType;
    private final List<String> columnNames;
    private final List<String> columnTypes;
    private final String testExpression;
    private final String baseTableName;
    private final String derivedTableName;
    private final String derivedColumnName;
    private final List<String> testColumnNames;
    private final List<String> otherColumnNames;
    private final List<String> otherColumnTypes;

    public EDCDataTestScenario(String operationName, EDCDataOperationType opType,
                                List<String> columnNames, List<String> columnTypes,
                                String testExpression, String baseTableName, String derivedTableName,
                                String derivedColumnName, List<String> testColumnNames,
                                List<String> otherColumnNames, List<String> otherColumnTypes) {
        this.operationName = operationName;
        this.opType = opType;
        this.columnNames = Collections.unmodifiableList(new ArrayList<>(columnNames));
        this.columnTypes = Collections.unmodifiableList(new ArrayList<>(columnTypes));
        this.testExpression = testExpression;
        this.baseTableName = baseTableName;
        this.derivedTableName = derivedTableName;
        this.derivedColumnName = derivedColumnName;
        this.testColumnNames = Collections.unmodifiableList(new ArrayList<>(testColumnNames));
        this.otherColumnNames = Collections.unmodifiableList(new ArrayList<>(otherColumnNames));
        this.otherColumnTypes = Collections.unmodifiableList(new ArrayList<>(otherColumnTypes));
    }

    public String getOperationName() {
        return operationName;
    }

    public EDCDataOperationType getOpType() {
        return opType;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<String> getColumnTypes() {
        return columnTypes;
    }

    public String getTestExpression() {
        return testExpression;
    }

    public String getBaseTableName() {
        return baseTableName;
    }

    public String getDerivedTableName() {
        return derivedTableName;
    }

    public String getDerivedColumnName() {
        return derivedColumnName;
    }

    public List<String> getTestColumnNames() {
        return testColumnNames;
    }

    public List<String> getOtherColumnNames() {
        return otherColumnNames;
    }

    public List<String> getOtherColumnTypes() {
        return otherColumnTypes;
    }

    @Override
    public String toString() {
        return String.format("EDCDataTestScenario[op=%s, type=%s, expr=%s, base=%s, derived=%s]",
                operationName, opType, testExpression, baseTableName, derivedTableName);
    }
}
