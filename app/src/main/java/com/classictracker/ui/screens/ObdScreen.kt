package com.classictracker.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classictracker.data.models.ObdData
import com.classictracker.obd.ObdState
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.components.*
import com.classictracker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val obdState by viewModel.obdState.collectAsState()
    val obdData by viewModel.obdData.collectAsState()
    val obdRecords by viewModel.allObdRecords.collectAsState(initial = emptyList())
    
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val btAdapter = btManager?.adapter

    var tabIndex by remember { mutableIntStateOf(0) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val pairedObdDevices = remember {
        try { btAdapter?.let { viewModel.obdManager.getObdDevices(it) } ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageBlack)
    ) {
        TopAppBar(
            title = { Text("🔌 Scanner OBD2", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = GaragePanel,
                titleContentColor = TextPrimary
            )
        )

        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = GaragePanel,
            contentColor = GaugeTeal
        ) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                Text("Sensores", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                Text("Histórico", modifier = Modifier.padding(12.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (tabIndex == 0) {
                // ─── Aba Sensores ───────────────────────────────────────────
                
                // Status card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GarageCard)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val (statusColor, statusText, statusIcon) = when (obdState) {
                            is ObdState.Disconnected -> Triple(TextSecondary, "Desconectado", Icons.Default.BluetoothDisabled)
                            is ObdState.Scanning -> Triple(GaugeBlue, "Conectando...", Icons.Default.Bluetooth)
                            is ObdState.Connected -> Triple(GaugeTeal, "Conectado: ${(obdState as ObdState.Connected).deviceName}", Icons.Default.BluetoothConnected)
                            is ObdState.Reading -> Triple(GaugeTeal, "Lendo sensores...", Icons.Default.Sensors)
                            is ObdState.Error -> Triple(GaugeRed, (obdState as ObdState.Error).message, Icons.Default.Error)
                        }
                        Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }

                // Device list or Readings
                if (obdState is ObdState.Disconnected || obdState is ObdState.Error) {
                    SectionHeader("Dispositivos OBD2 Pareados")
                    if (pairedObdDevices.isEmpty()) {
                        Text("Nenhum scanner OBD2 pareado no Android.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        pairedObdDevices.forEach { device ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { scope.launch { viewModel.obdManager.connect(device) } },
                                colors = CardDefaults.cardColors(containerColor = GarageCard)
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bluetooth, null, tint = GaugeBlue)
                                    Spacer(Modifier.width(12.dp))
                                    Text(device.name ?: "OBDII", color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                if (obdState is ObdState.Reading || obdState is ObdState.Connected) {
                    SectionHeader("Sensores em Tempo Real")
                    ObdDataGrid(obdData)
                    
                    if (obdData.fuelLevelPercent == null) {
                        Text("⚠️ Nível de combustível não detectado via OBD neste veículo.", color = GaugeAmber, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showRecordDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GaugeTeal)
                        ) {
                            Icon(Icons.Default.Save, null, tint = GarageBlack)
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar Leitura", color = GarageBlack)
                        }
                        
                        Button(
                            onClick = { viewModel.disconnectObd() },
                            modifier = Modifier.height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GarageCard)
                        ) {
                            Icon(Icons.Default.Stop, null, tint = GaugeRed)
                        }
                    }
                }
            } else {
                // ─── Aba Histórico ──────────────────────────────────────────
                SectionHeader("Registros Anteriores")
                
                if (obdRecords.isEmpty()) {
                    Text("Nenhum registro de sensor salvo ainda.", color = TextSecondary)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(obdRecords) { record ->
                            ObdRecordCard(record) { viewModel.deleteObdRecord(record) }
                        }
                    }
                }
            }
        }
    }

    if (showRecordDialog) {
        AlertDialog(
            onDismissRequest = { showRecordDialog = false },
            title = { Text("Registrar dados dos sensores", color = TextPrimary) },
            text = {
                Column {
                    Text("O nível de combustível (%.0f%%) e outros sensores serão gravados.".format(obdData.fuelLevelPercent ?: 0.0), color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Nota (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveCurrentObdData(notes)
                    showRecordDialog = false
                    notes = ""
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showRecordDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ObdRecordCard(record: com.classictracker.data.models.ObdRecord, onDelete: () -> Unit) {
    val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GarageCard)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sdf.format(java.util.Date(record.timestamp)), color = GaugeTeal, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, null, tint = GaugeRed)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                record.fuelLevelPercent?.let { Text("Comb: %.0f%%".format(it), color = TextPrimary, fontSize = 13.sp) }
                record.batteryVoltage?.let { Text("Bat: %.1fV".format(it), color = TextPrimary, fontSize = 13.sp) }
                record.coolantTempC?.let { Text("Temp: ${it}C", color = TextPrimary, fontSize = 13.sp) }
            }
            if (record.notes.isNotEmpty()) {
                Text(record.notes, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun ObdDataGrid(data: ObdData) {
    val items = listOf(
        Triple("RPM", data.rpm?.let { "$it rpm" } ?: "--", GaugeAmber),
        Triple("Velocidade", data.speedKmh?.let { "$it km/h" } ?: "--", GaugeTeal),
        Triple("Temp. Motor", data.coolantTempC?.let { "${it}°C" } ?: "--", GaugeRed),
        Triple("Nível Comb.", data.fuelLevelPercent?.let { "%.0f%%".format(it) } ?: "--", GaugeTeal),
        Triple("Carga Motor", data.engineLoadPercent?.let { "%.0f%%".format(it) } ?: "--", GaugeAmber),
        Triple("Temp. Ar", data.intakeAirTempC?.let { "${it}°C" } ?: "--", GaugeBlue),
        Triple("Acelerador", data.throttlePercent?.let { "%.0f%%".format(it) } ?: "--", GaugeAmber),
        Triple("Bateria", data.batteryVoltage?.let { "%.1fV".format(it) } ?: "--", GaugeBlue),
        Triple("Trim Comb.", data.fuelTrimLongPercent?.let { "%.1f%%".format(it) } ?: "--", GaugeTeal)
    )

    val columns = 3
    val rows = (items.size + columns - 1) / columns

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < items.size) {
                        val (label, value, color) = items[idx]
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = GarageCard)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(label, color = TextSecondary, fontSize = 10.sp)
                                Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
