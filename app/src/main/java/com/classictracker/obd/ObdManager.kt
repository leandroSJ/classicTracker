package com.classictracker.obd

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.classictracker.data.models.ObdData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

sealed class ObdState {
    object Disconnected : ObdState()
    object Scanning : ObdState()
    data class Connected(val deviceName: String) : ObdState()
    data class Error(val message: String) : ObdState()
    data class Reading(val data: ObdData) : ObdState()
}

class ObdManager {
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _state = MutableStateFlow<ObdState>(ObdState.Disconnected)
    val state: StateFlow<ObdState> = _state

    private val _obdData = MutableStateFlow(ObdData())
    val obdData: StateFlow<ObdData> = _obdData

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    fun getObdDevices(adapter: BluetoothAdapter): List<BluetoothDevice> {
        return adapter.bondedDevices
            .filter { it.name?.contains("OBD", ignoreCase = true) == true ||
                      it.name?.contains("ELM", ignoreCase = true) == true ||
                      it.name?.contains("OBDII", ignoreCase = true) == true }
    }

    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ObdState.Scanning
            disconnect()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            // Initialize ELM327
            sendCommand("ATZ")    // Reset
            delay(1000)
            sendCommand("ATE0")   // Echo off
            delay(200)
            sendCommand("ATL0")   // Line feeds off
            delay(200)
            sendCommand("ATH0")   // Headers off
            delay(200)
            sendCommand("ATSP0")  // Auto protocol
            delay(500)

            _state.value = ObdState.Connected(device.name ?: "OBD2")
            startReading()
            true
        } catch (e: Exception) {
            _state.value = ObdState.Error("Falha ao conectar: ${e.message}")
            disconnect()
            false
        }
    }

    private fun startReading() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && socket?.isConnected == true) {
                try {
                    val data = readAllSensors()
                    _obdData.value = data
                    _state.value = ObdState.Reading(data)
                    delay(2000)
                } catch (e: Exception) {
                    if (isActive) {
                        _state.value = ObdState.Error("Erro de leitura: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    private suspend fun readAllSensors(): ObdData = withContext(Dispatchers.IO) {
        ObdData(
            rpm = parseRpm(sendCommand("010C")),
            speedKmh = parseSpeed(sendCommand("010D")),
            coolantTempC = parseTemp(sendCommand("0105")),
            throttlePercent = parsePercent(sendCommand("0111")),
            fuelLevelPercent = parsePercent(sendCommand("012F")),
            intakeAirTempC = parseTemp(sendCommand("010F")),
            engineLoadPercent = parsePercent(sendCommand("0104")),
            batteryVoltage = parseBattery(sendCommand("ATRV")),
            fuelTrimShortPercent = parseFuelTrim(sendCommand("0106")),
            fuelTrimLongPercent = parseFuelTrim(sendCommand("0107"))
        )
    }

    private fun sendCommand(cmd: String): String {
        return try {
            outputStream?.write("$cmd\r".toByteArray())
            outputStream?.flush()
            Thread.sleep(200)
            val resp = readResponse()
            android.util.Log.d("CT_OBD", "CMD: $cmd -> RESP: $resp")
            resp
        } catch (e: Exception) {
            android.util.Log.e("CT_OBD", "Erro enviando $cmd: ${e.message}")
            ""
        }
    }

    private fun readResponse(): String {
        val buffer = StringBuilder()
        val bytes = ByteArray(256)
        return try {
            var bytesRead: Int
            val timeout = System.currentTimeMillis() + 500
            while (System.currentTimeMillis() < timeout) {
                if ((inputStream?.available() ?: 0) > 0) {
                    bytesRead = inputStream?.read(bytes) ?: 0
                    val chunk = String(bytes, 0, bytesRead)
                    buffer.append(chunk)
                    if (buffer.contains(">")) break
                } else {
                    Thread.sleep(20)
                }
            }
            buffer.toString().trim().replace(">", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    // ─── Parsers ─────────────────────────────────────────────────────────────

    private fun parseRpm(response: String): Int? {
        return try {
            val cleaned = cleanResponse(response, "010C") ?: return null
            if (cleaned.length >= 4) {
                val a = cleaned.substring(0, 2).toInt(16)
                val b = cleaned.substring(2, 4).toInt(16)
                ((a * 256) + b) / 4
            } else null
        } catch (e: Exception) { null }
    }

    private fun parseSpeed(response: String): Int? {
        return try {
            val cleaned = cleanResponse(response, "010D") ?: return null
            cleaned.substring(0, 2).toInt(16)
        } catch (e: Exception) { null }
    }

    private fun parseTemp(response: String): Int? {
        return try {
            val cleaned = cleanResponse(response, "01") ?: return null
            cleaned.substring(0, 2).toInt(16) - 40
        } catch (e: Exception) { null }
    }

    private fun parsePercent(response: String): Double? {
        return try {
            val cleaned = cleanResponse(response, "01") ?: return null
            cleaned.substring(0, 2).toInt(16) * 100.0 / 255.0
        } catch (e: Exception) { null }
    }

    private fun parseFuelTrim(response: String): Double? {
        return try {
            val cleaned = cleanResponse(response, "01") ?: return null
            (cleaned.substring(0, 2).toInt(16) - 128) * 100.0 / 128.0
        } catch (e: Exception) { null }
    }

    private fun parseBattery(response: String): Double? {
        return try {
            // ATRV retorna algo como "12.4V"
            val cleaned = response.replace("V", "").replace(">", "").trim()
            cleaned.toDoubleOrNull()
        } catch (e: Exception) { null }
    }

    private fun cleanResponse(response: String, prefix: String): String? {
        val lines = response.split("\r", "\n")
            .map { it.trim().replace(" ", "").uppercase() }
            .filter { it.isNotBlank() && !it.contains("SEARCHING") && !it.contains("UNABLE") && !it.contains("NODATA") }
        
        for (line in lines) {
            // Para comandos 01XX, a resposta deve começar com 41XX
            // Mas com ATH0 pode vir apenas 41... ou o valor direto dependendo do modo
            if (line.startsWith("41") || line.startsWith("4")) {
                // Tenta remover o prefixo de resposta (ex: 410C -> 410C)
                val expectedResponsePrefix = "4" + prefix.drop(1)
                return if (line.startsWith(expectedResponsePrefix)) {
                    line.drop(expectedResponsePrefix.length)
                } else {
                    line.drop(2) // Fallback: remove apenas os 2 primeiros (ex: 41)
                }
            }
        }
        return null
    }

    fun disconnect() {
        readJob?.cancel()
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        _state.value = ObdState.Disconnected
    }
}
