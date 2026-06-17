package sqlancer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

public final class ComparatorHelper {

    private ComparatorHelper() {
    }

    public static boolean isEqualDouble(String first, String second) {
        try {
            double val = Double.parseDouble(first);
            double secVal = Double.parseDouble(second);
            return equals(val, secVal);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean equals(double a, double b) {
        if (a == b) {
            return true;
        }
        // If the difference is less than epsilon, treat as equal.
        return Math.abs(a - b) < 0.001 * Math.max(Math.abs(a), Math.abs(b)) + 0.001;
    }

    public static List<String> getResultSetFirstColumnAsString(String queryString, ExpectedErrors errors,
            SQLGlobalState<?, ?> state) throws SQLException {
        if (state.getOptions().logEachSelect()) {
            // TODO: refactor me
            state.getLogger().writeCurrent(queryString);
            try {
                state.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        boolean canonicalizeString = state.getOptions().canonicalizeSqlString();
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors, true, canonicalizeString);
        List<String> resultSet = new ArrayList<>();
        SQLancerResultSet result = null;
        try {
            result = q.executeAndGet(state);
            state.getManager().incrementSelectQueryCount();
            if (result == null) {
                throw new IgnoreMeException();
            }
            while (result.next()) {
                String resultTemp = result.getString(1);
                if (resultTemp != null) {
                    resultTemp = resultTemp.replaceAll("[\\.]0+$", ""); // Remove the trailing zeros as many DBMS treat
                    // it as non-bugs
                }
                resultSet.add(resultTemp);
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }

            if (e.getMessage() == null) {
                throw new AssertionError(queryString, e);
            }
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(queryString, e);
        } finally {
            if (result != null && !result.isClosed()) {
                result.close();
            }
        }
        return resultSet;
    }

    public static void assumeResultSetsAreEqual(List<String> resultSet, List<String> secondResultSet,
            String originalQueryString, List<String> combinedString, SQLGlobalState<?, ?> state) {
        if (resultSet.size() != secondResultSet.size()) {
            String queryFormatString = "-- %s;" + System.lineSeparator() + "-- cardinality: %d"
                    + System.lineSeparator();
            String firstQueryString = String.format(queryFormatString, originalQueryString, resultSet.size());
            String combinedQueryString = String.join(";", combinedString);
            String secondQueryString = String.format(queryFormatString, combinedQueryString, secondResultSet.size());
            state.getState().getLocalState()
                    .log(String.format("%s" + System.lineSeparator() + "%s", firstQueryString, secondQueryString));
            String assertionMessage = String.format(
                    "The size of the result sets mismatch (%d and %d)!" + System.lineSeparator()
                            + "First query: \"%s\", whose cardinality is: %d" + System.lineSeparator()
                            + "Second query:\"%s\", whose cardinality is: %d",
                    resultSet.size(), secondResultSet.size(), originalQueryString, resultSet.size(),
                    combinedQueryString, secondResultSet.size());
            throw new AssertionError(assertionMessage);
        }

        Set<String> firstHashSet = new HashSet<>(resultSet);
        Set<String> secondHashSet = new HashSet<>(secondResultSet);

        boolean validateResultSizeOnly = state.getOptions().validateResultSizeOnly();
        if (!validateResultSizeOnly && !firstHashSet.equals(secondHashSet)) {
            Set<String> firstResultSetMisses = new HashSet<>(firstHashSet);
            firstResultSetMisses.removeAll(secondHashSet);
            Set<String> secondResultSetMisses = new HashSet<>(secondHashSet);
            secondResultSetMisses.removeAll(firstHashSet);

            String queryFormatString = "-- Query: \"%s\"; It misses: \"%s\"";
            String firstQueryString = String.format(queryFormatString, originalQueryString, firstResultSetMisses);
            String secondQueryString = String.format(queryFormatString, String.join(";", combinedString),
                    secondResultSetMisses);
            // update the SELECT queries to be logged at the bottom of the error log file
            state.getState().getLocalState()
                    .log(String.format("%s" + System.lineSeparator() + "%s", firstQueryString, secondQueryString));
            String assertionMessage = String.format("The content of the result sets mismatch!" + System.lineSeparator()
                    + "First query : \"%s\"" + System.lineSeparator() + "Second query: \"%s\"", originalQueryString,
                    secondQueryString);
            throw new AssertionError(assertionMessage);
        }
    }

    public static void assumeResultSetsAreEqual(List<String> resultSet, List<String> secondResultSet,
            String originalQueryString, List<String> combinedString, SQLGlobalState<?, ?> state,
            UnaryOperator<String> canonicalizationRule) {
        // Overloaded version of assumeResultSetsAreEqual that takes a canonicalization function which is applied to
        // both result sets before their comparison.
        List<String> canonicalizedResultSet = resultSet.stream().map(canonicalizationRule).collect(Collectors.toList());
        List<String> canonicalizedSecondResultSet = secondResultSet.stream().map(canonicalizationRule)
                .collect(Collectors.toList());
        assumeResultSetsAreEqual(canonicalizedResultSet, canonicalizedSecondResultSet, originalQueryString,
                combinedString, state);
    }

    /**
     * Multiset (bag) comparison: preserves duplicate row counts. Required for JIR Oracle where UNION ALL semantics
     * demand bag equality (not set equality). Also applies a canonicalization rule to normalize values like -0.0 to 0.0.
     */
    public static void assumeResultSetsAreEqualMultiset(List<String> resultSet, List<String> secondResultSet,
            String originalQueryString, List<String> combinedString, SQLGlobalState<?, ?> state,
            UnaryOperator<String> canonicalizationRule) {
        List<String> canonicalizedFirst = resultSet.stream().map(canonicalizationRule).collect(Collectors.toList());
        List<String> canonicalizedSecond = secondResultSet.stream().map(canonicalizationRule)
                .collect(Collectors.toList());

        if (canonicalizedFirst.size() != canonicalizedSecond.size()) {
            String queryFormatString = "-- %s;" + System.lineSeparator() + "-- cardinality: %d"
                    + System.lineSeparator();
            String firstQueryString = String.format(queryFormatString, originalQueryString, canonicalizedFirst.size());
            String combinedQueryString = String.join(";", combinedString);
            String secondQueryString = String.format(queryFormatString, combinedQueryString,
                    canonicalizedSecond.size());
            state.getState().getLocalState()
                    .log(String.format("%s" + System.lineSeparator() + "%s", firstQueryString, secondQueryString));
            String assertionMessage = String.format(
                    "The size of the result sets mismatch (%d and %d)!" + System.lineSeparator()
                            + "First query: \"%s\", whose cardinality is: %d" + System.lineSeparator()
                            + "Second query:\"%s\", whose cardinality is: %d",
                    canonicalizedFirst.size(), canonicalizedSecond.size(), originalQueryString,
                    canonicalizedFirst.size(), combinedQueryString, canonicalizedSecond.size());
            throw new AssertionError(assertionMessage);
        }

        boolean validateResultSizeOnly = state.getOptions().validateResultSizeOnly();
        if (!validateResultSizeOnly) {
            List<String> sortedFirst = new ArrayList<>(canonicalizedFirst);
            List<String> sortedSecond = new ArrayList<>(canonicalizedSecond);
            sortedFirst.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
            sortedSecond.sort(Comparator.nullsFirst(Comparator.naturalOrder()));

            if (!sortedFirst.equals(sortedSecond)) {
                // Find differences for diagnostic output
                List<String> firstMisses = new ArrayList<>(sortedFirst);
                firstMisses.removeAll(sortedSecond);
                List<String> secondMisses = new ArrayList<>(sortedSecond);
                secondMisses.removeAll(sortedFirst);

                String queryFormatString = "-- Query: \"%s\"; It misses: \"%s\"";
                String firstQueryString = String.format(queryFormatString, originalQueryString, firstMisses);
                String secondQueryString = String.format(queryFormatString, String.join(";", combinedString),
                        secondMisses);
                state.getState().getLocalState().log(
                        String.format("%s" + System.lineSeparator() + "%s", firstQueryString, secondQueryString));
                String assertionMessage = String.format(
                        "The content of the result sets mismatch (multiset comparison)!" + System.lineSeparator()
                                + "First query : \"%s\"" + System.lineSeparator() + "Second query: \"%s\"",
                        originalQueryString, secondQueryString);
                throw new AssertionError(assertionMessage);
            }
        }
    }

    public static List<String> getCombinedResultSet(String firstQueryString, String secondQueryString,
            String thirdQueryString, List<String> combinedString, boolean asUnion, SQLGlobalState<?, ?> state,
            ExpectedErrors errors) throws SQLException {
        List<String> secondResultSet;
        if (asUnion) {
            String unionString = firstQueryString + " UNION ALL " + secondQueryString + " UNION ALL "
                    + thirdQueryString;
            combinedString.add(unionString);
            secondResultSet = getResultSetFirstColumnAsString(unionString, errors, state);
        } else {
            secondResultSet = new ArrayList<>();
            secondResultSet.addAll(getResultSetFirstColumnAsString(firstQueryString, errors, state));
            secondResultSet.addAll(getResultSetFirstColumnAsString(secondQueryString, errors, state));
            secondResultSet.addAll(getResultSetFirstColumnAsString(thirdQueryString, errors, state));
            combinedString.add(firstQueryString);
            combinedString.add(secondQueryString);
            combinedString.add(thirdQueryString);
        }
        return secondResultSet;
    }

    public static List<String> getCombinedResultSetNoDuplicates(String firstQueryString, String secondQueryString,
            String thirdQueryString, List<String> combinedString, boolean asUnion, SQLGlobalState<?, ?> state,
            ExpectedErrors errors) throws SQLException {
        String unionString;
        if (asUnion) {
            unionString = firstQueryString + " UNION " + secondQueryString + " UNION " + thirdQueryString;
        } else {
            unionString = "SELECT DISTINCT * FROM (" + firstQueryString + " UNION ALL " + secondQueryString
                    + " UNION ALL " + thirdQueryString + ")";
        }
        List<String> secondResultSet;
        combinedString.add(unionString);
        secondResultSet = getResultSetFirstColumnAsString(unionString, errors, state);
        return secondResultSet;
    }

    public static String canonicalizeResultValue(String value) {
        if (value == null) {
            return value;
        }

        switch (value) {
        case "-0.0":
            return "0.0";
        case "-0":
            return "0";
        default:
        }

        return value;
    }

    /**
     * Read all columns from each row and concatenate them into a single string per row. Delimiter: "|" (unlikely to
     * appear in normal data). Required for JIR Oracle SELECT * and multi-column fetch comparisons where single-column
     * comparison (getString(1)) is insufficient.
     */
    public static List<String> getResultSetAllColumnsAsString(String queryString, ExpectedErrors errors,
            SQLGlobalState<?, ?> state) throws SQLException {
        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(queryString);
            try {
                state.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        boolean canonicalizeString = state.getOptions().canonicalizeSqlString();
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors, true, canonicalizeString);
        List<String> resultSet = new ArrayList<>();
        SQLancerResultSet result = null;
        try {
            result = q.executeAndGet(state);
            state.getManager().incrementSelectQueryCount();
            if (result == null) {
                throw new IgnoreMeException();
            }
            int columnCount = result.getMetaData().getColumnCount();
            while (result.next()) {
                StringBuilder rowBuilder = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        rowBuilder.append("|");
                    }
                    String value = result.getString(i);
                    if (value != null) {
                        value = canonicalizeResultValue(value);
                        value = value.replaceAll("[\\.]0+$", "");
                    }
                    rowBuilder.append(value == null ? "NULL" : value);
                }
                resultSet.add(rowBuilder.toString());
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }
            if (e.getMessage() == null) {
                throw new AssertionError(queryString, e);
            }
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(queryString, e);
        } finally {
            if (result != null && !result.isClosed()) {
                result.close();
            }
        }
        return resultSet;
    }

}
