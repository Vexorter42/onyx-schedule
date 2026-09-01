package com.vexorter.onyx.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.vexorter.onyx.data.remote.ReleaseDto
import com.vexorter.onyx.domain.UpdateDownload
import com.vexorter.onyx.domain.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Проверка обновлений по релизам GitHub и установка нового APK.
 *
 * Приложение раздаётся не через Play, поэтому обновление скачивается само
 * и передаётся системному установщику — иначе пришлось бы каждый раз ходить
 * на страницу релизов вручную.
 */
class UpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
) {

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()

    private var lastCheckAt = 0L

    val currentVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }

    /**
     * @return true, если проверка прошла успешно (даже когда обновления нет).
     */
    suspend fun check(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckAt < CHECK_INTERVAL_MS && _available.value != null) {
            return@withContext true
        }

        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) return@withContext false

                val release = json.decodeFromString(ReleaseDto.serializer(), body)
                if (release.draft || release.prerelease) {
                    _available.value = null
                    return@withContext true
                }

                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val remoteVersion = release.tag.removePrefix("v").trim()

                _available.value = if (apk != null && isNewer(remoteVersion, currentVersion)) {
                    UpdateInfo(
                        version = remoteVersion,
                        title = release.name.ifBlank { "Onyx $remoteVersion" },
                        notes = release.body.trim(),
                        apkUrl = apk.downloadUrl,
                        sizeBytes = apk.size,
                        pageUrl = release.pageUrl,
                    )
                } else {
                    null
                }
                lastCheckAt = now
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        _download.value = UpdateDownload.InProgress(0f)

        val file = withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val target = File(dir, "onyx-${info.version}.apk")

                val request = Request.Builder().url(info.apkUrl).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        error("Сервер вернул ${response.code}")
                    }
                    val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                    var read = 0L

                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                read += count
                                if (total > 0) {
                                    _download.value = UpdateDownload.InProgress(
                                        (read.toFloat() / total).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                    }
                }
                target
            } catch (e: Exception) {
                _download.value = UpdateDownload.Failed(e.message ?: "Не удалось скачать обновление")
                null
            }
        } ?: return

        _download.value = UpdateDownload.Ready
        launchInstaller(file)
    }

    fun resetDownload() {
        _download.value = UpdateDownload.Idle
    }

    /** Запасной путь: открыть страницу релиза, если установщик почему-то не поднялся. */
    fun openReleasePage(info: UpdateInfo) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) {
            _download.value = UpdateDownload.Failed("Не удалось открыть установщик")
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val a = remote.toVersionParts()
        val b = current.toVersionParts()
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun String.toVersionParts(): List<Int> =
        trim().removePrefix("v")
            .takeWhile { it.isDigit() || it == '.' }
            .split(".")
            .mapNotNull { it.toIntOrNull() }

    companion object {
        const val REPO_URL = "https://github.com/Vexorter42/onyx-schedule"
        const val DONATE_URL = "https://www.donationalerts.com/r/vexorter"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Vexorter42/onyx-schedule/releases/latest"
        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
