package com.vexorter.onyx.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM branches ORDER BY sortIndex")
    fun observeBranches(): Flow<List<BranchEntity>>

    @Query("SELECT COUNT(*) FROM branches")
    suspend fun branchCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranches(items: List<BranchEntity>)

    @Query("DELETE FROM branches")
    suspend fun clearBranches()

    @Transaction
    suspend fun replaceBranches(items: List<BranchEntity>) {
        clearBranches()
        insertBranches(items)
    }

    @Query("SELECT * FROM years ORDER BY sortIndex DESC")
    fun observeYears(): Flow<List<YearEntity>>

    @Query("SELECT COUNT(*) FROM years")
    suspend fun yearCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYears(items: List<YearEntity>)

    @Query("DELETE FROM years")
    suspend fun clearYears()

    @Transaction
    suspend fun replaceYears(items: List<YearEntity>) {
        clearYears()
        insertYears(items)
    }

    @Query("SELECT * FROM teachers WHERE branchGuid = :branchGuid ORDER BY sortIndex")
    fun observeTeachers(branchGuid: String): Flow<List<TeacherEntity>>

    @Query("SELECT COUNT(*) FROM teachers WHERE branchGuid = :branchGuid")
    suspend fun teacherCount(branchGuid: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeachers(items: List<TeacherEntity>)

    @Query("DELETE FROM teachers WHERE branchGuid = :branchGuid")
    suspend fun clearTeachers(branchGuid: String)

    @Transaction
    suspend fun replaceTeachers(branchGuid: String, items: List<TeacherEntity>) {
        clearTeachers(branchGuid)
        insertTeachers(items)
    }

    @Query("SELECT * FROM `groups` WHERE branchGuid = :branchGuid AND yearGuid = :yearGuid ORDER BY sortIndex")
    fun observeGroups(branchGuid: String, yearGuid: String): Flow<List<GroupEntity>>

    @Query("SELECT COUNT(*) FROM `groups` WHERE branchGuid = :branchGuid AND yearGuid = :yearGuid")
    suspend fun groupCount(branchGuid: String, yearGuid: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(items: List<GroupEntity>)

    @Query("DELETE FROM `groups` WHERE branchGuid = :branchGuid AND yearGuid = :yearGuid")
    suspend fun clearGroups(branchGuid: String, yearGuid: String)

    @Transaction
    suspend fun replaceGroups(branchGuid: String, yearGuid: String, items: List<GroupEntity>) {
        clearGroups(branchGuid, yearGuid)
        insertGroups(items)
    }
}

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM lessons WHERE weekKey = :weekKey ORDER BY date, orderNum, timeStart")
    fun observeWeek(weekKey: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM week_meta WHERE weekKey = :weekKey")
    fun observeWeekMeta(weekKey: String): Flow<WeekMetaEntity?>

    @Query("SELECT * FROM week_meta WHERE weekKey = :weekKey")
    suspend fun weekMeta(weekKey: String): WeekMetaEntity?

    @Query("SELECT * FROM lessons WHERE weekKey = :weekKey ORDER BY date, orderNum, timeStart")
    suspend fun weekLessons(weekKey: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE ownerGuid = :ownerGuid AND date = :date ORDER BY orderNum, timeStart")
    suspend fun lessonsOn(ownerGuid: String, date: String): List<LessonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(items: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeekMeta(meta: WeekMetaEntity)

    @Query("DELETE FROM lessons WHERE weekKey = :weekKey")
    suspend fun clearWeek(weekKey: String)

    @Transaction
    suspend fun replaceWeek(meta: WeekMetaEntity, lessons: List<LessonEntity>) {
        clearWeek(meta.weekKey)
        insertLessons(lessons)
        insertWeekMeta(meta)
    }

    /** Чистим всё, что дальше указанных границ, чтобы база не разрасталась. */
    @Query("DELETE FROM lessons WHERE weekStart < :fromWeek OR weekStart > :toWeek")
    suspend fun pruneLessons(fromWeek: String, toWeek: String)

    @Query("DELETE FROM week_meta WHERE weekStart < :fromWeek OR weekStart > :toWeek")
    suspend fun pruneWeekMeta(fromWeek: String, toWeek: String)

    @Transaction
    suspend fun prune(fromWeek: String, toWeek: String) {
        pruneLessons(fromWeek, toWeek)
        pruneWeekMeta(fromWeek, toWeek)
    }

    @Query("DELETE FROM lessons")
    suspend fun clearAllLessons()

    @Query("DELETE FROM week_meta")
    suspend fun clearAllWeekMeta()

    @Transaction
    suspend fun clearAllSchedule() {
        clearAllLessons()
        clearAllWeekMeta()
    }
}
