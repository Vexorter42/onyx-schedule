package com.vexorter.onyx.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BranchEntity::class,
        YearEntity::class,
        GroupEntity::class,
        LessonEntity::class,
        WeekMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "onyx.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
