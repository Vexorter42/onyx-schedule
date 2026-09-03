package com.vexorter.onyx.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vexorter.onyx.ui.common.EmptyState
import com.vexorter.onyx.ui.common.FullScreenLoader
import com.vexorter.onyx.ui.common.SearchField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScaffold(
    title: String,
    stepLabel: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    geoBlocked: Boolean,
    onRetry: () -> Unit,
    listContent: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val caption = subtitle?.takeIf { it.isNotBlank() } ?: stepLabel
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = searchPlaceholder,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                error != null -> EmptyState(
                    icon = if (geoBlocked) Icons.Outlined.Public else Icons.Outlined.WifiOff,
                    title = if (geoBlocked) {
                        "Сервер не пускает с этого адреса"
                    } else {
                        "Не удалось загрузить список"
                    },
                    description = if (geoBlocked) {
                        "Сайт расписания открыт только для российских адресов. " +
                            "Включи VPN с российским IP и попробуй снова."
                    } else {
                        "$error.\nПодключитесь к интернету один раз — список сохранится в приложении и дальше будет открываться офлайн."
                    },
                    actionLabel = "Повторить",
                    onAction = onRetry,
                )

                isLoading -> FullScreenLoader()

                isEmpty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Ничего не найдено",
                    description = "Попробуйте изменить запрос.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    content = listContent,
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun CategoryHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun BranchPickerScreen(
    onBack: (() -> Unit)?,
    onSelected: () -> Unit,
    viewModel: BranchPickerViewModel = viewModel(factory = BranchPickerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(state.items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            state.items
        } else {
            state.items.filter {
                it.name.lowercase().contains(q) || it.code.lowercase().contains(q)
            }
        }
    }

    PickerScaffold(
        title = "Выберите филиал",
        stepLabel = "Начнём с филиала",
        subtitle = null,
        onBack = onBack,
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Поиск филиала",
        isLoading = state.isLoading && state.items.isEmpty(),
        error = state.error,
        isEmpty = filtered.isEmpty() && !state.isLoading,
        geoBlocked = state.geoBlocked,
        onRetry = { viewModel.refresh() },
    ) {
        items(filtered, key = { it.guid }) { branch ->
            PickerRow(title = branch.name) { viewModel.select(branch, onSelected) }
        }
    }
}

@Composable
fun YearPickerScreen(
    onBack: () -> Unit,
    onSelected: () -> Unit,
    viewModel: YearPickerViewModel = viewModel(factory = YearPickerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val branchName by viewModel.branchName.collectAsStateWithLifecycle(initialValue = "")
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(state.items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) state.items else state.items.filter { it.name.lowercase().contains(q) }
    }

    PickerScaffold(
        title = "Год набора",
        stepLabel = "Год набора",
        subtitle = branchName,
        onBack = onBack,
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Например, 2025",
        isLoading = state.isLoading && state.items.isEmpty(),
        error = state.error,
        isEmpty = filtered.isEmpty() && !state.isLoading,
        geoBlocked = state.geoBlocked,
        onRetry = { viewModel.refresh() },
    ) {
        items(filtered, key = { it.guid }) { year ->
            PickerRow(title = year.name) { viewModel.select(year, onSelected) }
        }
    }
}

@Composable
fun GroupPickerScreen(
    onBack: () -> Unit,
    onSelected: () -> Unit,
    viewModel: GroupPickerViewModel = viewModel(factory = GroupPickerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subtitle by viewModel.subtitle.collectAsStateWithLifecycle(initialValue = "")
    var query by rememberSaveable { mutableStateOf("") }

    val grouped = remember(state.items, query) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            state.items
        } else {
            state.items.filter {
                it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
            }
        }
        filtered.groupBy { it.category }.toList()
    }

    PickerScaffold(
        title = "Выберите группу",
        stepLabel = "Осталось выбрать группу",
        subtitle = subtitle,
        onBack = onBack,
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Поиск группы",
        isLoading = state.isLoading && state.items.isEmpty(),
        error = state.error,
        isEmpty = grouped.isEmpty() && !state.isLoading,
        geoBlocked = state.geoBlocked,
        onRetry = { viewModel.refresh() },
    ) {
        grouped.forEach { (category, groups) ->
            // Заголовок нужен, только когда он реально что-то объединяет: у большинства
            // направлений всего одна группа, и повтор названия лишь удлиняет список.
            if (category.isNotBlank() && groups.size > 1) {
                item(key = "header_$category") { CategoryHeader(category) }
            }
            items(groups, key = { it.guid }) { group ->
                PickerRow(title = group.name) { viewModel.select(group, onSelected) }
            }
        }
    }
}

@Composable
fun TeacherPickerScreen(
    onBack: () -> Unit,
    onSelected: () -> Unit,
    viewModel: TeacherPickerViewModel = viewModel(factory = TeacherPickerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val branchName by viewModel.branchName.collectAsStateWithLifecycle(initialValue = "")
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(state.items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            state.items
        } else {
            state.items.filter {
                it.name.lowercase().contains(q) || it.position.lowercase().contains(q)
            }
        }
    }

    PickerScaffold(
        title = "Выберите преподавателя",
        stepLabel = "Преподаватель",
        subtitle = branchName,
        onBack = onBack,
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Поиск по фамилии",
        isLoading = state.isLoading && state.items.isEmpty(),
        error = state.error,
        isEmpty = filtered.isEmpty() && !state.isLoading,
        geoBlocked = state.geoBlocked,
        onRetry = { viewModel.refresh() },
    ) {
        items(filtered, key = { it.guid }) { teacher ->
            PickerRow(title = teacher.name) { viewModel.select(teacher, onSelected) }
        }
    }
}

/** Развилка мастера: чьё расписание смотрим. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KindPickerScreen(
    onBack: () -> Unit,
    onGroup: () -> Unit,
    onTeacher: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Чьё расписание?")
                        Text(
                            text = "Группы или преподавателя",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindCard(
                icon = Icons.Outlined.Groups,
                title = "Группы",
                subtitle = "Расписание учебной группы",
                onClick = onGroup,
            )
            KindCard(
                icon = Icons.Outlined.Person,
                title = "Преподавателя",
                subtitle = "Когда и где ведёт занятия",
                onClick = onTeacher,
            )
        }
    }
}

@Composable
private fun KindCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
