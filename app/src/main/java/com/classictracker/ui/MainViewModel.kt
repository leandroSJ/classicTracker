package com.classictracker.ui

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classictracker.data.db.AppDatabase
import com.classictracker.data.models.*
import com.classictracker.data.repository.AppRepository
import com.classictracker.data.repository.PreferencesManager
import com.classictracker.data.repository.SyncManager
import com.classictracker.obd.ObdManager
import com.classictracker.obd.ObdState
import com.classictracker.service.TrackingService
import com.classictracker.utils.DataTransferUtils
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class HomeUiState(
    val vehicleConfig: VehicleConfig = VehicleConfig(),
    val totalHistoryKm: Double = 0.0,
    val avgConsumptionKmL: Double = 0.0,
    val estimatedRangeKm: Double = 0.0,
    val kmUntilOilChange: Double = 5000.0,
    val costPerKm: Double = 0.0,
    val isTracking: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val todayDistanceKm: Double = 0.0,
    val lastFuelRecord: FuelRecord? = null,
    val obdConnected: Boolean = false,
    val lastOilChange: OilChange? = null,
    val isSyncEnabled: Boolean = false,
    val userEmail: String? = null,
    val selectedVoiceName: String? = null,
    val availableVoices: List<String> = emptyList(),
    val isDarkMode: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository = AppRepository(
        db.fuelDao(),
        db.oilChangeDao(),
        db.routePointDao(),
        db.tripSessionDao(),
        db.savedLocationDao(),
        db.obdDao()
    )
    val prefsManager = PreferencesManager(application)
    val obdManager = ObdManager()
    val syncManager = SyncManager(application)
    private val weatherRepository = com.classictracker.data.repository.WeatherRepository()
    private val searchRepository = com.classictracker.data.repository.SearchRepository()

    private var ttsInitializer: android.speech.tts.TextToSpeech? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val allFuelRecords = repository.allFuelRecords
    val allTripSessions = repository.allTripSessions
    val allSavedLocations = repository.allSavedLocations
    val allOilChanges = repository.allOilChanges
    val allObdRecords = repository.allObdRecords
    val obdState: StateFlow<ObdState> = obdManager.state
    val obdData: StateFlow<ObdData> = obdManager.obdData

    init {
        observeDashboardData()
        observeObd()
        loadVoices()

        // Recalcula o histórico assim que o ViewModel inicia
        viewModelScope.launch(Dispatchers.IO) {
            repository.recalcularConsumoExistente()
        }
    }

    private fun loadVoices() {
        ttsInitializer = android.speech.tts.TextToSpeech(getApplication()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val locale = java.util.Locale("pt", "BR")
                val voices = ttsInitializer?.voices?.filter { it.locale == locale } ?: emptyList()
                val voiceNames = voices.map { it.name }
                _uiState.update { it.copy(availableVoices = voiceNames) }
                
                // Cleanup
                ttsInitializer?.shutdown()
                ttsInitializer = null
            }
        }
    }

    private fun observeDashboardData() {
        // Observa mudanças no banco de dados e configuração apenas para atualizar a TELA (UI)
        combine(
            prefsManager.vehicleConfig,
            repository.allFuelRecords,
            repository.allOilChanges,
            repository.allTripSessions,
            repository.allSavedLocations
        ) { config, fuel, oil, sessions, locations ->
            val totalHistory = sessions.sumOf { it.distanceKm }
            
            // Calcula distância de hoje
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todayDist = sessions.filter { it.startTime >= todayStart }.sumOf { it.distanceKm }

            refreshDashboard(config, totalHistory, todayDist)
        }.launchIn(viewModelScope)

        prefsManager.selectedVoice.onEach { voice ->
            _uiState.update { it.copy(selectedVoiceName = voice) }
        }.launchIn(viewModelScope)

        prefsManager.isDarkMode.onEach { isDark ->
            _uiState.update { it.copy(isDarkMode = isDark) }
        }.launchIn(viewModelScope)
        
        // Removemos a sincronização automática daqui para evitar o erro "Job was cancelled"
        // devido ao excesso de requisições simultâneas.

        // Observa se o serviço está rodando em tempo real
        TrackingService.isRunningFlow.onEach { isRunning ->
            _uiState.update { it.copy(isTracking = isRunning) }
        }.launchIn(viewModelScope)

        // Atualiza estado do login
        _uiState.update { 
            it.copy(
                isSyncEnabled = syncManager.isLoggedIn,
                userEmail = syncManager.currentUser?.email
            )
        }
    }

    // ─── Firebase Auth ───────────────────────────────────────────────────────

    fun signInWithFirebase(credential: AuthCredential) {
        viewModelScope.launch {
            try {
                Firebase.auth.signInWithCredential(credential).await()
                
                // --- RECUPERAÇÃO DE DADOS APÓS LOGIN ---
                val cloudFuel = syncManager.getFuelRecordsFromCloud()
                val cloudOil = syncManager.getOilChangesFromCloud()
                val cloudTrips = syncManager.getTripSessionsFromCloud()
                val cloudConfig = syncManager.getVehicleConfigFromCloud()
                val cloudLocations = syncManager.getSavedLocationsFromCloud()

                cloudFuel.forEach { repository.addFuelRecordCloud(it) }
                cloudOil.forEach { repository.addOilChangeCloud(it) }
                cloudTrips.forEach { repository.addTripSessionCloud(it) }
                cloudLocations.forEach { repository.addSavedLocationCloud(it) }
                cloudConfig?.let { prefsManager.saveVehicleConfig(it) }
                // ----------------------------------------

                _uiState.update { 
                    it.copy(
                        isSyncEnabled = true,
                        userEmail = syncManager.currentUser?.email
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("CT_AUTH", "Erro no login Firebase: ${e.message}")
            }
        }
    }

    fun logout() {
        syncManager.signOut()
        _uiState.update { it.copy(isSyncEnabled = false, userEmail = null) }
    }

    fun forceSyncAllToCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("CT_SYNC", "Iniciando sincronização forçada para a nuvem...")
                val fuel = repository.allFuelRecords.first()
                val oil = repository.allOilChanges.first()
                val trips = repository.allTripSessions.first()
                val config = prefsManager.vehicleConfig.first()
                val locations = repository.allSavedLocations.first()

                syncManager.syncVehicleConfig(config)
                syncManager.syncFuelRecords(fuel)
                syncManager.syncOilChanges(oil)
                syncManager.syncTripSessions(trips)
                syncManager.syncSavedLocations(locations)
                
                // Sincroniza também todos os pontos de GPS de todos os trajetos
                trips.forEach { session ->
                    val points = repository.getRoutePoints(session.sessionId)
                    if (points.isNotEmpty()) {
                        syncManager.syncRoutePoints(session.sessionId, points)
                    }
                }
                
                android.util.Log.d("CT_SYNC", "Sincronização forçada concluída!")
                launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "✅ Sincronização com a nuvem concluída!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("CT_SYNC", "Erro na sincronização forçada: ${e.message}")
                launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "❌ Erro ao sincronizar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun restoreDataFromCloud() {
        viewModelScope.launch {
            try {
                android.util.Log.d("CT_SYNC", "Iniciando restauração manual...")
                val cloudFuel = syncManager.getFuelRecordsFromCloud()
                val cloudOil = syncManager.getOilChangesFromCloud()
                val cloudTrips = syncManager.getTripSessionsFromCloud()
                val cloudConfig = syncManager.getVehicleConfigFromCloud()
                val cloudLocations = syncManager.getSavedLocationsFromCloud()

                android.util.Log.d("CT_SYNC", "Dados baixados: Fuel=${cloudFuel.size}, Oil=${cloudOil.size}, Trips=${cloudTrips.size}, Locations=${cloudLocations.size}")

                cloudFuel.forEach { 
                    val id = repository.addFuelRecordCloud(it)
                    android.util.Log.d("CT_SYNC", "Inserido Fuel ID: $id")
                }
                cloudOil.forEach { 
                    val id = repository.addOilChangeCloud(it)
                    android.util.Log.d("CT_SYNC", "Inserido Oil ID: $id")
                }
                cloudTrips.forEach { session -> 
                    repository.addTripSessionCloud(session)
                    // Baixa e restaura os pontos de GPS de cada viagem
                    // Mudamos para rodar dentro do mesmo contexto para garantir que não seja cancelado
                    val points = syncManager.getRoutePointsFromCloud(session.sessionId)
                    if (points.isNotEmpty()) {
                        points.forEach { repository.addRoutePointCloud(it) }
                        android.util.Log.d("CT_SYNC", "Trajeto restaurado: ${points.size} pontos para ${session.sessionId}")
                    } else {
                        android.util.Log.w("CT_SYNC", "Nenhum ponto encontrado na nuvem para a sessão: ${session.sessionId}")
                    }
                }
                cloudLocations.forEach { 
                    val id = repository.addSavedLocationCloud(it)
                    android.util.Log.d("CT_SYNC", "Inserida Location ID: $id (${it.name})")
                }
                cloudConfig?.let { 
                    android.util.Log.d("CT_SYNC", "Restaurando config: ${it.brand} ${it.model}")
                    prefsManager.saveVehicleConfig(it) 
                }
                
                // Recalcula o histórico local para garantir que as métricas (médias) apareçam
                repository.recalcularConsumoExistente()
                
                // Força atualização da UI após restaurar
                val updatedConfig = prefsManager.vehicleConfig.first()
                val totalHistory = repository.allTripSessions.first().sumOf { it.distanceKm }
                refreshDashboard(updatedConfig, totalHistory)
                
                android.util.Log.d("CT_SYNC", "Restauração concluída com sucesso!")
                android.widget.Toast.makeText(getApplication(), "✅ Dados restaurados com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("CT_SYNC", "Erro ao restaurar dados: ${e.message}")
                android.widget.Toast.makeText(getApplication(), "❌ Erro ao restaurar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun refreshDashboard(config: VehicleConfig, totalHistory: Double = 0.0, todayDist: Double = 0.0) {
        val avgConsumption = repository.getAverageConsumption()
        val lastFuel = repository.getLastFuelRecord()
        val lastOil = repository.getLastOilChange()
        val kmUntilOil = repository.getKmUntilNextOilChange(config.currentOdometerKm)
        val costPerKm = repository.getCostPerKm()

        // Lógica de Autonomia Estimada
        val remainingKm = if (avgConsumption > 0) {
            repository.estimateRemainingRange(config.tankLiters, config.reserveLiters, avgConsumption)
        } else {
            // Se não tem média calculada ainda, usa 10km/l como base para o Classic
            repository.estimateRemainingRange(config.tankLiters, config.reserveLiters, 10.0)
        }

        _uiState.update {
            it.copy(
                vehicleConfig = config,
                totalHistoryKm = totalHistory,
                todayDistanceKm = todayDist,
                avgConsumptionKmL = avgConsumption,
                estimatedRangeKm = remainingKm,
                kmUntilOilChange = kmUntilOil,
                costPerKm = costPerKm,
                lastFuelRecord = lastFuel,
                lastOilChange = lastOil,
                isTracking = TrackingService.isRunning
            )
        }
    }

    private fun observeObd() {
        viewModelScope.launch {
            obdManager.state.collect { state ->
                _uiState.update { it.copy(obdConnected = state is ObdState.Connected || state is ObdState.Reading) }
            }
        }
    }

    // ─── Fuel Actions ─────────────────────────────────────────────────────────

    fun addFuelRecord(
        fuelType: String,
        liters: Double,
        pricePerLiter: Double,
        isReserve: Boolean,
        odometerKm: Double? = null,
        manualTimestamp: Long? = null
    ) {
        viewModelScope.launch {
            try {
                val config = prefsManager.vehicleConfig.first()
                val kmToUse = odometerKm ?: config.currentOdometerKm
                
                if (kmToUse > config.currentOdometerKm) {
                    prefsManager.updateOdometer(kmToUse)
                }
                
                var record = repository.addFuelRecord(
                    fuelType = fuelType,
                    liters = liters,
                    pricePerLiter = pricePerLiter,
                    odometerKm = kmToUse,
                    isReserve = isReserve,
                    hasAC = config.hasAC
                )
                
                if (manualTimestamp != null) {
                    record = record.copy(timestamp = manualTimestamp)
                    repository.addFuelRecordCloud(record)
                }
                
                if (syncManager.isLoggedIn) {
                    val success = syncManager.syncFuelRecords(listOf(record))
                    val msg = if (success) "Abastecimento salvo e sincronizado!" else "Salvo localmente, mas falhou ao sincronizar (ver Logcat CT_SYNC)"
                    android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(getApplication(), "Salvo localmente (sem login Google)", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication(), "Erro ao salvar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteFuelRecord(record: FuelRecord) {
        viewModelScope.launch {
            repository.deleteFuelRecord(record)
            if (syncManager.isLoggedIn) {
                syncManager.deleteFuelRecordFromCloud(record)
            }
        }
    }

    fun markReserveEntry() {
        viewModelScope.launch {
            val config = prefsManager.vehicleConfig.first()
            val record = repository.addFuelRecord(
                fuelType = "RESERVA",
                liters = config.reserveLiters, // Pega o valor da reserva das configurações
                pricePerLiter = 0.0,
                odometerKm = config.currentOdometerKm,
                isReserve = true
            )

            // FORÇA SINCRONIZAÇÃO IMEDIATA
            if (syncManager.isLoggedIn) {
                val success = syncManager.syncFuelRecords(listOf(record))
                if (success) {
                    android.util.Log.d("CT_SYNC", "Aviso de Reserva enviado imediatamente para nuvem")
                } else {
                    android.util.Log.e("CT_SYNC", "Falha ao sincronizar aviso de reserva")
                }
            }
        }
    }

    // ─── Oil Change ──────────────────────────────────────────────────────────

    fun addOilChange(notes: String = "", customInterval: Double? = null) {
        viewModelScope.launch {
            val config = prefsManager.vehicleConfig.first()
            val interval = customInterval ?: config.oilChangeIntervalKm
            repository.addOilChange(config.currentOdometerKm, interval, notes)

            if (syncManager.isLoggedIn) {
                val record = repository.getLastOilChange()
                if (record != null) {
                    val success = syncManager.syncOilChanges(listOf(record))
                    if (!success) {
                        android.util.Log.e("CT_SYNC", "Falha ao sincronizar troca de óleo")
                    }
                }
            }
        }
    }

    fun deleteOilChange(oilChange: OilChange) {
        viewModelScope.launch {
            repository.deleteOilChange(oilChange)
            if (syncManager.isLoggedIn) {
                syncManager.deleteOilChangeFromCloud(oilChange)
            }
        }
    }

    fun deleteTripSession(session: TripSession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(session)
            if (syncManager.isLoggedIn) {
                syncManager.deleteTripSessionFromCloud(session.sessionId)
            }
        }
    }

    fun deleteAllTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllTrips()
            if (syncManager.isLoggedIn) {
                syncManager.deleteAllTripsFromCloud()
            }
            launch(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Todos os trajetos foram excluídos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Tracking ─────────────────────────────────────────────────────────────

    fun startTracking() {
        viewModelScope.launch {
            val config = prefsManager.vehicleConfig.first()
            val ctx = getApplication<Application>()
            
            // Tenta pegar a localização atual ANTES de começar para marcar a Origem
            val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(ctx)
            var startLat: Double? = null
            var startLng: Double? = null
            
            try {
                // Verifica permissão antes de pedir local
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val location = fusedClient.lastLocation.await()
                    if (location != null) {
                        startLat = location.latitude
                        startLng = location.longitude
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CT_START", "Erro ao pegar local de origem: ${e.message}")
            }

            val sessionId = repository.startNewSession(config.currentOdometerKm, startLat, startLng)
            prefsManager.setCurrentSessionId(sessionId)

            val intent = Intent(ctx, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
            _uiState.update { it.copy(isTracking = true) }
            
            Toast.makeText(ctx, "Rastreamento iniciado", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopTracking() {
        val ctx = getApplication<Application>()
        // 1. DISPARA O STOP IMEDIATAMENTE (SEM SUSPENDER)
        val stopIntent = Intent(ctx, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        ctx.startService(stopIntent)
        _uiState.update { it.copy(isTracking = false) }

        // 2. PROCESSA OS DADOS EM SEGUNDO PLANO
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessionId = prefsManager.currentSessionId.first()
                if (sessionId != null) {
                    val avgConsumption = repository.getAverageConsumption()
                    val config = prefsManager.vehicleConfig.first()
                    
                    val updatedSession = repository.endSession(sessionId, config.currentOdometerKm, avgConsumption)
                    
                    if (updatedSession != null) {
                        val newOdometer = config.currentOdometerKm + updatedSession.distanceKm
                        prefsManager.updateOdometer(newOdometer)

                        if (syncManager.isLoggedIn) {
                            // Sincroniza a sessão (Metadados: KM, Tempo, etc)
                            syncManager.syncTripSessions(listOf(updatedSession))
                            
                            // Busca todos os pontos salvos localmente para esta sessão
                            val points = repository.getRoutePoints(sessionId)
                            android.util.Log.d("CT_STOP", "Sincronizando ${points.size} pontos para nuvem...")
                            
                            if (points.isNotEmpty()) {
                                val success = syncManager.syncRoutePoints(sessionId, points)
                                if (success) {
                                    android.util.Log.d("CT_STOP", "Pontos sincronizados com sucesso!")
                                } else {
                                    android.util.Log.e("CT_STOP", "Falha ao sincronizar pontos da rota")
                                }
                            }
                        }
                    }
                }
                prefsManager.setCurrentSessionId(null)
            } catch (e: Exception) {
                android.util.Log.e("CT_STOP", "Erro background stop: ${e.message}")
            }
        }
    }

    // ─── Odometer ─────────────────────────────────────────────────────────────

    fun updateOdometer(km: Double) {
        viewModelScope.launch {
            prefsManager.updateOdometer(km)
        }
    }

    // ─── Vehicle Config ───────────────────────────────────────────────────────

    fun saveVehicleConfig(config: VehicleConfig) {
        viewModelScope.launch {
            prefsManager.saveVehicleConfig(config)

            if (syncManager.isLoggedIn) {
                val success = syncManager.syncVehicleConfig(config)
                if (!success) {
                    android.util.Log.e("CT_SYNC", "Falha ao sincronizar configuração do veículo")
                }
            }
        }
    }

    // ─── Saved Locations ──────────────────────────────────────────────────────

    fun saveLocation(name: String, lat: Double, lng: Double, isHome: Boolean = false) {
        viewModelScope.launch {
            val location = repository.saveFavoriteLocation(name, lat, lng, isHome)

            if (syncManager.isLoggedIn) {
                val success = syncManager.syncSavedLocations(listOf(location))
                if (!success) {
                    android.util.Log.e("CT_SYNC", "Falha ao sincronizar local salvo: $name")
                }
            }
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.deleteSavedLocation(location)
            if (syncManager.isLoggedIn) {
                syncManager.deleteSavedLocationFromCloud(location)
            }
        }
    }

    fun updateLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.updateSavedLocation(location)
            if (syncManager.isLoggedIn) {
                syncManager.syncSavedLocations(listOf(location))
            }
        }
    }

    fun getTodayDistanceKm(): Double {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
        
        // This is a bit inefficient to sum everything in memory, but for a local DB trip list it's usually fine
        // Better would be a DAO query, but let's use what we have for now.
        return _uiState.value.todayDistanceKm
    }

    // ─── Import / Export ─────────────────────────────────────────────────────

    fun exportMapData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val fuel = repository.allFuelRecords.first()
                val oil = repository.allOilChanges.first()
                val trips = repository.allTripSessions.first()
                val config = prefsManager.vehicleConfig.first()
                val locations = repository.allSavedLocations.first()

                val file = DataTransferUtils.exportData(ctx, config, fuel, oil, trips, locations)
                if (file != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(intent, "Compartilhar Backup Classic Tracker")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(chooser)
                }
            } catch (e: Exception) {
                android.util.Log.e("CT_BACKUP", "Erro ao exportar: ${e.message}")
            }
        }
    }

    fun importMapData(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = DataTransferUtils.parseImportData(json)
            if (pkg != null) {
                // Restaura Configuração
                prefsManager.saveVehicleConfig(pkg.vehicleConfig)
                
                // Restaura Registros (id = 0 para o Room auto-gerar novos IDs se necessário)
                pkg.fuelRecords.forEach { repository.addFuelRecordCloud(it.copy(id = 0)) }
                pkg.oilChanges.forEach { repository.addOilChangeCloud(it.copy(id = 0)) }
                pkg.tripSessions.forEach { repository.addTripSessionCloud(it) }
                pkg.savedLocations.forEach { repository.addSavedLocationCloud(it.copy(id = 0)) }
                
                repository.recalcularConsumoExistente()
                
                launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "✅ Backup importado com sucesso!", Toast.LENGTH_LONG).show()
                }
            } else {
                launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "❌ Arquivo de backup inválido ou corrompido.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── Route Points ─────────────────────────────────────────────────────────

    suspend fun getRoutePoints(sessionId: String) = repository.getRoutePoints(sessionId)

    // ─── OBD ─────────────────────────────────────────────────────────────────

    fun saveCurrentObdData(notes: String = "") {
        viewModelScope.launch {
            repository.saveObdRecord(obdData.value, notes)
        }
    }

    fun deleteObdRecord(record: ObdRecord) {
        viewModelScope.launch { repository.deleteObdRecord(record) }
    }

    fun processVoiceCommand(text: String) {
        if (text.isBlank()) return
        
        val command = text.lowercase().trim()
        val ctx = getApplication<Application>()
        val state = _uiState.value

        viewModelScope.launch {
            val response = when {
                // --- Métricas do App ---
                command.contains("previsão do tempo") || command.contains("clima") -> {
                    val weatherSalvador = try { weatherRepository.getWeatherInfo("Salvador") } catch (e: Exception) { "" }
                    if (weatherSalvador.isNotEmpty()) "Em Salvador faz $weatherSalvador." else "Não consegui buscar o clima agora."
                }
                command.contains("autonomia") -> {
                    if (state.estimatedRangeKm > 0) "Sua autonomia estimada é de aproximadamente ${state.estimatedRangeKm.toInt()} quilômetros."
                    else "Ainda não tenho dados suficientes para calcular sua autonomia."
                }
                command.contains("quanto já rodei hoje") || command.contains("quilômetros hoje") || command.contains("km hoje") -> {
                    "Você já percorreu ${state.todayDistanceKm.toInt()} quilômetros hoje."
                }
                command.contains("odômetro") || command.contains("quilometragem total") -> {
                    "O odômetro atual marca ${state.vehicleConfig.currentOdometerKm.toInt()} quilômetros."
                }
                command.contains("consumo") || command.contains("média") -> {
                    if (state.avgConsumptionKmL > 0) "Sua média de consumo atual é de %.1f quilômetros por litro.".format(state.avgConsumptionKmL)
                    else "Ainda não calculei sua média. Preciso de pelo menos dois abastecimentos."
                }
                command.contains("troca de óleo") || command.contains("manutenção") -> {
                    "Faltam ${state.kmUntilOilChange.toInt()} quilômetros para a próxima troca de óleo."
                }
                command.contains("último abastecimento") -> {
                    state.lastFuelRecord?.let {
                        "Seu último abastecimento foi de %.2f litros de %s.".format(it.liters, it.fuelType)
                    } ?: "Não encontrei registros de abastecimento."
                }
                command.contains("que horas são") || command.contains("hora") -> {
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                    "Agora são $time."
                }
                
                // --- Inteligência Externa (Google Assistant Style) ---
                else -> {
                    val externalAnswer = searchRepository.searchInstantAnswer(text)
                    if (externalAnswer.isNotEmpty()) {
                        externalAnswer
                    } else {
                        "Desculpe, não encontrei essa informação no seu app nem na internet. Tente perguntar sobre autonomia ou consumo."
                    }
                }
            }

            // Fala a resposta
            val intent = Intent(ctx, TrackingService::class.java).apply {
                action = TrackingService.ACTION_SPEAK
                putExtra(TrackingService.EXTRA_TEXT, response)
            }
            ctx.startService(intent)
        }
    }

    fun saveSelectedVoice(voiceName: String?) {
        viewModelScope.launch {
            prefsManager.saveSelectedVoice(voiceName)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            prefsManager.setDarkMode(!_uiState.value.isDarkMode)
        }
    }

    fun disconnectObd() {
        obdManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        obdManager.disconnect()
    }
}
