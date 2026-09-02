package com.vexorter.onyx.ui.funmode

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val hours: Int = 0,
    val busiestDay: String? = null,
    val topSubject: String? = null,
    val topRoom: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FunViewModel(private val container: AppContainer) : ViewModel() {

    val accent = container.prefs.accent

    val stats = container.prefs.profile
        .map { it.branchGuid to it.groupGuid }
        .distinctUntilChanged()
        .flatMapLatest { (branch, group) ->
            if (group.isBlank()) {
                flowOf(null)
            } else {
                val zone = BranchTimeZones.zoneOf(branch)
                container.scheduleRepository.observeWeek(group, WeekUtils.currentWeekStart(zone))
            }
        }
        .map { week -> week?.let(::buildStats) ?: WeekStats() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStats())

    fun setAccent(value: AccentColor) {
        viewModelScope.launch { container.prefs.setAccent(value) }
    }

    private fun buildStats(week: WeekSchedule): WeekStats {
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

        return WeekStats(
            lessons = all.size,
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
                FunViewModel(app.appContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunScreen(
    onBack: () -> Unit,
    viewModel: FunViewModel = viewModel(factory = FunViewModel.Factory),
) {
    val accent by viewModel.accent.collectAsStateWithLifecycle(initialValue = AccentColor.MINT)
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Веселье") },
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
                }
            }
        }
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
