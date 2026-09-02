package com.vexorter.onyx.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vexorter.onyx.appContainer
import com.vexorter.onyx.domain.ScheduleChange
import com.vexorter.onyx.util.WeekUtils
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Раз в час перекачивает текущую и следующую неделю и сравнивает их с тем,
 * что уже лежит в базе. Держит кэш свежим и показывает, что именно изменилось,
 * если расписание правили.
 *
 * Точность интервала обеспечивает система: в глубоком сне запуск может сдвинуться.
 */
class ScheduleCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val container = context.appContainer

        val profile = container.prefs.profile.first()
        if (!profile.isComplete) return Result.success()

        val settings = container.prefs.notifications.first()
        val changes = mutableListOf<ScheduleChange>()

        val weeks = listOf(
            WeekUtils.currentWeekStart(),
            WeekUtils.currentWeekStart().plusWeeks(1),
        )

        for (week in weeks) {
            val (result, weekChanges) = container.scheduleRepository.refreshWeekWithChanges(
                branchGuid = profile.branchGuid,
                groupGuid = profile.groupGuid,
                weekStart = week,
            )
            if (result is com.vexorter.onyx.domain.SyncResult.Error) {
                return if (result.offline) Result.retry() else Result.success()
            }
            changes += weekChanges
        }

        if (settings.scheduleChanges && changes.isNotEmpty()) {
            val notifier = Notifier(context)
            notifier.ensureChannels()
            notifier.showChanges(changes)
        }

        // Расписание могло поменяться — план напоминаний на день пересобираем.
        AlarmScheduler(context, container).rescheduleAll()
        return Result.success()
    }

    companion object {
        private const val NAME = "schedule-daily-check"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            // UPDATE, а не KEEP: иначе у тех, кто уже поставил приложение,
            // остался бы прежний суточный интервал.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
