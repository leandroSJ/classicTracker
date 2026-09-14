package com.classictracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.classictracker.data.models.VehicleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "classic_tracker_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_BRAND = stringPreferencesKey("vehicle_brand")
        val KEY_MODEL = stringPreferencesKey("vehicle_model")
        val KEY_YEAR = intPreferencesKey("vehicle_year")
        val KEY_MANUF_YEAR = intPreferencesKey("vehicle_manuf_year")
        val KEY_ENGINE = stringPreferencesKey("vehicle_engine")
        val KEY_TANK = doublePreferencesKey("vehicle_tank")
        val KEY_RESERVE = doublePreferencesKey("vehicle_reserve")
        val KEY_ODOMETER = doublePreferencesKey("vehicle_odometer")
        val KEY_OIL_INTERVAL = doublePreferencesKey("oil_interval")
        val KEY_HAS_AC = booleanPreferencesKey("has_ac")
        val KEY_HAS_PS = booleanPreferencesKey("has_power_steering")
        val KEY_TRACKING_ACTIVE = booleanPreferencesKey("tracking_active")
        val KEY_CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
        val KEY_SELECTED_VOICE = stringPreferencesKey("selected_voice")
        val KEY_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: true // Padrão escuro
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    val selectedVoice: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_VOICE]
    }

    suspend fun saveSelectedVoice(voiceName: String?) {
        context.dataStore.edit { prefs ->
            if (voiceName == null) prefs.remove(KEY_SELECTED_VOICE)
            else prefs[KEY_SELECTED_VOICE] = voiceName
        }
    }

    val vehicleConfig: Flow<VehicleConfig> = context.dataStore.data.map { prefs ->
        VehicleConfig(
            brand = prefs[KEY_BRAND] ?: "",
            model = prefs[KEY_MODEL] ?: "",
            year = prefs[KEY_YEAR] ?: 0,
            manufacturingYear = prefs[KEY_MANUF_YEAR] ?: 0,
            engineCC = prefs[KEY_ENGINE] ?: "",
            tankLiters = prefs[KEY_TANK] ?: 0.0,
            reserveLiters = prefs[KEY_RESERVE] ?: 0.0,
            currentOdometerKm = prefs[KEY_ODOMETER] ?: 0.0,
            oilChangeIntervalKm = prefs[KEY_OIL_INTERVAL] ?: 5000.0,
            hasAC = prefs[KEY_HAS_AC] ?: false,
            hasPowerSteering = prefs[KEY_HAS_PS] ?: false
        )
    }

    val currentSessionId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_SESSION_ID]
    }

    suspend fun saveVehicleConfig(config: VehicleConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BRAND] = config.brand
            prefs[KEY_MODEL] = config.model
            prefs[KEY_YEAR] = config.year
            prefs[KEY_MANUF_YEAR] = config.manufacturingYear
            prefs[KEY_ENGINE] = config.engineCC
            prefs[KEY_TANK] = config.tankLiters
            prefs[KEY_RESERVE] = config.reserveLiters
            prefs[KEY_ODOMETER] = config.currentOdometerKm
            prefs[KEY_OIL_INTERVAL] = config.oilChangeIntervalKm
            prefs[KEY_HAS_AC] = config.hasAC
            prefs[KEY_HAS_PS] = config.hasPowerSteering
        }
    }

    suspend fun updateOdometer(km: Double) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ODOMETER] = km
        }
    }

    suspend fun setCurrentSessionId(sessionId: String?) {
        context.dataStore.edit { prefs ->
            if (sessionId == null) prefs.remove(KEY_CURRENT_SESSION_ID)
            else prefs[KEY_CURRENT_SESSION_ID] = sessionId
        }
    }
}
