package com.vexorter.onyx.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vexorter.onyx.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** После перезагрузки все будильники стираются системой — ставим их заново. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                Notifier(appContext).ensureChannels()
                AlarmScheduler(appContext, appContext.appContainer).rescheduleAll()
                ScheduleCheckWorker.enqueue(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
