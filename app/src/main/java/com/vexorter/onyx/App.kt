package com.vexorter.onyx

import android.app.Application
import android.content.Context
import com.vexorter.onyx.data.local.AppDatabase
import com.vexorter.onyx.data.prefs.UserPrefs
import com.vexorter.onyx.data.remote.NetworkModule
import com.vexorter.onyx.data.remote.ScheduleApi
import com.vexorter.onyx.data.repo.CatalogRepository
import com.vexorter.onyx.data.repo.ScheduleRepository
import com.vexorter.onyx.notifications.AlarmScheduler
import com.vexorter.onyx.notifications.Notifier
import com.vexorter.onyx.notifications.ScheduleCheckWorker
import com.vexorter.onyx.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Простой контейнер зависимостей — на такой размер приложения DI-фреймворк излишен. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.build(appContext) }
    private val api by lazy {
        ScheduleApi(NetworkModule.okHttpClient(appContext), NetworkModule.json())
    }

    val prefs by lazy { UserPrefs(appContext) }
    val networkMonitor by lazy { NetworkMonitor(appContext) }
    val catalogRepository by lazy { CatalogRepository(api, database.catalogDao()) }
    val scheduleRepository by lazy { ScheduleRepository(api, database.scheduleDao()) }
    val notifier by lazy { Notifier(appContext) }
    val alarmScheduler by lazy { AlarmScheduler(appContext, this) }
}

class App : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.notifier.ensureChannels()
        ScheduleCheckWorker.enqueue(this)
        scope.launch { container.alarmScheduler.rescheduleAll() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
