package com.classictracker.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.classictracker.MainActivity
import com.classictracker.R
import com.classictracker.data.db.AppDatabase
import com.classictracker.data.repository.AppRepository
import com.classictracker.data.repository.PreferencesManager
import com.classictracker.data.repository.dataStore
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class TrackingService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START_TRACKING"
        const val ACTION_STOP = "STOP_TRACKING"
        const val ACTION_SPEAK = "SPEAK_TEXT"
        const val EXTRA_TEXT = "text_to_speak"
        
        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isRunningFlow = _isRunning.asStateFlow()
        
        var isRunning: Boolean
            get() = _isRunning.value
            set(value) { _isRunning.value = value }

        var currentSpeedKmh = 0f
            private set
        var sessionDistance = 0.0
            private set
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: AppRepository
    private lateinit var prefsManager: PreferencesManager
    private val weatherRepository = com.classictracker.data.repository.WeatherRepository()
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentSessionId: String? = null
    private var lastLocation: Location? = null
    private var lastNotifiedLocationId: Long? = null
    private var lastNotifiedHour: Int = -1
    private var lastTipTimestamp: Long = 0
    private var startupMessagePlayed = false
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var ignoreFirstArrival = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        val db = AppDatabase.getInstance(this)
        repository = AppRepository(
            db.fuelDao(),
            db.oilChangeDao(),
            db.routePointDao(),
            db.tripSessionDao(),
            db.savedLocationDao(),
            db.obdDao()
        )
        prefsManager = PreferencesManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        createNotificationChannel()

        // Começa a monitorar velocidade assim que o app abre
        startLocationUpdates()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleNewLocation(location)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = java.util.Locale("pt", "BR")
            tts?.language = locale

            serviceScope.launch {
                val savedVoiceName = prefsManager.selectedVoice.first()
                
                // Busca uma voz disponível no sistema
                try {
                    val voices = tts?.voices ?: emptySet()
                    val selectedVoice = if (savedVoiceName != null) {
                        voices.find { it.name == savedVoiceName }
                    } else {
                        // Se não tem uma salva, busca a primeira masculina em pt-BR
                        voices.find { it.locale == locale && it.name.contains("male", ignoreCase = true) }
                    }
                    
                    selectedVoice?.let { tts?.voice = it }
                } catch (e: Exception) {
                    android.util.Log.e("CT_VOICE", "Erro ao selecionar voz: ${e.message}")
                }

                isTtsReady = true
                android.util.Log.d("CT_VOICE", "TTS Inicializado com sucesso")
            }
        } else {
            android.util.Log.e("CT_VOICE", "Erro ao inicializar TTS")
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            android.util.Log.d("CT_VOICE", "Falando: $text")
            // QUEUE_ADD garante que uma fala não corte a outra
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "CT_SPEECH_ID")
        }
    }

    private fun handleNewLocation(location: Location) {
        currentSpeedKmh = (location.speed * 3.6f).coerceAtLeast(0f)

        // AUTO-START: Inicia viagem se > 7km/h
        if (!isRunning && currentSpeedKmh >= 7f) {
            serviceScope.launch {
                val config = prefsManager.vehicleConfig.first()
                val sessionId = repository.startNewSession(config.currentOdometerKm)
                currentSessionId = sessionId
                prefsManager.setCurrentSessionId(sessionId)
                isRunning = true
                ignoreFirstArrival = true
                
                speak("Viagem iniciada")

                // Notifica o usuário que o rastreio começou
                val notification = buildNotification("Viagem iniciada automaticamente (%.0f km/h)".format(currentSpeedKmh))
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        if (lastLocation != null) {
            val dist = lastLocation!!.distanceTo(location) / 1000.0
            if (dist > 0.005) { // Mínimo 5 metros para evitar ruído de sinal parado
                sessionDistance += dist
            }
        }
        lastLocation = location

        if (isRunning) {
            serviceScope.launch {
                val sessionId = prefsManager.currentSessionId.first()
                if (sessionId != null) {
                    currentSessionId = sessionId
                    repository.addRoutePoint(
                        sessionId = sessionId,
                        lat = location.latitude,
                        lng = location.longitude,
                        speedKmh = currentSpeedKmh.toDouble(),
                        accuracy = location.accuracy
                    )
                    
                    val nearby = repository.getNearbyLocation(location.latitude, location.longitude)
                    
                    if (nearby != null) {
                        // Só notifica se não for o ponto de partida (ignoreFirstArrival)
                        // E se for um local diferente do último notificado
                        if (ignoreFirstArrival) {
                            lastNotifiedLocationId = nearby.id
                            ignoreFirstArrival = false
                        } else if (lastNotifiedLocationId != nearby.id) {
                            showLocationNotification(nearby.name)
                            
                            val nameLower = nearby.name.lowercase()
                            val message = when {
                                nameLower.contains("buraco") -> "Cuidado! Buraco na pista à frente."
                                nameLower.contains("quebra molas") || nameLower.contains("quebra-molas") || nameLower.contains("lombada") -> "Atenção! Quebra-mola na pista à frente."
                                nameLower.contains("radar") -> "Atenção! Radar à frente."
                                else -> "Você chegou em ${nearby.name}"
                            }
                            speak(message)
                            lastNotifiedLocationId = nearby.id
                        }
                    } else {
                        // Se saiu de qualquer raio de local, reseta para poder notificar o próximo
                        lastNotifiedLocationId = null
                        ignoreFirstArrival = false // Se estiver em local desconhecido, a próxima chegada deve avisar
                    }

                    // --- RELÓGIO DE HORA EM HORA ---
                    val now = System.currentTimeMillis()
                    val calendar = java.util.Calendar.getInstance()
                    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val currentMinute = calendar.get(java.util.Calendar.MINUTE)
                    
                    if (currentMinute == 0 && lastNotifiedHour != currentHour) {
                        speak("Atenção! Agora são $currentHour horas.")
                        lastNotifiedHour = currentHour
                    }

                    // --- DICAS DO APP (A cada 20 minutos ou conforme eventos) ---
                    if (isRunning && now - lastTipTimestamp > 1_200_000) { // 20 minutos
                        playRandomTip()
                        lastTipTimestamp = now
                    }
                }
            }
        }
        updateNotification()
    }

    private fun playRandomTip() {
        val tips = listOf(
            "Atenção agora é o momento de Dicas do app! Para garantir que eu consiga calcular Sempre o consumo correto, não se esqueça de informar seu abastecimento, e registrar quando entrar na reserva alem disso lembre-se sempre de iniciar o aplicativo.",
            "Você sabia que você pode clicar no mapa e salvar seus locais favoritos? basta clicar na aba Mapa e clicar no local, quando abrir a caixinha de texto escreva o nome do local.",
            "Você sabia que você pode clicar no mapa e salvar buracos na pista, quebra molas, radares e muito mais? basta clicar na aba Mapa e clicar no local, quando abrir a caixinha de texto escreva o nome da ocorrência. Exemplo: buraco na pista, ou radar de 50km. Dessa forma eu te aviso sempre que você estiver próximo.",
            "Você sabia que eu mantenho seus trajetos salvos? E no fim de cada um você pode compartilhar para provar o seu percurso. Nele você obtém data, hora, quilometragem, velocidade média e quantos litros percorreu no total."
        )
        speak(tips.random())
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                ignoreFirstArrival = true
                sessionDistance = 0.0
                startupMessagePlayed = false
                currentSessionId = null
                // Define o timestamp inicial recuado em 18 minutos para que,
                // somado aos 2 minutos iniciais, atinja o intervalo de 20 minutos.
                lastTipTimestamp = System.currentTimeMillis() - 1_080_000
                
                serviceScope.launch {
                    val config = prefsManager.vehicleConfig.first()
                    val sessionId = repository.startNewSession(config.currentOdometerKm)
                    currentSessionId = sessionId
                    prefsManager.setCurrentSessionId(sessionId)

                    val avg = repository.getAverageConsumption()
                    val range = repository.estimateRemainingRange(config.tankLiters, config.reserveLiters, avg)
                    
                    val msg = "Rastreamento Iniciado. Sua autonomia estimada é de ${range.toInt()} quilômetros."
                    speak(msg)
                    startupMessagePlayed = true
                }

                val notification = buildNotification("Rastreando... 0 km/h")
                startForeground(NOTIFICATION_ID, notification)
                startLocationUpdates()
            }
            ACTION_STOP -> {
                isRunning = false
                speak("Viagem finalizada")
                stopLocationUpdates()
                currentSpeedKmh = 0f
                sessionDistance = 0.0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    speak(text)
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L) // ← era 5000, agora 1 segundo
            .setMinUpdateDistanceMeters(0f)       // ← sem filtro de distância
            .setMinUpdateIntervalMillis(500L)     // ← atualiza a cada 500ms
            .setMaxUpdateDelayMillis(1000L)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Rastreamento GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Rastreamento de rota em segundo plano"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Classic Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val text = "%.0f km/h  |  Trajeto: %.1f km".format(currentSpeedKmh, sessionDistance)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showLocationNotification(locationName: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chegou em $locationName")
            .setContentText("Ponto registrado no trajeto")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .build()
        nm.notify(100, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        stopLocationUpdates()
        serviceScope.cancel()
        isRunning = false
    }
}
