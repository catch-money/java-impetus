package io.github.jockerCN.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeUtilsTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void dateDefaultsOnlyAcceptDateFormats() {
        LocalDate expected = LocalDate.of(2028, 2, 29);
        assertEquals(expected, DateTimeUtils.stringToLocalDate("2028-02-29"));
        assertEquals(expected, DateTimeUtils.stringToLocalDate("20280229"));
        assertEquals(expected, DateTimeUtils.stringToLocalDate("2028/2/29"));
        assertEquals(expected, DateTimeUtils.stringToLocalDate("2028.2.29"));
        assertEquals(expected, DateTimeUtils.stringToLocalDate("2028年2月29日"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDate("2028-02-29 10:15:30"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDate("2026-02-30"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDate("2026-02"));
    }

    @Test
    void localDateTimeDefaultsOnlyAcceptDateTimeFormats() {
        LocalDateTime expected = LocalDateTime.of(2026, 9, 30, 10, 15, 30);
        assertEquals(expected, DateTimeUtils.stringToLocalDateTime("2026-09-30 10:15:30"));
        assertEquals(expected, DateTimeUtils.stringToLocalDateTime("2026-09-30T10:15:30"));
        assertEquals(expected, DateTimeUtils.stringToLocalDateTime("20260930101530"));
        assertEquals(expected, DateTimeUtils.stringToLocalDateTime("2026/9/30 10:15:30"));
        assertEquals(expected, DateTimeUtils.stringToLocalDateTime("2026.9.30 10:15:30"));
        assertEquals(expected, DateTimeUtils.stringToLocalDateTime("2026年9月30日 10:15:30"));
        assertEquals(expected.withSecond(0), DateTimeUtils.stringToLocalDateTime("2026-09-30 10:15"));
        assertEquals(expected.withNano(100_000_000),
                DateTimeUtils.stringToLocalDateTime("2026-09-30 10:15:30.1"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDateTime("2026-09-30"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDateTime("10:15:30"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDateTime("2026-09-30T10:15:30Z"));
    }

    @Test
    void localTimeDefaultsOnlyAcceptTimeFormats() {
        LocalTime expected = LocalTime.of(10, 15, 30);
        assertEquals(expected, DateTimeUtils.stringToLocalTime("10:15:30"));
        assertEquals(expected, DateTimeUtils.stringToLocalTime("101530"));
        assertEquals(expected, DateTimeUtils.stringToLocalTime("10时15分30秒"));
        assertEquals(LocalTime.of(10, 15), DateTimeUtils.stringToLocalTime("10:15"));
        assertEquals(LocalTime.of(10, 15), DateTimeUtils.stringToLocalTime("10时15分"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalTime("2026-09-30 10:15:30"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalTime("2026-09-30"));
    }

    @Test
    void offsetDateTimesHaveTheirOwnParsingAndZoneConversion() {
        OffsetDateTime offset = DateTimeUtils.parseOffsetDateTime("2026-09-30T10:15:30Z");
        assertEquals(ZoneOffset.UTC, offset.getOffset());
        assertEquals(ZoneOffset.ofHours(8),
                DateTimeUtils.parseOffsetDateTime("2026-09-30 18:15:30+08:00").getOffset());
        assertEquals(ZoneOffset.UTC,
                DateTimeUtils.parseOffsetDateTime("Wed, 30 Sep 2026 10:15:30 GMT").getOffset());
        assertEquals(LocalDateTime.of(2026, 9, 30, 18, 15, 30),
                DateTimeUtils.stringToLocalDateTime("2026-09-30T10:15:30Z", SHANGHAI));
        assertEquals(LocalDateTime.of(2026, 9, 30, 10, 15, 30),
                DateTimeUtils.stringToLocalDateTime("2026-09-30T12:15:30+02:00", ZoneOffset.UTC));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.parseOffsetDateTime("2026-09-30T10:15:30"));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDateTime("2026-09-30 10:15:30", SHANGHAI));
    }

    @Test
    void customFormattersExtendOnlyTheirDeclaredTarget() {
        DateTimeFormatter dayFirst = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        DateTimeFormatter englishDateTime = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH);
        DateTimeFormatter twelveHourTime = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
        assertEquals(LocalDate.of(2026, 9, 30),
                DateTimeUtils.parseLocalDate("30/09/2026", dayFirst));
        assertEquals(LocalDateTime.of(2026, 9, 30, 10, 15),
                DateTimeUtils.parseLocalDateTime("30 Sep 2026 10:15", englishDateTime));
        assertEquals(LocalTime.of(22, 15), DateTimeUtils.parseLocalTime("10:15 PM", twelveHourTime));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.parseLocalDateTime("30/09/2026", dayFirst));
        assertThrows(DateTimeParseException.class,
                () -> DateTimeUtils.stringToLocalDate("30/09/2026"));
    }

    @Test
    void incompleteDatesAndEmptyInputsHaveExplicitContracts() {
        assertEquals(YearMonth.of(2026, 9), DateTimeUtils.stringToYearMonth("2026-09"));
        assertEquals(MonthDay.of(2, 29), DateTimeUtils.stringToMonthDay("02-29"));
        assertNull(DateTimeUtils.stringToLocalDate(null));
        assertNull(DateTimeUtils.stringToLocalTime(""));
        assertNull(DateTimeUtils.parseOffsetDateTime(null));
        assertThrows(DateTimeParseException.class, () -> DateTimeUtils.stringToLocalDate(" "));
        assertThrows(DateTimeParseException.class, () -> DateTimeUtils.stringToMonthDay("02-30"));
    }

    @Test
    void formatConstantsShareTheSameSourceOfTruth() {
        assertEquals("2026-09-30", LocalDate.of(2026, 9, 30).format(DateTimeUtils.FORMATTER_YMD));
        assertEquals("2026-09-30 10:15:30", LocalDateTime.of(2026, 9, 30, 10, 15, 30)
                .format(DateTimeUtils.FORMATTER_YMD_HMS));
        assertEquals("20260930101530", LocalDateTime.of(2026, 9, 30, 10, 15, 30)
                .format(DateTimeUtils.FORMATTER_YMD_HMS_COMPACT));
    }

    @Test
    void addsAndSubtractsCalendarAndClockUnits() {
        LocalDate january31 = LocalDate.of(2024, 1, 31);
        assertEquals(LocalDate.of(2024, 2, 29), DateTimeUtils.addMonths(january31, 1));
        assertEquals(LocalDate.of(2024, 1, 30), DateTimeUtils.addDays(january31, -1));
        assertEquals(LocalDate.of(2025, 1, 31), DateTimeUtils.addYears(january31, 1));
        LocalDateTime value = LocalDateTime.of(2026, 9, 30, 10, 15, 30);
        assertEquals(value.plusDays(2), DateTimeUtils.addDays(value, 2));
        assertEquals(value.plusMonths(2), DateTimeUtils.addMonths(value, 2));
        assertEquals(value.plusYears(2), DateTimeUtils.addYears(value, 2));
        assertEquals(value.minusHours(1), DateTimeUtils.addHours(value, -1));
        assertEquals(value.plusMinutes(5), DateTimeUtils.addMinutes(value, 5));
        assertEquals(value.plusSeconds(10), DateTimeUtils.addSeconds(value, 10));
        assertEquals(LocalTime.of(0, 15, 30), DateTimeUtils.addHours(LocalTime.of(23, 15, 30), 1));
    }

    @Test
    void differencesCountWholeUnitsFromStartToEnd() {
        LocalDate start = LocalDate.of(2024, 1, 15);
        LocalDate end = LocalDate.of(2026, 3, 15);
        assertEquals(790, DateTimeUtils.daysBetween(start, end));
        assertEquals(26, DateTimeUtils.monthsBetween(start, end));
        assertEquals(2, DateTimeUtils.yearsBetween(start, end));
        assertEquals(-26, DateTimeUtils.monthsBetween(end, start));
        LocalDateTime morning = LocalDateTime.of(2026, 9, 30, 10, 15, 30);
        LocalDateTime later = morning.plusDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);
        assertEquals(1, DateTimeUtils.daysBetween(morning, later));
        assertEquals(26, DateTimeUtils.hoursBetween(morning, later));
        assertEquals(1_563, DateTimeUtils.minutesBetween(morning, later));
        assertEquals(93_784, DateTimeUtils.secondsBetween(morning, later));
        assertEquals(-22, DateTimeUtils.hoursBetween(LocalTime.of(23, 0), LocalTime.of(1, 0)));
    }

    @Test
    void epochMillisAndUtcConversionsRequireOrChooseAZoneExplicitly() {
        LocalDateTime shanghaiEpoch = LocalDateTime.of(1970, 1, 1, 8, 0);
        assertEquals(0, DateTimeUtils.toEpochMillis(shanghaiEpoch, SHANGHAI));
        assertEquals(0, DateTimeUtils.toEpochMillisUtc(LocalDateTime.of(1970, 1, 1, 0, 0)));
        assertEquals(shanghaiEpoch, DateTimeUtils.fromEpochMillis(0, SHANGHAI));
        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0), DateTimeUtils.fromEpochMillisUtc(0));
        assertEquals(LocalDate.of(1970, 1, 1), DateTimeUtils.localDateFromEpochMillis(0, SHANGHAI));
        assertEquals(LocalTime.of(8, 0), DateTimeUtils.localTimeFromEpochMillis(0, SHANGHAI));
        assertEquals(0, DateTimeUtils.toEpochMillis(LocalDate.of(1970, 1, 1),
                LocalTime.of(8, 0), SHANGHAI));
        assertEquals(0, DateTimeUtils.toEpochMillis(LocalDate.of(1970, 1, 1), ZoneOffset.UTC));
        assertEquals(0, DateTimeUtils.toEpochMillisUtc(LocalDate.of(1970, 1, 1)));
        assertEquals(0, DateTimeUtils.toEpochMillisUtc(LocalDate.of(1970, 1, 1), LocalTime.MIDNIGHT));
        assertEquals(LocalDate.of(1970, 1, 1), DateTimeUtils.localDateFromEpochMillisUtc(0));
        assertEquals(LocalTime.MIDNIGHT, DateTimeUtils.localTimeFromEpochMillisUtc(0));
        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0), DateTimeUtils.toUtc(shanghaiEpoch, SHANGHAI));
        assertEquals(shanghaiEpoch,
                DateTimeUtils.fromUtc(LocalDateTime.of(1970, 1, 1, 0, 0), SHANGHAI));
        assertEquals(shanghaiEpoch, DateTimeUtils.convertZone(LocalDateTime.of(1970, 1, 1, 0, 0),
                ZoneOffset.UTC, SHANGHAI));
        assertEquals(DateTimeUtils.toEpochMillis(shanghaiEpoch, ZoneId.systemDefault()),
                DateTimeUtils.toEpochMillis(shanghaiEpoch));
        assertEquals(DateTimeUtils.toEpochMillis(LocalDate.of(1970, 1, 1), ZoneId.systemDefault()),
                DateTimeUtils.toEpochMillis(LocalDate.of(1970, 1, 1)));
        assertEquals(DateTimeUtils.toEpochMillis(LocalDate.of(1970, 1, 1), LocalTime.MIDNIGHT,
                ZoneId.systemDefault()), DateTimeUtils.toEpochMillis(LocalDate.of(1970, 1, 1), LocalTime.MIDNIGHT));
        assertEquals(DateTimeUtils.fromEpochMillis(0, ZoneId.systemDefault()),
                DateTimeUtils.fromEpochMillis(0));
        assertEquals(DateTimeUtils.localDateFromEpochMillis(0, ZoneId.systemDefault()),
                DateTimeUtils.localDateFromEpochMillis(0));
        assertEquals(DateTimeUtils.localTimeFromEpochMillis(0, ZoneId.systemDefault()),
                DateTimeUtils.localTimeFromEpochMillis(0));
    }
}
