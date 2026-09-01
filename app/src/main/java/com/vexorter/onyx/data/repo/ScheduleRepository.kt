package com.vexorter.onyx.data.repo

import com.vexorter.onyx.data.local.LessonEntity
import com.vexorter.onyx.data.local.ScheduleDao
import com.vexorter.onyx.data.local.WeekMetaEntity
import com.vexorter.onyx.data.remote.ScheduleApi
import com.vexorter.onyx.domain.DaySchedule
import com.vexorter.onyx.domain.Lesson
import com.vexorter.onyx.domain.ScheduleChange
import com.vexorter.onyx.domain.SyncResult
import com.vexorter.onyx.domain.WeekSchedule
import com.vexorter.onyx.util.WeekUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours

/**
 * Расписание. Room — единственный источник правды для UI: экран всегда рисует то,
 * что лежит в базе, а сеть лишь дописывает туда свежие данные.
 */
class ScheduleRepository(
    private val api: ScheduleApi,
    private val dao: ScheduleDao,
) {

    /** Неделя считается свежей столько времени; дальше при открытии тихо перезапрашиваем. */
    private val freshFor = 3.hours.inWholeMilliseconds

    /** Глубина хранения кэша: месяц назад и два месяца вперёд. */
    private val keepWeeksBack = 4L
    private val keepWeeksForward = 8L

    private fun weekKey(groupGuid: String, weekStart: LocalDate) = "$groupGuid|$weekStart"

    /**
     * @return null, если неделя ещё ни разу не выкачивалась (кэша нет),
     *         иначе — расписание из базы, пусть даже пустое.
     */
    fun observeWeek(groupGuid: String, weekStart: LocalDate): Flow<WeekSchedule?> {
        val key = weekKey(groupGuid, weekStart)
        return combine(
            dao.observeWeekMeta(key),
            dao.observeWeek(key),
        ) { meta, lessons ->
            if (meta == null) {
                null
            } else {
                buildWeek(weekStart, lessons, meta.updatedAt)
            }
        }
    }

    suspend fun isFresh(groupGuid: String, weekStart: LocalDate): Boolean {
        val meta = dao.weekMeta(weekKey(groupGuid, weekStart)) ?: return false
        return System.currentTimeMillis() - meta.updatedAt < freshFor
    }

    suspend fun isCached(groupGuid: String, weekStart: LocalDate): Boolean =
        dao.weekMeta(weekKey(groupGuid, weekStart)) != null

    suspend fun refreshWeek(
        branchGuid: String,
        groupGuid: String,
        weekStart: LocalDate,
    ): SyncResult = runSync {
        val key = weekKey(groupGuid, weekStart)
        val response = api.getGroupSchedule(
            branchGuid = branchGuid,
            groupGuid = groupGuid,
            date = WeekUtils.toApiDate(weekStart),
        )

        val lessons = response.schedule.flatMap { (dayTitle, dayLessons) ->
            val fallbackDate = dayTitle.substringBefore(" - ").trim()
            dayLessons.mapNotNull { dto ->
                val date = WeekUtils.parseLessonDate(dto.date.ifBlank { fallbackDate })
                    ?: return@mapNotNull null
                LessonEntity(
                    weekKey = key,
                    ownerGuid = groupGuid,
                    weekStart = weekStart.toString(),
                    date = date.toString(),
                    orderNum = dto.time.trim().toIntOrNull() ?: 0,
                    timeStart = dto.timeStart.trim(),
                    timeEnd = dto.timeEnd.trim(),
                    discipline = dto.discipline.trim(),
                    type = dto.type.trim(),
                    employee = dto.employee.trim(),
                    classroom = dto.classroom.trim(),
                    subGroup = dto.subGroup.trim(),
                )
            }
        }

        dao.replaceWeek(
            meta = WeekMetaEntity(
                weekKey = key,
                ownerGuid = groupGuid,
                weekStart = weekStart.toString(),
                updatedAt = System.currentTimeMillis(),
            ),
            lessons = lessons,
        )

        pruneOldWeeks()
    }

    /** Тихо подтягиваем соседнюю неделю, чтобы листание вперёд было мгновенным. */
    suspend fun prefetchWeek(branchGuid: String, groupGuid: String, weekStart: LocalDate) {
        if (isFresh(groupGuid, weekStart)) return
        refreshWeek(branchGuid, groupGuid, weekStart)
    }

    /** Пары конкретного дня — нужны планировщику уведомлений. */
    suspend fun lessonsOn(groupGuid: String, date: LocalDate): List<Lesson> =
        dao.lessonsOn(groupGuid, date.toString()).mapNotNull { it.toDomain() }

    /**
     * Обновляет неделю и попутно сообщает, что в ней изменилось по сравнению
     * с тем, что уже лежало в базе. Пустой список — либо изменений нет,
     * либо неделя выкачивается впервые (тогда «изменением» считать нечего).
     */
    suspend fun refreshWeekWithChanges(
        branchGuid: String,
        groupGuid: String,
        weekStart: LocalDate,
    ): Pair<SyncResult, List<ScheduleChange>> {
        val key = weekKey(groupGuid, weekStart)
        val hadCache = dao.weekMeta(key) != null
        val before = if (hadCache) dao.weekLessons(key) else emptyList()

        val result = refreshWeek(branchGuid, groupGuid, weekStart)
        if (result !is SyncResult.Success || !hadCache) return result to emptyList()

        return result to diff(before, dao.weekLessons(key))
    }

    private fun diff(before: List<LessonEntity>, after: List<LessonEntity>): List<ScheduleChange> {
        // Пару опознаём по дате и номеру: дисциплина и аудитория как раз и меняются.
        fun key(l: LessonEntity) = "${l.date}#${l.orderNum}"
        val old = before.associateBy(::key)
        val new = after.associateBy(::key)
        val changes = mutableListOf<ScheduleChange>()

        (old.keys + new.keys).sorted().forEach { k ->
            val a = old[k]
            val b = new[k]
            val date = runCatching { LocalDate.parse((b ?: a)!!.date) }.getOrNull() ?: return@forEach
            when {
                a == null && b != null ->
                    changes += ScheduleChange(date, "добавилась пара «${b.discipline}» в ${b.timeStart}")

                a != null && b == null ->
                    changes += ScheduleChange(date, "отменена пара «${a.discipline}» в ${a.timeStart}")

                a != null && b != null -> {
                    val details = buildList {
                        if (a.discipline != b.discipline) add("теперь «${b.discipline}»")
                        if (a.timeStart != b.timeStart) add("начало в ${b.timeStart}")
                        if (a.classroom != b.classroom) add("аудитория ${b.classroom}")
                        if (a.employee != b.employee) add("преподаватель ${b.employee}")
                        if (a.type != b.type) add("вид: ${b.type}")
                    }
                    if (details.isNotEmpty()) {
                        changes += ScheduleChange(
                            date,
                            "${a.timeStart} «${a.discipline}»: ${details.joinToString(", ")}",
                        )
                    }
                }
            }
        }
        return changes
    }

    suspend fun clearCache() = dao.clearAllSchedule()

    private suspend fun pruneOldWeeks() {
        val current = WeekUtils.currentWeekStart()
        dao.prune(
            fromWeek = current.minusWeeks(keepWeeksBack).toString(),
            toWeek = current.plusWeeks(keepWeeksForward).toString(),
        )
    }

    private fun buildWeek(
        weekStart: LocalDate,
        lessons: List<LessonEntity>,
        updatedAt: Long,
    ): WeekSchedule {
        val byDate = lessons.mapNotNull { it.toDomain() }.groupBy { it.date }
        val canonical = (0L..6L).map { weekStart.plusDays(it) }
        // на всякий случай не теряем пары, если сервер вернул дату за пределами недели
        val extra = byDate.keys.filterNot { it in canonical }
        val days = (canonical + extra).sorted().map { date ->
            DaySchedule(
                date = date,
                lessons = byDate[date].orEmpty().sortedWith(
                    compareBy({ it.orderNum }, { it.timeStart })
                ),
            )
        }
        return WeekSchedule(weekStart = weekStart, days = days, updatedAt = updatedAt)
    }
}
