package com.vexorter.onyx.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Сохранённый профиль. Активный лежит в настройках, а здесь — весь список,
 * между которыми можно переключаться.
 */
@Entity(tableName = "saved_profiles")
data class SavedProfileEntity(
    @PrimaryKey val ownerGuid: String,
    val kind: String,
    val branchGuid: String,
    val branchName: String,
    val yearGuid: String,
    val yearName: String,
    val ownerName: String,
    val addedAt: Long,
)

@Dao
interface SavedProfileDao {

    @Query("SELECT * FROM saved_profiles ORDER BY addedAt")
    fun observeAll(): Flow<List<SavedProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SavedProfileEntity)

    @Query("DELETE FROM saved_profiles WHERE ownerGuid = :ownerGuid")
    suspend fun delete(ownerGuid: String)

    @Query("SELECT * FROM saved_profiles ORDER BY addedAt LIMIT 1")
    suspend fun first(): SavedProfileEntity?

    @Query("SELECT COUNT(*) FROM saved_profiles")
    suspend fun count(): Int
}
