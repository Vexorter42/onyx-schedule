package com.vexorter.onyx.domain

sealed interface SyncResult {

    data object Success : SyncResult

    /**
     * [offline] — не достучались до сети; показываем кэш и мягкое предупреждение.
     * [geoBlocked] — сеть есть, но сервер не пускает по стране: подсказка нужна другая.
     */
    data class Error(
        val message: String,
        val offline: Boolean,
        val geoBlocked: Boolean = false,
    ) : SyncResult

    val errorOrNull: Error? get() = this as? Error
}
