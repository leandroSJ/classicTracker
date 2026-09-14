package com.classictracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classictracker.ui.theme.*

@Composable
fun GaugeCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val defaultAccent = accentColor ?: colorScheme.primary
    val accent = if (warning) colorScheme.error else defaultAccent
    
    Card(
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = color ?: colorScheme.primary
    
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = colorScheme.onPrimary
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = modifier.fillMaxWidth(),
        suffix = suffix?.let { { Text(it, color = TextSecondary, fontSize = 13.sp) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GaugeTeal,
            unfocusedBorderColor = Divider,
            focusedLabelColor = GaugeTeal,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = GaugeTeal
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
fun FuelTypeSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf("GASOLINA", "ÁLCOOL", "DIESEL", "GNV")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = selected == option
            val color = if (isSelected) GaugeTeal else GarageCard
            val contentColor = if (isSelected) GarageBlack else TextSecondary
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) },
                shape = RoundedCornerShape(10.dp),
                color = color,
                border = if (!isSelected) BorderStroke(1.dp, Divider) else null
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
