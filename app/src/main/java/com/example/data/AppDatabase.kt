package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ComicEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comics ADD COLUMN tapZoneMode TEXT NOT NULL DEFAULT 'STANDARD'")
                db.execSQL("ALTER TABLE comics ADD COLUMN volumeKeysEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE comics ADD COLUMN volumeKeysInverted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE comics ADD COLUMN orientationLock TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE comics ADD COLUMN dualPageSplit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE comics ADD COLUMN colorFilter TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE comics ADD COLUMN backgroundColor INTEGER NOT NULL DEFAULT -16777216")
                db.execSQL("ALTER TABLE comics ADD COLUMN brightness REAL NOT NULL DEFAULT 1.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opencomic_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

