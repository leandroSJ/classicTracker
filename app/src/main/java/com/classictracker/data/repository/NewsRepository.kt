package com.classictracker.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class NewsRepository {
    private val client = OkHttpClient()
    
    // Lista de fontes de notícias sugeridas
    private val sources = listOf(
        "https://www.infomoney.com.br/feed/",
        "https://www.correio24horas.com.br/rss",
        "https://jornalcandeias.com.br/feed/",
        "https://www.tiacandia.com.br/feed/",
        "https://g1.globo.com/rss/bahia/"
    )

    suspend fun getSalvadorNews(): List<String> = withContext(Dispatchers.IO) {
        val allTitles = mutableListOf<String>()
        
        // Tenta buscar de 2 fontes aleatórias para diversificar
        sources.shuffled().take(2).forEach { url ->
            try {
                android.util.Log.d("CT_NEWS", "Acessando fonte: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                // Regex aprimorado para capturar títulos de feeds RSS
                val matcher = Pattern.compile("<title>(.*?)</title>").matcher(body)
                
                var count = 0
                while (matcher.find() && count < 3) {
                    var title = matcher.group(1) ?: ""
                    title = title.replace("<![CDATA[", "").replace("]]>", "").trim()
                    
                    // Filtra títulos genéricos ou muito curtos
                    if (title.isNotEmpty() && 
                        title.length > 15 && 
                        !title.contains("RSS") && 
                        !title.contains("Feed") &&
                        !allTitles.contains(title)) {
                        allTitles.add(title)
                        count++
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CT_NEWS", "Erro na fonte $url: ${e.message}")
            }
        }
        
        allTitles.take(4) // Retorna as 4 melhores notícias encontradas
    }
}
