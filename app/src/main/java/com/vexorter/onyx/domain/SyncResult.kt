package com.vexorter.onyx.domain

sealed interface SyncResult {

    data object Success : SyncResult

    /** [offline] — не достучались до сети; показываем кэш и мягкое предупреждение. */
    data class Error(val message: String, val offline: Boolean) : SyncResult

    val errorOrNull: Error? get() = this as? Error
}
