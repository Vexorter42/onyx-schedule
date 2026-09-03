package com.vexorter.onyx.domain

import java.time.LocalDate

data class Branch(val guid: String, val name: String, val code: String)

data class Year(val guid: String, val name: String, val code: String)

/** Группа внутри категории (например «МДК(МДК)»). */
data class Teacher(
    val guid: String,
    val name: String,
    val position: String,
)

data class Group(
    val guid: String,
    val name: String,
    val category: String,
    val owner: String,
)

data class Lesson(
    val date: LocalDate,
    val orderNum: Int,
    val timeStart: String,
    val timeEnd: String,
    val discipline: String,
    val type: String,
    val employee: String,
    val classroom: String,
    val subGroup: String,
    val group: String,
)

data class DaySchedule(val date: LocalDate, val lessons: List<Lesson>)

data class WeekSchedule(
    val weekStart: LocalDate,
    val days: List<DaySchedule>,
    /** Момент последней успешной загрузки этой недели с сервера, epoch millis. */
    val updatedAt: Long,
) {
    val isEmpty: Boolean get() = days.all { it.lessons.isEmpty() }
    val lessonCount: Int get() = days.sumOf { it.lessons.size }
}

/** Незавершённый выбор в мастере: филиал и год до того, как выбран владелец. */
data class SetupDraft(
    val branchGuid: String = "",
    val branchName: String = "",
    val yearGuid: String = "",
    val yearName: String = "",
)

/** Чьё расписание смотрим: группы или преподавателя. */
enum class ScheduleKind { GROUP, EMPLOYEE }

/**
 * Профиль — то, что выбирается один раз и дальше подставляется само.
 * Их может быть несколько; активный лежит в настройках, остальные — в базе.
 */
data class Profile(
    val kind: ScheduleKind = ScheduleKind.GROUP,
    val branchGuid: String = "",
    val branchName: String = "",
    val yearGuid: String = "",
    val yearName: String = "",
    /** GUID группы либо преподавателя. */
    val ownerGuid: String = "",
    val ownerName: String = "",
) {
    companion object {
        val EMPTY = Profile()
    }

    // У преподавателя года набора нет, поэтому его наличие не требуем.
    val isComplete: Boolean
        get() = branchGuid.isNotEmpty() && ownerGuid.isNotEmpty() &&
            (kind == ScheduleKind.EMPLOYEE || yearGuid.isNotEmpty())

    val isEmployee: Boolean get() = kind == ScheduleKind.EMPLOYEE
}
