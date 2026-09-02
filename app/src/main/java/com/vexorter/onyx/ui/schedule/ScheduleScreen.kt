package com.vexorter.onyx.ui.schedule

import android.content.Intent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vexorter.onyx.domain.DaySchedule
import com.vexorter.onyx.domain.Lesson
import com.vexorter.onyx.ui.common.EmptyState
import com.vexorter.onyx.ui.common.FullScreenLoader
import com.vexorter.onyx.ui.common.OfflineBanner
import com.vexorter.onyx.ui.theme.LocalLessonPalette
import com.vexorter.onyx.ui.update.UpdateBadgeButton
import com.vexorter.onyx.ui.update.UpdateDialog
import com.vexorter.onyx.ui.update.UpdateViewModel
import com.vexorter.onyx.util.WeekUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Идущая прямо сейчас пара: доля пройденного и сколько минут осталось. */
private data class Ongoing(val progress: Float, val minutesLeft: Int)

/** Плоская модель списка: нужна, чтобы полоска дней знала, куда прокручивать. */
private sealed interface ScheduleRow {
    data class Header(val day: DaySchedule, val isToday: Boolean) : ScheduleRow
    data class LessonItem(val lesson: Lesson, val ongoing: Ongoing?) : ScheduleRow
    data class EmptyDay(val date: LocalDate) : ScheduleRow
    data object Footer : ScheduleRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenSettings: () -> Unit,
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory),
    updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Каждый выход приложения на передний план — повод перезапросить неделю.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnOpen() }

    val update by updateViewModel.available.collectAsStateWithLifecycle()
    val downloadState by updateViewModel.download.collectAsStateWithLifecycle()
    var showUpdate by rememberSaveable { mutableStateOf(false) }

    // «Сейчас» считаем во времени филиала: у заочника из другого пояса иначе
    // подсветилась бы не та пара. Раз в полминуты обновляем, чтобы не устаревало.
    val zone = state.zone
    val now by produceState(initialValue = LocalDateTime.now(zone), zone) {
        while (true) {
            value = LocalDateTime.now(zone)
            delay(30_000)
        }
    }
    val today = now.toLocalDate()

    val days = state.week?.days.orEmpty()
    // В списке и в полоске дней должен быть один и тот же набор дат, иначе тап по дню
    // ведёт в пустоту.
    val visibleDays = remember(days) { days.filter { it.lessons.isNotEmpty() || it.date.dayOfWeek.value != 7 } }
    val rows = remember(visibleDays, now) { buildRows(visibleDays, now) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val visibleDay by remember(rows) {
        derivedStateOf {
            rows.take(listState.firstVisibleItemIndex + 1)
                .filterIsInstance<ScheduleRow.Header>()
                .lastOrNull()?.day?.date
        }
    }

    // Открывая текущую неделю, показываем сразу сегодняшний день, а не понедельник.
    LaunchedEffect(state.weekStart, rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        val index = rows.indexOfFirst { it is ScheduleRow.Header && it.day.date == today }
        if (index > 0) listState.scrollToItem(index)
    }

    update?.let { info ->
        if (showUpdate) {
            UpdateDialog(
                info = info,
                currentVersion = updateViewModel.currentVersion,
                download = downloadState,
                onInstall = { updateViewModel.install(info) },
                onOpenPage = { updateViewModel.openReleasePage(info) },
                onDismiss = {
                    showUpdate = false
                    updateViewModel.resetDownload()
                },
            )
        }
    }

    Scaffold(
        topBar = {
            ScheduleHeader(
                groupName = state.profile.groupName,
                branchName = state.profile.branchName,
                showTodayAction = !state.isCurrentWeek,
                onToday = viewModel::showCurrentWeek,
                onOpenSettings = onOpenSettings,
                hasUpdate = update != null,
                onUpdateClick = { showUpdate = true },
                canShare = (state.week?.lessonCount ?: 0) > 0,
                onShare = {
                    val text = buildShareText(
                        groupName = state.profile.groupName,
                        weekStart = state.weekStart,
                        days = visibleDays,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "Поделиться расписанием"))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            WeekSwitcher(
                weekStart = state.weekStart,
                zone = zone,
                onPrevious = viewModel::showPreviousWeek,
                onNext = viewModel::showNextWeek,
            )

            if (visibleDays.isNotEmpty()) {
                DayStrip(
                    days = visibleDays,
                    today = today,
                    activeDay = visibleDay,
                    onDayClick = { date ->
                        val index = rows.indexOfFirst {
                            it is ScheduleRow.Header && it.day.date == date
                        }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    },
                )
            }

            AnimatedVisibility(visible = state.staleWarning != null || !state.isOnline) {
                val updated = state.week?.updatedAt ?: 0L
                OfflineBanner(
                    text = if (state.isOnline) {
                        "${state.staleWarning}. Данные от ${WeekUtils.formatUpdatedAt(updated)}"
                    } else {
                        "Нет сети. Данные от ${WeekUtils.formatUpdatedAt(updated)}"
                    },
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.fatalError != null -> EmptyState(
                        icon = if (state.geoBlocked) Icons.Outlined.Public else Icons.Outlined.WifiOff,
                        title = if (state.geoBlocked) {
                            "Сервер не пускает с этого адреса"
                        } else {
                            "Расписание не загружено"
                        },
                        description = if (state.geoBlocked) {
                            "Сайт расписания открыт только для российских адресов. " +
                                "Включи VPN с российским IP и попробуй снова.\n\n" +
                                "Уже загруженные недели при этом продолжают открываться офлайн."
                        } else {
                            "${state.fatalError}.\nЭту неделю нужно один раз открыть с интернетом — дальше она будет доступна офлайн."
                        },
                        actionLabel = "Повторить",
                        onAction = viewModel::refresh,
                    )

                    state.isFirstLoad -> FullScreenLoader()

                    state.week?.lessonCount == 0 -> EmptyState(
                        icon = Icons.Outlined.EventBusy,
                        title = "На этой неделе пар нет",
                        description = "Возможно, каникулы или расписание ещё не выложили.",
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = rows.size,
                            key = { index -> rowKey(rows[index], index) },
                        ) { index ->
                            when (val row = rows[index]) {
                                is ScheduleRow.Header -> DayHeader(row.day, row.isToday)
                                is ScheduleRow.LessonItem -> LessonCard(row.lesson, row.ongoing)
                                is ScheduleRow.EmptyDay -> EmptyDayRow()
                                ScheduleRow.Footer -> UpdatedFooter(state.week?.updatedAt ?: 0L)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Текст недели для отправки в чат: без разметки, чтобы читалось где угодно. */
private fun buildShareText(
    groupName: String,
    weekStart: java.time.LocalDate,
    days: List<DaySchedule>,
): String = buildString {
    append(groupName.ifBlank { "Расписание" })
    append(" · ")
    appendLine(WeekUtils.weekRangeTitle(weekStart))

    days.filter { it.lessons.isNotEmpty() }.forEach { day ->
        appendLine()
        appendLine("${WeekUtils.weekDayName(day.date)}, ${WeekUtils.dayAndMonth(day.date)}")
        day.lessons.forEach { lesson ->
            append("${lesson.timeStart} — ${lesson.timeEnd}  ${lesson.discipline}")
            if (lesson.type.isNotBlank()) append(" (${lesson.type})")
            if (lesson.classroom.isNotBlank()) append(", ауд. ${lesson.classroom}")
            appendLine()
        }
    }
}.trim()

private fun rowKey(row: ScheduleRow, index: Int): String = when (row) {
    is ScheduleRow.Header -> "h_${row.day.date}"
    is ScheduleRow.EmptyDay -> "e_${row.date}"
    is ScheduleRow.LessonItem -> "l_${row.lesson.date}_$index"
    ScheduleRow.Footer -> "footer"
}

private fun buildRows(days: List<DaySchedule>, now: LocalDateTime): List<ScheduleRow> {
    if (days.isEmpty()) return emptyList()
    val rows = mutableListOf<ScheduleRow>()
    days.forEach { day ->
        rows += ScheduleRow.Header(day, day.date == now.toLocalDate())
        if (day.lessons.isEmpty()) {
            rows += ScheduleRow.EmptyDay(day.date)
        } else {
            day.lessons.forEach { lesson ->
                rows += ScheduleRow.LessonItem(lesson, lesson.ongoingAt(now))
            }
        }
    }
    rows += ScheduleRow.Footer
    return rows
}

private fun Lesson.ongoingAt(now: LocalDateTime): Ongoing? {
    if (date != now.toLocalDate()) return null
    val start = WeekUtils.parseTime(timeStart) ?: return null
    val end = WeekUtils.parseTime(timeEnd) ?: return null
    val time = now.toLocalTime()
    if (time.isBefore(start) || !time.isBefore(end)) return null

    val total = Duration.between(start, end).toMinutes()
    if (total <= 0L) return null
    val passed = Duration.between(start, time).toMinutes()
    return Ongoing(
        progress = (passed.toFloat() / total).coerceIn(0f, 1f),
        minutesLeft = (total - passed).toInt().coerceAtLeast(1),
    )
}

@Composable
private fun ScheduleHeader(
    groupName: String,
    branchName: String,
    showTodayAction: Boolean,
    onToday: () -> Unit,
    onOpenSettings: () -> Unit,
    hasUpdate: Boolean,
    onUpdateClick: () -> Unit,
    canShare: Boolean,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = groupName.ifBlank { "Onyx" },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (branchName.isNotBlank()) {
                Text(
                    text = branchName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasUpdate) {
            UpdateBadgeButton(onClick = onUpdateClick)
            Spacer(Modifier.width(8.dp))
        }
        if (showTodayAction) {
            RoundIconButton(Icons.Rounded.Today, "К текущей неделе", onToday)
            Spacer(Modifier.width(8.dp))
        }
        if (canShare) {
            RoundIconButton(Icons.Rounded.Share, "Поделиться неделей", onShare)
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(Icons.Rounded.Settings, "Настройки", onOpenSettings)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekSwitcher(
    weekStart: LocalDate,
    zone: ZoneId,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            "Предыдущая неделя",
            onPrevious,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = WeekUtils.weekRangeTitle(weekStart),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = WeekUtils.relativeWeekLabel(weekStart, zone) ?: "${weekStart.year}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RoundIconButton(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "Следующая неделя",
            onNext,
        )
    }
}

@Composable
private fun DayStrip(
    days: List<DaySchedule>,
    today: LocalDate,
    activeDay: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = days.size, key = { days[it].date.toString() }) { index ->
            val day = days[index]
            DayChip(
                day = day,
                isToday = day.date == today,
                isActive = day.date == activeDay,
                onClick = { onDayClick(day.date) },
            )
        }
    }
}

@Composable
private fun DayChip(
    day: DaySchedule,
    isToday: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background = if (isActive) scheme.primary else scheme.surfaceContainer
    val content = when {
        isActive -> scheme.onPrimary
        day.lessons.isEmpty() -> scheme.outline
        else -> scheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = Modifier
            .size(width = 48.dp, height = 62.dp)
            .then(
                if (isToday && !isActive) {
                    Modifier.border(1.5.dp, scheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = WeekUtils.weekDayShort(day.date),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
            )
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(4.dp)
                    .background(
                        color = if (day.lessons.isEmpty()) {
                            Color.Transparent
                        } else {
                            content.copy(alpha = 0.6f)
                        },
                        shape = CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun DayHeader(day: DaySchedule, isToday: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = WeekUtils.weekDayName(day.date),
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = WeekUtils.dayAndMonth(day.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (isToday) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Text(
                    text = "сегодня",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyDayRow() {
    Text(
        text = "Пар нет",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun LessonCard(lesson: Lesson, ongoing: Ongoing?) {
    val scheme = MaterialTheme.colorScheme
    val accent = lessonTypeColor(lesson.type)
    val isNow = ongoing != null

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isNow) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNow) Modifier.border(1.5.dp, accent, RoundedCornerShape(20.dp)) else Modifier
            ),
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .height(IntrinsicSize.Min)
        ) {
            TimeColumn(lesson)

            // У идущей пары полоска слева работает шкалой: заполняется сверху вниз
            // ровно на столько, сколько от пары уже прошло.
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = if (isNow) accent.copy(alpha = 0.22f) else accent,
                            shape = RoundedCornerShape(2.dp),
                        )
                )
                if (ongoing != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(ongoing.progress)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.discipline,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ongoing != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = accent, shape = CircleShape) {
                            Text(
                                text = "ещё ${ongoing.minutesLeft} мин",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.surface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (lesson.type.isNotBlank()) {
                    val subgroup = lesson.subGroup.takeIf { it.isNotBlank() }
                        ?.let { " · подгруппа $it" }.orEmpty()
                    Text(
                        text = lesson.type + subgroup,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (lesson.employee.isNotBlank()) {
                    IconLine(Icons.Rounded.Person, lesson.employee)
                }
                if (lesson.classroom.isNotBlank()) {
                    IconLine(Icons.Rounded.Place, lesson.classroom)
                }
            }
        }
    }
}

@Composable
private fun TimeColumn(lesson: Lesson) {
    Column(
        modifier = Modifier.width(54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-1).dp),
    ) {
        if (lesson.orderNum > 0) {
            Text(
                text = "№${lesson.orderNum}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = lesson.timeStart,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = lesson.timeEnd,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun UpdatedFooter(updatedAt: Long) {
    Text(
        text = "Обновлено ${WeekUtils.formatUpdatedAt(updatedAt)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    )
}

@Composable
private fun lessonTypeColor(type: String): Color {
    val palette = LocalLessonPalette.current
    val normalized = type.lowercase()
    return when {
        normalized.startsWith("лекц") -> palette.lecture
        normalized.startsWith("практ") || normalized.startsWith("семин") -> palette.practice
        normalized.startsWith("лаборат") -> palette.lab
        normalized.contains("экзамен") || normalized.contains("зачёт") ||
            normalized.contains("зачет") || normalized.contains("консульт") -> palette.exam

        else -> palette.other
    }
}
