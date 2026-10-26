package com.example.susnet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NetworkEntity::class,
        AccessPointEntity::class,
        DetectionEventEntity::class,
        NetworkConfigEntity::class
    ],
    version = 4
)
abstract class SusnetDatabase : RoomDatabase() {

    abstract fun baselineDao(): BaselineDao
    abstract fun detectionEventDao(): DetectionEventDao
    abstract fun networkConfigDao(): NetworkConfigDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE access_points ADD COLUMN userTrusted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE network_configs ADD COLUMN userTrusted INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: SusnetDatabase? = null

        fun getInstance(context: Context): SusnetDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SusnetDatabase::class.java,
                    "susnet.db"
                ).addMigrations(MIGRATION_3_4).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
