package com.classictracker.data.db

import androidx.room.*
import com.classictracker.data.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel_records ORDER BY timestamp DESC")
    fun getAllFuelRecords(): Flow<List<FuelRecord>>

    @Query("SELECT * FROM fuel_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastNRecords(limit: Int): List<FuelRecord>

    @Query("SELECT * FROM fuel_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastRecord(): FuelRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FuelRecord): Long

    @Delete
    suspend fun delete(record: FuelRecord)

    @Query("""
    SELECT AVG(consumption) FROM (
        SELECT consumption FROM fuel_records 
        WHERE consumption > 0 
        ORDER BY timestamp DESC 
        LIMIT 5
    )
""")
    suspend fun getAvgConsumptionLast5(): Double?

    @Query("SELECT SUM(totalCost) FROM fuel_records WHERE timestamp >= :since")
    suspend fun getTotalSpentSince(since: Long): Double?

    @Query("SELECT SUM(liters) FROM fuel_records WHERE timestamp >= :since")
    suspend fun getTotalLitersSince(since: Long): Double?

    @Query("SELECT * FROM fuel_records ORDER BY timestamp ASC")
    suspend fun getAllFuelRecordsList(): List<FuelRecord>

    @Update
    suspend fun update(record: FuelRecord)
}

@Dao
interface OilChangeDao {
    @Query("SELECT * FROM oil_changes ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOilChange(): OilChange?

    @Query("SELECT * FROM oil_changes ORDER BY timestamp DESC")
    fun getAllOilChanges(): Flow<List<OilChange>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(oilChange: OilChange): Long

    @Delete
    suspend fun delete(oilChange: OilChange)
}

@Dao
interface RoutePointDao {
    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSession(sessionId: String): List<RoutePoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: RoutePoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<RoutePoint>)

    @Delete
    suspend fun delete(points: List<RoutePoint>)

    @Query("DELETE FROM route_points WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM route_points")
    suspend fun deleteAllPoints()
}

@Dao
interface TripSessionDao {
    @Query("SELECT * FROM trip_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<TripSession>>

    @Query("SELECT * FROM trip_sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveSession(): TripSession?

    @Query("SELECT * FROM trip_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): TripSession?

    @Query("SELECT * FROM trip_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastSession(): TripSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TripSession)

    @Delete
    suspend fun delete(session: TripSession)

    @Update
    suspend fun update(session: TripSession)

    @Query("SELECT SUM(distanceKm) FROM trip_sessions WHERE startTime >= :since")
    suspend fun getTotalDistanceSince(since: Long): Double?

    @Query("DELETE FROM trip_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations")
    suspend fun getAllLocationsList(): List<SavedLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation): Long

    @Delete
    suspend fun delete(location: SavedLocation)

    @Update
    suspend fun update(location: SavedLocation)
}

@Dao
interface ObdDao {
    @Query("SELECT * FROM obd_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ObdRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ObdRecord): Long

    @Delete
    suspend fun delete(record: ObdRecord)

    @Query("DELETE FROM obd_records")
    suspend fun deleteAll()
}
