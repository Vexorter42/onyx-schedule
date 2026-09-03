package com.vexorter.onyx.data.repo

import com.vexorter.onyx.data.local.BranchEntity
import com.vexorter.onyx.data.local.GroupEntity
import com.vexorter.onyx.data.local.LessonEntity
import com.vexorter.onyx.data.local.TeacherEntity
import com.vexorter.onyx.data.local.YearEntity
import com.vexorter.onyx.domain.Branch
import com.vexorter.onyx.domain.Group
import com.vexorter.onyx.domain.Lesson
import com.vexorter.onyx.domain.Teacher
import com.vexorter.onyx.domain.Year
import java.time.LocalDate

internal fun BranchEntity.toDomain() = Branch(guid = guid, name = name.trim(), code = code.trim())

internal fun YearEntity.toDomain() = Year(guid = guid, name = name.trim(), code = code.trim())

internal fun GroupEntity.toDomain() = Group(
    guid = guid,
    name = name.trim(),
    category = category.trim(),
    owner = owner.trim(),
)

internal fun TeacherEntity.toDomain() = Teacher(
    guid = guid,
    name = name.trim(),
    position = position.trim(),
)

internal fun LessonEntity.toDomain(): Lesson? {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return Lesson(
        date = parsed,
        orderNum = orderNum,
        timeStart = timeStart,
        timeEnd = timeEnd,
        discipline = discipline,
        type = type,
        employee = employee,
        classroom = classroom,
        subGroup = subGroup,
        group = groupName,
    )
}
