package com.harimu.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        VaultKeyEntity::class,
        JobEntity::class,
        VideoProjectEntity::class,
        AudioProjectEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun vaultKeyDao(): VaultKeyDao
    abstract fun jobDao(): JobDao
    abstract fun videoProjectDao(): VideoProjectDao
    abstract fun audioProjectDao(): AudioProjectDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN jobId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN outputsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN textOutput TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE history ADD COLUMN errorMessage TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE jobs ADD COLUMN isEnglish INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE jobs ADD COLUMN executionMode TEXT NOT NULL DEFAULT 'WORKER'")
                db.execSQL("ALTER TABLE jobs ADD COLUMN inputJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE jobs ADD COLUMN inputSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE jobs ADD COLUMN outputsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE jobs ADD COLUMN textOutput TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE jobs ADD COLUMN isRetryable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE jobs ADD COLUMN startedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE jobs ADD COLUMN completedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "harimu.db"
        )
            .addMigrations(MIGRATION_4_5)
            // Hanya downgrade yang boleh destruktif. Upgrade versi skema WAJIB punya
            // Migration eksplisit (lihat docs/DECISIONS.md §18) supaya riwayat, favorit,
            // kunci vault, dan proyek tidak pernah terhapus diam-diam.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}

