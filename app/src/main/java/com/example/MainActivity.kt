package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.NavyBackgroundTopGlow
import com.example.ui.theme.NavyBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.PirSecurityForegroundService
import com.example.ui.SecurityTab
import com.example.ui.SecurityViewModel
import com.example.ui.screens.BluetoothDevicePickerDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FullScreenAlarmOverlay
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityGreenContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockscreenFlags()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        PirSecurityForegroundService.createNotificationChannels(this)
        PirSecurityForegroundService.ensureServiceStarted(this)

        setContent {
            MyApplicationTheme {
                SmartPirSecurityApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun configureLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun SmartPirSecurityApp(
    securityViewModel: SecurityViewModel = viewModel()
) {
    val context = LocalContext.current
    val systemState by securityViewModel.systemState.collectAsStateWithLifecycle()
    val selectedTab by securityViewModel.selectedTab.collectAsStateWithLifecycle()
    val showDevicePicker by securityViewModel.showDevicePickerDialog.collectAsStateWithLifecycle()

    var hasRequestedInitialPermissions by rememberSaveable { mutableStateOf(false) }

    fun checkBluetoothConnectGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    var bluetoothPermissionGranted by remember {
        mutableStateOf(checkBluetoothConnectGranted())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothPermissionGranted = checkBluetoothConnectGranted()
        PirSecurityForegroundService.ensureServiceStarted(context)
        PirSecurityForegroundService.syncNotificationIfRunning(context)
        if (bluetoothPermissionGranted) {
            securityViewModel.onPermissionsOrLifecycleReady()
        }
    }

    fun requestRequiredPermissionsIfMissing() {
        val permsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        } else {
            bluetoothPermissionGranted = true
            PirSecurityForegroundService.ensureServiceStarted(context)
            securityViewModel.onPermissionsOrLifecycleReady()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bluetoothPermissionGranted = checkBluetoothConnectGranted()
                PirSecurityForegroundService.ensureServiceStarted(context)
                PirSecurityForegroundService.syncNotificationIfRunning(context)
                if (bluetoothPermissionGranted) {
                    securityViewModel.onPermissionsOrLifecycleReady()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedInitialPermissions) {
            hasRequestedInitialPermissions = true
            requestRequiredPermissionsIfMissing()
        } else if (checkBluetoothConnectGranted()) {
            securityViewModel.onPermissionsOrLifecycleReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NavyBackgroundTopGlow,
                        NavyBackground,
                        Color(0xFFF8FAFC)
                    )
                )
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 640.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!isExpandedScreen) {
                        SecurityBottomNavigationBar(
                            selectedTab = selectedTab,
                            isAlarmPlaying = systemState.isAlarmAudioLooping,
                            onSelectTab = { securityViewModel.selectTab(it) }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        SecuritySideNavigationRail(
                            selectedTab = selectedTab,
                            isAlarmPlaying = systemState.isAlarmAudioLooping,
                            onSelectTab = { securityViewModel.selectTab(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (selectedTab) {
                            SecurityTab.DASHBOARD -> {
                                DashboardScreen(
                                    state = systemState,
                                    onSelectDeviceClick = {
                                        bluetoothPermissionGranted = checkBluetoothConnectGranted()
                                        securityViewModel.openDevicePicker()
                                    },
                                    onArmClick = { securityViewModel.armSystem() },
                                    onDisarmClick = { securityViewModel.disarmSystem() }
                                )
                            }

                            SecurityTab.SETTINGS -> {
                                SettingsScreen(
                                    state = systemState,
                                    onSelectSound = { securityViewModel.setAlarmSound(it) },
                                    onVolumeChange = { securityViewModel.setAlarmVolume(it) },
                                    onVibrationChange = { securityViewModel.setVibrationEnabled(it) },
                                    onHighPriorityNotificationChange = {
                                        securityViewModel.setHighPriorityNotifications(it)
                                    },
                                    onNavigateBack = {
                                        securityViewModel.selectTab(SecurityTab.DASHBOARD)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Prominent Full-Screen Emergency Alarm Overlay when active
        if (systemState.isAlarmAudioLooping) {
            FullScreenAlarmOverlay(
                state = systemState,
                onStopAlarm = {
                    securityViewModel.stopAlarm("Full-Screen STOP ALARM")
                }
            )
        }

        // Paired HC-05 Bluetooth Device Selection Modal
        if (showDevicePicker) {
            BluetoothDevicePickerDialog(
                pairedDevices = systemState.pairedDevices,
                selectedAddress = systemState.selectedDeviceAddress,
                onSelectDevice = { device ->
                    securityViewModel.selectAndConnectDevice(device)
                },
                onRefreshDevices = {
                    bluetoothPermissionGranted = checkBluetoothConnectGranted()
                    securityViewModel.refreshPairedDevices()
                },
                onRequestPermissions = {
                    requestRequiredPermissionsIfMissing()
                },
                hasBluetoothPermission = bluetoothPermissionGranted,
                onDismiss = { securityViewModel.closeDevicePicker() }
            )
        }
    }
}

private data class NavTabMeta(
    val tab: SecurityTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_TAB_ITEMS = listOf(
    NavTabMeta(
        tab = SecurityTab.DASHBOARD,
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        testTag = "nav_tab_dashboard"
    ),
    NavTabMeta(
        tab = SecurityTab.SETTINGS,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
private fun SecurityBottomNavigationBar(
    selectedTab: SecurityTab,
    isAlarmPlaying: Boolean,
    onSelectTab: (SecurityTab) -> Unit
) {
    NavigationBar(
        containerColor = NavySurface,
        contentColor = TextPrimary,
        tonalElevation = 3.dp
    ) {
        NAV_TAB_ITEMS.forEach { item ->
            val isSelected = selectedTab == item.tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(item.tab) },
                modifier = Modifier.testTag(item.testTag),
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.tab.label
                    )
                },
                label = {
                    Text(text = item.tab.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (isAlarmPlaying) AlarmRed else SecurityGreen,
                    selectedTextColor = if (isAlarmPlaying) AlarmRed else SecurityGreen,
                    indicatorColor = SecurityGreenContainer,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}

@Composable
private fun SecuritySideNavigationRail(
    selectedTab: SecurityTab,
    isAlarmPlaying: Boolean,
    onSelectTab: (SecurityTab) -> Unit
) {
    NavigationRail(
        containerColor = NavySurface,
        contentColor = TextPrimary
    ) {
        NAV_TAB_ITEMS.forEach { item ->
            val isSelected = selectedTab == item.tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelectTab(item.tab) },
                modifier = Modifier.testTag(item.testTag),
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.tab.label
                    )
                },
                label = {
                    Text(text = item.tab.label)
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = if (isAlarmPlaying) AlarmRed else SecurityGreen,
                    selectedTextColor = if (isAlarmPlaying) AlarmRed else SecurityGreen,
                    indicatorColor = SecurityGreenContainer,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
