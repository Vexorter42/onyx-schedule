package com.vexorter.onyx.data.repo

import com.vexorter.onyx.data.remote.ApiException
import com.vexorter.onyx.data.remote.GeoBlockedException
import com.vexorter.onyx.domain.SyncResult
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Единая точка превращения исключений сети в понятный пользователю результат. */
internal suspend fun runSync(block: suspend () -> Unit): SyncResult =
    try {
        block()
        SyncResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: GeoBlockedException) {
        SyncResult.Error(
            message = "Сервер расписания не пускает с этого адреса",
            offline = false,
            geoBlocked = true,
        )
    } catch (e: UnknownHostException) {
        SyncResult.Error("Нет подключения к интернету", offline = true)
    } catch (e: SocketTimeoutException) {
        SyncResult.Error("Сервер не отвечает", offline = true)
    } catch (e: ApiException) {
        SyncResult.Error(e.message ?: "Ошибка сервера", offline = false)
    } catch (e: IOException) {
        SyncResult.Error("Нет связи с сервером расписания", offline = true)
    } catch (e: Exception) {
        SyncResult.Error(e.message ?: "Неизвестная ошибка", offline = false)
    }
