package com.vexorter.onyx.domain

import java.time.LocalTime

data class NotificationSettings(
    val morningSummary: Boolean = true,
    /** Минуты от полуночи — так их проще хранить в DataStore. */
    val morningAtMinutes: Int = 7 * 60 + 30,
    val beforeLesson: Boolean = true,
    val beforeLessonMinutes: Int = 15,
    val scheduleChanges: Boolean = true,
    val eveningPreview: Boolean = true,
    val eveningAtMinutes: Int = 20 * 60,
) {
    val morningAt: LocalTime get() = LocalTime.of(morningAtMinutes / 60, morningAtMinutes % 60)
    val eveningAt: LocalTime get() = LocalTime.of(eveningAtMinutes / 60, eveningAtMinutes % 60)

    val anyEnabled: Boolean
        get() = morningSummary || beforeLesson || scheduleChanges || eveningPreview

    /** Нужны ли точные будильники: сводки терпят сдвиг, напоминание за 15 минут — нет. */
    val needsExactAlarms: Boolean get() = beforeLesson
}

/** Одно изменение в расписании, найденное фоновой проверкой. */
data class ScheduleChange(
    val date: java.time.LocalDate,
    val text: String,
)
