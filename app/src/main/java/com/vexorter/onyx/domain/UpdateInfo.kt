package com.vexorter.onyx.domain

/** Релиз с GitHub, который новее установленной версии. */
data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

/** Итог проверки: важно отличать «обновление есть» от «запрос прошёл». */
sealed interface UpdateCheckResult {
    data class Found(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class InProgress(val progress: Float) : UpdateDownload
    data object Ready : UpdateDownload
    data class Failed(val message: String) : UpdateDownload
}
