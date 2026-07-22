package com.example.ui.channelboard

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppThemeMode
import com.example.model.EqBand
import com.example.ui.components.DualStereoVuMeter
import com.example.ui.components.ReadoutPill
import com.example.ui.components.TactileBypassChip
import com.example.ui.components.TactileCard
import com.example.ui.components.VerticalIndentedFader
import kotlin.math.log10
import kotlin.math.pow

import androidx.compose.material.icons.filled.Settings

/**
 * Top Header Bar Toolbar with Title & Badge, Preset Selector Pill, Undo/Redo, Play/Stop audio demo, and Settings Gear.
 */
@Composable
fun HeaderToolbar(
    presetName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    isPlayingAudio: Boolean,
    selectedAudioTrack: String,
    isGraphicEqMode: Boolean,
    themeMode: AppThemeMode,
    shizukuConnected: Boolean = false,
    shizukuGranted: Boolean = false,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenPresetSelector: () -> Unit,
    onToggleAudioPlayback: () -> Unit,
    onToggleGraphicEqMode: () -> Unit,
    onResetAllBands: () -> Unit,
    onExportCsv: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestShizuku: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title & Active Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "EQUALIZER",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (shizukuGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onRequestShizuku()
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("shizuku_status_badge")
            ) {
                Text(
                    text = if (shizukuGranted) "SHIZUKU" else "SESSION 0",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        color = Color.White
                    )
                )
            }
        }

        // Preset Selector Pill (Centered)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenPresetSelector()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("preset_selector_pill"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Equalizer,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$presetName ▾",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Icons: Undo, Redo, Audio Play, Settings Gear
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onUndo()
                },
                enabled = canUndo,
                modifier = Modifier.size(36.dp).testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(18.dp),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onRedo()
                },
                enabled = canRedo,
                modifier = Modifier.size(36.dp).testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(18.dp),
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggleAudioPlayback()
                },
                modifier = Modifier.size(36.dp).testTag("audio_play_button")
            ) {
                Icon(
                    imageVector = if (isPlayingAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = "Toggle Audio Playback",
                    modifier = Modifier.size(20.dp),
                    tint = if (isPlayingAudio) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenSettings()
                },
                modifier = Modifier.size(36.dp).testTag("settings_gear_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Bottom Channel Board Dock (65% Viewport) containing:
 * - Pinned Master Preamp Card (Far left stationary)
 * - Horizontal scrolling row of 160dp Parametric Band Cards
 */
@Composable
fun ChannelBoardDock(
    bands: List<EqBand>,
    preampDb: Float,
    guardEnabled: Boolean,
    leftPeakVu: Float,
    rightPeakVu: Float,
    selectedBandId: Int?,
    isGraphicEqMode: Boolean,
    onPreampChange: (Float) -> Unit,
    onGuardToggle: (Boolean) -> Unit,
    onBandSelect: (Int) -> Unit,
    onBandBypassToggle: (Int, Boolean) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onBandFreqChange: (Int, Float) -> Unit,
    onBandQChange: (Int, Float) -> Unit,
    onExpandModal: (Int) -> Unit,
    onAddBand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()

    // Auto-scroll when a band is selected from the graph
    LaunchedEffect(selectedBandId) {
        if (selectedBandId != null && !isGraphicEqMode) {
            val index = bands.indexOfFirst { it.id == selectedBandId }
            if (index >= 0) {
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pinned Master Preamp Card (Far Left stationary card)
        PinnedPreampCard(
            preampDb = preampDb,
            guardEnabled = guardEnabled,
            leftPeakVu = leftPeakVu,
            rightPeakVu = rightPeakVu,
            onPreampChange = onPreampChange,
            onGuardToggle = onGuardToggle,
            modifier = Modifier
                .width(135.dp)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 8.dp)
        )

        // Channel Rail for Band Cards
        if (isGraphicEqMode) {
            // Graphic EQ Board (Fixed 10 Bands)
            GraphicEqBoard(
                bands = bands,
                onGainChange = onBandGainChange,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        } else {
            // Parametric Band Cards Horizontal Rail
            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(bands, key = { _, band -> band.id }) { _, band ->
                    ParametricBandCard(
                        band = band,
                        isSelected = band.id == selectedBandId,
                        onCardClick = {
                            onBandSelect(band.id)
                            onExpandModal(band.id)
                        },
                        onBypassToggle = { enabled -> onBandBypassToggle(band.id, enabled) },
                        onGainChange = { gain -> onBandGainChange(band.id, gain) },
                        onFreqChange = { freq -> onBandFreqChange(band.id, freq) },
                        onQChange = { q -> onBandQChange(band.id, q) },
                        modifier = Modifier
                            .width(160.dp)
                            .fillMaxHeight()
                    )
                }

                // Add Band Card Button
                item {
                    AddBandCard(
                        onAdd = onAddBand,
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Pinned Stationary Master Preamp Card.
 */
@Composable
fun PinnedPreampCard(
    preampDb: Float,
    guardEnabled: Boolean,
    leftPeakVu: Float,
    rightPeakVu: Float,
    onPreampChange: (Float) -> Unit,
    onGuardToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TactileCard(
        modifier = modifier.testTag("pinned_preamp_card"),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: "PREAMP"
            Text(
                text = "PREAMP",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            // Body: Preamp Fader Alone
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Preamp Fader (-12dB to +12dB)
                val normVal = ((preampDb + 12f) / 24f).coerceIn(0f, 1f)
                VerticalIndentedFader(
                    value = normVal,
                    onValueChange = { norm ->
                        val newDb = norm * 24f - 12f
                        onPreampChange(newDb)
                    },
                    trackWidth = 36.dp,
                    thumbHeight = 36.dp,
                    accentColor = MaterialTheme.colorScheme.primary,
                    zeroCrossingNormalized = 0.5f,
                    testTag = "preamp_fader"
                )
            }

            // Readout Pill
            ReadoutPill(
                text = "${if (preampDb > 0) "+" else ""}${String.format("%.1f", preampDb)} dB",
                active = true,
                accentColor = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // GUARD ON/OFF Toggle Chip
            TactileBypassChip(
                enabled = guardEnabled,
                onToggle = onGuardToggle,
                accentColor = Color(0xFF34C759)
            )
        }
    }
}

/**
 * Collapsed Parametric Band Card (160dp width).
 */
@Composable
fun ParametricBandCard(
    band: EqBand,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onBypassToggle: (Boolean) -> Unit,
    onGainChange: (Float) -> Unit,
    onFreqChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    TactileCard(
        modifier = modifier.testTag("band_card_${band.id}"),
        cornerRadius = 24.dp,
        borderColor = if (isSelected) band.displayColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        onClick = onCardClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Card Header: Band Badge & Bypass Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Band Pill Badge #1, #2, etc.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (band.enabled) band.displayColor else Color.Gray)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = band.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                // Tactile Bypass Chip
                TactileBypassChip(
                    enabled = band.enabled,
                    onToggle = onBypassToggle,
                    accentColor = band.displayColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Card Body: 3-Slider Vertical Hardware Layout
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Slim Vertical Freq Micro-Fader
                val logMin = log10(20f)
                val logMax = log10(20000f)
                val freqNorm = ((log10(band.frequencyHz.coerceIn(20f, 20000f)) - logMin) / (logMax - logMin)).coerceIn(0f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text("FREQ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                    VerticalIndentedFader(
                        value = freqNorm,
                        onValueChange = { norm ->
                            val freq = 10f.pow(logMin + norm * (logMax - logMin))
                            onFreqChange(freq)
                        },
                        trackWidth = 18.dp,
                        thumbHeight = 22.dp,
                        accentColor = band.displayColor,
                        zeroCrossingNormalized = null,
                        testTag = "freq_fader_${band.id}"
                    )
                }

                // Center: Wide Prominent Gain Fader
                val gainNorm = ((band.gainDb + 18f) / 36f).coerceIn(0f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text("GAIN", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = band.displayColor))
                    VerticalIndentedFader(
                        value = gainNorm,
                        onValueChange = { norm ->
                            val gain = norm * 36f - 18f
                            onGainChange(gain)
                        },
                        trackWidth = 26.dp,
                        thumbHeight = 28.dp,
                        accentColor = band.displayColor,
                        zeroCrossingNormalized = 0.5f,
                        testTag = "gain_fader_${band.id}"
                    )
                }

                // Right: Slim Vertical Q-Factor Micro-Fader
                val qNorm = ((band.qFactor - 0.1f) / 9.9f).coerceIn(0f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text("Q", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                    VerticalIndentedFader(
                        value = qNorm,
                        onValueChange = { norm ->
                            val q = norm * 9.9f + 0.1f
                            onQChange(q)
                        },
                        trackWidth = 18.dp,
                        thumbHeight = 22.dp,
                        accentColor = band.displayColor,
                        zeroCrossingNormalized = null,
                        testTag = "q_fader_${band.id}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Card Footer: Numeric Readout Pills
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ReadoutPill(
                    text = "${if (band.gainDb > 0) "+" else ""}${String.format("%.1f", band.gainDb)} dB",
                    active = band.enabled,
                    accentColor = band.displayColor
                )

                Text(
                    text = "${formatFreq(band.frequencyHz)} | Q ${String.format("%.2f", band.qFactor)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Add Band Card Button in horizontal rail.
 */
@Composable
fun AddBandCard(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    TactileCard(
        modifier = modifier.testTag("add_band_card"),
        cornerRadius = 24.dp,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onAdd()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Band",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add Band",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Graphic EQ Board (Multi-band GEQ mode).
 */
@Composable
fun GraphicEqBoard(
    bands: List<EqBand>,
    onGainChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(bands) { _, band ->
            TactileCard(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight(),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFreq(band.frequencyHz),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )

                    val gainNorm = ((band.gainDb + 18f) / 36f).coerceIn(0f, 1f)
                    VerticalIndentedFader(
                        value = gainNorm,
                        onValueChange = { norm ->
                            val gain = norm * 36f - 18f
                            onGainChange(band.id, gain)
                        },
                        trackWidth = 24.dp,
                        thumbHeight = 28.dp,
                        accentColor = band.displayColor,
                        zeroCrossingNormalized = 0.5f,
                        testTag = "geq_fader_${band.id}"
                    )

                    ReadoutPill(
                        text = "${if (band.gainDb > 0) "+" else ""}${String.format("%.1f", band.gainDb)}",
                        active = true,
                        accentColor = band.displayColor
                    )
                }
            }
        }
    }
}

private fun formatFreq(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        "${String.format("%.1f", freqHz / 1000f)}k"
    } else {
        "${freqHz.toInt()} Hz"
    }
}
