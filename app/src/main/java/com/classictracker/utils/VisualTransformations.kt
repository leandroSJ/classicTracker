package com.classictracker.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * VisualTransformation para formatar números decimais enquanto o usuário digita.
 * A máscara espera que a String original contenha apenas dígitos representando os centavos.
 * Exemplo: "800000" (8 mil) -> formatado como "8.000,00"
 */
class DecimalVisualTransformation : VisualTransformation {
    private val symbols = DecimalFormatSymbols(Locale("pt", "BR"))
    private val df = DecimalFormat("#,##0.00", symbols)

    override fun filter(text: AnnotatedString): TransformedText {
        val cleanText = text.text.filter { it.isDigit() }
        
        if (cleanText.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val parsed = cleanText.toDoubleOrNull() ?: 0.0
        val formatted = df.format(parsed / 100.0)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = cleanText.length
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Converte a String de dígitos (centavos) para Double real.
 * "800000" -> 8000.0
 */
fun String.cleanToDouble(): Double {
    val clean = this.filter { it.isDigit() }
    return (clean.toDoubleOrNull() ?: 0.0) / 100.0
}

/**
 * Converte um Double para a String de dígitos que a máscara espera.
 * 8000.0 -> "800000"
 */
fun Double.toMaskedString(): String {
    return "%.0f".format(this * 100)
}
