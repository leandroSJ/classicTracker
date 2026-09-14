package com.classictracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.classictracker.data.models.*

@Database(
    entities = [
        FuelRecord::class,
        OilChange::class,
        RoutePoint::class,
        TripSession::class,
        SavedLocation::class,
        ObdRecord::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fuelDao(): FuelDao
    abstract fun oilChangeDao(): OilChangeDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun tripSessionDao(): TripSessionDao
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun obdDao(): ObdDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "classictracker.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
