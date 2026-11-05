package com.findtrack.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import com.findtrack.app.ui.components.AppTab
import com.findtrack.app.ui.components.FindTrackBottomBar
import com.findtrack.app.ui.screens.DeviceDetailScreen
import com.findtrack.app.ui.screens.DevicesScreen
import com.findtrack.app.ui.screens.FinderMapScreen
import com.findtrack.app.ui.screens.HistoryScreen
import com.findtrack.app.ui.screens.HomeScreen
import com.findtrack.app.ui.screens.SettingsScreen
import com.findtrack.app.ui.screens.TrackingScreen
import com.findtrack.app.ui.screens.WelcomeScreen
import com.findtrack.app.ui.theme.PageBg

/**
 * App navigation: the design's four tabs plus pushed screens (device detail,
 * tracking, settings). The bottom bar persists across all of them, exactly
 * like the approved mockups.
 */
@Composable
fun AppNav() {
    val vm: FindTrackViewModel = viewModel()
    val navController = rememberNavController()
    val context = LocalContext.current

    // First launch: show the welcome flow before the main scaffold.
    // A null value means DataStore is still loading — render a plain backdrop
    // so the welcome screen never flashes for returning users.
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    when (onboardingDone) {
        null -> {
            Box(modifier = Modifier.fillMaxSize().background(PageBg))
            return
        }
        false -> {
            WelcomeScreen(onFinish = { vm.completeOnboarding() })
            return
        }
        true -> Unit
    }

    // Bumped whenever permissions may have changed (resume from settings,
    // after a launcher result) so screens refresh their permission state.
    var permissionsVersion by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsVersion++
        // Re-prime location if it just became available.
        if (results.values.any { it }) {
            vm.onLocationPermissionPossiblyGranted()
        }
    }

    LifecycleResumeEffect(Unit) {
        permissionsVersion++
        onPauseOrDispose { }
    }

    val requestPermissions: () -> Unit = {
        permissionLauncher.launch(vm.requiredPermissions())
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // Headers start below the status bar (clock/battery) on every screen.
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val tab = when (currentBackStackEntry?.destination?.route) {
                "home" -> AppTab.HOME
                "devices" -> AppTab.DEVICES
                "map", "tracking" -> AppTab.MAP
                "device" -> AppTab.MAP
                "history" -> AppTab.HISTORY
                else -> AppTab.HOME
            }
            FindTrackBottomBar(
                selected = tab,
                onSelect = { selected ->
                    navController.navigate(selected.route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(AppTab.HOME.route) {
                HomeScreen(
                    vm = vm,
                    onOpenDevices = {
                        navController.navigate(AppTab.DEVICES.route) { launchSingleTop = true }
                    },
                    onOpenDevice = { address ->
                        vm.select(address)
                        navController.navigate("device/$address")
                    },
                    onOpenSettings = { navController.navigate("settings") },
                    onRequestPermissions = requestPermissions,
                    contentPadding = statusBarPadding
                )
            }
            composable(AppTab.DEVICES.route) {
                DevicesScreen(
                    vm = vm,
                    onOpenDevice = { address ->
                        vm.select(address)
                        navController.navigate("device/$address")
                    },
                    contentPadding = statusBarPadding
                )
            }
            composable(
                route = "device/{address}",
                arguments = listOf(navArgument("address") { type = NavType.StringType })
            ) { backStack ->
                val address = backStack.arguments?.getString("address") ?: return@composable
                DeviceDetailScreen(
                    vm = vm,
                    address = address,
                    onBack = { navController.popBackStack() },
                    onStartTracking = {
                        val entry = vm.devices.value.firstOrNull { it.address == address }
                        if (entry != null) {
                            vm.startTracking(context, entry)
                            navController.navigate("tracking") { launchSingleTop = true }
                        }
                    },
                    contentPadding = statusBarPadding
                )
            }
            composable("tracking") {
                TrackingScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate("settings") },
                    contentPadding = statusBarPadding
                )
            }
            composable(AppTab.MAP.route) {
                FinderMapScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpenDevice = { address ->
                        vm.select(address)
                        navController.navigate("device/$address")
                    },
                    onPickDevice = {
                        navController.navigate(AppTab.DEVICES.route) { launchSingleTop = true }
                    },
                    contentPadding = statusBarPadding
                )
            }
            composable(AppTab.HISTORY.route) {
                HistoryScreen(
                    vm = vm,
                    onOpenDevice = { address ->
                        vm.select(address)
                        navController.navigate("device/$address")
                    },
                    contentPadding = statusBarPadding
                )
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onRequestPermissions = requestPermissions,
                    contentPadding = statusBarPadding
                )
            }
        }
    }
}
