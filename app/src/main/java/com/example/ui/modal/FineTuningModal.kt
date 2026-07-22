package com.example.ui.modal

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.BandColors
import com.example.model.EqBand
import com.example.model.FilterType
import com.example.ui.components.ReadoutPill
import kotlin.math.log10
import kotlin.math.pow

/**
 * Expanded Fine-Tuning Centered Modal Popup.
 * Provides high-precision horizontal sliders, filter type dropdown, decimal text keyboard input,
 * color picker row, reset gain, delete, and done actions.
 */
@Composable
fun FineTuningModal(
    band: EqBand,
    onDismiss: () -> Unit,
    onBandUpdate: (EqBand) -> Unit,
    onDeleteBand: (Int) -> Unit
) {
    val view = LocalView.current
    var currentBand by remember(band) { mutableStateOf(band) }
    var showFilterDropdown by remember { mutableStateOf(false) }

    // Text editing dialog state for direct numeric entry
    var editingField by remember { mutableStateOf<String?>(null) } // "FREQ", "GAIN", "Q"
    var editTextValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("fine_tuning_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Band Badge & Label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentBand.displayColor)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentBand.label,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }

                        Text(
                            text = "Fine Tuning",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // Filter Type Selector Pill Button
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(currentBand.displayColor.copy(alpha = 0.15f))
                                .border(1.dp, currentBand.displayColor, RoundedCornerShape(16.dp))
                                .clickable { showFilterDropdown = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("filter_type_pill")
                        ) {
                            Text(
                                text = "${currentBand.filterType.iconSymbol} ${currentBand.filterType.displayName} ▾",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = currentBand.displayColor
                                )
                            )
                        }

                        DropdownMenu(
                            expanded = showFilterDropdown,
                            onDismissRequest = { showFilterDropdown = false }
                        ) {
                            FilterType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${type.iconSymbol}  ${type.displayName}")
                                    },
                                    onClick = {
                                        showFilterDropdown = false
                                        val updated = currentBand.copy(filterType = type)
                                        currentBand = updated
                                        onBandUpdate(updated)
                                    }
                                )
                            }
                        }
                    }
                }

                // Precision Horizontal Sliders Stack

                // 1. Frequency Slider (20 Hz - 20000 Hz)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Frequency", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        ReadoutPill(
                            text = "${formatFreq(currentBand.frequencyHz)} ✎",
                            active = true,
                            accentColor = currentBand.displayColor,
                            onClick = {
                                editingField = "FREQ"
                                editTextValue = currentBand.frequencyHz.toInt().toString()
                            }
                        )
                    }

                    val logMin = log10(20f)
                    val logMax = log10(20000f)
                    val freqNorm = ((log10(currentBand.frequencyHz.coerceIn(20f, 20000f)) - logMin) / (logMax - logMin)).coerceIn(0f, 1f)

                    Slider(
                        value = freqNorm,
                        onValueChange = { norm ->
                            val freq = 10f.pow(logMin + norm * (logMax - logMin))
                            val updated = currentBand.copy(frequencyHz = freq.coerceIn(20f, 20000f))
                            currentBand = updated
                            onBandUpdate(updated)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = currentBand.displayColor,
                            activeTrackColor = currentBand.displayColor
                        ),
                        modifier = Modifier.testTag("modal_freq_slider")
                    )
                }

                // 2. Gain Slider (-18 dB to +18 dB)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gain (dB)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        ReadoutPill(
                            text = "${if (currentBand.gainDb > 0) "+" else ""}${String.format("%.1f", currentBand.gainDb)} dB ✎",
                            active = true,
                            accentColor = currentBand.displayColor,
                            onClick = {
                                editingField = "GAIN"
                                editTextValue = String.format("%.1f", currentBand.gainDb)
                            }
                        )
                    }

                    Slider(
                        value = currentBand.gainDb,
                        onValueChange = { gain ->
                            val updated = currentBand.copy(gainDb = gain)
                            currentBand = updated
                            onBandUpdate(updated)
                        },
                        valueRange = -18f..18f,
                        colors = SliderDefaults.colors(
                            thumbColor = currentBand.displayColor,
                            activeTrackColor = currentBand.displayColor
                        ),
                        modifier = Modifier.testTag("modal_gain_slider")
                    )
                }

                // 3. Q-Factor Slider (0.1 to 10.0)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Q-Factor (Bandwidth)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        ReadoutPill(
                            text = "Q ${String.format("%.2f", currentBand.qFactor)} ✎",
                            active = true,
                            accentColor = currentBand.displayColor,
                            onClick = {
                                editingField = "Q"
                                editTextValue = String.format("%.2f", currentBand.qFactor)
                            }
                        )
                    }

                    Slider(
                        value = currentBand.qFactor,
                        onValueChange = { q ->
                            val updated = currentBand.copy(qFactor = q)
                            currentBand = updated
                            onBandUpdate(updated)
                        },
                        valueRange = 0.1f..10.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = currentBand.displayColor,
                            activeTrackColor = currentBand.displayColor
                        ),
                        modifier = Modifier.testTag("modal_q_slider")
                    )
                }

                // Band Color Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Band Color", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BandColors.Palette.forEach { hex ->
                            val isColorSelected = currentBand.colorHex.equals(hex, ignoreCase = true)
                            val dotColor = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                Color(0xFFE85A3C)
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(
                                        width = if (isColorSelected) 3.dp else 0.dp,
                                        color = if (isColorSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val updated = currentBand.copy(colorHex = hex)
                                        currentBand = updated
                                        onBandUpdate(updated)
                                    }
                            )
                        }
                    }
                }

                // Footer Action Pills: Reset Gain, Delete, Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val updated = currentBand.copy(gainDb = 0f)
                            currentBand = updated
                            onBandUpdate(updated)
                        },
                        modifier = Modifier.testTag("reset_gain_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Gain")
                    }

                    TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onDeleteBand(currentBand.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF3B30)),
                        modifier = Modifier.testTag("delete_band_button")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = currentBand.displayColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("done_band_button")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done")
                    }
                }
            }
        }
    }

    // Direct Numeric Soft Decimal Keyboard Editor Dialog
    if (editingField != null) {
        Dialog(onDismissRequest = { editingField = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Enter Exact $editingField Value",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = editTextValue,
                        onValueChange = { editTextValue = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                applyExactTextValue(
                                    editingField = editingField!!,
                                    textValue = editTextValue,
                                    currentBand = currentBand,
                                    onUpdate = { updated ->
                                        currentBand = updated
                                        onBandUpdate(updated)
                                    }
                                )
                                editingField = null
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("exact_numeric_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingField = null }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                applyExactTextValue(
                                    editingField = editingField!!,
                                    textValue = editTextValue,
                                    currentBand = currentBand,
                                    onUpdate = { updated ->
                                        currentBand = updated
                                        onBandUpdate(updated)
                                    }
                                )
                                editingField = null
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

private fun applyExactTextValue(
    editingField: String,
    textValue: String,
    currentBand: EqBand,
    onUpdate: (EqBand) -> Unit
) {
    val number = textValue.toFloatOrNull() ?: return
    val updated = when (editingField) {
        "FREQ" -> currentBand.copy(frequencyHz = number.coerceIn(20f, 20000f))
        "GAIN" -> currentBand.copy(gainDb = number.coerceIn(-18f, 18f))
        "Q" -> currentBand.copy(qFactor = number.coerceIn(0.1f, 10f))
        else -> currentBand
    }
    onUpdate(updated)
}

private fun formatFreq(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        "${String.format("%.1f", freqHz / 1000f)} kHz"
    } else {
        "${freqHz.toInt()} Hz"
    }
}
