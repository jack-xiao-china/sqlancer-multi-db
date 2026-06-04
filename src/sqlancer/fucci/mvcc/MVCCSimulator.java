package sqlancer.fucci.mvcc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.fucci.transaction.FucciTransaction;

/**
 * MVCC Simulator that models version visibility per isolation level.
 * References Troc's TrocChecker visibility logic:
 *
 * <pre>
 *   RU (NEWEST):           See newest version (including uncommitted)
 *   RC (COMMITTED):        See committed versions (txInit + curTx + committed others)
 *   RR (SNAPSHOT):         See snapshot taken at first SELECT
 *   SER (COMMITTED+LOCK):  Same as RC + SELECT takes SHARE lock
 * </pre>
 *
 * Version chains: each row has a list of versions, newest at end.
 * Visibility: iterate from newest to oldest, find first visible version.
 */
public class MVCCSimulator {

    /** Version chains: tableName → rowId → version list. */
    private final Map<String, Map<Integer, List<Version>>> versionChains;

    /** Committed transactions. */
    private final List<FucciTransaction> committedTxs;

    /** Snapshot transaction IDs per transaction (for RR). */
    private final Map<FucciTransaction, List<Integer>> snapshotTxs;

    /** Initial transaction marker. */
    private static final String TX_INIT = "initial";

    public MVCCSimulator(Map<String, Map<Integer, List<Version>>> versionChains) {
        this.versionChains = versionChains;
        this.committedTxs = new ArrayList<>();
        this.snapshotTxs = new HashMap<>();
    }

    /**
     * Mark a transaction as committed.
     */
    public void commitTransaction(FucciTransaction tx) {
        committedTxs.add(tx);
    }

    /**
     * Remove a transaction's versions (on rollback).
     */
    public void rollbackTransaction(FucciTransaction tx) {
        for (Map<Integer, List<Version>> tableVersions : versionChains.values()) {
            for (List<Version> versions : tableVersions.values()) {
                versions.removeIf(v -> v.getTransaction() instanceof FucciTransaction
                        && ((FucciTransaction) v.getTransaction()).getId() == tx.getId());
            }
        }
    }

    /**
     * Create a snapshot for a transaction (for RR isolation).
     * Snapshot includes: txInit + curTx + committed tx IDs at the time of snapshot.
     */
    public void createSnapshot(FucciTransaction curTx) {
        List<Integer> snapTxIds = new ArrayList<>();
        snapTxIds.add(curTx.getId());
        for (FucciTransaction committedTx : committedTxs) {
            snapTxIds.add(committedTx.getId());
        }
        snapshotTxs.put(curTx, snapTxIds);
    }

    /**
     * Check if a snapshot exists for a transaction.
     */
    public boolean hasSnapshot(FucciTransaction tx) {
        return snapshotTxs.containsKey(tx) && !snapshotTxs.get(tx).isEmpty();
    }

    /**
     * Build the visible view for a transaction under the given visibility rule.
     * Returns: tableName → list of visible row data.
     */
    public Map<String, List<Object[]>> buildView(FucciTransaction curTx, VisibilityRule rule) {
        Map<String, List<Object[]>> view = new HashMap<>();

        for (Map.Entry<String, Map<Integer, List<Version>>> tableEntry : versionChains.entrySet()) {
            String tableName = tableEntry.getKey();
            List<Object[]> visibleRows = new ArrayList<>();

            for (Map.Entry<Integer, List<Version>> rowEntry : tableEntry.getValue().entrySet()) {
                List<Version> versions = rowEntry.getValue();
                Object[] visibleData = findVisibleVersion(versions, curTx, rule);
                if (visibleData != null) {
                    visibleRows.add(visibleData);
                }
            }
            view.put(tableName, visibleRows);
        }
        return view;
    }

    /**
     * Compute the final database state (all committed, non-deleted versions).
     */
    public Map<String, List<Object>> computeFinalStates() {
        Map<String, List<Object>> finalStates = new HashMap<>();

        for (Map.Entry<String, Map<Integer, List<Version>>> tableEntry : versionChains.entrySet()) {
            String tableName = tableEntry.getKey();
            List<Object> tableData = new ArrayList<>();

            for (Map.Entry<Integer, List<Version>> rowEntry : tableEntry.getValue().entrySet()) {
                List<Version> versions = rowEntry.getValue();
                for (int i = versions.size() - 1; i >= 0; i--) {
                    Version v = versions.get(i);
                    if (isCommitted(v) && !v.isDeleted()) {
                        Object[] data = v.getData();
                        if (data != null) {
                            for (Object o : data) {
                                tableData.add(o);
                            }
                        }
                        break;
                    }
                }
            }
            finalStates.put(tableName, tableData);
        }
        return finalStates;
    }

    /**
     * Add a new version to a row's version chain.
     */
    public void addVersion(String tableName, int rowId, Version version) {
        Map<Integer, List<Version>> tableVersions = versionChains.get(tableName);
        if (tableVersions == null) {
            tableVersions = new HashMap<>();
            versionChains.put(tableName, tableVersions);
        }
        List<Version> versions = tableVersions.computeIfAbsent(rowId, k -> new ArrayList<>());
        versions.add(version);
    }

    /**
     * Find the visible version for a row under the given visibility rule.
     * Iterates from newest to oldest.
     *
     * @return the row data if a visible version exists, null otherwise
     */
    private Object[] findVisibleVersion(List<Version> versions, FucciTransaction curTx, VisibilityRule rule) {
        for (int i = versions.size() - 1; i >= 0; i--) {
            Version v = versions.get(i);

            switch (rule) {
            case NEWEST:
                // RU: see everything (including uncommitted)
                if (!v.isDeleted()) {
                    return v.getData();
                }
                return null; // deleted

            case COMMITTED:
            case COMMITTED_WITH_LOCK:
                // RC/SER: see committed versions + own changes
                if (isVisibleCommitted(v, curTx)) {
                    if (!v.isDeleted()) {
                        return v.getData();
                    }
                    return null; // deleted
                }
                break;

            case SNAPSHOT:
                // RR: see snapshot versions + own changes
                if (isVisibleSnapshot(v, curTx)) {
                    if (!v.isDeleted()) {
                        return v.getData();
                    }
                    return null; // deleted
                }
                break;

            default:
                break;
            }
        }
        return null;
    }

    /**
     * Check if a version is visible under COMMITTED rule.
     * Visible if: initial, or curTx's own version, or committed by another tx.
     */
    private boolean isVisibleCommitted(Version v, FucciTransaction curTx) {
        Object txRef = v.getTransaction();
        if (TX_INIT.equals(txRef)) {
            return true;
        }
        if (txRef instanceof FucciTransaction) {
            FucciTransaction vTx = (FucciTransaction) txRef;
            if (vTx.getId() == curTx.getId()) {
                return true; // own version
            }
            return vTx.isCommitted();
        }
        return true;
    }

    /**
     * Check if a version is visible under SNAPSHOT rule.
     * Visible if: initial, or curTx's own version, or tx was in snapshot at creation time.
     */
    private boolean isVisibleSnapshot(Version v, FucciTransaction curTx) {
        Object txRef = v.getTransaction();
        if (TX_INIT.equals(txRef)) {
            return true;
        }
        if (txRef instanceof FucciTransaction) {
            FucciTransaction vTx = (FucciTransaction) txRef;
            if (vTx.getId() == curTx.getId()) {
                return true; // own version
            }
            // Check if the version's transaction was committed and in the snapshot
            if (vTx.isCommitted()) {
                List<Integer> snapIds = snapshotTxs.get(curTx);
                return snapIds != null && snapIds.contains(vTx.getId());
            }
        }
        return true;
    }

    /**
     * Check if a version's transaction is committed.
     */
    private boolean isCommitted(Version v) {
        Object txRef = v.getTransaction();
        if (TX_INIT.equals(txRef)) {
            return true;
        }
        if (txRef instanceof FucciTransaction) {
            return ((FucciTransaction) txRef).isCommitted();
        }
        return true;
    }
}
