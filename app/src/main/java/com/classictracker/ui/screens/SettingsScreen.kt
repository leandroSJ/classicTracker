package com.classictracker.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.classictracker.data.models.VehicleConfig
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.components.*
import com.classictracker.ui.theme.*
import com.classictracker.utils.DecimalVisualTransformation
import com.classictracker.utils.cleanToDouble
import com.classictracker.utils.toMaskedString
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider

val CAR_BRANDS = listOf(
    "Chevrolet", "Fiat", "Volkswagen", "Ford", "Toyota", "Honda", "Renault",
    "Hyundai", "Jeep", "Nissan", "Peugeot", "Citroën", "Kia", "Mitsubishi",
    "Honda Moto", "Yamaha", "Suzuki", "Kawasaki", "BMW Moto", "Ducati",
    "Harley-Davidson", "Royal Enfield", "Dafra", "Haojue", "Shineray", "Traxx",
    "Outro"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val config = state.vehicleConfig
    val colorScheme = MaterialTheme.colorScheme

    // Configuração Google Sign-In
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("894283538530-t6ejbt89e5ra33klultiuan3uracelu9.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account?.idToken?.let { idToken ->
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                viewModel.signInWithFirebase(credential)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro no login: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val json = inputStream?.bufferedReader().use { r -> r?.readText() }
                if (json != null) {
                    viewModel.importMapData(json)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao ler arquivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var brand by remember(config) { mutableStateOf(config.brand) }
    var model by remember(config) { mutableStateOf(config.model) }
    var year by remember(config) { mutableStateOf(config.year.toString()) }
    var manufYear by remember(config) { mutableStateOf(config.manufacturingYear.toString()) }
    var engine by remember(config) { mutableStateOf(config.engineCC) }
    var tank by remember(config) { mutableStateOf(config.tankLiters.toMaskedString()) }
    var reserve by remember(config) { mutableStateOf(config.reserveLiters.toMaskedString()) }
    var odometer by remember(config) { mutableStateOf(config.currentOdometerKm.toMaskedString()) }
    var oilInterval by remember(config) { mutableStateOf(config.oilChangeIntervalKm.toMaskedString()) }
    var hasAC by remember(config) { mutableStateOf(config.hasAC) }
    var brandExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Configurações", fontSize = 16.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = colorScheme.onSurface)
                }
            },
            actions = {
                TextButton(onClick = {
                    viewModel.saveVehicleConfig(
                        config.copy(
                            brand = brand,
                            model = model,
                            year = year.toIntOrNull() ?: 0,
                            manufacturingYear = manufYear.toIntOrNull() ?: 0,
                            engineCC = engine,
                            tankLiters = tank.cleanToDouble(),
                            reserveLiters = reserve.cleanToDouble(),
                            currentOdometerKm = odometer.cleanToDouble(),
                            oilChangeIntervalKm = oilInterval.cleanToDouble(),
                            hasAC = hasAC
                        )
                    )
                    Toast.makeText(context, "Configurações salvas!", Toast.LENGTH_SHORT).show()
                    onBack()
                }) {
                    Text("SALVAR", color = colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader("Identificação do Veículo")
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(expanded = brandExpanded, onExpandedChange = { brandExpanded = it }) {
                        OutlinedTextField(
                            value = brand,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Marca") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = brandExpanded,
                            onDismissRequest = { brandExpanded = false }
                        ) {
                            CAR_BRANDS.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b) },
                                    onClick = { brand = b; brandExpanded = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = manufYear,
                            onValueChange = { manufYear = it.take(4) },
                            label = { Text("Ano Fab.") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it.take(4) },
                            label = { Text("Ano Mod.") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    OutlinedTextField(
                        value = engine,
                        onValueChange = { engine = it },
                        label = { Text("Motorização") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionHeader("Tanque e Combustível")
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tank,
                        onValueChange = { tank = it },
                        label = { Text("Capacidade do Tanque (L)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DecimalVisualTransformation()
                    )
                    OutlinedTextField(
                        value = reserve,
                        onValueChange = { reserve = it },
                        label = { Text("Litros na Reserva (L)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DecimalVisualTransformation()
                    )
                }
            }

            SectionHeader("Hodômetro e Óleo")
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = odometer,
                        onValueChange = { odometer = it },
                        label = { Text("Quilometragem Atual (km)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DecimalVisualTransformation()
                    )
                    OutlinedTextField(
                        value = oilInterval,
                        onValueChange = { oilInterval = it },
                        label = { Text("Intervalo Troca Óleo (km)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = DecimalVisualTransformation()
                    )
                }
            }

            SectionHeader("Opcionais")
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Ar Condicionado", color = colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text("Influencia no consumo", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(checked = hasAC, onCheckedChange = { hasAC = it })
                }
            }

            SectionHeader("Assistente de Voz")
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Voz do Assistente", color = colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    
                    ExposedDropdownMenuBox(expanded = voiceExpanded, onExpandedChange = { voiceExpanded = it }) {
                        OutlinedTextField(
                            value = state.selectedVoiceName ?: "Padrão do Sistema",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) }
                        )
                        ExposedDropdownMenu(expanded = voiceExpanded, onDismissRequest = { voiceExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Padrão do Sistema") },
                                onClick = { viewModel.saveSelectedVoice(null); voiceExpanded = false }
                            )
                            state.availableVoices.forEach { voiceName ->
                                DropdownMenuItem(
                                    text = { Text(voiceName) },
                                    onClick = { viewModel.saveSelectedVoice(voiceName); voiceExpanded = false }
                                )
                            }
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            val intent = Intent().apply {
                                action = "com.android.settings.TTS_SETTINGS"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Baixar novas vozes")
                    }
                }
            }

            SectionHeader("Sincronização na Nuvem")
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.isSyncEnabled) {
                        Button(
                            onClick = { authLauncher.launch(googleSignInClient.signInIntent) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Login, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Entrar com Google")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sincronizado", color = colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(state.userEmail ?: "", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            TextButton(onClick = { viewModel.logout() }) {
                                Text("Sair", color = colorScheme.error)
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.forceSyncAllToCloud() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Sync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enviar para Nuvem")
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreDataFromCloud() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Restaurar da Nuvem")
                        }
                    }
                }
            }

            SectionHeader("Backup Local")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.exportMapData() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.surfaceVariant, contentColor = colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Exportar")
                }
                Button(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.surfaceVariant, contentColor = colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Importar")
                }
            }

            SectionHeader("Limpeza de Dados")
            var showDeleteTripsDialog by remember { mutableStateOf(false) }
            
            Button(
                onClick = { showDeleteTripsDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.onErrorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Apagar Todos os Trajetos")
            }

            if (showDeleteTripsDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteTripsDialog = false },
                    containerColor = colorScheme.surface,
                    title = { Text("Apagar todos os trajetos?", fontWeight = FontWeight.Bold) },
                    text = { Text("Isso removerá permanentemente todos os registros de viagens do app e da nuvem. Locais salvos não serão afetados.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteAllTrips()
                                showDeleteTripsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) { Text("Apagar Tudo", color = colorScheme.onError) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteTripsDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
