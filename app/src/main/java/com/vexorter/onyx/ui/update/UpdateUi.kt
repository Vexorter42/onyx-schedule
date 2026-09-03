package com.vexorter.onyx.ui.update

import android.app.Application
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vexorter.onyx.AppContainer
import com.vexorter.onyx.appContainer
import com.vexorter.onyx.domain.UpdateCheckResult
import com.vexorter.onyx.domain.UpdateDownload
import com.vexorter.onyx.domain.UpdateInfo
import kotlinx.coroutines.launch

class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    val available = container.updateRepository.available
    val download = container.updateRepository.download
    val currentVersion: String get() = container.updateRepository.currentVersion

    fun check(force: Boolean = false, onResult: (UpdateCheckResult) -> Unit = {}) {
        viewModelScope.launch { onResult(container.updateRepository.check(force)) }
    }

    fun install(info: UpdateInfo) {
        viewModelScope.launch { container.updateRepository.downloadAndInstall(info) }
    }

    fun openReleasePage(info: UpdateInfo) = container.updateRepository.openReleasePage(info)

    fun resetDownload() = container.updateRepository.resetDownload()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                UpdateViewModel(app.appContainer)
            }
        }
    }
}

/** Небольшая круглая кнопка с пульсирующей точкой — появляется, только когда есть обновление. */
@Composable
fun UpdateBadgeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "update-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .alpha(pulse)
                    .scale(0.85f + 0.15f * pulse)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    download: UpdateDownload,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Обновление",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Установлена $currentVersion · загрузка ${formatSize(info.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    )

                    ReleaseNotes(info.notes)
                }

                UpdateActions(
                    download = download,
                    onInstall = onInstall,
                    onOpenPage = onOpenPage,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun UpdateActions(
    download: UpdateDownload,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (download) {
            is UpdateDownload.InProgress -> {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Скачиваем… ${(download.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            UpdateDownload.Ready -> Text(
                text = "Открываем установщик. Если система спросит разрешение на установку " +
                    "из этого источника — его нужно выдать.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateDownload.Failed -> {
                Text(
                    text = download.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onOpenPage, modifier = Modifier.fillMaxWidth()) {
                    Text("Открыть страницу релиза")
                }
            }

            UpdateDownload.Idle -> {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Обновить")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Позже")
                }
            }
        }
    }
}

/**
 * Заметки к релизу приходят в Markdown. Полноценный парсер тут не нужен —
 * достаточно заголовков, списков и жирного текста.
 */
@Composable
private fun ReleaseNotes(notes: String) {
    val blocks = remember(notes) { notes.lines() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Box(Modifier.size(4.dp))

                line.startsWith("#") -> Text(
                    text = inlineMarkdown(line.trimStart('#').trim()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )

                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = inlineMarkdown(line.trimStart().drop(2)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                else -> Text(
                    text = inlineMarkdown(line),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Поддерживаем только **жирный** и `код` — этого хватает для заметок к релизу. */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text.replace("`", "")
    while (true) {
        val start = rest.indexOf("**")
        if (start < 0) break
        val end = rest.indexOf("**", start + 2)
        if (end < 0) break

        append(rest.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(rest.substring(start + 2, end))
        }
        rest = rest.substring(end + 2)
    }
    append(rest)
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024.0)
    else -> "${bytes / 1024} КБ"
}
