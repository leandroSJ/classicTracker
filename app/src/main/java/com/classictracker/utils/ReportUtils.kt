package com.classictracker.utils

import android.content.Context
import android.content.Intent
import androidx.viewbinding.BuildConfig
import com.classictracker.data.models.TripSession
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil


object ReportUtils {

    fun buildTripReport(
        session: TripSession,
        routePoints: List<com.classictracker.data.models.RoutePoint>,
        avgConsumptionKmL: Double,
        costPerKm: Double
    ): String {
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val sdfTime = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        val distKm = session.distanceKm
        val durationMin = if (session.endTime != null) (session.endTime - session.startTime) / 60000 else 0L
        val durationHours = durationMin / 60
        val durationMinRem = durationMin % 60
        val fuelUsed = if (avgConsumptionKmL > 0) distKm / avgConsumptionKmL else 0.0
        val cost = distKm * costPerKm

        // Cria o link da rota completa usando os pontos reais coletados
        val mapLink = if (routePoints.isNotEmpty()) {
            val baseUrl = "https://www.google.com/maps/dir/"
            // Pega o primeiro ponto, o último, e alguns intermediários (máximo 10 para o link não ficar gigante)
            val pointsToInclude = mutableListOf<com.classictracker.data.models.RoutePoint>()
            pointsToInclude.add(routePoints.first())
            
            if (routePoints.size > 2) {
                val step = (routePoints.size / 8).coerceAtLeast(1)
                for (i in step until routePoints.size - 1 step step) {
                    pointsToInclude.add(routePoints[i])
                }
            }
            
            if (routePoints.size > 1) {
                pointsToInclude.add(routePoints.last())
            }

            val waypoints = pointsToInclude.joinToString("/") { "${it.latitude},${it.longitude}" }
            baseUrl + waypoints
        } else if (session.pointALat != 0.0 && session.pointBLat != 0.0) {
            "https://www.google.com/maps/dir/${session.pointALat},${session.pointALng}/${session.pointBLat},${session.pointBLng}"
        } else ""

        return buildString {
            appendLine("*RELATÓRIO DE DESLOCAMENTO*")
            appendLine("*Data:* ${sdfDate.format(Date(session.startTime))}")
            appendLine("*Início:* ${sdfTime.format(Date(session.startTime))}")
            if (session.endTime != null) {
                appendLine("*Fim:* ${sdfTime.format(Date(session.endTime))}")
            }
            appendLine()

            if (session.pointAName.isNotEmpty() || session.pointBName.isNotEmpty()) {
                if (session.pointAName.isNotEmpty())
                    appendLine("*Origem:* ${session.pointAName}")
                if (session.pointBName.isNotEmpty())
                    appendLine("*Destino:* ${session.pointBName}")
                appendLine()
            }

            appendLine("*Distância percorrida:* %.2f km".format(distKm))

            if (session.avgSpeedKmh > 0) {
                appendLine("*Velocidade média:* %.1f km/h".format(session.avgSpeedKmh))
            }
            if (session.maxSpeedKmh > 0) {
                appendLine("*Velocidade máxima:* %.1f km/h".format(session.maxSpeedKmh))
            }

            if (durationMin > 0) {
                val durText = if (durationHours > 0) "${durationHours}h ${durationMinRem}min" else "${durationMinRem} min"
                appendLine("*Tempo total:* $durText")
            }

            if (avgConsumptionKmL > 0) {
                appendLine("*Consumo médio:* %.1f km/l".format(avgConsumptionKmL))
                appendLine("*Combustível gasto:* %.2f L".format(fuelUsed))
            }

            if (costPerKm > 0) {
                appendLine("*Custo estimado:* R$ %.2f".format(cost))
            }

            if (mapLink.isNotEmpty()) {
                appendLine()
                appendLine("*COMPROVAÇÃO DE ROTA:*")
                appendLine(mapLink)
            }

            appendLine()
            appendLine("════════════════════════════════")
            appendLine("Classic Tracker v1.84")
            appendLine("Desenvolvido por Leandro SJ")
            appendLine("Gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())}")
        }
    }

    fun shareReport(context: Context, report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Relatório de Deslocamento")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
    }

    fun buildMonthlyReport(
        totalDistanceKm: Double,
        totalSpentR: Double,
        totalLiters: Double,
        avgConsumptionKmL: Double,
        month: String
    ): String {
        return buildString {
            appendLine("📊 RELATÓRIO MENSAL — $month")
            appendLine("════════════════════════════════")
            appendLine("📏 Total percorrido: ${ceil(totalDistanceKm).toLong()} km")
            appendLine("⛽ Total abastecido: %.1f L".format(totalLiters))
            appendLine("💰 Total gasto em combustível: R$ %.2f".format(totalSpentR))
            if (avgConsumptionKmL > 0) {
                appendLine("📈 Consumo médio: %.1f km/l".format(avgConsumptionKmL))
                appendLine("💵 Custo por km: R$ %.3f".format(totalSpentR / totalDistanceKm.coerceAtLeast(1.0)))
            }
            appendLine("════════════════════════════════")
            appendLine("Relatório gerado pelo Classic Tracker")
        }
    }
}
