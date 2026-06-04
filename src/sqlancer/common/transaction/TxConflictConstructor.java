package sqlancer.common.transaction;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.SQLGlobalState;
import sqlancer.common.query.ExpectedErrors;

/**
 * Constructs artificial conflicts between generated transactions.
 *
 * References Troc's TableTool.makeConflict(): after generating transaction pairs,
 * modifies their SQL statements so they operate on the same rows, ensuring lock
 * conflicts that would otherwise be extremely unlikely under random generation.
 *
 * Two strategies:
 * - Strategy A: Copy WHERE clause from one statement to another (same table, same filter)
 * - Strategy B: Inject a shared row-targeting condition (AND pk = value) on both
 */
public final class TxConflictConstructor {

    private TxConflictConstructor() {
    }

    /**
     * Inject conflicts between transaction pairs by making them share operation targets.
     * For each pair of transactions, one data statement is selected from each and their
     * WHERE clauses are aligned.
     *
     * @param transactions the generated transactions (must be >= 2)
     * @param globalState  global state for accessing schema and connection
     */
    public static void makeConflict(List<Transaction> transactions, SQLGlobalState<?, ?> globalState) {
        if (transactions.size() < 2) {
            return;
        }

        List<List<TxStatement>> candidates = new ArrayList<>();
        for (Transaction tx : transactions) {
            List<TxStatement> txCandidates = filterDataStatements(tx);
            candidates.add(txCandidates);
        }

        // For each pair, create at least one conflict
        for (int i = 0; i < transactions.size(); i++) {
            for (int j = i + 1; j < transactions.size(); j++) {
                List<TxStatement> c1 = candidates.get(i);
                List<TxStatement> c2 = candidates.get(j);
                if (c1.isEmpty() || c2.isEmpty()) {
                    continue;
                }
                TxStatement stmt1 = c1.get((int) Randomly.getNotCachedInteger(0, c1.size()));
                TxStatement stmt2 = c2.get((int) Randomly.getNotCachedInteger(0, c2.size()));
                try {
                    createConflict(stmt1, stmt2, globalState);
                } catch (Exception ignored) {
                    // If conflict construction fails, skip this pair silently
                }
            }
        }
    }

    /**
     * Filter statements that carry data conditions (UPDATE, DELETE, SELECT variants).
     * BEGIN, COMMIT, ROLLBACK, INSERT are excluded.
     */
    private static List<TxStatement> filterDataStatements(Transaction tx) {
        List<TxStatement> result = new ArrayList<>();
        for (TxStatement stmt : tx.getStatements()) {
            String sql = stmt.getTxQueryAdapter().getQueryString().toUpperCase().trim();
            if (sql.startsWith("SELECT") || sql.startsWith("UPDATE") || sql.startsWith("DELETE")) {
                result.add(stmt);
            }
        }
        return result;
    }

    /**
     * Create a conflict between two statements by aligning their WHERE clauses.
     *
     * Strategy A (60%): Copy WHERE — if at least one statement has a WHERE clause,
     *   copy it to the other statement.
     * Strategy B (40%): Inject shared PK condition — add AND pk_col = value to both.
     *
     * Falls back to adding WHERE 1 = 1 if neither has a WHERE clause.
     */
    private static void createConflict(TxStatement stmt1, TxStatement stmt2, SQLGlobalState<?, ?> globalState) {
        String sql1 = stmt1.getTxQueryAdapter().getQueryString();
        String sql2 = stmt2.getTxQueryAdapter().getQueryString();

        String where1 = extractWhereCondition(sql1);
        String where2 = extractWhereCondition(sql2);

        if (Randomly.getBooleanWithRatherLowProbability() && where1 != null && where2 != null) {
            // Strategy B: Inject shared PK condition into both
            String pkCondition = buildPkCondition(globalState);
            if (pkCondition != null) {
                modifyStatementWhere(stmt1, where1, pkCondition);
                modifyStatementWhere(stmt2, where2, pkCondition);
                return;
            }
        }

        // Strategy A: Copy WHERE clause
        if (where1 != null && where2 != null) {
            if (Randomly.getBoolean()) {
                replaceWhere(stmt1, where2);
            } else {
                replaceWhere(stmt2, where1);
            }
        } else if (where1 != null) {
            replaceWhere(stmt2, where1);
        } else if (where2 != null) {
            replaceWhere(stmt1, where2);
        } else {
            // Neither has WHERE: add WHERE 1 = 1 to stmt1 (ensures at least some overlap)
            injectWhere(stmt1, "1 = 1");
        }
    }

    /**
     * Modify a statement's existing WHERE clause by appending AND pkCondition.
     */
    private static void modifyStatementWhere(TxStatement stmt, String existingWhere, String pkCondition) {
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String newWhere = "(" + existingWhere + ") AND " + pkCondition;
        String newSql = replaceWhereInSql(sql, newWhere);
        if (newSql != null) {
            stmt.setTxSQLQueryAdapter(new TxSQLQueryAdapter(newSql,
                    new ExpectedErrors(stmt.getTxQueryAdapter().getExpectedErrors())));
        }
    }

    /**
     * Build a primary key condition like "c0 = 3" using schema information.
     * Uses reflection to access PK columns since AbstractTable doesn't expose getPrimaryKeyColumns().
     * Returns null if no suitable PK column is found.
     */
    private static String buildPkCondition(SQLGlobalState<?, ?> globalState) {
        try {
            List<?> tables = globalState.getSchema().getDatabaseTablesWithoutViews();
            if (tables.isEmpty()) {
                return null;
            }
            Object table = tables.get(0);

            // Get columns via AbstractTable.getColumns()
            java.lang.reflect.Method getColsMethod = table.getClass().getMethod("getColumns");
            List<?> cols = (List<?>) getColsMethod.invoke(table);
            if (cols.isEmpty()) {
                return null;
            }

            // Try to find PK column via isPrimaryKey() (available on MySQLColumn, etc.)
            Object pkCol = null;
            try {
                java.lang.reflect.Method isPkMethod = cols.get(0).getClass().getMethod("isPrimaryKey");
                for (Object col : cols) {
                    if ((Boolean) isPkMethod.invoke(col)) {
                        pkCol = col;
                        break;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // DBMS doesn't have isPrimaryKey()
            }

            // Fall back to first column
            if (pkCol == null) {
                pkCol = cols.get(0);
            }

            java.lang.reflect.Method getNameMethod = pkCol.getClass().getMethod("getName");
            String colName = (String) getNameMethod.invoke(pkCol);
            int value = (int) Randomly.getNotCachedInteger(0, 10);
            return colName + " = " + value;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== SQL String Manipulation ====================

    /**
     * Extract the WHERE condition from a SQL string (excluding trailing postfixes
     * like FOR UPDATE, LOCK IN SHARE MODE, etc).
     *
     * @return the WHERE condition string, or null if no WHERE clause exists
     */
    static String extractWhereCondition(String sql) {
        int whereIdx = findMainWhereKeyword(sql);
        if (whereIdx < 0) {
            return null;
        }
        String afterWhere = sql.substring(whereIdx + 5).trim();
        return stripTrailingPostfix(afterWhere);
    }

    /**
     * Find the position of the main WHERE keyword, skipping sub-query WHEREs.
     * Scans from the end backwards to find the outermost WHERE.
     */
    private static int findMainWhereKeyword(String sql) {
        String upper = sql.toUpperCase();
        int depth = 0;
        // Scan backwards to find the outermost WHERE
        for (int i = upper.length() - 1; i >= 5; i--) {
            char c = upper.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
            } else if (depth == 0 && i + 5 <= upper.length()
                    && upper.substring(i, i + 5).equals("WHERE")
                    && (i == 0 || !Character.isLetterOrDigit(upper.charAt(i - 1)))
                    && (i + 5 >= upper.length() || !Character.isLetterOrDigit(upper.charAt(i + 5)))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Strip trailing SQL postfixes (FOR UPDATE, LOCK IN SHARE MODE, LIMIT, ORDER BY)
     * from a WHERE condition string.
     */
    private static String stripTrailingPostfix(String condition) {
        String upper = condition.toUpperCase();
        String[] postfixKeywords = {"FOR UPDATE", "FOR SHARE", "LOCK IN SHARE MODE",
                "LIMIT", "ORDER BY", "GROUP BY", "HAVING"};

        int depth = 0;
        for (int i = 0; i < condition.length(); i++) {
            char c = condition.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                for (String postfix : postfixKeywords) {
                    if (i + postfix.length() <= condition.length()
                            && upper.substring(i, i + postfix.length()).equals(postfix)
                            && (i == 0 || !Character.isLetterOrDigit(condition.charAt(i - 1)))) {
                        return condition.substring(0, i).trim();
                    }
                }
            }
        }
        return condition.trim();
    }

    /**
     * Replace the WHERE clause of a statement with a new condition.
     */
    private static void replaceWhere(TxStatement stmt, String newCondition) {
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String newSql = replaceWhereInSql(sql, newCondition);
        if (newSql != null) {
            stmt.setTxSQLQueryAdapter(new TxSQLQueryAdapter(newSql,
                    new ExpectedErrors(stmt.getTxQueryAdapter().getExpectedErrors())));
        }
    }

    /**
     * Replace WHERE clause in a SQL string with a new condition.
     * Preserves trailing postfixes (FOR UPDATE, etc).
     *
     * @return the modified SQL, or null if no WHERE clause exists to replace
     */
    private static String replaceWhereInSql(String sql, String newCondition) {
        int whereIdx = findMainWhereKeyword(sql);
        if (whereIdx < 0) {
            return null;
        }
        String before = sql.substring(0, whereIdx + 5); // up to and including "WHERE"
        String afterWhere = sql.substring(whereIdx + 5).trim();
        String postfix = extractTrailingPostfix(afterWhere);
        return before + " " + newCondition + (postfix.isEmpty() ? "" : " " + postfix);
    }

    /**
     * Inject a WHERE clause into a statement that has none.
     * Inserts before any trailing postfix (FOR UPDATE, etc).
     */
    private static void injectWhere(TxStatement stmt, String condition) {
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String newSql = injectWhereInSql(sql, condition);
        stmt.setTxSQLQueryAdapter(new TxSQLQueryAdapter(newSql,
                new ExpectedErrors(stmt.getTxQueryAdapter().getExpectedErrors())));
    }

    /**
     * Inject WHERE clause into a SQL string that has no WHERE.
     * Inserts before trailing postfix (FOR UPDATE, LOCK IN SHARE MODE, etc).
     */
    private static String injectWhereInSql(String sql, String condition) {
        String upper = sql.toUpperCase();
        String[] postfixKeywords = {"FOR UPDATE", "FOR SHARE", "LOCK IN SHARE MODE"};
        for (String postfix : postfixKeywords) {
            int depth = 0;
            for (int i = 0; i < sql.length(); i++) {
                char c = sql.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (depth == 0 && i + postfix.length() <= sql.length()
                        && upper.substring(i, i + postfix.length()).equals(postfix)
                        && (i == 0 || !Character.isLetterOrDigit(sql.charAt(i - 1)))) {
                    String before = sql.substring(0, i).trim();
                    String after = sql.substring(i);
                    return before + " WHERE " + condition + " " + after;
                }
            }
        }
        return sql + " WHERE " + condition;
    }

    /**
     * Extract trailing postfix (FOR UPDATE, LOCK IN SHARE MODE, etc) from a condition string.
     */
    private static String extractTrailingPostfix(String condition) {
        String upper = condition.toUpperCase();
        String[] postfixKeywords = {"FOR UPDATE", "FOR SHARE", "LOCK IN SHARE MODE",
                "LIMIT", "ORDER BY", "GROUP BY", "HAVING"};

        int depth = 0;
        for (int i = 0; i < condition.length(); i++) {
            char c = condition.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                for (String postfix : postfixKeywords) {
                    if (i + postfix.length() <= condition.length()
                            && upper.substring(i, i + postfix.length()).equals(postfix)
                            && (i == 0 || !Character.isLetterOrDigit(condition.charAt(i - 1)))) {
                        return condition.substring(i).trim();
                    }
                }
            }
        }
        return "";
    }
}
