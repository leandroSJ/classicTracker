package com.classictracker.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.classictracker.data.models.*
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

data class FullBackupPackage(
    val vehicleConfig: VehicleConfig,
    val fuelRecords: List<FuelRecord>,
    val oilChanges: List<OilChange>,
    val tripSessions: List<TripSession>,
    val savedLocations: List<SavedLocation>,
    val appVersion: String = "2.0"
)

object DataTransferUtils {

    private val gson = Gson()

    fun exportData(
        context: Context,
        config: VehicleConfig,
        fuel: List<FuelRecord>,
        oil: List<OilChange>,
        trips: List<TripSession>,
        locations: List<SavedLocation>
    ): File? {
        return try {
            val pkg = FullBackupPackage(config, fuel, oil, trips, locations)
            val json = gson.toJson(pkg)
            
            val folder = File(context.getExternalFilesDir(null), "Backup")
            if (!folder.exists()) folder.mkdirs()
            
            val file = File(folder, "ClassicTracker_FullBackup.json")
            FileOutputStream(file).use { 
                it.write(json.toByteArray())
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun shareExportFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar Backup Classic Tracker"))
    }

    fun parseImportData(json: String): FullBackupPackage? {
        return try {
            gson.fromJson(json, FullBackupPackage::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
