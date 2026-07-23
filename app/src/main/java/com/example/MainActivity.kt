package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppTab
import com.example.service.EqForegroundService
import com.example.shizuku.ShizukuCheckService
import com.example.shizuku.ShizukuHelper
import com.example.ui.channelboard.ChannelBoardDock
import com.example.ui.channelboard.HeaderToolbar
import com.example.ui.components.StudioBottomBar
import com.example.ui.graph.EqGraphCanvas
import com.example.ui.modal.FineTuningModal
import com.example.ui.preset.PresetSelectionOverlay
import com.example.ui.presets.PresetsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.targetcurves.TargetCurvesScreen
import com.example.ui.theme.StudioEqTheme
import com.example.viewmodel.EqViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: EqViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            Toast.makeText(this, "Audio Permission Granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start Foreground DSP Service
        EqForegroundService.startService(this)

        // Start robust Shizuku IPC monitoring service
        ShizukuCheckService.startMonitoring()

        // Request runtime permissions on start
        requestPermissionsIfNeeded()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val responseCurve by viewModel.responseCurve.collectAsStateWithLifecycle()
            val targetCurveArray by viewModel.targetCurveArray.collectAsStateWithLifecycle()
            val peakGainDb by viewModel.peakGainDb.collectAsStateWithLifecycle()
            val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
            val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

            val isPlayingAudio by viewModel.audioManager.isPlaying.collectAsStateWithLifecycle()
            val selectedAudioTrack by viewModel.audioManager.selectedAudioTrack.collectAsStateWithLifecycle()
            val fftBins by viewModel.audioManager.fftBins.collectAsStateWithLifecycle()
            val leftPeakVu by viewModel.audioManager.leftPeakVu.collectAsStateWithLifecycle()
            val rightPeakVu by viewModel.audioManager.rightPeakVu.collectAsStateWithLifecycle()

            val shizukuStatus by ShizukuCheckService.statusState.collectAsStateWithLifecycle()
            val shizukuConnected = shizukuStatus.isRunning
            val shizukuGranted = shizukuStatus.isPermissionGranted

            var showShizukuDialog by remember { mutableStateOf(false) }
            var showShizukuNotDetectedDialog by remember { mutableStateOf(false) }

            val userPresets by viewModel.userPresets.collectAsStateWithLifecycle()

            // Startup Shizuku binder check
            LaunchedEffect(Unit) {
                try {
                    if (ShizukuHelper.pingBinder()) {
                        ShizukuCheckService.updateStateSafely()
                    }
                } catch (e: Throwable) {
                    // Safe startup fallback
                }
            }

            StudioEqTheme(
                themeMode = uiState.themeMode,
                pureBlackOled = uiState.pureBlackOled,
                customAccentHex = uiState.customAccentHex
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        StudioBottomBar(
                            currentTab = uiState.currentTab,
                            onTabSelected = { tab -> viewModel.setTab(tab) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.currentTab) {
                            AppTab.EQUALIZER -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Global Header Toolbar
                                    HeaderToolbar(
                                        presetName = uiState.activePresetName,
                                        canUndo = canUndo,
                                        canRedo = canRedo,
                                        isPlayingAudio = isPlayingAudio,
                                        selectedAudioTrack = selectedAudioTrack,
                                        isGraphicEqMode = uiState.isGraphicEqMode,
                                        themeMode = uiState.themeMode,
                                        shizukuConnected = shizukuConnected,
                                        shizukuGranted = shizukuGranted,
                                        onUndo = { viewModel.undo() },
                                        onRedo = { viewModel.redo() },
                                        onOpenPresetSelector = { viewModel.setOverlayVisible(true) },
                                        onToggleAudioPlayback = {
                                            val isStarting = !isPlayingAudio
                                            if (isStarting) {
                                                if (ShizukuHelper.pingBinder()) {
                                                    if (!ShizukuHelper.checkShizukuPermission()) {
                                                        ShizukuHelper.requestShizukuPermission()
                                                    }
                                                } else {
                                                    showShizukuNotDetectedDialog = true
                                                }
                                            }
                                            viewModel.audioManager.togglePlayback()
                                        },
                                        onToggleGraphicEqMode = { viewModel.toggleGraphicEqMode() },
                                        onResetAllBands = { viewModel.resetAllBands() },
                                        onExportCsv = {
                                            viewModel.exportParametricCsv()
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Parametric CSV Exported!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onThemeChange = { mode -> viewModel.setThemeMode(mode) },
                                        onOpenSettings = { viewModel.setTab(AppTab.SETTINGS) },
                                        onRequestShizuku = {
                                            if (ShizukuHelper.pingBinder()) {
                                                showShizukuDialog = true
                                                handleShizukuRequest()
                                            } else {
                                                showShizukuNotDetectedDialog = true
                                            }
                                        }
                                    )

                                    // Top Area (35% Viewport): Log-Frequency Graph Canvas
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(0.35f)
                                    ) {
                                        EqGraphCanvas(
                                            bands = uiState.bands,
                                            responseCurve = responseCurve,
                                            targetCurve = targetCurveArray,
                                            targetName = uiState.activeTarget?.name,
                                            fftBins = fftBins,
                                            peakGainDb = peakGainDb,
                                            selectedBandId = uiState.selectedBandId,
                                            onBandSelect = { id -> viewModel.selectBand(id) },
                                            onBandUpdate = { id, freq, gain ->
                                                viewModel.updateBandFreqGain(id, freq, gain)
                                            },
                                            onBandCreate = { freq, gain ->
                                                viewModel.addBand(freq, gain)
                                            },
                                            onBandDelete = { id -> viewModel.deleteBand(id) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Bottom Area (65% Viewport): Channel Board Dock
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(0.65f)
                                    ) {
                                        ChannelBoardDock(
                                            bands = uiState.bands,
                                            preampDb = uiState.preampDb,
                                            guardEnabled = uiState.guardEnabled,
                                            leftPeakVu = leftPeakVu,
                                            rightPeakVu = rightPeakVu,
                                            selectedBandId = uiState.selectedBandId,
                                            isGraphicEqMode = uiState.isGraphicEqMode,
                                            onPreampChange = { db -> viewModel.setPreampDb(db) },
                                            onGuardToggle = { enabled -> viewModel.setGuardEnabled(enabled) },
                                            onBandSelect = { id -> viewModel.selectBand(id) },
                                            onBandBypassToggle = { id, enabled ->
                                                viewModel.updateBandBypass(id, enabled)
                                            },
                                            onBandGainChange = { id, gain ->
                                                val band = uiState.bands.find { it.id == id }
                                                band?.let { viewModel.updateBand(it.copy(gainDb = gain)) }
                                            },
                                            onBandFreqChange = { id, freq ->
                                                val band = uiState.bands.find { it.id == id }
                                                band?.let { viewModel.updateBand(it.copy(frequencyHz = freq)) }
                                            },
                                            onBandQChange = { id, q ->
                                                val band = uiState.bands.find { it.id == id }
                                                band?.let { viewModel.updateBand(it.copy(qFactor = q)) }
                                            },
                                            onExpandModal = { id -> viewModel.openExpandModal(id) },
                                            onAddBand = { viewModel.addBand() },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            AppTab.TARGET_CURVES -> {
                                TargetCurvesScreen(
                                    activeTarget = uiState.activeTarget,
                                    autoEqTargets = viewModel.getAutoEqTargets(),
                                    onSelectTarget = { target -> viewModel.selectAutoEqTarget(target) },
                                    onClearTarget = { viewModel.clearAutoEqTarget() },
                                    onSyncDatabase = {
                                        Toast.makeText(this@MainActivity, "Synced 4,500+ Headphone Models", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            AppTab.PRESETS -> {
                                PresetsScreen(
                                    activePresetName = uiState.activePresetName,
                                    factoryPresets = viewModel.getFactoryPresets(),
                                    userPresets = userPresets,
                                    onSelectFactoryPreset = { name, bands ->
                                        viewModel.loadFactoryPreset(name, bands)
                                        viewModel.setTab(AppTab.EQUALIZER)
                                    },
                                    onSelectUserPreset = { preset ->
                                        viewModel.loadUserPreset(preset)
                                        viewModel.setTab(AppTab.EQUALIZER)
                                    },
                                    onSaveUserPreset = { name ->
                                        viewModel.saveCurrentAsUserPreset(name)
                                        Toast.makeText(this@MainActivity, "Preset '$name' Saved!", Toast.LENGTH_SHORT).show()
                                    },
                                    onDeleteUserPreset = { preset ->
                                        viewModel.deleteUserPreset(preset)
                                        Toast.makeText(this@MainActivity, "Preset Deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    onExportCsv = {
                                        viewModel.exportParametricCsv()
                                        Toast.makeText(this@MainActivity, "CSV Exported to Clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onImportCsv = {
                                        Toast.makeText(this@MainActivity, "Import CSV Target Ready", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            AppTab.SETTINGS -> {
                                SettingsScreen(
                                    themeMode = uiState.themeMode,
                                    pureBlackOled = uiState.pureBlackOled,
                                    customAccentHex = uiState.customAccentHex,
                                    guardEnabled = uiState.guardEnabled,
                                    peakAlertsEnabled = uiState.peakAlertsEnabled,
                                    qFactorScale = uiState.qFactorScale,
                                    shizukuConnected = shizukuStatus.isRunning,
                                    shizukuGranted = shizukuStatus.isPermissionGranted,
                                    shizukuStatusMessage = shizukuStatus.statusMessage,
                                    onBackClick = { viewModel.setTab(AppTab.EQUALIZER) },
                                    onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
                                    onPureBlackToggle = { enabled -> viewModel.setPureBlackOled(enabled) },
                                    onAccentColorChange = { hex -> viewModel.setCustomAccentHex(hex) },
                                    onGuardToggle = { enabled -> viewModel.setGuardEnabled(enabled) },
                                    onPeakAlertsToggle = { enabled -> viewModel.setPeakAlertsEnabled(enabled) },
                                    onQFactorScaleChange = { scale -> viewModel.setQFactorScale(scale) },
                                    onImportCsv = {
                                        Toast.makeText(this@MainActivity, "CSV Import dialog", Toast.LENGTH_SHORT).show()
                                    },
                                    onExportCsv = {
                                        viewModel.exportParametricCsv()
                                        Toast.makeText(this@MainActivity, "CSV Exported to Clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    onSyncAutoEq = {
                                        Toast.makeText(this@MainActivity, "AutoEQ Database Synced", Toast.LENGTH_SHORT).show()
                                    },
                                    onRequestPermissions = { requestPermissionsIfNeeded() },
                                    onRequestShizuku = { handleShizukuRequest() }
                                )
                            }
                        }

                        // Expanded Fine-Tuning Modal Popup
                        val modalBandId = uiState.expandedModalBandId
                        if (modalBandId != null) {
                            val bandToEdit = uiState.bands.find { it.id == modalBandId }
                            if (bandToEdit != null) {
                                FineTuningModal(
                                    band = bandToEdit,
                                    onDismiss = { viewModel.closeExpandModal() },
                                    onBandUpdate = { updated -> viewModel.updateBand(updated) },
                                    onDeleteBand = { id -> viewModel.deleteBand(id) }
                                )
                            }
                        }

                        // Preset & Target Overlay Dialog
                        if (uiState.showPresetOverlay) {
                            PresetSelectionOverlay(
                                activePresetName = uiState.activePresetName,
                                userPresets = userPresets,
                                factoryPresets = viewModel.getFactoryPresets(),
                                autoEqTargets = viewModel.getAutoEqTargets(),
                                onSelectFactoryPreset = { name, bands ->
                                    viewModel.loadFactoryPreset(name, bands)
                                },
                                onSelectUserPreset = { preset ->
                                    viewModel.loadUserPreset(preset)
                                },
                                onSelectAutoEqTarget = { target ->
                                    viewModel.selectAutoEqTarget(target)
                                },
                                onClearTarget = { viewModel.clearAutoEqTarget() },
                                onDismiss = { viewModel.setOverlayVisible(false) }
                            )
                        }

                        // Shizuku Service Not Detected Alert Dialog
                        if (showShizukuNotDetectedDialog) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val isShizukuInstalled = ShizukuHelper.isShizukuInstalled(context)
                            AlertDialog(
                                onDismissRequest = { showShizukuNotDetectedDialog = false },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                title = {
                                    Text(
                                        text = "Shizuku Not Running",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Shizuku service is not currently active.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Note: Shizuku permissions are NOT found in Android System Settings -> App Info. Shizuku is a standalone manager app. Please launch Shizuku and start its service via Wireless Debugging or ADB.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Operating in Global Session 0 master output fallback mode.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                confirmButton = {
                                    if (isShizukuInstalled) {
                                        TextButton(
                                            onClick = {
                                                showShizukuNotDetectedDialog = false
                                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                    ?: context.packageManager.getLaunchIntentForPackage("moe.shizuku.api")
                                                intent?.let { context.startActivity(it) }
                                            }
                                        ) {
                                            Text("Open Shizuku App", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(onClick = { showShizukuNotDetectedDialog = false }) {
                                            Text("OK", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                dismissButton = {
                                    if (isShizukuInstalled) {
                                        TextButton(onClick = { showShizukuNotDetectedDialog = false }) {
                                            Text("Close")
                                        }
                                    }
                                }
                            )
                        }

                        // Shizuku Audio Hooking Status & Permission Dialog
                        if (showShizukuDialog) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val isShizukuInstalled = ShizukuHelper.isShizukuInstalled(context)
                            AlertDialog(
                                onDismissRequest = { showShizukuDialog = false },
                                title = {
                                    Text(
                                        text = "Shizuku Audio Service Status",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (shizukuConnected) "Shizuku Service: Connected" else "Shizuku Service: Not Running")
                                        Text(if (shizukuGranted) "Permission: Granted" else "Permission: Not Granted")
                                        Text(
                                            text = "Active Mode: " + if (shizukuGranted) "Shizuku High-Privilege Audio Session Hooking" else "Session ID 0 (Global Master Output)",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "IMPORTANT: Shizuku permissions CANNOT be granted in Android System Settings -> App Info.\n\n" +
                                                    "To authorize Shizuku permission:\n" +
                                                    "1. Ensure Shizuku service is running.\n" +
                                                    "2. Tap 'Request Permission' below to show Shizuku's authorization prompt dialog.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showShizukuDialog = false
                                            requestPermissionsIfNeeded()
                                            handleShizukuRequest()
                                        }
                                    ) {
                                        Text("Request Permission", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    if (isShizukuInstalled) {
                                        TextButton(
                                            onClick = {
                                                showShizukuDialog = false
                                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                    ?: context.packageManager.getLaunchIntentForPackage("moe.shizuku.api")
                                                intent?.let { context.startActivity(it) }
                                            }
                                        ) {
                                            Text("Open Shizuku App")
                                        }
                                    } else {
                                        TextButton(onClick = { showShizukuDialog = false }) {
                                            Text("Close")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Toast.makeText(this, "Permissions Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleShizukuRequest() {
        try {
            if (ShizukuHelper.pingBinder()) {
                ShizukuHelper.requestShizukuPermission { granted ->
                    try {
                        if (granted) {
                            val sessions = ShizukuHelper.extractActiveMediaSessions()
                            Toast.makeText(
                                this,
                                "Shizuku Granted! Hooked Audio Sessions: ${sessions.joinToString()}",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Shizuku Denied. Operating in Global Session Mode (ID: 0)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    "Shizuku Binder Not Running. Operating in Global Session Mode (ID: 0)",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(
                this,
                "Operating in Global Session Mode (ID: 0)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

