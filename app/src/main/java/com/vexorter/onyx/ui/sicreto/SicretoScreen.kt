package com.vexorter.onyx.ui.sicreto

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vexorter.onyx.AppContainer
import com.vexorter.onyx.appContainer
import com.vexorter.onyx.data.prefs.AccentColor
import com.vexorter.onyx.domain.WeekSchedule
import com.vexorter.onyx.ui.theme.Amber
import com.vexorter.onyx.ui.theme.Coral
import com.vexorter.onyx.ui.theme.LocalLessonPalette
import com.vexorter.onyx.ui.theme.Mint
import com.vexorter.onyx.ui.theme.Violet
import com.vexorter.onyx.util.BranchTimeZones
import com.vexorter.onyx.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeekStats(
    val lessons: Int = 0,
    val remaining: Int = 0,
    val hours: Int = 0,
    val busiestDay: String? = null,
    val topSubject: String? = null,
    val topRoom: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SicretoViewModel(private val container: AppContainer) : ViewModel() {

    val accent = container.prefs.accent
    val celebrate = container.prefs.celebrateLessonEnd
    val amoled = container.prefs.amoled
    val countdown = container.prefs.countdown

    val stats = container.prefs.profile
        .map { it.branchGuid to it.ownerGuid }
        .distinctUntilChanged()
        .flatMapLatest { (branch, group) ->
            if (group.isBlank()) {
                flowOf(branch to null)
            } else {
                val zone = BranchTimeZones.zoneOf(branch)
                container.scheduleRepository.observeWeek(group, WeekUtils.currentWeekStart(zone))
                    .map { week -> branch to week }
            }
        }
        .map { (branch, week) -> week?.let { buildStats(it, branch) } ?: WeekStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStats())

    fun setAccent(value: AccentColor) {
        viewModelScope.launch { container.prefs.setAccent(value) }
    }

    fun setCelebrate(enabled: Boolean) {
        viewModelScope.launch { container.prefs.setCelebrateLessonEnd(enabled) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { container.prefs.setAmoled(enabled) }
    }

    fun setCountdown(enabled: Boolean) {
        viewModelScope.launch { container.prefs.setCountdown(enabled) }
    }

    private fun buildStats(week: WeekSchedule, branchGuid: String): WeekStats {
        val all = week.days.flatMap { it.lessons }
        if (all.isEmpty()) return WeekStats()

        val minutes = all.sumOf { lesson ->
            val start = WeekUtils.parseTime(lesson.timeStart)
            val end = WeekUtils.parseTime(lesson.timeEnd)
            if (start != null && end != null) {
                java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0)
            } else {
                0L
            }
        }

        val busiest = week.days.maxByOrNull { it.lessons.size }?.takeIf { it.lessons.isNotEmpty() }

        // «Осталось» считаем во времени филиала — иначе у заочника цифра врёт.
        val zone = BranchTimeZones.zoneOf(branchGuid)
        val now = java.time.LocalDateTime.now(zone)
        val remaining = all.count { lesson ->
            val end = WeekUtils.parseTime(lesson.timeEnd)
            when {
                lesson.date.isAfter(now.toLocalDate()) -> true
                lesson.date.isBefore(now.toLocalDate()) -> false
                end == null -> false
                else -> end.isAfter(now.toLocalTime())
            }
        }

        return WeekStats(
            lessons = all.size,
            remaining = remaining,
            hours = Math.round(minutes / 60.0).toInt(),
            busiestDay = busiest?.let { "${WeekUtils.weekDayName(it.date)} — ${it.lessons.size}" },
            topSubject = all.groupingBy { it.discipline }.eachCount()
                .maxByOrNull { it.value }?.key?.takeIf { it.isNotBlank() },
            topRoom = all.mapNotNull { it.classroom.takeIf(String::isNotBlank) }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SicretoViewModel(app.appContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SicretoScreen(
    onBack: () -> Unit,
    viewModel: SicretoViewModel = viewModel(factory = SicretoViewModel.Factory),
) {
    val accent by viewModel.accent.collectAsStateWithLifecycle(initialValue = AccentColor.MINT)
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val celebrate by viewModel.celebrate.collectAsStateWithLifecycle(initialValue = true)
    val amoled by viewModel.amoled.collectAsStateWithLifecycle(initialValue = false)
    val countdown by viewModel.countdown.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val palette = LocalLessonPalette.current
    var previewing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SICRETO") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Скрытый раздел. Сюда складываем всё, чему не место в обычных настройках.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Акцентный цвет")

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AccentSwatch(AccentColor.MINT, Mint, accent, viewModel::setAccent)
                    AccentSwatch(AccentColor.AMBER, Amber, accent, viewModel::setAccent)
                    AccentSwatch(AccentColor.VIOLET, Violet, accent, viewModel::setAccent)
                    AccentSwatch(AccentColor.CORAL, Coral, accent, viewModel::setAccent)
                }
            }

            SectionTitle("Экран")

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                SwitchRow(
                    title = "Чистый чёрный",
                    subtitle = "Вместо графитового фона — настоящий чёрный, на OLED экономит батарею",
                    checked = amoled,
                    onCheckedChange = viewModel::setAmoled,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "Таймер до конца пары",
                    subtitle = "Крупный обратный отсчёт над расписанием, пока пара идёт",
                    checked = countdown,
                    onCheckedChange = viewModel::setCountdown,
                )
            }

            SectionTitle("Салют")

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Хлопушки в конце пары",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Если приложение открыто, когда пара кончилась — " +
                                "конфетти с краёв экрана и звук",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = celebrate, onCheckedChange = viewModel::setCelebrate)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            previewing = true
                            playCelebrationSound(context)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Проверить салют",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Покажем прямо сейчас, как это выглядит и звучит",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Эта неделя в цифрах")

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                if (stats.lessons == 0) {
                    StatRow("Пар на неделе", "нет")
                } else {
                    StatRow("Пар на неделе", stats.lessons.toString())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow("Часов в аудиториях", "≈ ${stats.hours}")
                    stats.busiestDay?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatRow("Самый тяжёлый день", it)
                    }
                    stats.topSubject?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatRow("Чаще всего", it)
                    }
                    stats.topRoom?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatRow("Любимая аудитория", it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow("Осталось до конца недели", "${stats.remaining}")
                }
            }
        }
    }

        ConfettiOverlay(
            playing = previewing,
            colors = listOf(
                palette.lecture,
                palette.practice,
                palette.lab,
                palette.exam,
                MaterialTheme.colorScheme.onSurface,
            ),
            onFinished = { previewing = false },
        )
    }
}

@Composable
private fun AccentSwatch(
    value: AccentColor,
    color: Color,
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
) {
    val isSelected = value == selected
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onSelect(value) },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
