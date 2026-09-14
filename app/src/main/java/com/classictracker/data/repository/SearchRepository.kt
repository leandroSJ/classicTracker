package com.classictracker.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class SearchRepository {
    private val client = OkHttpClient()

    /**
     * Usa o DuckDuckGo Instant Answer API (Gratuito, sem chave) para buscar respostas rápidas.
     */
    suspend fun searchInstantAnswer(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            
            val json = JSONObject(body)
            
            // Tenta pegar a resposta abstrata (o resumo principal do DDG)
            val abstractText = json.optString("AbstractText")
            if (abstractText.isNotEmpty()) return@withContext abstractText

            // Tenta pegar o Answer (quando é algo mais direto tipo calculadora ou fatos)
            val answer = json.optString("Answer")
            if (answer.isNotEmpty()) return@withContext answer

            // Tenta o snippet do primeiro tópico relacionado como fallback
            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null && relatedTopics.length() > 0) {
                val first = relatedTopics.optJSONObject(0)
                if (first != null) {
                    val text = first.optString("Text")
                    if (text.isNotEmpty()) return@withContext text
                }
            }

            ""
        } catch (e: Exception) {
            android.util.Log.e("CT_SEARCH", "Erro na busca DDG: ${e.message}")
            ""
        }
    }
}
