package com.classictracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classictracker.service.TrackingService
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.components.*
import com.classictracker.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddFuel: () -> Unit,
    onAddOil: () -> Unit,
    onNavigateMap: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateObd: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme

    // Live speed ticker and Clock
    var liveSpeed by remember { mutableFloatStateOf(0f) }
    var liveDist by remember { mutableDoubleStateOf(0.0) }
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            liveSpeed = TrackingService.currentSpeedKmh
            liveDist = TrackingService.sessionDistance
            currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            kotlinx.coroutines.delay(200)
        }
    }

    val liveOdometer = state.vehicleConfig.currentOdometerKm + state.totalHistoryKm + liveDist

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${state.vehicleConfig.brand} ${state.vehicleConfig.model}",
                    color = colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${state.vehicleConfig.manufacturingYear}/${state.vehicleConfig.year} · ${state.vehicleConfig.engineCC}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { viewModel.toggleDarkMode() }) {
                    Icon(
                        if (state.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        null,
                        tint = colorScheme.primary
                    )
                }
                Text(
                    currentTime,
                    color = colorScheme.primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                if (state.obdConnected) {
                    StatusChip("OBD ✓", colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Speed + Tracking ─────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (state.isTracking) "• RASTREANDO" else "○ PARADO",
                        color = if (state.isTracking) colorScheme.primary else colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ODO: %.1f km".format(liveOdometer),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.0f".format(liveSpeed),
                        color = if (liveSpeed > 5) colorScheme.primary else colorScheme.onBackground,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "km/h", color = colorScheme.onSurfaceVariant, fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = (liveSpeed / 140f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 40.dp),
                    color = if (liveSpeed > 110) colorScheme.error else colorScheme.primary,
                    trackColor = colorScheme.outline
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!state.isTracking) {
                        Button(
                            onClick = { viewModel.startTracking() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = colorScheme.onPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Iniciar Viagem", color = colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopTracking() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, null, tint = colorScheme.onPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Parar", color = colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNavigateMap,
                            modifier = Modifier.height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Map, null, tint = colorScheme.onSecondary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Painel ───────────────────────────────────────────────────────────
        SectionHeader("Painel do Veículo")
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GaugeCard(
                title = "CONSUMO MÉDIO",
                value = if (state.avgConsumptionKmL > 0) "%.1f".format(state.avgConsumptionKmL) else "--",
                unit = "km/l",
                icon = Icons.Default.Speed,
                accentColor = colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            GaugeCard(
                title = "AUTONOMIA",
                value = if (state.estimatedRangeKm > 0) "%.0f".format(state.estimatedRangeKm) else "--",
                unit = "km",
                icon = Icons.Default.Route,
                accentColor = colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GaugeCard(
                title = "PRÓX. TROCA ÓLEO",
                value = if (state.kmUntilOilChange > 0) "%.0f".format(state.kmUntilOilChange) else "--",
                unit = "km",
                icon = Icons.Default.OilBarrel,
                accentColor = colorScheme.secondary,
                warning = state.kmUntilOilChange in 1.0..500.0,
                modifier = Modifier.weight(1f)
            )
            GaugeCard(
                title = "CUSTO POR KM RODADO",
                value = if (state.costPerKm > 0) "R$%.2f".format(state.costPerKm) else "--",
                unit = "",
                icon = Icons.Default.MonetizationOn,
                accentColor = colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Ações Rápidas ─────────────────────────────────────────────────────
        SectionHeader("Ações Rápidas")
        Spacer(Modifier.height(10.dp))

        var showReserveDialog by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryActionButton(
                text = "Abastecer",
                icon = Icons.Default.LocalGasStation,
                onClick = onAddFuel,
                modifier = Modifier.fillMaxWidth()
            )
            PrimaryActionButton(
                text = "Entrei na Reserva",
                icon = Icons.Default.Warning,
                onClick = { showReserveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.secondary
            )
            PrimaryActionButton(
                text = "Troca de Óleo",
                icon = Icons.Default.OilBarrel,
                onClick = onAddOil,
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.secondary
            )
            PrimaryActionButton(
                text = "OBD2 Scanner",
                icon = Icons.Default.Bluetooth,
                onClick = onNavigateObd,
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.secondary
            )
        }

        // Dialog reserva
        if (showReserveDialog) {
            AlertDialog(
                onDismissRequest = { showReserveDialog = false },
                containerColor = colorScheme.surfaceVariant,
                icon = {
                    Icon(Icons.Default.Warning, null, tint = colorScheme.secondary, modifier = Modifier.size(36.dp))
                },
                title = {
                    Text("⚠️ Reserva Acionada", color = colorScheme.secondary, fontWeight = FontWeight.Bold)
                },
                text = {
                    val autonomia = if (state.avgConsumptionKmL > 0)
                        "~%.0f km restantes".format(state.vehicleConfig.reserveLiters * state.avgConsumptionKmL)
                    else "Abasteça em breve"
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Reserva registrada!", color = colorScheme.onBackground)
                        Text("Autonomia estimada: $autonomia", color = colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(
                            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
                                .format(java.util.Date()),
                            color = colorScheme.onSurfaceVariant, fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.markReserveEntry()
                            showReserveDialog = false
                            onAddFuel()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.secondary)
                    ) {
                        Text("Abastecer agora", color = colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.markReserveEntry()
                        showReserveDialog = false
                    }) {
                        Text("Só registrar", color = colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Último Abastecimento ─────────────────────────────────────────────
        state.lastFuelRecord?.let { fuel ->
            SectionHeader("Último Abastecimento")
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(fuel.fuelType, color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Text("%.2f L".format(fuel.liters), color = colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("R$ %.2f".format(fuel.totalCost), color = colorScheme.secondary, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("R$ %.3f/l".format(fuel.pricePerLiter), color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        if (fuel.consumption > 0) {
                            Text("%.1f km/l".format(fuel.consumption), color = colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${fuel.kmSinceLastFill.roundToInt()} km percorridos", color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

    }
}