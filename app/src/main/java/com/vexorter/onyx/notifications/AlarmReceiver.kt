package com.vexorter.onyx.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vexorter.onyx.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handle(appContext, action, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String, intent: Intent) {
        val container = context.appContainer
        val notifier = Notifier(context)
        notifier.ensureChannels()

        val profile = container.prefs.profile.first()
        val settings = container.prefs.notifications.first()

        if (profile.isComplete) {
            when (action) {
                ACTION_MORNING -> if (settings.morningSummary) {
                    val today = LocalDate.now()
                    notifier.showDaySummary(
                        date = today,
                        lessons = container.scheduleRepository.lessonsOn(profile.groupGuid, today),
                        isTomorrow = false,
                    )
                }

                ACTION_EVENING -> if (settings.eveningPreview) {
                    val tomorrow = LocalDate.now().plusDays(1)
                    notifier.showDaySummary(
                        date = tomorrow,
                        lessons = container.scheduleRepository.lessonsOn(profile.groupGuid, tomorrow),
                        isTomorrow = true,
                    )
                }

                ACTION_LESSON -> if (settings.beforeLesson) {
                    val date = intent.getStringExtra(EXTRA_DATE)
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val orderNum = intent.getIntExtra(EXTRA_ORDER, -1)
                    val minutes = intent.getIntExtra(EXTRA_MINUTES, settings.beforeLessonMinutes)
                    if (date != null) {
                        container.scheduleRepository.lessonsOn(profile.groupGuid, date)
                            .firstOrNull { it.orderNum == orderNum }
                            ?.let { notifier.showLessonReminder(it, minutes) }
                    }
                }
            }
        }

        // Любое срабатывание — повод пересобрать план на ближайшие сутки.
        AlarmScheduler(context, container).rescheduleAll()
    }

    companion object {
        const val ACTION_MORNING = "com.vexorter.onyx.action.MORNING"
        const val ACTION_EVENING = "com.vexorter.onyx.action.EVENING"
        const val ACTION_LESSON = "com.vexorter.onyx.action.LESSON"
        const val ACTION_REPLAN = "com.vexorter.onyx.action.REPLAN"

        private const val EXTRA_DATE = "date"
        private const val EXTRA_ORDER = "order"
        private const val EXTRA_MINUTES = "minutes"

        fun simpleIntent(context: Context, action: String): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(action)

        fun lessonIntent(
            context: Context,
            date: LocalDate,
            orderNum: Int,
            minutesBefore: Int,
        ): Intent = simpleIntent(context, ACTION_LESSON)
            .putExtra(EXTRA_DATE, date.toString())
            .putExtra(EXTRA_ORDER, orderNum)
            .putExtra(EXTRA_MINUTES, minutesBefore)
    }
}
