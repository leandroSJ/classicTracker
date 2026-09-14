package com.classictracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import com.classictracker.data.models.RoutePoint
import com.classictracker.data.models.SavedLocation
import com.classictracker.data.models.TripSession
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.components.*
import com.classictracker.ui.theme.*
import com.classictracker.utils.ReportUtils
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by viewModel.allTripSessions.collectAsState(initial = emptyList())
    val savedLocations by viewModel.allSavedLocations.collectAsState(initial = emptyList())
    
    var selectedSession by remember { mutableStateOf<TripSession?>(null) }
    var routePoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var showReportDialog by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var clickedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var showSaveAtClickDialog by remember { mutableStateOf(false) }
    var showRadarSpeedDialog by remember { mutableStateOf(false) }
    var radarLatLng by remember { mutableStateOf<LatLng?>(null) }
    
    var selectedLocationForAction by remember { mutableStateOf<SavedLocation?>(null) }
    var showEditLocationDialog by remember { mutableStateOf(false) }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-12.97, -38.50), 12f)
    }

    var followMe by remember { mutableStateOf(true) }

    // Efeito para o mapa acompanhar a localização
    LaunchedEffect(viewModel.uiState.collectAsState().value.currentSpeedKmh) {
        if (followMe) {
            val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            try {
                // Usando a API de localização mais recente para pegar a posição fluida
                fusedClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        location?.let {
                            scope.launch {
                                cameraState.animate(
                                    com.google.android.gms.maps.CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude))
                                )
                            }
                        }
                    }
            } catch (e: SecurityException) {}
        }
    }

    // Carrega pontos quando uma sessão é selecionada
    LaunchedEffect(selectedSession) {
        selectedSession?.sessionId?.let { id ->
            val points = viewModel.getRoutePoints(id)
            routePoints = points
            if (points.isNotEmpty()) {
                cameraState.animate(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                        LatLng(points.first().latitude, points.first().longitude), 15f
                    )
                )
            }
        } ?: run {
            routePoints = emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(GarageBlack)) {
        TopAppBar(
            title = { Text("Mapa & Alertas", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = GaragePanel, titleContentColor = TextPrimary)
        )

        TabRow(selectedTabIndex = tab, containerColor = GaragePanel, contentColor = GaugeTeal) {
            val titles = listOf("Mapa", "Trajetos", "Locais")
            titles.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }) {
                    Text(title, modifier = Modifier.padding(12.dp), color = if (tab == index) GaugeTeal else TextSecondary, fontSize = 13.sp)
                }
            }
        }

        when (tab) {
            0 -> MapViewTab(
                cameraState = cameraState,
                locations = savedLocations,
                routePoints = routePoints,
                onMapClick = { clickedLatLng = it; showSaveAtClickDialog = true },
                onLocationClick = { selectedLocationForAction = it },
                context = context,
                followMe = followMe,
                onFollowMeChange = { followMe = it }
            )
            1 -> TripHistoryListFull(sessions, savedLocations,
                onSelect = { s -> selectedSession = s; tab = 0 },
                onReport = { s -> selectedSession = s; showReportDialog = true },
                onDelete = { viewModel.deleteTripSession(it) }
            )
            2 -> SavedLocationsListFull(savedLocations, viewModel)
        }
    }

    if (selectedLocationForAction != null) {
        AlertDialog(
            onDismissRequest = { selectedLocationForAction = null },
            containerColor = GarageCard,
            title = { Text(selectedLocationForAction!!.name, color = TextPrimary) },
            text = { Text("O que deseja fazer com este local?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showEditLocationDialog = true }) { 
                    Text("Editar", color = GaugeTeal) 
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { 
                        viewModel.deleteLocation(selectedLocationForAction!!)
                        selectedLocationForAction = null
                    }) { 
                        Text("Excluir", color = GaugeRed) 
                    }
                    TextButton(onClick = { selectedLocationForAction = null }) { 
                        Text("Cancelar", color = TextSecondary) 
                    }
                }
            }
        )
    }

    if (showEditLocationDialog && selectedLocationForAction != null) {
        var newName by remember { mutableStateOf(selectedLocationForAction!!.name) }
        AlertDialog(
            onDismissRequest = { showEditLocationDialog = false; selectedLocationForAction = null },
            containerColor = GarageCard,
            title = { Text("Editar Nome", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Novo nome do local") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateLocation(selectedLocationForAction!!.copy(name = newName))
                    showEditLocationDialog = false
                    selectedLocationForAction = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditLocationDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showSaveAtClickDialog && clickedLatLng != null) {
        SaveLocationDialogFull(
            onSave = { name, isHome ->
                viewModel.saveLocation(name, clickedLatLng!!.latitude, clickedLatLng!!.longitude, isHome)
                showSaveAtClickDialog = false
            },
            onDismiss = { showSaveAtClickDialog = false }
        )
    }

    if (showReportDialog && selectedSession != null) {
        val uiState by viewModel.uiState.collectAsState()
        TripReportDialogFull(
            session = selectedSession!!,
            routePoints = routePoints,
            avgConsumption = uiState.avgConsumptionKmL,
            costPerKm = uiState.costPerKm,
            onShareText = { ReportUtils.shareReport(context, it) },
            onSharePdf = { session, points, avg, cost ->
                val file = com.classictracker.utils.PdfReportGenerator.generate(context, session, points, avg, cost)
                com.classictracker.utils.PdfReportGenerator.sharePdf(context, file)
            },
            onDismiss = { showReportDialog = false }
        )
    }

    if (showRadarSpeedDialog && radarLatLng != null) {
        var speedText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRadarSpeedDialog = false },
            containerColor = GarageCard,
            title = { Text("Reportar Radar", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Qual a velocidade máxima do radar?", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = speedText,
                        onValueChange = { speedText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Velocidade (km/h)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GaugeTeal, unfocusedBorderColor = Divider)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = if (speedText.isNotBlank()) "Radar de $speedText" else "Radar"
                        viewModel.saveLocation(name, radarLatLng!!.latitude, radarLatLng!!.longitude, false)
                        showRadarSpeedDialog = false
                        Toast.makeText(context, "Radar salvo como local!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeTeal)
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showRadarSpeedDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun MapViewTab(
    cameraState: CameraPositionState,
    locations: List<SavedLocation>,
    routePoints: List<RoutePoint>,
    onMapClick: (LatLng) -> Unit,
    onLocationClick: (SavedLocation) -> Unit,
    context: android.content.Context,
    followMe: Boolean,
    onFollowMeChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            onMapClick = onMapClick
        ) {
            if (routePoints.size >= 2) {
                Polyline(points = routePoints.map { LatLng(it.latitude, it.longitude) }, color = GaugeTeal, width = 10f)
            }
            locations.forEach { loc ->
                MarkerComposable(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)), 
                    anchor = Offset(0.5f, 1f),
                    onClick = { 
                        onLocationClick(loc)
                        onFollowMeChange(false) // Para de seguir se o usuário clicar em algo
                        true 
                    }
                ) {
                    MapLabel(loc.name, if (loc.isHome) GaugeAmber else GaugeTeal, if (loc.isHome) Icons.Default.Home else Icons.Default.Place)
                }
            }
        }

        // Botão para reativar o seguimento do mapa
        if (!followMe) {
            FloatingActionButton(
                onClick = { onFollowMeChange(true) },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).padding(bottom = 70.dp).size(44.dp),
                containerColor = GarageCard,
                contentColor = GaugeTeal
            ) { Icon(Icons.Default.Navigation, "Centralizar") }
        }

        FloatingActionButton(
            onClick = {
                val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                try {
                    fusedClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            scope.launch { cameraState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17f)) }
                        }
                    }
                } catch (e: SecurityException) {}
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(44.dp),
            containerColor = GarageCard, contentColor = GaugeTeal
        ) { Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun TripSessionCard(
    session: TripSession,
    onSelect: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale("pt", "BR"))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GarageCard),
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val startTime = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(session.startTime))
                val endTime = if (session.endTime != null) SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(session.endTime)) else "--:--"
                val date = SimpleDateFormat("dd/MM/yy", Locale("pt", "BR")).format(Date(session.startTime))
                
                Column {
                    Text(date, color = TextSecondary, fontSize = 11.sp)
                    Text("$startTime → $endTime", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.endTime != null) {
                        val totalMin = (session.endTime - session.startTime) / 60000
                        val hours = totalMin / 60
                        val minutes = totalMin % 60
                        val timeText = if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
                        StatusChip(timeText, GaugeBlue)

                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, "Excluir",
                            tint = GaugeRed.copy(alpha = 0.7f), modifier = Modifier.size(25.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.2f".format(session.distanceKm),
                    color = GaugeTeal, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(" km", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                    if (session.avgSpeedKmh > 0) {
                        Text("Média: %.1f km/h".format(session.avgSpeedKmh), color = TextSecondary, fontSize = 10.sp)
                    }
                    if (session.maxSpeedKmh > 0) {
                        Text("Máxima: %.1f km/h".format(session.maxSpeedKmh), color = GaugeRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (session.fuelUsedLiters > 0) {
                    Text("%.2f L".format(session.fuelUsedLiters),
                        color = GaugeAmber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            
            // Exibição de Localidades (Origem e Destino)
            if (session.pointAName.isNotEmpty() || session.pointBName.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (session.pointAName.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TripOrigin, null, tint = GaugeTeal, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(session.pointAName, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    if (session.pointBName.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, null, tint = GaugeAmber, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(session.pointBName, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GaugeTeal),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Map, null, tint = GaugeTeal, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver Rota", color = GaugeTeal, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onReport,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GaugeBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Description, null, tint = GaugeBlue, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Relatório", color = GaugeBlue, fontSize = 12.sp)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = GarageCard,
            title = { Text("Excluir trajeto?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Trajeto de %.1f km (%s) será excluído permanentemente com todos os pontos GPS."
                        .format(session.distanceKm, sdf.format(Date(session.startTime))),
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
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

@Composable
fun TripHistoryListFull(sessions: List<TripSession>, savedLocations: List<SavedLocation>, onSelect: (TripSession) -> Unit, onReport: (TripSession) -> Unit, onDelete: (TripSession) -> Unit) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum trajeto registrado.", color = TextSecondary) }
    } else {
        LazyColumn(Modifier.fillMaxSize().background(GarageBlack), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sessions) { session ->
                TripSessionCard(session = session, onSelect = { onSelect(session) }, onReport = { onReport(session) }, onDelete = { onDelete(session) })
            }
        }
    }
}

@Composable
fun SavedLocationsListFull(locations: List<SavedLocation>, viewModel: MainViewModel) {
    val context = LocalContext.current
    if (locations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum local salvo.", color = TextSecondary) }
    } else {
        LazyColumn(Modifier.fillMaxSize().background(GarageBlack), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(locations) { loc ->
                Card(colors = CardDefaults.cardColors(containerColor = GarageCard)) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (loc.isHome) Icons.Default.Home else Icons.Default.Place, null, tint = if (loc.isHome) GaugeAmber else GaugeTeal)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(loc.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("%.4f, %.4f".format(loc.latitude, loc.longitude), color = TextSecondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { 
                            val gmmIntentUri = Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
                            context.startActivity(mapIntent)
                        }) { Icon(Icons.Default.Navigation, null, tint = GaugeTeal) }
                        IconButton(onClick = { viewModel.deleteLocation(loc) }) { Icon(Icons.Default.DeleteOutline, null, tint = GaugeRed) }
                    }
                }
            }
        }
    }
}

@Composable
fun SaveLocationDialogFull(onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var isHome by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = GarageCard,
        title = { Text("Salvar Local", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do Local") }, shape = RoundedCornerShape(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isHome, onCheckedChange = { isHome = it })
                    Text("Ponto de Origem (Casa)", color = TextPrimary)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, isHome) }, enabled = name.isNotBlank()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun MapLabel(text: String, color: androidx.compose.ui.graphics.Color, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.background(GarageBlack.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(text, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun TripReportDialogFull(
    session: TripSession,
    routePoints: List<RoutePoint>,
    avgConsumption: Double,
    costPerKm: Double,
    onShareText: (String) -> Unit,
    onSharePdf: (TripSession, List<RoutePoint>, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val report = ReportUtils.buildTripReport(session, routePoints, avgConsumption, costPerKm)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GarageCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = GaugeBlue)
                Spacer(Modifier.width(8.dp))
                Text("Relatório de Viagem", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = GarageBlack)
            ) {
                Text(
                    report,
                    modifier = Modifier.padding(14.dp),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = { onShareText(report) },
                    colors = ButtonDefaults.buttonColors(containerColor = GaugeBlue)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Texto")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSharePdf(session, routePoints, avgConsumption, costPerKm) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(6.dp))
                    Text("PDF")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = TextSecondary) }
        }
    )
}

@Composable
fun ReportFab(icon: ImageVector, label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    SmallFloatingActionButton(onClick = onClick, containerColor = color, contentColor = GarageBlack) { Icon(icon, label) }
}
