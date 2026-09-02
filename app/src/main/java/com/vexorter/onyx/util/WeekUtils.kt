package com.vexorter.onyx.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Вся неделя в приложении определяется её понедельником (ISO-8601).
 * API принимает дату в формате yyyy.MM.dd и возвращает неделю, в которую эта дата попадает.
 */
object WeekUtils {

    private val API_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val LESSON_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val MONTHS_GENITIVE = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    private val WEEKDAYS = arrayOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    private val WEEKDAYS_SHORT = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun currentWeekStart(): LocalDate = mondayOf(LocalDate.now())

    fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)

    fun currentWeekStart(zone: ZoneId): LocalDate = mondayOf(LocalDate.now(zone))

    fun toApiDate(date: LocalDate): String = date.format(API_DATE)

    fun parseLessonDate(raw: String): LocalDate? = runCatching {
        LocalDate.parse(raw.trim(), LESSON_DATE)
    }.getOrNull()

    fun weekDayName(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    fun weekDayShort(date: LocalDate): String = WEEKDAYS_SHORT[date.dayOfWeek.value - 1]

    /** «1 сентября» */
    fun dayAndMonth(date: LocalDate): String =
        "${date.dayOfMonth} ${MONTHS_GENITIVE[date.monthValue - 1]}"

    /** «1 — 7 сентября» либо «29 сентября — 5 октября» */
    fun weekRangeTitle(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        return if (weekStart.month == end.month) {
            "${weekStart.dayOfMonth} — ${end.dayOfMonth} ${MONTHS_GENITIVE[end.monthValue - 1]}"
        } else {
            "${dayAndMonth(weekStart)} — ${dayAndMonth(end)}"
        }
    }

    fun isCurrentWeek(weekStart: LocalDate, zone: ZoneId): Boolean =
        weekStart == currentWeekStart(zone)

    fun relativeWeekLabel(weekStart: LocalDate, zone: ZoneId): String? {
        val current = currentWeekStart(zone)
        return when (weekStart) {
            current -> "Текущая неделя"
            current.plusWeeks(1) -> "Следующая неделя"
            current.minusWeeks(1) -> "Прошлая неделя"
            else -> null
        }
    }

    /** «сегодня в 14:32», «вчера в 09:10», «29 августа в 18:05» */
    fun formatUpdatedAt(epochMillis: Long): String {
        if (epochMillis <= 0L) return "ещё не загружалось"
        val moment = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        val today = LocalDate.now()
        val time = "%02d:%02d".format(moment.hour, moment.minute)
        return when (moment.toLocalDate()) {
            today -> "сегодня в $time"
            today.minusDays(1) -> "вчера в $time"
            else -> "${dayAndMonth(moment.toLocalDate())} в $time"
        }
    }

    /** «14:00» -> LocalTime; null, если сервер прислал мусор. */
    fun parseTime(raw: String): LocalTime? = runCatching {
        val parts = raw.trim().split(":")
        LocalTime.of(parts[0].toInt(), parts.getOrElse(1) { "0" }.toInt())
    }.getOrNull()
}
