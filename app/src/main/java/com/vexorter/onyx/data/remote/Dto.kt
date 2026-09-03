package com.vexorter.onyx.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

@Serializable
data class BranchDto(
    val guid: String,
    val code: String = "",
    val name: String = "",
)

@Serializable
data class YearDto(
    val guid: String,
    val code: String = "",
    val name: String = "",
)

@Serializable
data class GroupDto(
    val guid: String,
    val name: String = "",
    val owner: String = "",
)

/** Ответ get_groups_filter — группы, разложенные по категориям направлений. */
@Serializable
data class GroupCategoryDto(
    val name: String = "",
    val groups: List<GroupDto> = emptyList(),
    @SerialName("single_element") val singleElement: Boolean = false,
)

/** Ответ get_employees — поля тут с заглавной буквы, в отличие от остальных методов. */
@Serializable
data class EmployeeDto(
    @SerialName("GUID") val guid: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Position") val position: String = "",
)

@Serializable
data class LessonDto(
    @SerialName("Data") val date: String = "",
    @SerialName("DayOFWeek") val dayOfWeek: String = "",
    @SerialName("Time") val time: String = "",
    @SerialName("Time_start") val timeStart: String = "",
    @SerialName("Time_end") val timeEnd: String = "",
    @SerialName("Group") val group: String = "",
    @SerialName("SubGroup") val subGroup: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Discipline") val discipline: String = "",
    @SerialName("Employee") val employee: String = "",
    @SerialName("Classroom") val classroom: String = "",
)

@Serializable
data class ScheduleDateDto(
    @SerialName("to_sch") val toSch: String = "",
    val type: String = "",
    val back: String = "",
    val next: String = "",
    val day: Int = 0,
    @SerialName("day_string") val dayString: String = "",
    val month: Int = 0,
    val year: Int = 0,
    val monday: Int = 0,
    val sunday: Int = 0,
)

@Serializable
data class ScheduleResponseDto(
    @Serializable(with = ScheduleMapSerializer::class)
    val schedule: Map<String, List<LessonDto>> = emptyMap(),
    val date: ScheduleDateDto? = null,
)

/**
 * Бэкенд отдаёт `schedule` объектом («01.09.2026 - Вторник» -> список пар),
 * но на пустой неделе может прислать пустой массив. Разбираем оба варианта.
 */
object ScheduleMapSerializer : KSerializer<Map<String, List<LessonDto>>> {

    private val delegate = MapSerializer(String.serializer(), ListSerializer(LessonDto.serializer()))

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, List<LessonDto>>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Map<String, List<LessonDto>> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = input.decodeJsonElement()
        return if (element is JsonObject) {
            input.json.decodeFromJsonElement(delegate, element)
        } else {
            emptyMap()
        }
    }
}
