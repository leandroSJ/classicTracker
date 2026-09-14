package com.classictracker.data.repository

import com.classictracker.data.db.*
import com.classictracker.data.models.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.math.*

class AppRepository(
    private val fuelDao: FuelDao,
    private val oilChangeDao: OilChangeDao,
    private val routePointDao: RoutePointDao,
    private val tripSessionDao: TripSessionDao,
    private val savedLocationDao: SavedLocationDao,
    private val obdDao: ObdDao
) {

    // ─── Fuel ────────────────────────────────────────────────────────────────

    val allFuelRecords: Flow<List<FuelRecord>> = fuelDao.getAllFuelRecords()

    suspend fun addFuelRecord(
        fuelType: String,
        liters: Double,
        pricePerLiter: Double,
        odometerKm: Double,
        isReserve: Boolean = false,
        hasAC: Boolean = false
    ): FuelRecord {
        val lastRecord = fuelDao.getLastRecord()

        // Km percorridos desde o último abastecimento
        val kmSinceLast = if (lastRecord != null) {
            val distFromTrips = tripSessionDao.getTotalDistanceSince(lastRecord.timestamp) ?: 0.0
            when {
                distFromTrips > 0 -> distFromTrips
                odometerKm > 0 && lastRecord.odometerKm > 0 -> odometerKm - lastRecord.odometerKm
                else -> 0.0
            }
        } else 0.0

        var rawConsumption = if (kmSinceLast > 0 && liters > 0) kmSinceLast / liters else 0.0
        
        // --- LÓGICA DE AR CONDICIONADO ---
        // Se o ar condicionado estava ligado durante o período, o consumo real foi PIOR.
        // Estimamos uma penalidade de 10% a 15% na média de km/l.
        if (hasAC && rawConsumption > 0) {
            rawConsumption *= 0.88 // Reduz a média em 12%
        }

        // Filtra valores impossíveis para o 1.0 Flex (abaixo de 4 ou acima de 22 km/l)
        val consumption = if (rawConsumption in 4.0..22.0) rawConsumption else 0.0

        android.util.Log.d("CT_REPO", "addFuelRecord: kmSinceLast=$kmSinceLast liters=$liters consumption=$consumption")

        val record = FuelRecord(
            fuelType      = fuelType,
            liters        = liters,
            pricePerLiter = pricePerLiter,
            totalCost     = liters * pricePerLiter,
            odometerKm    = odometerKm,
            isReserve     = isReserve,
            kmSinceLastFill = kmSinceLast,
            consumption   = consumption
        )
        val id = fuelDao.insert(record)
        return record.copy(id = id)
    }

    /**
     * Consumo médio ponderado por litros considerando TODO o histórico.
     * Ponderação: registros com mais litros têm maior peso (representam viagens mais longas).
     * Ignora amostras zeradas (abastecimentos sem km anterior calculado).
     */
    suspend fun getAverageConsumption(): Double {
        val allRecords = fuelDao.getAllFuelRecordsList()
        val valid = allRecords.filter { it.consumption > 0 && it.liters > 0 }
        if (valid.isEmpty()) return 0.0

        // Média ponderada: Σ(consumo × litros) / Σ(litros)
        val totalKm    = valid.sumOf { it.consumption * it.liters }
        val totalLiters = valid.sumOf { it.liters }
        return if (totalLiters > 0) totalKm / totalLiters else 0.0
    }

    suspend fun addFuelRecordCloud(record: FuelRecord) = fuelDao.insert(record)
    suspend fun addOilChangeCloud(record: OilChange) = oilChangeDao.insert(record)
    suspend fun addTripSessionCloud(record: TripSession) = tripSessionDao.insert(record)
    suspend fun addSavedLocationCloud(record: SavedLocation) = savedLocationDao.insert(record)
    suspend fun addRoutePointCloud(record: RoutePoint) = routePointDao.insert(record)

    suspend fun getLastFuelRecord(): FuelRecord? = fuelDao.getLastRecord()
    suspend fun deleteFuelRecord(record: FuelRecord) = fuelDao.delete(record)

    /**
     * Autonomia estimada com base no combustível restante.
     *
     * Lógica:
     * 1. Se o último abastecimento foi "tanque cheio" → calcula combustível consumido
     *    desde então usando km rodados / consumo médio, e subtrai do tanque.
     * 2. Se foi "reserva" → usa apenas o volume da reserva.
     * 3. Sem histórico → usa 80% do tanque como estimativa conservadora.
     */
    suspend fun estimateRemainingRange(
        tankLiters: Double,
        reserveLiters: Double,
        avgConsumption: Double
    ): Double {
        if (avgConsumption <= 0) return 0.0

        val lastFuel = fuelDao.getLastRecord()
            ?: return (tankLiters * 0.8) * avgConsumption   // sem histórico: 80% do tanque

        // Km rodados desde o último abastecimento (GPS prioritário, odômetro fallback)
        val kmSinceLastFuel = run {
            val fromTrips = tripSessionDao.getTotalDistanceSince(lastFuel.timestamp) ?: 0.0
            fromTrips.takeIf { it > 0 } ?: 0.0
        }

        val remainingLiters = if (lastFuel.isReserve) {
            // Já está na reserva — usa litros da reserva menos o que já consumiu nela
            val consumedInReserve = if (avgConsumption > 0) kmSinceLastFuel / avgConsumption else 0.0
            maxOf(0.0, reserveLiters - consumedInReserve)
        } else {
            // Abastecimento normal — subtrai o que já foi consumido desde então
            val consumed = if (avgConsumption > 0) kmSinceLastFuel / avgConsumption else 0.0
            maxOf(0.0, lastFuel.liters - consumed)
        }

        android.util.Log.d("CT_REPO",
            "estimateRange: kmSinceLastFuel=$kmSinceLastFuel remaining=${remainingLiters}L avg=$avgConsumption")

        return remainingLiters * avgConsumption
    }

    /**
     * Custo por km: total gasto em combustível / total de km rodados no histórico.
     * Considera apenas registros com km calculados para não distorcer.
     */
    suspend fun getCostPerKm(): Double {
        val allRecords = fuelDao.getAllFuelRecordsList()
        val valid = allRecords.filter { it.kmSinceLastFill > 0 && it.totalCost > 0 }
        if (valid.isEmpty()) return 0.0

        val totalCost = valid.sumOf { it.totalCost }
        val totalKm   = valid.sumOf { it.kmSinceLastFill }
        return if (totalKm > 0) totalCost / totalKm else 0.0
    }

    // ─── Oil ─────────────────────────────────────────────────────────────────

    val allOilChanges: Flow<List<OilChange>> = oilChangeDao.getAllOilChanges()

    suspend fun addOilChange(odometerKm: Double, intervalKm: Double = 5000.0, notes: String = "") {
        oilChangeDao.insert(OilChange(odometerKm = odometerKm, intervalKm = intervalKm, notes = notes))
    }

    suspend fun getLastOilChange(): OilChange? = oilChangeDao.getLastOilChange()
    suspend fun deleteOilChange(oilChange: OilChange) = oilChangeDao.delete(oilChange)

    suspend fun getKmUntilNextOilChange(currentOdometerKm: Double): Double {
        val lastOil = oilChangeDao.getLastOilChange() ?: return 5000.0
        val nextAt    = lastOil.odometerKm + lastOil.intervalKm
        val remaining = maxOf(0.0, nextAt - currentOdometerKm)
        android.util.Log.d("CT_REPO",
            "Óleo: última=${lastOil.odometerKm} intervalo=${lastOil.intervalKm} próxima=$nextAt atual=$currentOdometerKm faltam=$remaining")
        return remaining
    }

    // ─── Trip / Route ─────────────────────────────────────────────────────────

    val allTripSessions: Flow<List<TripSession>> = tripSessionDao.getAllSessions()

    suspend fun startNewSession(odometerKm: Double, startLat: Double? = null, startLng: Double? = null): String {
        val sessionId = UUID.randomUUID().toString()
        
        var pointAName = ""
        var pointALat = 0.0
        var pointALng = 0.0

        // Se recebemos a localização inicial, já tentamos identificar o local salvo
        if (startLat != null && startLng != null) {
            val nearby = getNearbyLocation(startLat, startLng, 150f) // Raio generoso para início
            nearby?.let {
                pointAName = it.name
                pointALat = it.latitude
                pointALng = it.longitude
            }
        }
        
        tripSessionDao.insert(
            TripSession(
                sessionId = sessionId,
                startOdometerKm = odometerKm,
                pointAName = pointAName,
                pointALat = pointALat,
                pointALng = pointALng
            )
        )
        return sessionId
    }

    suspend fun addRoutePoint(
        sessionId: String, lat: Double, lng: Double,
        speedKmh: Double = 0.0, accuracy: Float = 0f
    ) {
        val point = RoutePoint(
            sessionId = sessionId,
            latitude = lat,
            longitude = lng,
            speedKmh = speedKmh,
            accuracyMeters = accuracy
        )
        routePointDao.insert(point)

        // Se a sessão ainda não tem Ponto A, tenta identificar agora
        // Aumentamos o raio para 150m para garantir a identificação da Origem
        val session = tripSessionDao.getActiveSession()
        if (session != null && session.pointAName.isEmpty()) {
            val nearby = getNearbyLocation(lat, lng, 150f)
            nearby?.let {
                tripSessionDao.update(session.copy(
                    pointAName = it.name,
                    pointALat = it.latitude,
                    pointALng = it.longitude
                ))
            }
        }
    }

    /**
     * Calcula o fator de eficiência baseado na velocidade atual.
     * De 10 a 50 km/h: Consumo urbano (Z) - Geralmente menos eficiente (fator ~0.85)
     * De 50 a 80 km/h: Velocidade de cruzeiro ideal (X) - Máxima eficiência (fator 1.0)
     * Acima de 80 km/h: Arrasto aerodinâmico (Y) - Eficiência cai conforme a velocidade aumenta (fator decrescente)
     */
    fun getSpeedEfficiencyFactor(speedKmh: Double): Double {
        return when {
            speedKmh < 10 -> 0.70 // Marcha lenta / trânsito pesado
            speedKmh in 10.0..50.0 -> 0.85 // Urbano
            speedKmh in 50.1..85.0 -> 1.05 // Cruzeiro (Doce spot dos carros antigos)
            speedKmh in 85.1..110.0 -> 0.90 // Rodoviário normal
            speedKmh > 110.0 -> 0.75 // Alta velocidade (arrasto aerodinâmico pesado)
            else -> 1.0
        }
    }

    suspend fun endSession(sessionId: String, startOdometer: Double, avgConsumption: Double): TripSession? {
        val session = tripSessionDao.getSessionById(sessionId) ?: return null
        val points = routePointDao.getPointsForSession(sessionId)
        
        // Cálculo de litros baseado na eficiência por velocidade em cada ponto
        var totalFuelUsed = 0.0
        if (points.size >= 2 && avgConsumption > 0) {
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i+1]
                val dist = haversineDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude) / 1000.0
                
                // Aplica o fator de eficiência da velocidade média entre os dois pontos
                val avgSpeed = (p1.speedKmh + p2.speedKmh) / 2.0
                val efficiencyFactor = getSpeedEfficiencyFactor(avgSpeed)
                
                // Consumo real ajustado (km/l efetivo naquela velocidade)
                val effectiveKml = avgConsumption * efficiencyFactor
                totalFuelUsed += (dist / effectiveKml)
            }
        } else if (avgConsumption > 0) {
            val distanceKm = calculateRouteDistance(points)
            totalFuelUsed = distanceKm / avgConsumption
        }

        val distanceKm = calculateRouteDistance(points)
        val durationSec = if (points.size >= 2)
            (points.last().timestamp - points.first().timestamp) / 1000 else 0L

        // Cálculo de velocidades
        val validPoints = points.filter { it.speedKmh > 0 }
        val maxSpeed = if (validPoints.isNotEmpty()) validPoints.maxOf { it.speedKmh } else 0.0
        val avgSpeed = if (durationSec > 0) (distanceKm / (durationSec / 3600.0)) else 0.0

        // Identifica Local de FIM (Ponto B) - Também usamos 100m para identificação de nome
        var pointBName = ""
        var pointBLat = 0.0
        var pointBLng = 0.0
        
        if (points.isNotEmpty()) {
            val lastPoint = points.last()
            val nearby = getNearbyLocation(lastPoint.latitude, lastPoint.longitude, 100f)
            nearby?.let {
                pointBName = it.name
                pointBLat = it.latitude
                pointBLng = it.longitude
            }
        }

        val updatedSession = session.copy(
            endTime = System.currentTimeMillis(),
            endOdometerKm = startOdometer + distanceKm,
            distanceKm = distanceKm,
            durationSeconds = durationSec,
            fuelUsedLiters = totalFuelUsed,
            pointBName = pointBName,
            pointBLat = pointBLat,
            pointBLng = pointBLng,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeed
        )
        
        tripSessionDao.update(updatedSession)
        return updatedSession
    }

    suspend fun updateSessionPoints(sessionId: String) {
        val session = tripSessionDao.getActiveSession() ?: return
        val points     = routePointDao.getPointsForSession(sessionId)
        val distanceKm = calculateRouteDistance(points)
        tripSessionDao.update(session.copy(distanceKm = distanceKm))
    }

    suspend fun getRoutePoints(sessionId: String): List<RoutePoint> =
        routePointDao.getPointsForSession(sessionId)

    suspend fun getActiveSession(): TripSession? = tripSessionDao.getActiveSession()

    suspend fun updateSessionLabels(
        sessionId: String,
        pointAName: String, pointBName: String,
        pointALat: Double, pointALng: Double,
        pointBLat: Double, pointBLng: Double
    ) {
        val session = tripSessionDao.getLastSession() ?: return
        tripSessionDao.update(
            session.copy(
                pointAName = pointAName, pointBName = pointBName,
                pointALat  = pointALat,  pointALng  = pointALng,
                pointBLat  = pointBLat,  pointBLng  = pointBLng
            )
        )
    }

    suspend fun getLastSession(): TripSession? = tripSessionDao.getLastSession()

    suspend fun deleteSession(session: TripSession) {
        routePointDao.deleteSession(session.sessionId)
        tripSessionDao.delete(session)
    }

    suspend fun deleteAllTrips() {
        routePointDao.deleteAllPoints()
        tripSessionDao.deleteAllSessions()
    }

    // ─── Saved Locations ─────────────────────────────────────────────────────

    val allSavedLocations: Flow<List<SavedLocation>> = savedLocationDao.getAllLocations()

    suspend fun saveFavoriteLocation(name: String, lat: Double, lng: Double, isHome: Boolean = false): SavedLocation {
        val location = SavedLocation(name = name, latitude = lat, longitude = lng, isHome = isHome)
        val id = savedLocationDao.insert(location)
        return location.copy(id = id)
    }

    suspend fun getNearbyLocation(lat: Double, lng: Double, radiusMeters: Float = 100f): SavedLocation? {
        return savedLocationDao.getAllLocationsList().firstOrNull { loc ->
            haversineDistanceMeters(lat, lng, loc.latitude, loc.longitude) <= radiusMeters
        }
    }

    suspend fun deleteSavedLocation(location: SavedLocation) = savedLocationDao.delete(location)

    suspend fun updateSavedLocation(location: SavedLocation) = savedLocationDao.update(location)

    // ─── OBD ─────────────────────────────────────────────────────────────────

    val allObdRecords: Flow<List<ObdRecord>> = obdDao.getAllRecords()

    suspend fun saveObdRecord(data: ObdData, notes: String = "") {
        obdDao.insert(
            ObdRecord(
                rpm = data.rpm,
                speedKmh = data.speedKmh,
                coolantTempC = data.coolantTempC,
                fuelLevelPercent = data.fuelLevelPercent,
                batteryVoltage = data.batteryVoltage,
                engineLoadPercent = data.engineLoadPercent,
                notes = notes
            )
        )
    }

    suspend fun deleteObdRecord(record: ObdRecord) = obdDao.delete(record)
    suspend fun clearObdHistory() = obdDao.deleteAll()

    // ─── Statistics ──────────────────────────────────────────────────────────

    suspend fun getMonthlyStats(): Map<String, Double> {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        return mapOf(
            "totalSpent"     to (fuelDao.getTotalSpentSince(thirtyDaysAgo) ?: 0.0),
            "totalLiters"    to (fuelDao.getTotalLitersSince(thirtyDaysAgo) ?: 0.0),
            "totalDistanceKm" to (tripSessionDao.getTotalDistanceSince(thirtyDaysAgo) ?: 0.0)
        )
    }

    // ─── Recalcular histórico existente ──────────────────────────────────────

    /**
     * Recalcula kmSinceLast e consumption de todos os registros existentes.
     * Útil após migração ou quando os valores estavam zerados.
     */
    suspend fun recalcularConsumoExistente() {
        val sorted = fuelDao.getAllFuelRecordsList().sortedBy { it.timestamp }
        if (sorted.size < 2) return

        for (i in 1 until sorted.size) {
            val current  = sorted[i]
            val previous = sorted[i - 1]

            val distFromTrips = tripSessionDao.getTotalDistanceSince(previous.timestamp) ?: 0.0
            val kmSinceLast = when {
                distFromTrips > 0 -> distFromTrips
                current.odometerKm > 0 && previous.odometerKm > 0 ->
                    current.odometerKm - previous.odometerKm
                else -> 0.0
            }

            val raw             = if (kmSinceLast > 0 && current.liters > 0) kmSinceLast / current.liters else 0.0
            val validConsumption = if (raw in 5.0..20.0) raw else 0.0

            android.util.Log.d("CT_REPO",
                "Recalc id=${current.id}: km=$kmSinceLast liters=${current.liters} consumption=$validConsumption")

            fuelDao.update(current.copy(kmSinceLastFill = kmSinceLast, consumption = validConsumption))
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun calculateRouteDistance(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += haversineDistanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return total / 1000.0
    }

    private fun haversineDistanceMeters(
        lat1: Double, lng1: Double, lat2: Double, lng2: Double
    ): Double {
        val R       = 6371000.0
        val phi1    = Math.toRadians(lat1)
        val phi2    = Math.toRadians(lat2)
        val dPhi    = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lng2 - lng1)
        val a       = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c       = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}