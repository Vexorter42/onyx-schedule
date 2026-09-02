package com.vexorter.onyx.ui.schedule

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vexorter.onyx.AppContainer
import com.vexorter.onyx.appContainer
import com.vexorter.onyx.domain.Profile
import com.vexorter.onyx.domain.SyncResult
import com.vexorter.onyx.domain.WeekSchedule
import com.vexorter.onyx.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScheduleUiState(
    val profile: Profile = Profile.EMPTY,
    val weekStart: LocalDate = WeekUtils.currentWeekStart(),
    val week: WeekSchedule? = null,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    /** Сеть недоступна, но в базе есть данные — показываем их и мягко предупреждаем. */
    val staleWarning: String? = null,
    /** Ни кэша, ни сети — показываем экран ошибки. */
    val fatalError: String? = null,
    /** Сеть есть, но сервер отказал по стране — подсказка нужна другая. */
    val geoBlocked: Boolean = false,
) {
    val isFirstLoad: Boolean get() = week == null && fatalError == null
    val isCurrentWeek: Boolean get() = WeekUtils.isCurrentWeek(weekStart)
}

private data class WeekKey(val profile: Profile, val weekStart: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    private val weekStart = MutableStateFlow(WeekUtils.currentWeekStart())
    private val refreshing = MutableStateFlow(false)
    private val syncError = MutableStateFlow<SyncResult.Error?>(null)

    private val key: Flow<WeekKey> =
        combine(container.prefs.profile, weekStart) { profile, week -> WeekKey(profile, week) }
            .distinctUntilChanged()

    private val weekData: Flow<WeekSchedule?> = key.flatMapLatest { k ->
        if (!k.profile.isComplete) {
            flowOf(null)
        } else {
            container.scheduleRepository.observeWeek(k.profile.groupGuid, k.weekStart)
        }
    }

    val state: StateFlow<ScheduleUiState> = combine(
        key,
        weekData,
        refreshing,
        syncError,
        container.networkMonitor.isOnline,
    ) { k, week, isRefreshing, error, online ->
        ScheduleUiState(
            profile = k.profile,
            weekStart = k.weekStart,
            week = week,
            isRefreshing = isRefreshing,
            isOnline = online,
            staleWarning = error?.takeIf { week != null }?.message,
            fatalError = error?.takeIf { week == null }?.message,
            geoBlocked = error?.geoBlocked == true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    /** Первую неделю обновляет [refreshOnOpen], иначе на старте ушло бы два одинаковых запроса. */
    private var handledFirstKey = false

    init {
        // Смена недели или группы: показываем кэш мгновенно, свежесть подтягиваем следом.
        viewModelScope.launch {
            key.collectLatest { k ->
                if (!k.profile.isComplete) return@collectLatest
                syncError.value = null

                val isFirst = !handledFirstKey
                handledFirstKey = true

                val repo = container.scheduleRepository
                val cached = repo.isCached(k.profile.groupGuid, k.weekStart)
                if (!isFirst && !repo.isFresh(k.profile.groupGuid, k.weekStart)) {
                    if (!cached) refreshing.value = true
                    val result = repo.refreshWeek(k.profile.branchGuid, k.profile.groupGuid, k.weekStart)
                    syncError.value = result.errorOrNull
                    refreshing.value = false
                }
                prefetchNeighbours(k)
            }
        }

        // Сеть вернулась — тихо повторяем неудачную загрузку.
        viewModelScope.launch {
            container.networkMonitor.isOnline
                .distinctUntilChanged()
                .collect { online -> if (online && syncError.value != null) refresh() }
        }
    }

    /**
     * Вызывается каждый раз, когда приложение выходит на передний план.
     * Обновляет всегда, не глядя на «свежесть»: открыл приложение — видишь актуальное.
     * Молча, без индикатора: данные из базы уже на экране, дёргать спиннер незачем.
     */
    fun refreshOnOpen() {
        viewModelScope.launch {
            val profile = container.prefs.profile.first()
            if (!profile.isComplete) return@launch
            val result = container.scheduleRepository.refreshWeek(
                branchGuid = profile.branchGuid,
                groupGuid = profile.groupGuid,
                weekStart = weekStart.value,
            )
            syncError.value = result.errorOrNull
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = container.prefs.profile.first()
            if (!profile.isComplete) return@launch
            refreshing.value = true
            val result = container.scheduleRepository.refreshWeek(
                branchGuid = profile.branchGuid,
                groupGuid = profile.groupGuid,
                weekStart = weekStart.value,
            )
            syncError.value = result.errorOrNull
            refreshing.value = false
        }
    }

    fun showPreviousWeek() {
        weekStart.value = weekStart.value.minusWeeks(1)
    }

    fun showNextWeek() {
        weekStart.value = weekStart.value.plusWeeks(1)
    }

    fun showCurrentWeek() {
        weekStart.value = WeekUtils.currentWeekStart()
    }

    fun dismissWarning() {
        syncError.value = null
    }

    /** Соседние недели греем заранее — тогда листание не упирается в сеть. */
    private fun prefetchNeighbours(k: WeekKey) {
        viewModelScope.launch {
            listOf(k.weekStart.plusWeeks(1), k.weekStart.minusWeeks(1)).forEach { week ->
                container.scheduleRepository.prefetchWeek(
                    branchGuid = k.profile.branchGuid,
                    groupGuid = k.profile.groupGuid,
                    weekStart = week,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ScheduleViewModel(app.appContainer)
            }
        }
    }
}
