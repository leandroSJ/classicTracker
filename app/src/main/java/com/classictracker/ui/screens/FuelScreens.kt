package com.classictracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.components.*
import com.classictracker.ui.theme.*
import com.classictracker.utils.DecimalVisualTransformation
import com.classictracker.utils.cleanToDouble
import com.classictracker.utils.toMaskedString
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFuelScreen(
    viewModel: MainViewModel,
    isReserve: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var valueSupplied by remember { mutableStateOf("") }
    var pricePerLiter by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("GASOLINA") }
    var odometer by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()

    // Controle de Data Manual
    var useManualDate by remember { mutableStateOf(false) }
    var manualDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    var manualTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    // Pre-fill odometer
    LaunchedEffect(state.vehicleConfig.currentOdometerKm) {
        if (odometer.isEmpty() && state.vehicleConfig.currentOdometerKm > 0) {
            odometer = state.vehicleConfig.currentOdometerKm.toMaskedString()
        }
    }

    val estimatedKmL = if (state.avgConsumptionKmL > 0) state.avgConsumptionKmL else 10.0
    val totalPaid   = valueSupplied.cleanToDouble()
    val priceL      = pricePerLiter.cleanToDouble()
    val litersCalc  = if (priceL > 0) totalPaid / priceL else 0.0
    val estimatedRange = litersCalc * estimatedKmL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageBlack)
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    if (isReserve) "⚠️ Entrei na Reserva — Abastecer" else "⛽ Registrar Abastecimento",
                    fontSize = 16.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = GaragePanel,
                titleContentColor = TextPrimary
            )
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Opção de Registro Manual (Retroativo)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = GaugeTeal.copy(alpha = 0.05f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useManualDate, onCheckedChange = { useManualDate = it }, colors = CheckboxDefaults.colors(checkedColor = GaugeTeal))
                        Text("Registrar com data antiga (Manual)", color = TextPrimary, fontSize = 13.sp)
                    }
                    if (useManualDate) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = manualDate,
                                onValueChange = { manualDate = it },
                                label = { Text("Data (DD/MM/AAAA)") },
                                modifier = Modifier.weight(1.5f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                            OutlinedTextField(
                                value = manualTime,
                                onValueChange = { manualTime = it },
                                label = { Text("Hora (HH:MM)") },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                        }
                        Text("Use este campo para lançar abastecimentos que esqueceu de registrar.", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            SectionHeader("Tipo de Combustível")
            FuelTypeSelector(selected = fuelType, onSelect = { fuelType = it })

            SectionHeader("Valor abastecido e Preço do litro")
            InputField(
                value = valueSupplied,
                onValueChange = { if (it.length <= 12) valueSupplied = it.filter { c -> c.isDigit() } },
                label = "Valor total pago",
                suffix = "R$",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DecimalVisualTransformation()
            )
            InputField(
                value = pricePerLiter,
                onValueChange = { if (it.length <= 9) pricePerLiter = it.filter { c -> c.isDigit() } },
                label = "Preço por litro",
                suffix = "R$/l",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DecimalVisualTransformation()
            )

            SectionHeader("Hodômetro")
            InputField(
                value = odometer,
                onValueChange = {
                    odometer = it.filter { c -> c.isDigit() }
                },
                label = "Km atual do veículo",
                suffix = "km",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DecimalVisualTransformation()
            )

            // Live preview
            if (litersCalc > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GarageCard)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Quantidade de Litros", color = TextSecondary, fontSize = 11.sp)
                            Text("%.2f L".format(litersCalc), color = GaugeAmber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        if (estimatedRange > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Autonomia estimada", color = TextSecondary, fontSize = 11.sp)
                                Text("~%.0f km".format(estimatedRange), color = GaugeTeal, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val price = pricePerLiter.cleanToDouble()
                    val km = odometer.cleanToDouble()
                    
                    var timestamp: Long? = null
                    if (useManualDate) {
                        try {
                            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            timestamp = format.parse("$manualDate $manualTime")?.time
                        } catch (_: Exception) {
                            Toast.makeText(context, "Formato de data inválido!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                    }

                    viewModel.addFuelRecord(
                        fuelType = fuelType,
                        liters = litersCalc,
                        pricePerLiter = price,
                        isReserve = isReserve,
                        odometerKm = km,
                        manualTimestamp = timestamp
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GaugeTeal),
                enabled = valueSupplied.isNotEmpty() && pricePerLiter.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, null, tint = GarageBlack)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Salvar Abastecimento",
                    color = GarageBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOilChangeScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var notes by remember { mutableStateOf("") }
    var intervalKm by remember { mutableStateOf(5000.0.toMaskedString()) }
    var odometer by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.vehicleConfig.currentOdometerKm) {
        if (odometer.isEmpty() && state.vehicleConfig.currentOdometerKm > 0) {
            odometer = state.vehicleConfig.currentOdometerKm.toMaskedString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageBlack)
            .imePadding()
    ) {
        TopAppBar(
            title = { Text("🔧 Registrar Troca de Óleo", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = GaragePanel,
                titleContentColor = TextPrimary
            )
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            InputField(
                value = odometer,
                onValueChange = { if (it.length <= 12) odometer = it.filter { c -> c.isDigit() } },
                label = "Hodômetro atual",
                suffix = "km",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DecimalVisualTransformation()
            )
            InputField(
                value = intervalKm,
                onValueChange = { if (it.length <= 9) intervalKm = it.filter { c -> c.isDigit() } },
                label = "Próxima troca em",
                suffix = "km",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DecimalVisualTransformation()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observações (opcional)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GaugeAmber,
                    unfocusedBorderColor = Divider,
                    focusedLabelColor = GaugeAmber,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                maxLines = 3
            )

            state.lastOilChange?.let { last ->
                Text(
                    "Última troca: ${last.odometerKm.toLong()} km",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val km = odometer.cleanToDouble()
                    val interval = intervalKm.cleanToDouble()
                    viewModel.updateOdometer(km)
                    // Aqui assume-se que o addOilChange no ViewModel usa o valor atual do hodômetro salvo
                    viewModel.addOilChange(notes, customInterval = interval)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GaugeAmber)
            ) {
                Icon(Icons.Default.Check, null, tint = GarageBlack)
                Spacer(Modifier.width(8.dp))
                Text("Salvar Troca de Óleo", color = GarageBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
