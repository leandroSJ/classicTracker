package com.classictracker.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherRepository {
    private val client = OkHttpClient()

    /**
     * Usa o serviço Open-Meteo (Gratuito e sem necessidade de chave API)
     */
    suspend fun getWeatherInfo(city: String): String = withContext(Dispatchers.IO) {
        try {
            // Coordenadas aproximadas para Salvador e Candeias - BA
            val (lat, lon) = when (city.lowercase()) {
                "salvador" -> "-12.97" to "-38.51"
                "candeias" -> "-12.67" to "-38.52"
                else -> return@withContext ""
            }

            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            android.util.Log.d("CT_WEATHER", "Buscando clima Open-Meteo para: $city")
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            
            val json = JSONObject(body)
            val current = json.getJSONObject("current_weather")
            val temp = current.getDouble("temperature").toInt()
            val code = current.getInt("weathercode")
            
            val desc = mapWeatherCode(code)
            
            android.util.Log.d("CT_WEATHER", "Sucesso $city: $temp graus, $desc")
            "$temp graus com $desc"
        } catch (e: Exception) {
            android.util.Log.e("CT_WEATHER", "Erro ao buscar clima para $city: ${e.message}")
            ""
        }
    }

    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "céu limpo"
            1, 2, 3 -> "céu parcialmente nublado"
            45, 48 -> "nevoeiro"
            51, 53, 55 -> "chuvisco"
            61, 63, 65 -> "chuva"
            80, 81, 82 -> "pancadas de chuva"
            95, 96, 99 -> "tempestade"
            else -> "tempo estável"
        }
    }
}
