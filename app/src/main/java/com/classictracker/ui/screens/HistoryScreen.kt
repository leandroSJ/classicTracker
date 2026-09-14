package com.classictracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classictracker.data.models.FuelRecord
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.components.*
import com.classictracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val fuelRecords by viewModel.allFuelRecords.collectAsState(initial = emptyList())

    val oilChanges by viewModel.allOilChanges.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        TopAppBar(
            title = { Text("Histórico", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = colorScheme.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface
            )
        )

        TabRow(
            selectedTabIndex = tab,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.primary
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }) {
                Text("Abastecimentos", modifier = Modifier.padding(12.dp), color = if (tab == 0) colorScheme.primary else colorScheme.onSurfaceVariant)
            }
            Tab(selected = tab == 1, onClick = { tab = 1 }) {
                Text("Trocas de Óleo", modifier = Modifier.padding(12.dp), color = if (tab == 1) colorScheme.primary else colorScheme.onSurfaceVariant)
            }
        }

        when (tab) {
            0 -> {
                // Stats summary
                if (fuelRecords.isNotEmpty()) {
                    val totalSpent = fuelRecords.sumOf { it.totalCost }
                    val totalLiters = fuelRecords.sumOf { it.liters }
                    val validSamples = fuelRecords.filter { it.consumption > 0 }
                    val avgKmL = if (validSamples.isNotEmpty()) validSamples.map { it.consumption }.average() else 0.0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceVariant)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTAL GASTO", color = colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text("R$ %.0f".format(totalSpent), color = colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTAL LITROS", color = colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text("%.2f L".format(totalLiters), color = colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MÉDIA GERAL", color = colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text(
                                if (avgKmL > 0) "%.1f km/l".format(avgKmL) else "--",
                                color = colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp
                            )
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.background(colorScheme.background)
                ) {
                    if (fuelRecords.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("Nenhum abastecimento registrado ainda.", color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(fuelRecords) { record ->
                        FuelRecordCard(record, sdf, onDelete = {viewModel.deleteFuelRecord(record)})
                    }
                }
            }

            1 -> {
                val oils by viewModel.allOilChanges.collectAsState(initial = emptyList())
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (oils.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("Nenhuma troca de óleo registrada.", color = TextSecondary)
                            }
                        }
                    }
                    items(oils) { oil ->
                        var showConfirm by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = GarageCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.OilBarrel, null, tint = GaugeAmber, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sdf.format(Date(oil.timestamp)), color = TextSecondary, fontSize = 11.sp)
                                    Text("${oil.odometerKm.toLong()} km", color = GaugeAmber, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Próxima troca em ${(oil.odometerKm + oil.intervalKm).toLong()} km", color = TextSecondary, fontSize = 12.sp)
                                    if (oil.notes.isNotEmpty()) Text(oil.notes, color = TextPrimary, fontSize = 12.sp)
                                }
                                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.DeleteOutline, "Excluir",
                                        tint = GaugeRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (showConfirm) {
                            AlertDialog(
                                onDismissRequest = { showConfirm = false },
                                containerColor = GarageCard,
                                title = { Text("Excluir troca de óleo?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                                text = { Text("Registro de ${oil.odometerKm.toLong()} km será removido.", color = TextSecondary) },
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.deleteOilChange(oil); showConfirm = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = GaugeRed)
                                    ) {
                                        Text("Excluir", color = GarageBlack, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirm = false }) {
                                        Text("Cancelar", color = TextSecondary)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FuelRecordCard(record: FuelRecord, sdf: SimpleDateFormat, onDelete: () -> Unit = {}) {
    var showConfirm by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalGasStation,
                        null,
                        tint = if (record.fuelType == "GASOLINA") colorScheme.secondary else colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        record.fuelType,
                        color = if (record.fuelType == "GASOLINA") colorScheme.secondary else colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (record.isReserve) {
                        Spacer(Modifier.width(6.dp))
                        StatusChip("RESERVA", colorScheme.error)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sdf.format(Date(record.timestamp)),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline, "Excluir",
                            tint = colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("%.2f L".format(record.liters), color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("R$ %.2f".format(record.totalCost), color = colorScheme.onSurfaceVariant, fontSize = 12.sp)

                }

                if (record.consumption > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.1f km/l".format(record.consumption), color = colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("${record.kmSinceLastFill.toLong()} km rodados", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            if (record.odometerKm > 0) {
                Spacer(Modifier.height(4.dp))
                Text("Hodômetro: ${record.odometerKm.toLong()} km", color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = colorScheme.surfaceVariant,
            title = { Text("Excluir abastecimento?", color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("${record.fuelType} — %.2f L — R$ %.2f será removido.".format(record.liters, record.totalCost), color = colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error, contentColor = colorScheme.onPrimary)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancelar", color = colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
