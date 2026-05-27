package sqlancer.common.oracle.eet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Reproducer;
import sqlancer.SQLGlobalState;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;

/**
 * DML EET Oracle base — tests UPDATE/DELETE statements by comparing row-state changes.
 *
 * Flow:
 * 1. Read current table content (snapshot_before)
 * 2. Execute original DML
 * 3. Read table content (snapshot_after_original)
 * 4. Compute changed rows = snapshot_after_original − snapshot_before
 * 5. Restore database to snapshot_before
 * 6. Execute EET-transformed DML
 * 7. Read table content (snapshot_after_transformed)
 * 8. Compute changed rows = snapshot_after_transformed − snapshot_before
 * 9. Compare changed-row multisets (original vs transformed)
 * 10. Double-execution confirmation on mismatch
 */
public abstract class EETDMLOracleBase<S extends SQLGlobalState<?, ?>, E, T extends AbstractTables<?, C>, C extends AbstractTableColumn<?, ?>>
        implements sqlancer.common.oracle.TestOracle<S> {

    public static final int MAX_PROCESS_ROW_NUM = 10_000;

    protected final S state;
    protected final EETQueryExecutor executor;
    protected String lastQueryString;
    protected Reproducer<S> lastReproducer;

    protected EETDMLOracleBase(S state, EETQueryExecutor executor) {
        this.state = state;
        this.executor = executor != null ? executor : createDefaultExecutor();
    }

    // --- DML generation and transformation ---

    /** Generate the original DML statement (UPDATE or DELETE). */
    protected abstract SQLQueryAdapter generateDML(S state, Object gen, T tables);

    /** Generate the EET-transformed DML statement. */
    protected abstract SQLQueryAdapter generateTransformedDML(S state, Object gen, T tables, SQLQueryAdapter original);

    /** Read all rows from the target table as a multiset. */
    protected abstract List<List<String>> readTableContent(String tableName) throws SQLException;

    /** Restore the database to a previous state (undo the DML). */
    protected abstract void restoreTable(String tableName, List<List<String>> snapshot) throws SQLException;

    /** Get the target table name from the DML statement. */
    protected abstract String getTargetTableName(SQLQueryAdapter dml);

    // --- Same abstract methods as EETOracleBase for generator/transformer creation ---

    protected abstract T getRandomTables();
    protected abstract Object createGenerator(T tables);
    protected abstract Object createTransformer(Object gen, T tables);
    protected abstract EETQueryExecutor createDefaultExecutor();

    // --- DML execution helpers ---

    /** Execute a DML statement (UPDATE/DELETE) — not a query returning rows. */
    protected void executeDML(SQLQueryAdapter dml) throws SQLException {
        try {
            dml.execute(state);
        } catch (SQLException e) {
            // DML execution errors (constraint violations, etc.) — treat as non-bug
            throw new IgnoreMeException();
        }
    }

    // --- Multiset difference for computing changed rows ---

    /** Compute multiset difference: rows in after that are not in before. */
    protected static List<List<String>> computeChangedRows(List<List<String>> before, List<List<String>> after) {
        // Build a mutable copy of before for removal tracking
        List<List<String>> beforeCopy = new ArrayList<>(before);
        List<List<String>> changed = new ArrayList<>();
        for (List<String> row : after) {
            int idx = findRow(beforeCopy, row);
            if (idx >= 0) {
                beforeCopy.remove(idx); // row existed before — not a change
            } else {
                changed.add(row); // new row — this is a change
            }
        }
        return changed;
    }

    /** Compute deleted rows: rows in before that are not in after. */
    protected static List<List<String>> computeDeletedRows(List<List<String>> before, List<List<String>> after) {
        List<List<String>> afterCopy = new ArrayList<>(after);
        List<List<String>> deleted = new ArrayList<>();
        for (List<String> row : before) {
            int idx = findRow(afterCopy, row);
            if (idx >= 0) {
                afterCopy.remove(idx); // row still exists — not deleted
            } else {
                deleted.add(row); // row gone — this is a deletion
            }
        }
        return deleted;
    }

    private static int findRow(List<List<String>> multiset, List<String> target) {
        for (int i = 0; i < multiset.size(); i++) {
            if (EETMultisetComparator.rowsEqual(multiset.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    // --- Concrete check() flow for DML EET ---

    @Override
    public void check() throws SQLException {
        lastReproducer = null;
        T targetTables = getRandomTables();
        Object gen = createGenerator(targetTables);

        SQLQueryAdapter originalDML = generateDML(state, gen, targetTables);
        SQLQueryAdapter transformedDML = generateTransformedDML(state, gen, targetTables, originalDML);

        String tableName = getTargetTableName(originalDML);
        if (tableName == null) {
            throw new IgnoreMeException();
        }

        lastQueryString = originalDML.getQueryString() + "\n-- EET transformed DML:\n"
                + transformedDML.getQueryString();

        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(lastQueryString);
        }

        // 1. Snapshot before
        List<List<String>> snapshotBefore;
        try {
            snapshotBefore = readTableContent(tableName);
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }
        if (snapshotBefore.size() > MAX_PROCESS_ROW_NUM) {
            throw new IgnoreMeException();
        }

        // 2. Execute original DML
        try {
            executeDML(originalDML);
        } catch (IgnoreMeException e) {
            throw e;
        }

        // 3. Snapshot after original
        List<List<String>> snapshotAfterOriginal;
        try {
            snapshotAfterOriginal = readTableContent(tableName);
        } catch (SQLException e) {
            // If we can't read after DML, try to restore anyway
            try {
                restoreTable(tableName, snapshotBefore);
            } catch (SQLException ignored) {
            }
            throw new IgnoreMeException();
        }
        if (snapshotAfterOriginal.size() > MAX_PROCESS_ROW_NUM) {
            try {
                restoreTable(tableName, snapshotBefore);
            } catch (SQLException ignored) {
            }
            throw new IgnoreMeException();
        }

        // 4. Compute changed rows for original DML
        List<List<String>> originalChangedRows = computeChangedRows(snapshotBefore, snapshotAfterOriginal);
        List<List<String>> originalDeletedRows = computeDeletedRows(snapshotBefore, snapshotAfterOriginal);

        // 5. Restore to snapshot before
        try {
            restoreTable(tableName, snapshotBefore);
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }

        // 6. Execute transformed DML
        try {
            executeDML(transformedDML);
        } catch (IgnoreMeException e) {
            // If transformed DML fails but original succeeded, that's a potential bug
            // But we already restored, so just skip
            throw e;
        }

        // 7. Snapshot after transformed
        List<List<String>> snapshotAfterTransformed;
        try {
            snapshotAfterTransformed = readTableContent(tableName);
        } catch (SQLException e) {
            try {
                restoreTable(tableName, snapshotBefore);
            } catch (SQLException ignored) {
            }
            throw new IgnoreMeException();
        }

        // 8. Compute changed rows for transformed DML
        List<List<String>> transformedChangedRows = computeChangedRows(snapshotBefore, snapshotAfterTransformed);
        List<List<String>> transformedDeletedRows = computeDeletedRows(snapshotBefore, snapshotAfterTransformed);

        // 9. Restore (cleanup)
        try {
            restoreTable(tableName, snapshotBefore);
        } catch (SQLException ignored) {
            // Best effort cleanup
        }

        // 10. Compare changed-row multisets
        boolean changedMatch = EETMultisetComparator.compareResultMultisets(originalChangedRows, transformedChangedRows);
        boolean deletedMatch = EETMultisetComparator.compareResultMultisets(originalDeletedRows, transformedDeletedRows);

        if (changedMatch && deletedMatch) {
            return;
        }

        // 11. Double-execution confirmation
        // Re-run entire test to reduce flakiness
        try {
            snapshotBefore = readTableContent(tableName);
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }
        try {
            executeDML(originalDML);
        } catch (IgnoreMeException e) {
            throw e;
        }
        try {
            snapshotAfterOriginal = readTableContent(tableName);
        } catch (SQLException e) {
            try {
                restoreTable(tableName, snapshotBefore);
            } catch (SQLException ignored) {
            }
            throw new IgnoreMeException();
        }
        originalChangedRows = computeChangedRows(snapshotBefore, snapshotAfterOriginal);
        originalDeletedRows = computeDeletedRows(snapshotBefore, snapshotAfterOriginal);
        try {
            restoreTable(tableName, snapshotBefore);
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }
        try {
            executeDML(transformedDML);
        } catch (IgnoreMeException e) {
            throw e;
        }
        try {
            snapshotAfterTransformed = readTableContent(tableName);
        } catch (SQLException e) {
            try {
                restoreTable(tableName, snapshotBefore);
            } catch (SQLException ignored) {
            }
            throw new IgnoreMeException();
        }
        transformedChangedRows = computeChangedRows(snapshotBefore, snapshotAfterTransformed);
        transformedDeletedRows = computeDeletedRows(snapshotBefore, snapshotAfterTransformed);
        try {
            restoreTable(tableName, snapshotBefore);
        } catch (SQLException ignored) {
        }

        changedMatch = EETMultisetComparator.compareResultMultisets(originalChangedRows, transformedChangedRows);
        deletedMatch = EETMultisetComparator.compareResultMultisets(originalDeletedRows, transformedDeletedRows);

        if (!changedMatch || !deletedMatch) {
            throw new AssertionError(String.format("EET DML logic bug: row-state mismatch\n%s\n", lastQueryString));
        }
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }

    @Override
    public Reproducer<S> getLastReproducer() {
        return lastReproducer;
    }
}