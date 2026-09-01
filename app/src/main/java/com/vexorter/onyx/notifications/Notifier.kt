package com.vexorter.onyx.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vexorter.onyx.R
import com.vexorter.onyx.domain.Lesson
import com.vexorter.onyx.domain.ScheduleChange
import com.vexorter.onyx.ui.MainActivity
import com.vexorter.onyx.util.WeekUtils
import java.time.LocalDate

class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(
                CHANNEL_LESSON,
                "Напоминания о парах",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Приходят незадолго до начала пары" },

            NotificationChannel(
                CHANNEL_SUMMARY,
                "Сводки на день",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Утренняя сводка и вечерний обзор на завтра" },

            NotificationChannel(
                CHANNEL_CHANGES,
                "Изменения в расписании",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Если пару перенесли, отменили или сменили аудиторию" },
        ).forEach(system::createNotificationChannel)
    }

    fun canNotify(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    fun showLessonReminder(lesson: Lesson, minutesBefore: Int) {
        val place = listOfNotNull(
            lesson.classroom.takeIf { it.isNotBlank() }?.let { "ауд. $it" },
            lesson.employee.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        notify(
            id = ID_LESSON_BASE + lesson.orderNum,
            channel = CHANNEL_LESSON,
            title = "Через $minutesBefore мин — ${lesson.discipline}",
            text = listOfNotNull(
                "${lesson.timeStart} — ${lesson.timeEnd}",
                lesson.type.takeIf { it.isNotBlank() },
                place.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
        )
    }

    fun showDaySummary(date: LocalDate, lessons: List<Lesson>, isTomorrow: Boolean) {
        val dayWord = if (isTomorrow) "Завтра" else "Сегодня"

        if (lessons.isEmpty()) {
            notify(
                id = if (isTomorrow) ID_EVENING else ID_MORNING,
                channel = CHANNEL_SUMMARY,
                title = "$dayWord пар нет",
                text = "${WeekUtils.weekDayName(date)}, ${WeekUtils.dayAndMonth(date)}",
            )
            return
        }

        val first = lessons.first()
        val title = "$dayWord ${lessons.size} ${pluralPairs(lessons.size)}, " +
            "первая в ${first.timeStart}"
        val body = lessons.joinToString("\n") { lesson ->
            val room = lesson.classroom.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            "${lesson.timeStart} — ${lesson.timeEnd}  ${lesson.discipline}$room"
        }

        notify(
            id = if (isTomorrow) ID_EVENING else ID_MORNING,
            channel = CHANNEL_SUMMARY,
            title = title,
            text = body,
            expandable = true,
        )
    }

    fun showChanges(changes: List<ScheduleChange>) {
        if (changes.isEmpty()) return
        val body = changes.joinToString("\n") { change ->
            "${WeekUtils.weekDayName(change.date)}: ${change.text}"
        }
        notify(
            id = ID_CHANGES,
            channel = CHANNEL_CHANGES,
            title = if (changes.size == 1) {
                "Расписание изменилось"
            } else {
                "В расписании ${changes.size} изменения"
            },
            text = body,
            expandable = true,
        )
    }

    private fun notify(
        id: Int,
        channel: String,
        title: String,
        text: String,
        expandable: Boolean = false,
    ) {
        if (!canNotify()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.substringBefore("\n"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (expandable) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        runCatching { manager.notify(id, builder.build()) }
    }

    private fun pluralPairs(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "пар"
            mod10 == 1 -> "пара"
            mod10 in 2..4 -> "пары"
            else -> "пар"
        }
    }

    companion object {
        const val CHANNEL_LESSON = "lessons"
        const val CHANNEL_SUMMARY = "summary"
        const val CHANNEL_CHANGES = "changes"

        private const val ID_MORNING = 1
        private const val ID_EVENING = 2
        private const val ID_CHANGES = 3
        private const val ID_LESSON_BASE = 100
    }
}
