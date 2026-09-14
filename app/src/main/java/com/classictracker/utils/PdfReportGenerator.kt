package com.classictracker.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.classictracker.data.models.RoutePoint
import com.classictracker.data.models.TripSession
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.classictracker.R

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun generate(
        context: Context,
        session: TripSession,
        routePoints: List<RoutePoint>,
        avgConsumptionKmL: Double,
        costPerKm: Double
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        var pageCount = 1
        var y = 50f

        val titlePaint = Paint().apply {
            color = "#00D4AA".toColorInt() // GaugeTeal
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = "#333333".toColorInt()
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = "#555555".toColorInt()
            textSize = 12f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = "#DDDDDD".toColorInt()
            strokeWidth = 1f
        }

        val accentPaint = Paint().apply {
            color = "#00D4AA".toColorInt()
            style = Paint.Style.FILL
        }

        fun drawFooter(c: Canvas, pNum: Int) {
            val footerPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
            c.drawText("Página $pNum", PAGE_WIDTH / 2f, PAGE_HEIGHT - 30f, footerPaint)
            c.drawText("Classic Tracker v1.84 - Desenvolvido por Leandro SJ", PAGE_WIDTH / 2f, PAGE_HEIGHT - 15f, footerPaint)
        }

        fun checkNewPage() {
            if (y > PAGE_HEIGHT - 100) {
                drawFooter(canvas, pageCount)
                pdf.finishPage(page)
                pageCount++
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // --- CABEÇALHO COM LOGO ---
        try {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_xxhdpi_144px)
            if (logo != null) {
                val scaledLogo = Bitmap.createScaledBitmap(logo, 60, 60, true)
                canvas.drawBitmap(scaledLogo, MARGIN, y - 10, null)
            }
        } catch (e: Exception) {}

        canvas.drawText("CLASSIC TRACKER", MARGIN + 70f, y + 20, titlePaint)
        y += 50

        // Faixa colorida de título
        canvas.drawRect(0f, y, PAGE_WIDTH.toFloat(), y + 30, accentPaint)
        val whiteTitle = Paint(headerPaint).apply { color = Color.WHITE }
        canvas.drawText("RELATÓRIO DE VIAGEM", MARGIN, y + 22, whiteTitle)
        y += 60

        // --- DADOS DA VIAGEM ---
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val sdfTime = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

        fun drawRow(label: String, value: String) {
            checkNewPage()
            canvas.drawText("$label:", MARGIN, y, headerPaint)
            canvas.drawText(value, MARGIN + 120f, y, textPaint)
            y += 25
            canvas.drawLine(MARGIN, y - 5, PAGE_WIDTH - MARGIN, y - 5, linePaint)
            y += 10
        }

        drawRow("Data", sdfDate.format(Date(session.startTime)))
        drawRow("Início", sdfTime.format(Date(session.startTime)))
        if (session.endTime != null) {
            drawRow("Fim", sdfTime.format(Date(session.endTime)))
            val totalMin = (session.endTime - session.startTime) / 60000
            val hours = totalMin / 60
            val minutes = totalMin % 60
            drawRow("Duração", if (hours > 0) "${hours}h ${minutes}min" else "$minutes min")
        }

        if (session.pointAName.isNotBlank()) drawRow("Origem", session.pointAName)
        if (session.pointBName.isNotBlank()) drawRow("Destino", session.pointBName)
        
        drawRow("Distância", "%.2f km".format(session.distanceKm))
        drawRow("Vel. Média", "%.1f km/h".format(session.avgSpeedKmh))
        drawRow("Vel. Máxima", "%.1f km/h".format(session.maxSpeedKmh))

        val fuelUsed = if (avgConsumptionKmL > 0) session.distanceKm / avgConsumptionKmL else 0.0
        if (avgConsumptionKmL > 0) {
            drawRow("Consumo Médio", "%.2f km/L".format(avgConsumptionKmL))
            drawRow("Combustível", "%.2f L".format(fuelUsed))
        }
        if (costPerKm > 0) {
            drawRow("Custo Est.", "R$ %.2f".format(session.distanceKm * costPerKm))
        }

        y += 20
        checkNewPage()

        checkNewPage()
        // --- MINIATURA DO MAPA (CROQUI) ---
        /*if (routePoints.size > 2) {
            canvas.drawText("TRAJETO PERCORRIDO", MARGIN, y, headerPaint)
            y += 15
            val mapHeight = 150f
            val mapWidth = PAGE_WIDTH - (MARGIN * 2)
            
            val mapPaint = Paint().apply {
                color = "#F9F9F9".toColorInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(MARGIN, y, MARGIN + mapWidth, y + mapHeight, mapPaint)
            
            val pathPaint = Paint().apply {
                color = "#00D4AA".toColorInt()
                strokeWidth = 3f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            val lats = routePoints.map { it.latitude }
            val lngs = routePoints.map { it.longitude }
            val minLat = lats.minOrNull() ?: 0.0
            val maxLat = lats.maxOrNull() ?: 0.0
            val minLng = lngs.minOrNull() ?: 0.0
            val maxLng = lngs.maxOrNull() ?: 0.0
            
            val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
            val lngRange = (maxLng - minLng).coerceAtLeast(0.0001)

            val path = Path()
            routePoints.forEachIndexed { i, pt ->
                val px = MARGIN + ((pt.longitude - minLng) / lngRange * mapWidth).toFloat()
                val py = y + mapHeight - ((pt.latitude - minLat) / latRange * mapHeight).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, pathPaint)
            y += mapHeight + 30
        }*/

        // --- QR CODE ---
        checkNewPage()
        val mapLink = "https://www.google.com/maps/dir/${session.pointALat},${session.pointALng}/${session.pointBLat},${session.pointBLng}"
        try {
            val qrBitmap = generateQRCode(mapLink, 100)
            if (qrBitmap != null) {
                canvas.drawBitmap(qrBitmap, PAGE_WIDTH - MARGIN - 100f, y, null)
                val qrTextPaint = Paint(textPaint).apply { textSize = 8f; textAlign = Paint.Align.RIGHT }
                canvas.drawText("Escaneie para ver", PAGE_WIDTH - MARGIN, y + 115f, qrTextPaint)
                canvas.drawText("no Google Maps", PAGE_WIDTH - MARGIN, y + 125f, qrTextPaint)
            }
        } catch (e: Exception) {}

        // Observações
        if (session.notes.isNotBlank()) {
            y += 20
            checkNewPage()
            canvas.drawText("OBSERVAÇÕES:", MARGIN, y, headerPaint)
            y += 20
            
            // Quebra de linha simples para observações longas
            val words = session.notes.split(" ")
            var line = ""
            for (word in words) {
                if (textPaint.measureText("$line $word") < (PAGE_WIDTH - (MARGIN * 2))) {
                    line += "$word "
                } else {
                    canvas.drawText(line, MARGIN, y, textPaint)
                    y += 18
                    checkNewPage()
                    line = "$word "
                }
            }
            canvas.drawText(line, MARGIN, y, textPaint)
        }

        drawFooter(canvas, pageCount)
        pdf.finishPage(page)

        val folder = File(context.getExternalFilesDir(null), "Relatorios")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "Relatorio_${session.sessionId}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun generateQRCode(text: String, size: Int): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) { null }
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Relatório Classic Tracker")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar PDF"))
    }
}
