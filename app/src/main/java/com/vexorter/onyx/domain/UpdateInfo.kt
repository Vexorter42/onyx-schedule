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

sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class InProgress(val progress: Float) : UpdateDownload
    data object Ready : UpdateDownload
    data class Failed(val message: String) : UpdateDownload
}
