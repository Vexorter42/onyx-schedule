package com.vexorter.onyx.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ApiException(message: String, val code: Int? = null) : IOException(message)

/**
 * Сервер расписания стоит за DDoS-Guard и отвечает 451 тем, кто пришёл
 * не с российского адреса. Отличаем это от обычной сетевой ошибки, чтобы
 * не писать пользователю «нет интернета», когда интернет как раз есть.
 */
class GeoBlockedException : IOException("Сервер расписания недоступен из этой страны")

/**
 * Клиент публичного API расписания РУК.
 * Базовый адрес: https://api-schedule.ruc.su/api/v1
 */
class ScheduleApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = BASE_URL,
) {

    suspend fun getBranches(): List<BranchDto> =
        get("$baseUrl/get_branches", ListSerializer(BranchDto.serializer()))

    suspend fun getYears(): List<YearDto> =
        get("$baseUrl/get_years", ListSerializer(YearDto.serializer()))

    suspend fun getGroups(branchGuid: String, yearGuid: String): List<GroupCategoryDto> =
        get(
            "$baseUrl/get_groups_filter/$branchGuid/$yearGuid",
            ListSerializer(GroupCategoryDto.serializer())
        )

    /**
     * @param date дата в формате yyyy.MM.dd — вернётся неделя, в которую она попадает.
     *             Значение "no" означает текущую неделю.
     */
    suspend fun getGroupSchedule(
        branchGuid: String,
        groupGuid: String,
        date: String = CURRENT_WEEK,
    ): ScheduleResponseDto = get(
        "$baseUrl/get_schedule/group/$branchGuid/$groupGuid/$date",
        ScheduleResponseDto.serializer()
    )

    private suspend fun <T> get(url: String, deserializer: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 451) throw GeoBlockedException()
                if (response.code == 403 && body.contains("ddos-guard", ignoreCase = true)) {
                    throw GeoBlockedException()
                }
                if (!response.isSuccessful) {
                    throw ApiException("Сервер вернул ${response.code}", response.code)
                }
                if (body.isBlank()) {
                    throw ApiException("Пустой ответ сервера")
                }
                try {
                    json.decodeFromString(deserializer, body)
                } catch (e: Exception) {
                    throw ApiException("Не удалось разобрать ответ сервера: ${e.message}")
                }
            }
        }

    companion object {
        const val BASE_URL = "https://api-schedule.ruc.su/api/v1"
        const val CURRENT_WEEK = "no"
    }
}
