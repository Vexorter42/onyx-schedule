package com.vexorter.onyx.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vexorter.onyx.AppContainer
import com.vexorter.onyx.domain.NotificationSettings
import com.vexorter.onyx.util.BranchTimeZones
import com.vexorter.onyx.util.WeekUtils
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планировщик локальных будильников.
 *
 * Все будильники перепланируются целиком при каждом поводе (открытие приложения,
 * срабатывание любого будильника, обновление недели, перезагрузка) — так проще
 * рассуждать о состоянии, чем поддерживать инкрементальные правки.
 */
class AlarmScheduler(
    private val context: Context,
    private val container: AppContainer,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        cancelAll()

        val settings = container.prefs.notifications.first()
        val profile = container.prefs.profile.first()
        if (!settings.anyEnabled || !profile.isComplete) return

        // Пары заданы во времени филиала — в нём же считаем, когда звонить.
        val zone = BranchTimeZones.zoneOf(profile.branchGuid)
        val now = LocalDateTime.now(zone)

        if (settings.morningSummary) {
            scheduleDaily(REQUEST_MORNING, AlarmReceiver.ACTION_MORNING, settings.morningAtMinutes, now, zone)
        }
        if (settings.eveningPreview) {
            scheduleDaily(REQUEST_EVENING, AlarmReceiver.ACTION_EVENING, settings.eveningAtMinutes, now, zone)
        }
        if (settings.beforeLesson) {
            scheduleLessonReminders(settings, profile.ownerGuid, now, zone)
        }

        // Подстраховка: раз в сутки после полуночи пересобираем план на новый день.
        scheduleDaily(REQUEST_REPLAN, AlarmReceiver.ACTION_REPLAN, 5, now, zone)
    }

    private suspend fun scheduleLessonReminders(
        settings: NotificationSettings,
        ownerGuid: String,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val dates = listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))
        var slot = 0

        for (date in dates) {
            val lessons = container.scheduleRepository.lessonsOn(ownerGuid, date)
            for (lesson in lessons) {
                if (slot >= MAX_LESSON_ALARMS) return
                val start = WeekUtils.parseTime(lesson.timeStart) ?: continue
                val fireAt = LocalDateTime.of(date, start)
                    .minusMinutes(settings.beforeLessonMinutes.toLong())
                if (fireAt.isAfter(now)) {
                    schedule(
                        requestCode = REQUEST_LESSON_BASE + slot,
                        intent = AlarmReceiver.lessonIntent(
                            context = context,
                            date = date,
                            orderNum = lesson.orderNum,
                            minutesBefore = settings.beforeLessonMinutes,
                        ),
                        at = fireAt,
                        zone = zone,
                        exact = true,
                    )
                    slot++
                }
            }
        }
    }

    private fun scheduleDaily(
        requestCode: Int,
        action: String,
        atMinutes: Int,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val today = LocalDate.now(zone).atStartOfDay().plusMinutes(atMinutes.toLong())
        val fireAt = if (today.isAfter(now)) today else today.plusDays(1)
        schedule(
            requestCode = requestCode,
            intent = AlarmReceiver.simpleIntent(context, action),
            at = fireAt,
            zone = zone,
            exact = false,
        )
    }

    private fun schedule(
        requestCode: Int,
        intent: Intent,
        at: LocalDateTime,
        zone: ZoneId,
        exact: Boolean,
    ) {
        val manager = alarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = at.atZone(zone).toInstant().toEpochMilli()

        // Точные будильники нужны только для «за N минут до пары»; если система их
        // не разрешила, откатываемся на неточные — лучше сдвинутое напоминание, чем никакого.
        val useExact = exact && canScheduleExact()
        runCatching {
            if (useExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun cancelAll() {
        val manager = alarmManager ?: return
        // PendingIntent сопоставляется по action (extras при сравнении игнорируются),
        // поэтому отменять надо интентом с тем же action, каким будильник ставился.
        val targets = buildList {
            add(REQUEST_MORNING to AlarmReceiver.ACTION_MORNING)
            add(REQUEST_EVENING to AlarmReceiver.ACTION_EVENING)
            add(REQUEST_REPLAN to AlarmReceiver.ACTION_REPLAN)
            repeat(MAX_LESSON_ALARMS) { index ->
                add(REQUEST_LESSON_BASE + index to AlarmReceiver.ACTION_LESSON)
            }
        }

        targets.forEach { (code, action) ->
            val existing = PendingIntent.getBroadcast(
                context,
                code,
                AlarmReceiver.simpleIntent(context, action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (existing != null) {
                manager.cancel(existing)
                existing.cancel()
            }
        }
    }

    companion object {
        private const val REQUEST_MORNING = 1
        private const val REQUEST_EVENING = 2
        private const val REQUEST_REPLAN = 3
        private const val REQUEST_LESSON_BASE = 100
        private const val MAX_LESSON_ALARMS = 24
    }
}
