package com.example.susnet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NetworkEntity::class,
        AccessPointEntity::class,
        DetectionEventEntity::class,
        NetworkConfigEntity::class
    ],
    version = 3
)
abstract class SusnetDatabase : RoomDatabase() {

    abstract fun baselineDao(): BaselineDao
    abstract fun detectionEventDao(): DetectionEventDao
    abstract fun networkConfigDao(): NetworkConfigDao

    companion object {
        @Volatile
        private var instance: SusnetDatabase? = null

        fun getInstance(context: Context): SusnetDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SusnetDatabase::class.java,
                    "susnet.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}