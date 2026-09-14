package com.classictracker.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Fuel Fill-Up ───────────────────────────────────────────────────────────

@Entity(tableName = "fuel_records")
data class FuelRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val fuelType: String = "",           
    val liters: Double = 0.0,
    val pricePerLiter: Double = 0.0,
    val totalCost: Double = 0.0,
    val odometerKm: Double = 0.0,
    val isReserve: Boolean = false, 
    val kmSinceLastFill: Double = 0.0,
    val consumption: Double = 0.0   
)

// ─── Oil Change ──────────────────────────────────────────────────────────────

@Entity(tableName = "oil_changes")
data class OilChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val odometerKm: Double = 0.0,
    val intervalKm: Double = 5000.0, 
    val notes: String = ""
)

// ─── GPS Route Point ─────────────────────────────────────────────────────────

@Entity(tableName = "route_points")
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val speedKmh: Double = 0.0,
    val accuracyMeters: Float = 0f
)

// ─── Trip Session ────────────────────────────────────────────────────────────

@Entity(tableName = "trip_sessions")
data class TripSession(
    @PrimaryKey val sessionId: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val startOdometerKm: Double = 0.0,
    val endOdometerKm: Double? = null,
    val distanceKm: Double = 0.0,
    val durationSeconds: Long = 0,
    val pointAName: String = "",
    val pointBName: String = "",
    val pointALat: Double = 0.0,
    val pointALng: Double = 0.0,
    val pointBLat: Double = 0.0,
    val pointBLng: Double = 0.0,
    val fuelUsedLiters: Double = 0.0,
    val notes: String = "",
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0
)

// ─── Saved Location (Ponto A / Ponto B) ─────────────────────────────────────

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 20f,
    val isHome: Boolean = false
)

// ─── Vehicle Config ──────────────────────────────────────────────────────────

data class VehicleConfig(
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val manufacturingYear: Int = 0,
    val engineCC: String = "",
    val tankLiters: Double = 0.0,
    val reserveLiters: Double = 0.0,
    val currentOdometerKm: Double = 0.0,
    val oilChangeIntervalKm: Double = 5000.0,
    val hasAC: Boolean = false,
    val hasPowerSteering: Boolean = false
)

// ─── OBD Data ────────────────────────────────────────────────────────────────

data class ObdData(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantTempC: Int? = null,
    val throttlePercent: Double? = null,
    val fuelLevelPercent: Double? = null,
    val intakeAirTempC: Int? = null,
    val engineLoadPercent: Double? = null,
    val batteryVoltage: Double? = null,
    val fuelTrimShortPercent: Double? = null,
    val fuelTrimLongPercent: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "obd_records")
data class ObdRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantTempC: Int? = null,
    val fuelLevelPercent: Double? = null,
    val batteryVoltage: Double? = null,
    val engineLoadPercent: Double? = null,
    val notes: String = ""
)
