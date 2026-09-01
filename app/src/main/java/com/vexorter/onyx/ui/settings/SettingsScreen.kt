package com.vexorter.onyx.ui.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.vexorter.onyx.data.prefs.ThemeMode
import com.vexorter.onyx.domain.NotificationSettings
import com.vexorter.onyx.domain.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val profile = container.prefs.profile
    val themeMode = container.prefs.themeMode
    val notifications = container.prefs.notifications

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.prefs.setThemeMode(mode) }
    }

    fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch {
            container.prefs.updateNotifications(transform)
            // Настройки изменились — сразу пересобираем план будильников.
            container.alarmScheduler.rescheduleAll()
        }
    }

    fun canScheduleExactAlarms(): Boolean = container.alarmScheduler.canScheduleExact()

    /**
     * Отправляет пробное уведомление тем же кодом, что и настоящие напоминания —
     * так видно, не режет ли их система или энергосбережение.
     */
    fun sendTestNotification(onBlocked: () -> Unit) {
        viewModelScope.launch {
            val notifier = container.notifier
            notifier.ensureChannels()
            if (!notifier.canNotify()) {
                onBlocked()
                return@launch
            }
            val profile = container.prefs.profile.first()
            val tomorrow = java.time.LocalDate.now().plusDays(1)
            val lessons = if (profile.isComplete) {
                container.scheduleRepository.lessonsOn(profile.groupGuid, tomorrow)
            } else {
                emptyList()
            }
            notifier.showDaySummary(tomorrow, lessons, isTomorrow = true)
        }
    }

    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            container.scheduleRepository.clearCache()
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                SettingsViewModel(app.appContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeBranch: () -> Unit,
    onChangeGroup: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsStateWithLifecycle(initialValue = Profile.EMPTY)
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
    val notifications by viewModel.notifications
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var timePickerFor by remember { mutableStateOf<TimeTarget?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Без разрешения на уведомления напоминания приходить не будут"
                )
            }
        }
    }

    fun requestPermissionIfNeeded(enabling: Boolean) {
        if (enabling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            SectionTitle("Профиль")

            SettingsCard {
                InfoRow("Филиал", profile.branchName.ifBlank { "не выбран" })
                Divider()
                InfoRow("Набор", profile.yearName.ifBlank { "не выбран" })
                Divider()
                InfoRow("Группа", profile.groupName.ifBlank { "не выбрана" })
            }

            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.Groups,
                    title = "Сменить группу",
                    subtitle = "Филиал и год набора останутся прежними",
                    onClick = onChangeGroup,
                )
                Divider()
                ActionRow(
                    icon = Icons.Rounded.LocationCity,
                    title = "Сменить филиал",
                    subtitle = "Придётся заново выбрать год набора и группу",
                    onClick = onChangeBranch,
                )
            }

            SectionTitle("Уведомления")

            SettingsCard {
                SwitchRow(
                    title = "Утром — сводка на день",
                    subtitle = "В ${notifications.morningAt.format()} · сколько пар и во сколько первая",
                    checked = notifications.morningSummary,
                    onCheckedChange = { enabled ->
                        requestPermissionIfNeeded(enabled)
                        viewModel.updateNotifications { it.copy(morningSummary = enabled) }
                    },
                    onClick = { timePickerFor = TimeTarget.MORNING },
                )
                Divider()
                SwitchRow(
                    title = "Вечером — что завтра",
                    subtitle = "В ${notifications.eveningAt.format()} · список пар на завтра",
                    checked = notifications.eveningPreview,
                    onCheckedChange = { enabled ->
                        requestPermissionIfNeeded(enabled)
                        viewModel.updateNotifications { it.copy(eveningPreview = enabled) }
                    },
                    onClick = { timePickerFor = TimeTarget.EVENING },
                )
                Divider()
                SwitchRow(
                    title = "Перед каждой парой",
                    subtitle = "За ${notifications.beforeLessonMinutes} мин до начала",
                    checked = notifications.beforeLesson,
                    onCheckedChange = { enabled ->
                        requestPermissionIfNeeded(enabled)
                        viewModel.updateNotifications { it.copy(beforeLesson = enabled) }
                    },
                )
                if (notifications.beforeLesson) {
                    LeadTimeSelector(
                        selected = notifications.beforeLessonMinutes,
                        onSelect = { minutes ->
                            viewModel.updateNotifications { it.copy(beforeLessonMinutes = minutes) }
                        },
                    )
                }
                Divider()
                SwitchRow(
                    title = "Изменения в расписании",
                    subtitle = "Раз в день сверяем расписание и сообщаем, что поменялось",
                    checked = notifications.scheduleChanges,
                    onCheckedChange = { enabled ->
                        requestPermissionIfNeeded(enabled)
                        viewModel.updateNotifications { it.copy(scheduleChanges = enabled) }
                    },
                )
                Divider()
                ActionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Проверить уведомление",
                    subtitle = "Пришлём пробное — сразу видно, доходят ли они",
                    onClick = {
                        viewModel.sendTestNotification {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Уведомления выключены в настройках системы"
                                )
                            }
                        }
                    },
                )
            }

            if (notifications.beforeLesson && !viewModel.canScheduleExactAlarms()) {
                ExactAlarmWarning(
                    onOpenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }
                    }
                )
            }

            SectionTitle("Оформление")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ThemeMode.SYSTEM to "Как в системе",
                    ThemeMode.LIGHT to "Светлая",
                    ThemeMode.DARK to "Тёмная",
                )
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            SectionTitle("Данные")

            SettingsCard {
                ActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Очистить сохранённое расписание",
                    subtitle = "Пары загрузятся заново при следующем открытии",
                    onClick = {
                        viewModel.clearCache {
                            scope.launch {
                                snackbarHostState.showSnackbar("Сохранённое расписание удалено")
                            }
                        }
                    },
                )
            }

            Text(
                text = "Расписание берётся с сайта schedule.ruc.su. Загруженные недели хранятся " +
                    "в памяти телефона, поэтому открываются мгновенно и работают без интернета.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    timePickerFor?.let { target ->
        val initial = when (target) {
            TimeTarget.MORNING -> notifications.morningAt
            TimeTarget.EVENING -> notifications.eveningAt
        }
        TimePickerDialog(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            onDismiss = { timePickerFor = null },
            onConfirm = { hour, minute ->
                val minutes = hour * 60 + minute
                viewModel.updateNotifications {
                    when (target) {
                        TimeTarget.MORNING -> it.copy(morningAtMinutes = minutes)
                        TimeTarget.EVENING -> it.copy(eveningAtMinutes = minutes)
                    }
                }
                timePickerFor = null
            },
        )
    }
}

private enum class TimeTarget { MORNING, EVENING }

private fun java.time.LocalTime.format(): String = "%02d:%02d".format(hour, minute)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Во сколько напоминать") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimeInput(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun LeadTimeSelector(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 30)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        options.forEachIndexed { index, minutes ->
            SegmentedButton(
                selected = selected == minutes,
                onClick = { onSelect(minutes) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("$minutes мин", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ExactAlarmWarning(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Напоминания могут опаздывать",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Система не разрешила приложению точные будильники — уведомление " +
                    "перед парой придёт с задержкой в несколько минут.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 4.dp)) {
                Text("Разрешить")
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun InfoRow(label: String, value: String) {
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
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
