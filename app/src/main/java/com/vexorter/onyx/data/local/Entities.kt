package com.vexorter.onyx.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val guid: String,
    val name: String,
    val code: String,
    val sortIndex: Int,
)

@Entity(tableName = "years")
data class YearEntity(
    @PrimaryKey val guid: String,
    val name: String,
    val code: String,
    val sortIndex: Int,
)

@Entity(
    tableName = "groups",
    indices = [Index(value = ["branchGuid", "yearGuid"])]
)
data class GroupEntity(
    @PrimaryKey val guid: String,
    val branchGuid: String,
    val yearGuid: String,
    val name: String,
    val category: String,
    val owner: String,
    val sortIndex: Int,
)

/**
 * Одна пара. Неделя целиком идентифицируется [weekKey] = "<guid группы>|<понедельник в ISO>".
 */
@Entity(
    tableName = "lessons",
    indices = [Index(value = ["weekKey"]), Index(value = ["date"])]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekKey: String,
    val ownerGuid: String,
    val weekStart: String,
    val date: String,
    val orderNum: Int,
    val timeStart: String,
    val timeEnd: String,
    val discipline: String,
    val type: String,
    val employee: String,
    val classroom: String,
    val subGroup: String,
)

/** Метка о том, что неделя выкачана целиком (нужна, чтобы отличать «пусто» от «не загружено»). */
@Entity(tableName = "week_meta")
data class WeekMetaEntity(
    @PrimaryKey val weekKey: String,
    val ownerGuid: String,
    val weekStart: String,
    val updatedAt: Long,
)
