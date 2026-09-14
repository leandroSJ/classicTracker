package com.classictracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.classictracker.ui.MainViewModel
import com.classictracker.ui.screens.*
import com.classictracker.ui.theme.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddFuel : Screen("add_fuel")
    object AddFuelReserve : Screen("add_fuel_reserve")
    object AddOil : Screen("add_oil")
    object Map : Screen("map")
    object History : Screen("history")
    object Obd : Screen("obd")
    object Settings : Screen("settings")
}

data class BottomNavItem(val screen: Screen, val icon: ImageVector, val label: String)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, Icons.Default.Dashboard, "Painel"),
    BottomNavItem(Screen.Map, Icons.Default.Map, "Mapa"),
    BottomNavItem(Screen.History, Icons.Default.History, "Histórico"),
    BottomNavItem(Screen.Settings, Icons.Default.Settings, "Config")
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled gracefully in each screen */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Mantém a tela ligada enquanto o app estiver aberto (essencial para GPS/Dashboard)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestRequiredPermissions()

        setContent {
            val state by viewModel.uiState.collectAsState()
            ClassicTrackerTheme(darkTheme = state.isDarkMode) {
                ClassicTrackerApp(viewModel)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicTrackerApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val showBottomBar = bottomNavItems.any { it.screen.route == currentDest?.route }
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onAddFuel = { navController.navigate(Screen.AddFuel.route) },
                    onAddOil = { navController.navigate(Screen.AddOil.route) },
                    onNavigateMap = { navController.navigate(Screen.Map.route) },
                    onNavigateHistory = { navController.navigate(Screen.History.route) },
                    onNavigateObd = { navController.navigate(Screen.Obd.route) }
                )
            }
            composable(Screen.AddFuel.route) {
                AddFuelScreen(
                    viewModel = viewModel,
                    isReserve = false,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddFuelReserve.route) {
                AddFuelScreen(
                    viewModel = viewModel,
                    isReserve = true,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddOil.route) {
                AddOilChangeScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Map.route) {
                MapScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Obd.route) {
                ObdScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
