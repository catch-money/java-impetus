package io.github.jockerCN.time;

import javax.annotation.Nullable;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Objects;
import java.util.function.Function;

/** Formatting, strict parsing, arithmetic, and zone-aware conversion for Java time types. */
public final class DateTimeUtils {

    private DateTimeUtils() {
    }

    // Named output patterns and formatters are the single source of truth.
    public static final String FORMAT_YM = "yyyy-MM";
    public static final String FORMAT_YM_CHINESE = "yyyy年MM月";
    public static final String FORMAT_YMD = "yyyy-MM-dd";
    public static final String FORMAT_MD = "MM-dd";
    public static final String FORMAT_HMS = "HH:mm:ss";
    public static final String FORMAT_MD_CHINESE = "MM月dd日";
    public static final String FORMAT_YMD_COMPACT = "yyyyMMdd";
    public static final String FORMAT_YMDHMS_COMPACT = "yyyyMMddHHmmss";
    public static final String FORMAT_YMDHMS = "yyyy-MM-dd HH:mm:ss";
    public static final String FORMAT_YMDHMS_MILLIS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String FORMAT_YMDHMS_MILLIS_SHORT = "yyyy-MM-dd HH:mm:ss.S";
    public static final String FORMAT_YMDTHMS = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String FORMAT_YMDTHMS_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    /** Legacy formatting only: Z is a literal character, not an offset. */
    public static final String FORMAT_YMDTHMS_MILLIS_Z = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final DateTimeFormatter FORMATTER_MD_CHINESE = formatter(FORMAT_MD_CHINESE);
    public static final DateTimeFormatter FORMATTER_MD = formatter(FORMAT_MD);
    public static final DateTimeFormatter FORMATTER_YM = strict(FORMAT_YM);
    public static final DateTimeFormatter FORMATTER_HMS = strict(FORMAT_HMS);
    public static final DateTimeFormatter FORMATTER_YM_CHINESE = formatter(FORMAT_YM_CHINESE);
    public static final DateTimeFormatter FORMATTER_YMD = strict(FORMAT_YMD);
    public static final DateTimeFormatter FORMATTER_YMD_COMPACT = strict(FORMAT_YMD_COMPACT);
    public static final DateTimeFormatter FORMATTER_YMD_HMS_COMPACT = strict(FORMAT_YMDHMS_COMPACT);
    public static final DateTimeFormatter FORMATTER_YMD_HMS = formatter(FORMAT_YMDHMS);
    public static final DateTimeFormatter FORMATTER_YMD_HMS_MILLIS = formatter(FORMAT_YMDHMS_MILLIS);
    public static final DateTimeFormatter FORMATTER_YMD_HMS_MILLIS_SHORT = formatter(FORMAT_YMDHMS_MILLIS_SHORT);
    public static final DateTimeFormatter FORMATTER_YMD_THMS = formatter(FORMAT_YMDTHMS);
    public static final DateTimeFormatter FORMATTER_YMD_THMS_MILLIS = formatter(FORMAT_YMDTHMS_MILLIS);
    public static final DateTimeFormatter FORMATTER_YMD_THMS_MILLIS_Z = formatter(FORMAT_YMDTHMS_MILLIS_Z);

    // Fixed-width output formatters above can also parse their own strict input shape.
    // Flexible fractions and offsets need distinct parsing formatters.
    public static final DateTimeFormatter PARSER_YMD_HMS = dateTimeWithFraction("uuuu-MM-dd HH:mm:ss");
    public static final DateTimeFormatter PARSER_YMD_THMS = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .withResolverStyle(ResolverStyle.STRICT);
    public static final DateTimeFormatter PARSER_OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final DateTimeFormatter FORMATTER_YMD_SLASH = strict("uuuu/M/d");
    private static final DateTimeFormatter FORMATTER_YMD_DOT = strict("uuuu.M.d");
    private static final DateTimeFormatter FORMATTER_YMD_CHINESE = strict("uuuu年M月d日");
    private static final DateTimeFormatter FORMATTER_YMD_HM = strict("uuuu-MM-dd H:mm");
    private static final DateTimeFormatter FORMATTER_YMD_HM_COMPACT = strict("uuuuMMddHHmm");
    private static final DateTimeFormatter FORMATTER_YMD_HMS_SLASH = dateTimeWithFraction("uuuu/M/d H:mm:ss");
    private static final DateTimeFormatter FORMATTER_YMD_HM_SLASH = strict("uuuu/M/d H:mm");
    private static final DateTimeFormatter FORMATTER_YMD_HMS_DOT = dateTimeWithFraction("uuuu.M.d H:mm:ss");
    private static final DateTimeFormatter FORMATTER_YMD_HM_DOT = strict("uuuu.M.d H:mm");
    private static final DateTimeFormatter FORMATTER_YMD_HMS_CHINESE = dateTimeWithFraction("uuuu年M月d日 H:mm:ss");
    private static final DateTimeFormatter FORMATTER_YMD_HM_CHINESE = strict("uuuu年M月d日 H:mm");
    private static final DateTimeFormatter FORMATTER_HMS_COMPACT = strict("HHmmss");
    private static final DateTimeFormatter FORMATTER_HM_COMPACT = strict("HHmm");
    private static final DateTimeFormatter FORMATTER_HMS_CHINESE = strict("H时m分s秒");
    private static final DateTimeFormatter FORMATTER_HM_CHINESE = strict("H时m分");
    private static final DateTimeFormatter FORMATTER_OFFSET_SPACE_DATE_TIME =
            new DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd HH:mm:ss")
                    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
                    .appendOffsetId().toFormatter().withResolverStyle(ResolverStyle.STRICT);

    // Default input formats are separated by target type. All custom formatters are tried first.
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            FORMATTER_YMD, FORMATTER_YMD_COMPACT,
            FORMATTER_YMD_SLASH, FORMATTER_YMD_DOT, FORMATTER_YMD_CHINESE
    };
    private static final DateTimeFormatter[] LOCAL_DATE_TIME_FORMATTERS = {
            PARSER_YMD_HMS, PARSER_YMD_THMS, FORMATTER_YMD_HM,
            FORMATTER_YMD_HMS_COMPACT, FORMATTER_YMD_HM_COMPACT,
            FORMATTER_YMD_HMS_SLASH, FORMATTER_YMD_HM_SLASH,
            FORMATTER_YMD_HMS_DOT, FORMATTER_YMD_HM_DOT,
            FORMATTER_YMD_HMS_CHINESE, FORMATTER_YMD_HM_CHINESE
    };
    private static final DateTimeFormatter[] LOCAL_TIME_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_TIME, FORMATTER_HMS,
            FORMATTER_HMS_COMPACT, FORMATTER_HM_COMPACT,
            FORMATTER_HMS_CHINESE, FORMATTER_HM_CHINESE
    };
    private static final DateTimeFormatter[] OFFSET_DATE_TIME_FORMATTERS = {
            PARSER_OFFSET_DATE_TIME, DateTimeFormatter.RFC_1123_DATE_TIME,
            FORMATTER_OFFSET_SPACE_DATE_TIME
    };

    public static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern);
    }

    @Nullable
    public static LocalDate stringToLocalDate(String value) {
        return parseLocalDate(value);
    }

    @Nullable
    public static LocalDate parseLocalDate(String value, DateTimeFormatter... additional) {
        return parseFirst(value, "LocalDate", DateTimeUtils::dateOnly, DATE_FORMATTERS, additional);
    }

    @Nullable
    public static LocalDateTime stringToLocalDateTime(String value) {
        return parseLocalDateTime(value);
    }

    @Nullable
    public static LocalDateTime parseLocalDateTime(String value, DateTimeFormatter... additional) {
        return parseFirst(value, "LocalDateTime", DateTimeUtils::dateTimeOnly,
                LOCAL_DATE_TIME_FORMATTERS, additional);
    }

    @Nullable
    public static LocalTime stringToLocalTime(String value) {
        return parseLocalTime(value);
    }

    @Nullable
    public static LocalTime parseLocalTime(String value, DateTimeFormatter... additional) {
        return parseFirst(value, "LocalTime", DateTimeUtils::timeOnly, LOCAL_TIME_FORMATTERS, additional);
    }

    @Nullable
    public static OffsetDateTime parseOffsetDateTime(String value, DateTimeFormatter... additional) {
        return parseFirst(value, "OffsetDateTime", OffsetDateTime::from,
                OFFSET_DATE_TIME_FORMATTERS, additional);
    }

    /** Parses only offset-aware input and converts its instant into the requested zone. */
    @Nullable
    public static LocalDateTime stringToLocalDateTime(String value, ZoneId targetZone) {
        Objects.requireNonNull(targetZone, "targetZone");
        OffsetDateTime parsed = parseOffsetDateTime(value);
        return Objects.isNull(parsed) ? null : parsed.atZoneSameInstant(targetZone).toLocalDateTime();
    }

    @Nullable
    public static LocalDateTime parseLocalDateTime(String value, ZoneId targetZone,
                                                   DateTimeFormatter... additional) {
        Objects.requireNonNull(targetZone, "targetZone");
        OffsetDateTime parsed = parseOffsetDateTime(value, additional);
        return Objects.isNull(parsed) ? null : parsed.atZoneSameInstant(targetZone).toLocalDateTime();
    }

    @Nullable
    public static YearMonth stringToYearMonth(String value) {
        return Objects.isNull(value) || value.isEmpty() ? null : YearMonth.parse(value, FORMATTER_YM);
    }

    @Nullable
    public static MonthDay stringToMonthDay(String value) {
        return Objects.isNull(value) || value.isEmpty() ? null : MonthDay.parse(value, FORMATTER_MD);
    }

    // A negative amount subtracts. java.time handles leap years and month-end adjustment.
    public static LocalDate addDays(LocalDate value, long days) {
        return value.plusDays(days);
    }

    public static LocalDate addMonths(LocalDate value, long months) {
        return value.plusMonths(months);
    }

    public static LocalDate addYears(LocalDate value, long years) {
        return value.plusYears(years);
    }

    public static LocalDateTime addDays(LocalDateTime value, long days) {
        return value.plusDays(days);
    }

    public static LocalDateTime addMonths(LocalDateTime value, long months) {
        return value.plusMonths(months);
    }

    public static LocalDateTime addYears(LocalDateTime value, long years) {
        return value.plusYears(years);
    }

    public static LocalDateTime addHours(LocalDateTime value, long hours) {
        return value.plusHours(hours);
    }

    public static LocalDateTime addMinutes(LocalDateTime value, long minutes) {
        return value.plusMinutes(minutes);
    }

    public static LocalDateTime addSeconds(LocalDateTime value, long seconds) {
        return value.plusSeconds(seconds);
    }

    public static LocalTime addHours(LocalTime value, long hours) {
        return value.plusHours(hours);
    }

    public static LocalTime addMinutes(LocalTime value, long minutes) {
        return value.plusMinutes(minutes);
    }

    public static LocalTime addSeconds(LocalTime value, long seconds) {
        return value.plusSeconds(seconds);
    }

    /** Positive when end is later than start; months and years count complete units. */
    public static long daysBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    public static long monthsBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.MONTHS.between(start, end);
    }

    public static long yearsBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.YEARS.between(start, end);
    }

    public static long daysBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    public static long monthsBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.MONTHS.between(start, end);
    }

    public static long yearsBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.YEARS.between(start, end);
    }

    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.HOURS.between(start, end);
    }

    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.MINUTES.between(start, end);
    }

    public static long secondsBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.SECONDS.between(start, end);
    }

    /** LocalTime differences do not assume an overnight rollover. */
    public static long hoursBetween(LocalTime start, LocalTime end) {
        return ChronoUnit.HOURS.between(start, end);
    }

    public static long minutesBetween(LocalTime start, LocalTime end) {
        return ChronoUnit.MINUTES.between(start, end);
    }

    public static long secondsBetween(LocalTime start, LocalTime end) {
        return ChronoUnit.SECONDS.between(start, end);
    }

    public static long toEpochMillis(LocalDateTime value) {
        return toEpochMillis(value, ZoneId.systemDefault());
    }

    public static long toEpochMillis(LocalDateTime value, ZoneId zone) {
        return value.atZone(Objects.requireNonNull(zone, "zone")).toInstant().toEpochMilli();
    }

    public static long toEpochMillisUtc(LocalDateTime value) {
        return toEpochMillis(value, ZoneOffset.UTC);
    }

    public static long toEpochMillis(LocalDate date) {
        return toEpochMillis(date, ZoneId.systemDefault());
    }

    public static long toEpochMillis(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(Objects.requireNonNull(zone, "zone")).toInstant().toEpochMilli();
    }

    public static long toEpochMillisUtc(LocalDate date) {
        return toEpochMillis(date, ZoneOffset.UTC);
    }

    /** A LocalTime needs a date and zone before it can represent an instant. */
    public static long toEpochMillis(LocalDate date, LocalTime time) {
        return toEpochMillis(date, time, ZoneId.systemDefault());
    }

    public static long toEpochMillis(LocalDate date, LocalTime time, ZoneId zone) {
        return toEpochMillis(LocalDateTime.of(date, time), zone);
    }

    public static long toEpochMillisUtc(LocalDate date, LocalTime time) {
        return toEpochMillis(date, time, ZoneOffset.UTC);
    }

    public static LocalDateTime fromEpochMillis(long millis) {
        return fromEpochMillis(millis, ZoneId.systemDefault());
    }

    public static LocalDateTime fromEpochMillis(long millis, ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), Objects.requireNonNull(zone, "zone"));
    }

    public static LocalDateTime fromEpochMillisUtc(long millis) {
        return fromEpochMillis(millis, ZoneOffset.UTC);
    }

    public static LocalDate localDateFromEpochMillis(long millis) {
        return localDateFromEpochMillis(millis, ZoneId.systemDefault());
    }

    public static LocalDate localDateFromEpochMillis(long millis, ZoneId zone) {
        return fromEpochMillis(millis, zone).toLocalDate();
    }

    public static LocalDate localDateFromEpochMillisUtc(long millis) {
        return localDateFromEpochMillis(millis, ZoneOffset.UTC);
    }

    public static LocalTime localTimeFromEpochMillis(long millis) {
        return localTimeFromEpochMillis(millis, ZoneId.systemDefault());
    }

    public static LocalTime localTimeFromEpochMillis(long millis, ZoneId zone) {
        return fromEpochMillis(millis, zone).toLocalTime();
    }

    public static LocalTime localTimeFromEpochMillisUtc(long millis) {
        return localTimeFromEpochMillis(millis, ZoneOffset.UTC);
    }

    /** Same instant, represented as local clock fields in the target zone. */
    public static LocalDateTime convertZone(LocalDateTime value, ZoneId sourceZone, ZoneId targetZone) {
        return value.atZone(Objects.requireNonNull(sourceZone, "sourceZone"))
                .withZoneSameInstant(Objects.requireNonNull(targetZone, "targetZone")).toLocalDateTime();
    }

    public static LocalDateTime toUtc(LocalDateTime localValue) {
        return toUtc(localValue, ZoneId.systemDefault());
    }

    public static LocalDateTime toUtc(LocalDateTime localValue, ZoneId sourceZone) {
        return convertZone(localValue, sourceZone, ZoneOffset.UTC);
    }

    public static LocalDateTime fromUtc(LocalDateTime utcValue) {
        return fromUtc(utcValue, ZoneId.systemDefault());
    }

    public static LocalDateTime fromUtc(LocalDateTime utcValue, ZoneId targetZone) {
        return convertZone(utcValue, ZoneOffset.UTC, targetZone);
    }

    private static LocalDate dateOnly(TemporalAccessor parsed) {
        if (Objects.nonNull(parsed.query(TemporalQueries.localTime()))
                || Objects.nonNull(parsed.query(TemporalQueries.offset()))) {
            throw new DateTimeException("Expected a date without time or offset");
        }
        return LocalDate.from(parsed);
    }

    private static LocalDateTime dateTimeOnly(TemporalAccessor parsed) {
        if (Objects.nonNull(parsed.query(TemporalQueries.offset()))) {
            throw new DateTimeException("Expected a local date-time without offset");
        }
        return LocalDateTime.from(parsed);
    }

    private static LocalTime timeOnly(TemporalAccessor parsed) {
        if (Objects.nonNull(parsed.query(TemporalQueries.localDate()))
                || Objects.nonNull(parsed.query(TemporalQueries.offset()))) {
            throw new DateTimeException("Expected a time without date or offset");
        }
        return LocalTime.from(parsed);
    }

    @Nullable
    private static <T> T parseFirst(String value, String targetType, Function<TemporalAccessor, T> convert,
                                    DateTimeFormatter[] defaults, DateTimeFormatter... additional) {
        if (Objects.isNull(value) || value.isEmpty()) {
            return null;
        }
        Objects.requireNonNull(additional, "additional");
        DateTimeException lastFailure = null;
        for (DateTimeFormatter format : additional) {
            try {
                return convert.apply(Objects.requireNonNull(format, "format").parse(value));
            } catch (DateTimeException exception) {
                lastFailure = exception;
            }
        }
        for (DateTimeFormatter format : defaults) {
            try {
                return convert.apply(format.parse(value));
            } catch (DateTimeException exception) {
                lastFailure = exception;
            }
        }
        throw new DateTimeParseException("Cannot parse input as " + targetType
                + "; provide a matching DateTimeFormatter for custom formats", value, 0, lastFailure);
    }

    private static DateTimeFormatter strict(String pattern) {
        return formatter(pattern.replace("yyyy", "uuuu")).withResolverStyle(ResolverStyle.STRICT);
    }

    private static DateTimeFormatter dateTimeWithFraction(String pattern) {
        return new DateTimeFormatterBuilder().appendPattern(pattern)
                .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
                .toFormatter().withResolverStyle(ResolverStyle.STRICT);
    }
}
