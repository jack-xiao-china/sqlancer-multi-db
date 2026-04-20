package sqlancer.postgres.ast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sqlancer.IgnoreMeException;
import sqlancer.postgres.PostgresSchema.PostgresDataType;

final class PostgresTemporalUtil {

    enum TemporalField {
        YEAR, MONTH, DAY, HOUR, MINUTE, SECOND;

        static TemporalField fromString(String text) {
            switch (text.trim().toLowerCase()) {
            case "year":
                return YEAR;
            case "month":
            case "mon":
                return MONTH;
            case "day":
                return DAY;
            case "hour":
                return HOUR;
            case "minute":
            case "min":
                return MINUTE;
            case "second":
            case "sec":
                return SECOND;
            default:
                throw new IgnoreMeException();
            }
        }
    }

    static final class IntervalValue implements Comparable<IntervalValue> {

        private final int months;
        private final int days;
        private final long nanos;

        IntervalValue(int months, int days, long nanos) {
            this.months = months;
            this.days = days;
            this.nanos = nanos;
        }

        int getMonths() {
            return months;
        }

        int getDays() {
            return days;
        }

        long getNanos() {
            return nanos;
        }

        IntervalValue plus(IntervalValue other) {
            return new IntervalValue(months + other.months, days + other.days, nanos + other.nanos);
        }

        IntervalValue minus(IntervalValue other) {
            return new IntervalValue(months - other.months, days - other.days, nanos - other.nanos);
        }

        @Override
        public int compareTo(IntervalValue other) {
            int monthComparison = Integer.compare(months, other.months);
            if (monthComparison != 0) {
                return monthComparison;
            }
            int dayComparison = Integer.compare(days, other.days);
            if (dayComparison != 0) {
                return dayComparison;
            }
            return Long.compare(nanos, other.nanos);
        }

        String toCanonicalString() {
            StringBuilder sb = new StringBuilder();
            int years = months / 12;
            int remainingMonths = months % 12;
            if (years != 0) {
                sb.append(years).append(" years");
            }
            if (remainingMonths != 0) {
                appendWithSpace(sb, remainingMonths + " mons");
            }
            if (days != 0 || sb.length() == 0 && nanos == 0) {
                appendWithSpace(sb, days + " days");
            }
            if (nanos != 0 || sb.length() == 0) {
                appendWithSpace(sb, formatTimeNanos(nanos));
            }
            return sb.toString().trim();
        }

        private static void appendWithSpace(StringBuilder sb, String fragment) {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(fragment);
        }
    }

    private static final Pattern TRAILING_OFFSET_PATTERN = Pattern.compile("([+-])(\\d{2})(?::?(\\d{2}))?$");
    private static final Pattern UNIT_PATTERN = Pattern
            .compile("([+-]?\\d+)\\s+(years?|yrs?|mons?|months?|days?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN = Pattern.compile("([+-])?(\\d{1,2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?");

    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder().appendPattern("HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().toFormatter();
    private static final DateTimeFormatter OFFSET_TIME_FORMATTER = new DateTimeFormatterBuilder().append(TIME_FORMATTER)
            .appendOffset("+HH:MM", "Z").toFormatter();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss").optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd().toFormatter();

    private PostgresTemporalUtil() {
    }

    static String asText(PostgresDataType type, String value) {
        switch (type) {
        case DATE:
            return parseDate(value).toString();
        case TIME:
            return parseTime(value).format(TIME_FORMATTER);
        case TIMETZ:
            return parseOffsetTime(value).format(OFFSET_TIME_FORMATTER);
        case TIMESTAMP:
            return parseTimestamp(value).format(TIMESTAMP_FORMATTER);
        case TIMESTAMPTZ:
            return parseOffsetDateTime(value).toString().replace('T', ' ');
        case INTERVAL:
            return parseInterval(value).toCanonicalString();
        default:
            throw new IgnoreMeException();
        }
    }

    static int compare(PostgresDataType type, String left, String right) {
        switch (type) {
        case DATE:
            return parseDate(left).compareTo(parseDate(right));
        case TIME:
            return parseTime(left).compareTo(parseTime(right));
        case TIMETZ:
            return parseOffsetTime(left).compareTo(parseOffsetTime(right));
        case TIMESTAMP:
            return parseTimestamp(left).compareTo(parseTimestamp(right));
        case TIMESTAMPTZ:
            return parseOffsetDateTime(left).compareTo(parseOffsetDateTime(right));
        case INTERVAL:
            return parseInterval(left).compareTo(parseInterval(right));
        default:
            throw new IgnoreMeException();
        }
    }

    static String addInterval(PostgresDataType temporalType, String temporalValue, String intervalValue) {
        IntervalValue interval = parseInterval(intervalValue);
        switch (temporalType) {
        case TIME:
            return applyInterval(parseTime(temporalValue), interval).format(TIME_FORMATTER);
        case TIMETZ:
            return applyInterval(parseOffsetTime(temporalValue), interval).format(OFFSET_TIME_FORMATTER);
        case TIMESTAMP:
            return applyInterval(parseTimestamp(temporalValue), interval).format(TIMESTAMP_FORMATTER);
        case TIMESTAMPTZ:
            return applyInterval(parseOffsetDateTime(temporalValue), interval).toString().replace('T', ' ');
        case DATE:
            return applyInterval(parseDate(temporalValue).atStartOfDay(), interval).format(TIMESTAMP_FORMATTER);
        default:
            throw new IgnoreMeException();
        }
    }

    static String subtractInterval(PostgresDataType temporalType, String temporalValue, String intervalValue) {
        return addInterval(temporalType, temporalValue, negate(parseInterval(intervalValue)).toCanonicalString());
    }

    static long subtractDates(String left, String right) {
        return parseDate(left).toEpochDay() - parseDate(right).toEpochDay();
    }

    static String subtractTimestamps(PostgresDataType temporalType, String left, String right) {
        switch (temporalType) {
        case TIMESTAMP:
            return durationToInterval(parseTimestamp(left).toEpochSecond(ZoneOffset.UTC)
                    - parseTimestamp(right).toEpochSecond(ZoneOffset.UTC));
        case TIMESTAMPTZ:
            return durationToInterval(parseOffsetDateTime(left).toEpochSecond()
                    - parseOffsetDateTime(right).toEpochSecond());
        default:
            throw new IgnoreMeException();
        }
    }

    static String addIntervals(String left, String right) {
        return parseInterval(left).plus(parseInterval(right)).toCanonicalString();
    }

    static String subtractIntervals(String left, String right) {
        return parseInterval(left).minus(parseInterval(right)).toCanonicalString();
    }

    static long extractField(TemporalField field, PostgresDataType sourceType, String value) {
        switch (sourceType) {
        case DATE:
            return extractDateField(field, parseDate(value));
        case TIME:
            return extractTimeField(field, parseTime(value));
        case TIMETZ:
            return extractTimeField(field, parseOffsetTime(value).toLocalTime());
        case TIMESTAMP:
            return extractTimestampField(field, parseTimestamp(value));
        case TIMESTAMPTZ:
            return extractTimestampField(field, parseOffsetDateTime(value).toLocalDateTime());
        case INTERVAL:
            return extractIntervalField(field, parseInterval(value));
        default:
            throw new IgnoreMeException();
        }
    }

    static String dateTrunc(TemporalField field, PostgresDataType sourceType, String value) {
        switch (sourceType) {
        case TIMESTAMP:
            return truncateTimestamp(parseTimestamp(value), field).format(TIMESTAMP_FORMATTER);
        case TIMESTAMPTZ:
            return truncateOffsetTimestamp(parseOffsetDateTime(value), field).toString().replace('T', ' ');
        default:
            throw new IgnoreMeException();
        }
    }

    static String justifyHours(String value) {
        IntervalValue interval = parseInterval(value);
        long extraDays = interval.getNanos() / (24L * 60 * 60 * 1_000_000_000L);
        long nanos = interval.getNanos() % (24L * 60 * 60 * 1_000_000_000L);
        return new IntervalValue(interval.getMonths(), interval.getDays() + (int) extraDays, nanos).toCanonicalString();
    }

    static String justifyDays(String value) {
        IntervalValue interval = parseInterval(value);
        int extraMonths = interval.getDays() / 30;
        int days = interval.getDays() % 30;
        return new IntervalValue(interval.getMonths() + extraMonths, days, interval.getNanos()).toCanonicalString();
    }

    static String justifyInterval(String value) {
        return justifyDays(justifyHours(value));
    }

    static String makeInterval(int years, int months, int weeks, int days, int hours, int minutes, int seconds) {
        int totalMonths = years * 12 + months;
        int totalDays = weeks * 7 + days;
        long nanos = (((hours * 60L + minutes) * 60L) + seconds) * 1_000_000_000L;
        return new IntervalValue(totalMonths, totalDays, nanos).toCanonicalString();
    }

    static String timezone(String zone, String timestamptzValue) {
        ZoneOffset offset = parseZoneOffset(zone);
        return parseOffsetDateTime(timestamptzValue).withOffsetSameInstant(offset).toLocalDateTime()
                .format(TIMESTAMP_FORMATTER);
    }

    static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IgnoreMeException();
        }
    }

    static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IgnoreMeException();
        }
    }

    static OffsetTime parseOffsetTime(String value) {
        try {
            return OffsetTime.parse(normalizeOffset(value), OFFSET_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IgnoreMeException();
        }
    }

    static LocalDateTime parseTimestamp(String value) {
        try {
            return LocalDateTime.parse(normalizeTimestampSeparator(value), TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IgnoreMeException();
        }
    }

    static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(normalizeOffset(normalizeTimestampSeparator(value)).replace(" ", "T"));
        } catch (DateTimeParseException e) {
            throw new IgnoreMeException();
        }
    }

    static IntervalValue parseInterval(String value) {
        String input = value.trim();
        if (input.isEmpty()) {
            throw new IgnoreMeException();
        }
        int months = 0;
        int days = 0;
        long nanos = 0;
        Matcher unitMatcher = UNIT_PATTERN.matcher(input);
        StringBuffer remainderBuffer = new StringBuffer();
        while (unitMatcher.find()) {
            int amount = Integer.parseInt(unitMatcher.group(1));
            String unit = unitMatcher.group(2).toLowerCase();
            if (unit.startsWith("year") || unit.startsWith("yr")) {
                months += amount * 12;
            } else if (unit.startsWith("mon")) {
                months += amount;
            } else if (unit.startsWith("day")) {
                days += amount;
            } else {
                throw new IgnoreMeException();
            }
            unitMatcher.appendReplacement(remainderBuffer, " ");
        }
        unitMatcher.appendTail(remainderBuffer);
        String remainder = remainderBuffer.toString().trim();
        if (!remainder.isEmpty()) {
            Matcher timeMatcher = TIME_PATTERN.matcher(remainder);
            if (!timeMatcher.matches()) {
                throw new IgnoreMeException();
            }
            int sign = "-".equals(timeMatcher.group(1)) ? -1 : 1;
            int hours = Integer.parseInt(timeMatcher.group(2));
            int minutes = Integer.parseInt(timeMatcher.group(3));
            int seconds = Integer.parseInt(timeMatcher.group(4));
            String fractional = timeMatcher.group(5);
            int nanoPart = 0;
            if (fractional != null) {
                String normalizedFraction = (fractional + "000000000").substring(0, 9);
                nanoPart = Integer.parseInt(normalizedFraction);
            }
            nanos = sign * (((hours * 60L + minutes) * 60L + seconds) * 1_000_000_000L + nanoPart);
        }
        return new IntervalValue(months, days, nanos);
    }

    private static LocalTime applyInterval(LocalTime temporal, IntervalValue interval) {
        return temporal.plusNanos(interval.getNanos());
    }

    private static OffsetTime applyInterval(OffsetTime temporal, IntervalValue interval) {
        return temporal.plusNanos(interval.getNanos());
    }

    private static LocalDateTime applyInterval(LocalDateTime temporal, IntervalValue interval) {
        return temporal.plusMonths(interval.getMonths()).plusDays(interval.getDays()).plusNanos(interval.getNanos());
    }

    private static OffsetDateTime applyInterval(OffsetDateTime temporal, IntervalValue interval) {
        return temporal.plusMonths(interval.getMonths()).plusDays(interval.getDays()).plusNanos(interval.getNanos());
    }

    private static IntervalValue negate(IntervalValue interval) {
        return new IntervalValue(-interval.getMonths(), -interval.getDays(), -interval.getNanos());
    }

    private static long extractDateField(TemporalField field, LocalDate date) {
        switch (field) {
        case YEAR:
            return date.getYear();
        case MONTH:
            return date.getMonthValue();
        case DAY:
            return date.getDayOfMonth();
        default:
            throw new IgnoreMeException();
        }
    }

    private static long extractTimeField(TemporalField field, LocalTime time) {
        switch (field) {
        case HOUR:
            return time.getHour();
        case MINUTE:
            return time.getMinute();
        case SECOND:
            return time.getSecond();
        default:
            throw new IgnoreMeException();
        }
    }

    private static long extractTimestampField(TemporalField field, LocalDateTime timestamp) {
        switch (field) {
        case YEAR:
            return timestamp.getYear();
        case MONTH:
            return timestamp.getMonthValue();
        case DAY:
            return timestamp.getDayOfMonth();
        case HOUR:
            return timestamp.getHour();
        case MINUTE:
            return timestamp.getMinute();
        case SECOND:
            return timestamp.getSecond();
        default:
            throw new IgnoreMeException();
        }
    }

    private static long extractIntervalField(TemporalField field, IntervalValue interval) {
        switch (field) {
        case YEAR:
            return interval.getMonths() / 12;
        case MONTH:
            return interval.getMonths() % 12;
        case DAY:
            return interval.getDays();
        case HOUR:
            return interval.getNanos() / (60L * 60 * 1_000_000_000L);
        case MINUTE:
            return interval.getNanos() / (60L * 1_000_000_000L) % 60;
        case SECOND:
            return interval.getNanos() / 1_000_000_000L % 60;
        default:
            throw new IgnoreMeException();
        }
    }

    private static LocalDateTime truncateTimestamp(LocalDateTime timestamp, TemporalField field) {
        switch (field) {
        case YEAR:
            return LocalDateTime.of(timestamp.getYear(), 1, 1, 0, 0, 0);
        case MONTH:
            return LocalDateTime.of(timestamp.getYear(), timestamp.getMonthValue(), 1, 0, 0, 0);
        case DAY:
            return timestamp.toLocalDate().atStartOfDay();
        case HOUR:
            return timestamp.withMinute(0).withSecond(0).withNano(0);
        case MINUTE:
            return timestamp.withSecond(0).withNano(0);
        case SECOND:
            return timestamp.withNano(0);
        default:
            throw new IgnoreMeException();
        }
    }

    private static OffsetDateTime truncateOffsetTimestamp(OffsetDateTime timestamp, TemporalField field) {
        return truncateTimestamp(timestamp.toLocalDateTime(), field).atOffset(timestamp.getOffset());
    }

    private static String durationToInterval(long seconds) {
        return new IntervalValue(0, 0, seconds * 1_000_000_000L).toCanonicalString();
    }

    private static String normalizeTimestampSeparator(String value) {
        return value.trim().replace('T', ' ');
    }

    private static String normalizeOffset(String value) {
        String normalized = value.trim().replace('T', ' ');
        Matcher matcher = TRAILING_OFFSET_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return normalized;
        }
        String minutes = matcher.group(3) == null ? "00" : matcher.group(3);
        return normalized.substring(0, matcher.start()) + matcher.group(1) + matcher.group(2) + ":" + minutes;
    }

    private static ZoneOffset parseZoneOffset(String zone) {
        if ("UTC".equalsIgnoreCase(zone) || "Z".equalsIgnoreCase(zone)) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneOffset.of(zone);
        } catch (RuntimeException e) {
            throw new IgnoreMeException();
        }
    }

    private static String formatTimeNanos(long nanos) {
        long sign = nanos < 0 ? -1 : 1;
        long abs = Math.abs(nanos);
        long totalSeconds = abs / 1_000_000_000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long remainderNanos = abs % 1_000_000_000L;
        String value = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        if (remainderNanos != 0) {
            String fraction = String.format("%09d", remainderNanos).replaceFirst("0+$", "");
            value += "." + fraction;
        }
        return sign < 0 ? "-" + value : value;
    }
}
