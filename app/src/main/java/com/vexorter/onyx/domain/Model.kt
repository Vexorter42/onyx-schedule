package com.vexorter.onyx.domain

import java.time.LocalDate

data class Branch(val guid: String, val name: String, val code: String)

data class Year(val guid: String, val name: String, val code: String)

/** Группа внутри категории (например «МДК(МДК)»). */
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

/** Профиль пользователя — то, что выбирается один раз при первом запуске. */
data class Profile(
    val branchGuid: String,
    val branchName: String,
    val yearGuid: String,
    val yearName: String,
    val groupGuid: String,
    val groupName: String,
) {
    companion object {
        val EMPTY = Profile("", "", "", "", "", "")
    }

    val isComplete: Boolean
        get() = branchGuid.isNotEmpty() && yearGuid.isNotEmpty() && groupGuid.isNotEmpty()
}
