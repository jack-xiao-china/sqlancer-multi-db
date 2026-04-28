package sqlancer.gaussdbm.ast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import sqlancer.IgnoreMeException;

/**
 * Utility class for GaussDB-M temporal (date/time) operations. Provides parsing, calculation, and extraction methods
 * for date/time values. Compatible with MySQL temporal function behavior.
 */
public final class GaussDBTemporalUtil {

    // Date formatter: YYYY-MM-DD
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Datetime formatter: YYYY-MM-DD HH:mm:ss[.SSSSSS]
    private static final DateTimeFormatter DATETIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss").optionalStart()
            .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true).optionalEnd().toFormatter();

    private GaussDBTemporalUtil() {
        // Utility class, prevent instantiation
    }

    // ==================== Parsing Methods ====================

    /**
     * Parse a date string in MySQL format (YYYY-MM-DD).
     */
    public static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
    }

    /**
     * Parse a datetime string in MySQL format (YYYY-MM-DD HH:mm:ss[.SSSSSS]).
     */
    public static LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), DATETIME_FORMATTER);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
    }

    /**
     * Parse a temporal value (date, datetime, or timestamp).
     */
    public static LocalDateTime parseTemporal(String value) {
        String trimmed = value.trim();
        if (trimmed.contains(" ")) {
            return parseDateTime(trimmed);
        } else {
            return parseDate(trimmed).atStartOfDay();
        }
    }

    // ==================== Formatting Methods ====================

    /**
     * Format a LocalDate to MySQL date string.
     */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    /**
     * Format a LocalDateTime to MySQL datetime string.
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        int micros = dateTime.getNano() / 1000;
        if (micros != 0) {
            return String.format("%04d-%02d-%02d %02d:%02d:%02d.%06d", dateTime.getYear(), dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(), dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond(), micros);
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ==================== Date Difference Methods ====================

    /**
     * Calculate the number of days between two dates (date1 - date2). Only the date parts are compared, regardless of
     * time.
     */
    public static long dateDiff(String date1, String date2) {
        LocalDate d1 = parseTemporal(date1).toLocalDate();
        LocalDate d2 = parseTemporal(date2).toLocalDate();
        return ChronoUnit.DAYS.between(d2, d1); // d1 - d2
    }

    // ==================== Date Part Extraction Methods ====================

    /**
     * Extract year from a date/datetime value.
     */
    public static int extractYear(String value) {
        return parseTemporal(value).getYear();
    }

    /**
     * Extract month (1-12) from a date/datetime value.
     */
    public static int extractMonth(String value) {
        return parseTemporal(value).getMonthValue();
    }

    /**
     * Extract day of month (1-31) from a date/datetime value.
     */
    public static int extractDay(String value) {
        return parseTemporal(value).getDayOfMonth();
    }

    /**
     * Extract day of week (1=Sunday, 7=Saturday) from a date/datetime value. Note: MySQL's DAYOFWEEK returns 1 for
     * Sunday, while Java's DayOfWeek returns 1 for Monday.
     */
    public static int dayOfWeek(String value) {
        LocalDate date = parseTemporal(value).toLocalDate();
        // Java: Monday=1, Sunday=7; MySQL: Sunday=1, Saturday=7
        int javaDayOfWeek = date.getDayOfWeek().getValue();
        return (javaDayOfWeek % 7) + 1;
    }

    /**
     * Extract hour (0-23) from a time/datetime value.
     */
    public static int extractHour(String value) {
        LocalDateTime dt = parseTemporal(value);
        return dt.getHour();
    }

    /**
     * Extract minute (0-59) from a time/datetime value.
     */
    public static int extractMinute(String value) {
        LocalDateTime dt = parseTemporal(value);
        return dt.getMinute();
    }

    /**
     * Extract second (0-59) from a time/datetime value.
     */
    public static int extractSecond(String value) {
        LocalDateTime dt = parseTemporal(value);
        return dt.getSecond();
    }

    // ==================== Last Day of Month ====================

    /**
     * Get last day of month for a date.
     */
    public static LocalDate lastDayOfMonth(String value) {
        LocalDate date = parseTemporal(value).toLocalDate();
        return date.with(TemporalAdjusters.lastDayOfMonth());
    }
}