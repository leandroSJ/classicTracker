package com.classictracker.data.repository

import android.content.Context
import com.classictracker.data.models.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import android.util.Log

class SyncManager(private val context: Context) {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    
    val currentUser get() = auth.currentUser
    val isLoggedIn get() = auth.currentUser != null

    // ─── Sincronização de Dados ──────────────────────────────────────────────

    suspend fun syncFuelRecords(records: List<FuelRecord>): Boolean {
        if (records.isEmpty()) return true
        val userId = auth.currentUser?.uid ?: run {
            Log.e("CT_SYNC", "syncFuelRecords: usuário não logado, abortando sync")
            return false
        }
        return try {
            records.chunked(500).forEach { chunk ->
                val batch = db.batch()
                val collection = db.collection("users").document(userId).collection("fuel_records")
                chunk.forEach { record ->
                    // Usa o timestamp como ID do documento para garantir ordem cronológica e evitar sobreposição de IDs após resets
                    val docId = "fuel_${record.timestamp}"
                    val docRef = collection.document(docId)
                    batch.set(docRef, record)
                }
                batch.commit().await()
            }
            Log.d("CT_SYNC", "Sucesso: ${records.size} abastecimentos sincronizados no Firebase")
            true
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao sincronizar abastecimentos: ${e.message}", e)
            false
        }
    }

    suspend fun syncOilChanges(records: List<OilChange>): Boolean {
        if (records.isEmpty()) return true
        val userId = auth.currentUser?.uid ?: run {
            Log.e("CT_SYNC", "syncOilChanges: usuário não logado, abortando sync")
            return false
        }
        return try {
            records.chunked(500).forEach { chunk ->
                val batch = db.batch()
                val collection = db.collection("users").document(userId).collection("oil_changes")
                chunk.forEach { record ->
                    val docId = "oil_${record.timestamp}"
                    val docRef = collection.document(docId)
                    batch.set(docRef, record)
                }
                batch.commit().await()
            }
            Log.d("CT_SYNC", "Sucesso: ${records.size} trocas de óleo sincronizadas")
            true
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao sincronizar óleo: ${e.message}", e)
            false
        }
    }

    suspend fun syncTripSessions(records: List<TripSession>): Boolean {
        if (records.isEmpty()) return true
        val userId = auth.currentUser?.uid ?: run {
            Log.e("CT_SYNC", "syncTripSessions: usuário não logado, abortando sync")
            return false
        }
        return try {
            records.chunked(500).forEach { chunk ->
                val batch = db.batch()
                val collection = db.collection("users").document(userId).collection("trip_sessions")
                chunk.forEach { record ->
                    val docRef = collection.document(record.sessionId)
                    batch.set(docRef, record)
                }
                batch.commit().await()
            }
            Log.d("CT_SYNC", "Sucesso: ${records.size} viagens sincronizadas")
            true
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao sincronizar viagens: ${e.message}", e)
            false
        }
    }

    suspend fun syncRoutePoints(sessionId: String, points: List<RoutePoint>): Boolean {
        if (points.isEmpty()) return true
        val userId = auth.currentUser?.uid ?: run {
            Log.e("CT_SYNC", "syncRoutePoints: usuário não logado, abortando sync")
            return false
        }
        return try {
            // Dividir pontos em grupos de 500 para evitar erro de "Transaction too big"
            points.chunked(500).forEach { chunk ->
                val batch = db.batch()
                val collection = db.collection("users").document(userId)
                    .collection("trip_sessions").document(sessionId).collection("points")
                chunk.forEach { point ->
                    // Usamos o timestamp como ID do documento para evitar que IDs autogerados 
                    // do banco local causem conflitos ou sobrescritas na nuvem
                    val docId = "pt_${point.timestamp}"
                    val docRef = collection.document(docId)
                    batch.set(docRef, point)
                }
                batch.commit().await()
            }
            Log.d("CT_SYNC", "Sincronizados ${points.size} pontos para a sessão $sessionId")
            true
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao sincronizar pontos da rota: ${e.message}", e)
            false
        }
    }

    suspend fun syncVehicleConfig(config: VehicleConfig): Boolean {
        val userId = auth.currentUser?.uid ?: run {
            Log.e("CT_SYNC", "syncVehicleConfig: usuário não logado, abortando sync")
            return false
        }
        return try {
            db.collection("users").document(userId)
                .collection("config").document("vehicle")
                .set(config).await()
            Log.d("CT_SYNC", "Configuração salva no Firebase")
            true
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao salvar config: ${e.message}", e)
            false
        }
    }

    suspend fun syncSavedLocations(locations: List<SavedLocation>): Boolean {
        if (locations.isEmpty()) return true
        val userId = auth.currentUser?.uid ?: run {
            Log.e("CT_SYNC", "syncSavedLocations: usuário não logado, abortando sync")
            return false
        }
        return try {
            locations.chunked(500).forEach { chunk ->
                val batch = db.batch()
                val collection = db.collection("users").document(userId).collection("saved_locations")
                chunk.forEach { loc ->
                    val docRef = collection.document(loc.id.toString())
                    batch.set(docRef, loc)
                }
                batch.commit().await()
            }
            Log.d("CT_SYNC", "Sucesso: ${locations.size} locais sincronizados")
            true
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao sincronizar locais: ${e.message}", e)
            false
        }
    }

    // ─── Exclusão de Dados ──────────────────────────────────────────────────

    suspend fun deleteFuelRecordFromCloud(record: FuelRecord) {
        val userId = auth.currentUser?.uid ?: return
        try {
            val docId = "fuel_${record.timestamp}"
            db.collection("users").document(userId).collection("fuel_records").document(docId).delete().await()
            Log.d("CT_SYNC", "Sucesso: Abastecimento excluído da nuvem")
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao excluir abastecimento da nuvem: ${e.message}")
        }
    }

    suspend fun deleteOilChangeFromCloud(record: OilChange) {
        val userId = auth.currentUser?.uid ?: return
        try {
            val docId = "oil_${record.timestamp}"
            db.collection("users").document(userId).collection("oil_changes").document(docId).delete().await()
            Log.d("CT_SYNC", "Sucesso: Troca de óleo excluída da nuvem")
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao excluir óleo da nuvem: ${e.message}")
        }
    }

    suspend fun deleteTripSessionFromCloud(sessionId: String) {
        val userId = auth.currentUser?.uid ?: return
        try {
            val sessionRef = db.collection("users").document(userId).collection("trip_sessions").document(sessionId)
            
            // Exclui subcoleção de pontos primeiro (Firestore não exclui subcoleções automaticamente)
            val points = sessionRef.collection("points").get().await()
            val batch = db.batch()
            points.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()

            // Exclui o documento da sessão
            sessionRef.delete().await()
            Log.d("CT_SYNC", "Sucesso: Viagem e pontos excluídos da nuvem")
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao excluir viagem da nuvem: ${e.message}")
        }
    }

    suspend fun deleteSavedLocationFromCloud(location: SavedLocation) {
        val userId = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(userId).collection("saved_locations").document(location.id.toString()).delete().await()
            Log.d("CT_SYNC", "Sucesso: Local salvo excluído da nuvem")
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao excluir local da nuvem: ${e.message}")
        }
    }

    suspend fun deleteAllTripsFromCloud() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val sessions = db.collection("users").document(userId).collection("trip_sessions").get().await()
            for (doc in sessions.documents) {
                deleteTripSessionFromCloud(doc.id)
            }
            Log.d("CT_SYNC", "Sucesso: Todas as viagens e pontos excluídos da nuvem")
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro ao excluir todas as viagens da nuvem: ${e.message}")
        }
    }

    // ─── Recuperação de Dados ────────────────────────────────────────────────

    suspend fun getSavedLocationsFromCloud(): List<SavedLocation> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users").document(userId).collection("saved_locations").get().await()
            val list = mutableListOf<SavedLocation>()
            for (doc in snapshot.documents) {
                try {
                    val record = doc.toObject(SavedLocation::class.java)
                    if (record != null) list.add(record)
                } catch (e: Exception) {
                    val record = SavedLocation(
                        id = doc.id.toLongOrNull() ?: 0L,
                        name = doc.getString("name") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        radiusMeters = doc.getDouble("radiusMeters")?.toFloat() ?: 100f,
                        isHome = doc.getBoolean("isHome") ?: false
                    )
                    list.add(record)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFuelRecordsFromCloud(): List<FuelRecord> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users").document(userId).collection("fuel_records").get().await()
            val list = mutableListOf<FuelRecord>()
            for (doc in snapshot.documents) {
                try {
                    // Mapeamento manual ultra-resistente
                    val record = FuelRecord(
                        id = 0, // Força o Room a gerar um novo ID local para evitar conflitos
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        fuelType = doc.getString("fuelType") ?: "GASOLINA",
                        liters = doc.getDouble("liters") ?: 0.0,
                        pricePerLiter = doc.getDouble("pricePerLiter") ?: 0.0,
                        totalCost = doc.getDouble("totalCost") ?: 0.0,
                        odometerKm = doc.getDouble("odometerKm") ?: 0.0,
                        isReserve = doc.getBoolean("isReserve") ?: doc.getBoolean("reserve") ?: false,
                        kmSinceLastFill = doc.getDouble("kmSinceLastFill") ?: 0.0,
                        consumption = doc.getDouble("consumption") ?: 0.0
                    )
                    list.add(record)
                } catch (e: Exception) {
                    Log.e("CT_SYNC", "Falha ao converter doc fuel ${doc.id}: ${e.message}")
                }
            }
            list
        } catch (e: Exception) {
            Log.e("CT_SYNC", "Erro crítico ao baixar abastecimentos: ${e.message}")
            emptyList()
        }
    }

    suspend fun getOilChangesFromCloud(): List<OilChange> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users").document(userId).collection("oil_changes").get().await()
            val list = mutableListOf<OilChange>()
            for (doc in snapshot.documents) {
                try {
                    val record = doc.toObject(OilChange::class.java)
                    if (record != null) list.add(record)
                } catch (e: Exception) {
                    val record = OilChange(
                        id = doc.id.toLongOrNull() ?: 0L,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        odometerKm = doc.getDouble("odometerKm") ?: 0.0,
                        intervalKm = doc.getDouble("intervalKm") ?: 5000.0,
                        notes = doc.getString("notes") ?: ""
                    )
                    list.add(record)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTripSessionsFromCloud(): List<TripSession> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users").document(userId).collection("trip_sessions").get().await()
            val list = mutableListOf<TripSession>()
            for (doc in snapshot.documents) {
                try {
                    val record = doc.toObject(TripSession::class.java)
                    if (record != null) list.add(record)
                } catch (e: Exception) {
                    val record = TripSession(
                        sessionId = doc.getString("sessionId") ?: doc.id,
                        startTime = doc.getLong("startTime") ?: 0L,
                        endTime = doc.getLong("endTime"),
                        startOdometerKm = doc.getDouble("startOdometerKm") ?: 0.0,
                        endOdometerKm = doc.getDouble("endOdometerKm"),
                        distanceKm = doc.getDouble("distanceKm") ?: 0.0,
                        durationSeconds = doc.getLong("durationSeconds") ?: 0L,
                        pointAName = doc.getString("pointAName") ?: "",
                        pointBName = doc.getString("pointBName") ?: "",
                        pointALat = doc.getDouble("pointALat") ?: 0.0,
                        pointALng = doc.getDouble("pointALng") ?: 0.0,
                        pointBLat = doc.getDouble("pointBLat") ?: 0.0,
                        pointBLng = doc.getDouble("pointBLng") ?: 0.0,
                        fuelUsedLiters = doc.getDouble("fuelUsedLiters") ?: 0.0,
                        notes = doc.getString("notes") ?: "",
                        avgSpeedKmh = doc.getDouble("avgSpeedKmh") ?: 0.0,
                        maxSpeedKmh = doc.getDouble("maxSpeedKmh") ?: 0.0
                    )
                    list.add(record)
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRoutePointsFromCloud(sessionId: String): List<RoutePoint> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("users").document(userId)
                .collection("trip_sessions").document(sessionId).collection("points").get().await()
            val list = mutableListOf<RoutePoint>()
            for (doc in snapshot.documents) {
                try {
                    val record = RoutePoint(
                        id = 0, // Novo ID local
                        sessionId = doc.getString("sessionId") ?: sessionId,
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        speedKmh = doc.getDouble("speedKmh") ?: 0.0,
                        accuracyMeters = doc.getDouble("accuracyMeters")?.toFloat() ?: doc.getDouble("accuracy")?.toFloat() ?: 0f
                    )
                    list.add(record)
                } catch (e: Exception) {
                    Log.e("CT_SYNC", "Erro no ponto GPS ${doc.id}")
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getVehicleConfigFromCloud(): VehicleConfig? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            db.collection("users").document(userId).collection("config").document("vehicle")
                .get().await().toObject(VehicleConfig::class.java)
        } catch (e: Exception) { null }
    }

    fun signOut() {
        auth.signOut()
    }
}
