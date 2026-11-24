package com.harimu.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        VaultKeyEntity::class,
        JobEntity::class,
        VideoProjectEntity::class,
        AudioProjectEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun vaultKeyDao(): VaultKeyDao
    abstract fun jobDao(): JobDao
    abstract fun videoProjectDao(): VideoProjectDao
    abstract fun audioProjectDao(): AudioProjectDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "harimu.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}

