package com.example.ui.graph

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dsp.DspEngine
import com.example.model.EqBand
import com.example.ui.components.TactileCard
import com.example.ui.theme.LocalTactileColors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Top Area Graph Canvas System (35% Viewport).
 * Gesture-first, zero-button canvas with logarithmic grid, FFT visualizer,
 * target curve delta fill, interactive EQ nodes, and integrated clipping guard.
 */
@Composable
fun EqGraphCanvas(
    bands: List<EqBand>,
    responseCurve: FloatArray,
    targetCurve: FloatArray?,
    targetName: String?,
    fftBins: FloatArray,
    peakGainDb: Float,
    selectedBandId: Int?,
    onBandSelect: (Int) -> Unit,
    onBandUpdate: (Int, Float, Float) -> Unit, // id, freq, gain
    onBandCreate: (Float, Float) -> Unit,     // freq, gain
    onBandDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tactileColors = LocalTactileColors.current
    val view = LocalView.current

    val minFreqLog = remember { log10(20.0f) }
    val maxFreqLog = remember { log10(20000.0f) }
    val minDb = -18.0f
    val maxDb = 18.0f

    var activeDragBandId by remember { mutableStateOf<Int?>(null) }
    var lastHapticGain by remember { mutableStateOf(0f) }

    val isClipping = peakGainDb > 0.1f

    TactileCard(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("top_graph_canvas"),
        cornerRadius = 20.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Ambient Red Glow Along Top Edge when Clipping occurs
            if (isClipping) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF3B30).copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Main Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bands) {
                        detectTapGestures(
                            onTap = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    val tappedBand = findNearestBand(
                                        offset = offset,
                                        width = w,
                                        height = h,
                                        bands = bands,
                                        minFreqLog = minFreqLog,
                                        maxFreqLog = maxFreqLog,
                                        minDb = minDb,
                                        maxDb = maxDb
                                    )

                                    if (tappedBand != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onBandSelect(tappedBand.id)
                                    }
                                }
                            },
                            onDoubleTap = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    val normX = (offset.x / w).coerceIn(0.0f, 1.0f)
                                    val normY = (offset.y / h).coerceIn(0.0f, 1.0f)

                                    val freq = 10.0f.pow(minFreqLog + normX * (maxFreqLog - minFreqLog))
                                    val gain = maxDb - normY * (maxDb - minDb)

                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onBandCreate(freq.coerceIn(20f, 20000f), gain.coerceIn(-18f, 18f))
                                }
                            },
                            onLongPress = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    val targetedBand = findNearestBand(
                                        offset = offset,
                                        width = w,
                                        height = h,
                                        bands = bands,
                                        minFreqLog = minFreqLog,
                                        maxFreqLog = maxFreqLog,
                                        minDb = minDb,
                                        maxDb = maxDb
                                    )

                                    if (targetedBand != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onBandDelete(targetedBand.id)
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(bands) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                activeDragBandId = findNearestBand(
                                    offset = offset,
                                    width = w,
                                    height = h,
                                    bands = bands,
                                    minFreqLog = minFreqLog,
                                    maxFreqLog = maxFreqLog,
                                    minDb = minDb,
                                    maxDb = maxDb
                                )?.id
                                activeDragBandId?.let { onBandSelect(it) }
                            },
                            onDragEnd = { activeDragBandId = null },
                            onDragCancel = { activeDragBandId = null },
                            onDrag = { change, _ ->
                                change.consume()
                                val bandId = activeDragBandId ?: return@detectDragGestures
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    val pos = change.position
                                    val normX = (pos.x / w).coerceIn(0.0f, 1.0f)
                                    val normY = (pos.y / h).coerceIn(0.0f, 1.0f)

                                    val freq = 10.0f.pow(minFreqLog + normX * (maxFreqLog - minFreqLog))
                                    val gain = maxDb - normY * (maxDb - minDb)

                                    val roundedGain = kotlin.math.round(gain)
                                    if (roundedGain != lastHapticGain) {
                                        lastHapticGain = roundedGain
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }

                                    onBandUpdate(bandId, freq.coerceIn(20f, 20000f), gain.coerceIn(-18f, 18f))
                                }
                            }
                        )
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height

                // 1. Draw Hairline Logarithmic Grid
                val gridColor = tactileColors.gridHairline

                val freqLinesHz = listOf(20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f)
                for (f in freqLinesHz) {
                    val logF = log10(f)
                    val normX = (logF - minFreqLog) / (maxFreqLog - minFreqLog)
                    val x = normX * canvasW
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, canvasH),
                        strokeWidth = 1f
                    )
                }

                val dbLines = listOf(12f, 6f, 0f, -6f, -12f)
                for (db in dbLines) {
                    val normY = (maxDb - db) / (maxDb - minDb)
                    val y = normY * canvasH
                    val isBaseline = abs(db) < 0.1f
                    drawLine(
                        color = if (isBaseline) gridColor.copy(alpha = 0.35f) else gridColor,
                        start = Offset(0f, y),
                        end = Offset(canvasW, y),
                        strokeWidth = if (isBaseline) 2f else 1f,
                        pathEffect = if (!isBaseline) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                    )
                }

                // 2. Draw Target Curve & Delta Shaded Highlight Fill
                val zeroDbY = (maxDb - 0f) / (maxDb - minDb) * canvasH
                for (band in bands) {
                    if (!band.enabled) continue
                    val bandPath = Path()
                    val bandFillPath = Path()

                    val firstBandDb = DspEngine.computeBandGainDb(band, DspEngine.frequenciesHz[0])
                    val firstY = ((maxDb - firstBandDb) / (maxDb - minDb) * canvasH).coerceIn(0f, canvasH)

                    bandPath.moveTo(0f, firstY)
                    bandFillPath.moveTo(0f, zeroDbY)
                    bandFillPath.lineTo(0f, firstY)

                    for (i in 0 until DspEngine.NUM_FREQ_POINTS) {
                        val f = DspEngine.frequenciesHz[i]
                        val normX = (log10(f) - minFreqLog) / (maxFreqLog - minFreqLog)
                        val x = normX * canvasW
                        val db = DspEngine.computeBandGainDb(band, f)
                        val y = ((maxDb - db) / (maxDb - minDb) * canvasH).coerceIn(0f, canvasH)

                        bandPath.lineTo(x, y)
                        bandFillPath.lineTo(x, y)
                    }

                    bandFillPath.lineTo(canvasW, zeroDbY)
                    bandFillPath.close()

                    // Semi-transparent discrete envelope tint (15% - 20% opacity)
                    drawPath(
                        path = bandFillPath,
                        color = band.displayColor.copy(alpha = 0.16f)
                    )

                    // Individual filter outline
                    drawPath(
                        path = bandPath,
                        color = band.displayColor.copy(alpha = 0.55f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }

                // 3. Draw Target Curve & Delta Shaded Highlight Fill
                if (targetCurve != null && targetCurve.size == DspEngine.NUM_FREQ_POINTS) {
                    val targetPath = Path()
                    val deltaFillPath = Path()

                    val firstTargetY = (maxDb - targetCurve[0]) / (maxDb - minDb) * canvasH
                    targetPath.moveTo(0f, firstTargetY)
                    deltaFillPath.moveTo(0f, firstTargetY)

                    for (i in 0 until DspEngine.NUM_FREQ_POINTS) {
                        val f = DspEngine.frequenciesHz[i]
                        val normX = (log10(f) - minFreqLog) / (maxFreqLog - minFreqLog)
                        val x = normX * canvasW

                        val targetY = ((maxDb - targetCurve[i]) / (maxDb - minDb) * canvasH).coerceIn(0f, canvasH)
                        targetPath.lineTo(x, targetY)
                        deltaFillPath.lineTo(x, targetY)
                    }

                    // Loop back along active EQ curve for delta shaded fill
                    for (i in DspEngine.NUM_FREQ_POINTS - 1 downTo 0) {
                        val f = DspEngine.frequenciesHz[i]
                        val normX = (log10(f) - minFreqLog) / (maxFreqLog - minFreqLog)
                        val x = normX * canvasW

                        val eqY = ((maxDb - responseCurve[i]) / (maxDb - minDb) * canvasH).coerceIn(0f, canvasH)
                        deltaFillPath.lineTo(x, eqY)
                    }
                    deltaFillPath.close()

                    // Draw Delta Fill Shading
                    drawPath(
                        path = deltaFillPath,
                        color = Color(0xFF8B5CF6).copy(alpha = 0.12f)
                    )

                    // Draw Dashed Target Line
                    drawPath(
                        path = targetPath,
                        color = Color(0xFF8B5CF6).copy(alpha = 0.7f),
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                }

                // 4. Layer 3: Master Composite Response Curve
                val eqPath = Path()
                val eqFillPath = Path()

                val startY = ((maxDb - responseCurve[0]) / (maxDb - minDb) * canvasH).coerceIn(0f, canvasH)
                eqPath.moveTo(0f, startY)
                eqFillPath.moveTo(0f, canvasH)
                eqFillPath.lineTo(0f, startY)

                for (i in 0 until DspEngine.NUM_FREQ_POINTS) {
                    val f = DspEngine.frequenciesHz[i]
                    val normX = (log10(f) - minFreqLog) / (maxFreqLog - minFreqLog)
                    val x = normX * canvasW
                    val y = ((maxDb - responseCurve[i]) / (maxDb - minDb) * canvasH).coerceIn(0f, canvasH)

                    eqPath.lineTo(x, y)
                    eqFillPath.lineTo(x, y)
                }

                val lastX = canvasW
                eqFillPath.lineTo(lastX, canvasH)
                eqFillPath.close()

                // Gradient area under composite curve
                drawPath(
                    path = eqFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF5D00).copy(alpha = 0.22f),
                            Color(0xFFFF5D00).copy(alpha = 0.0f)
                        )
                    )
                )

                // Thick master composite response line
                drawPath(
                    path = eqPath,
                    color = Color(0xFFFF5D00),
                    style = Stroke(
                        width = 5.5f,
                        cap = StrokeCap.Round
                    )
                )

                // 5. Layer 4: Discrete Interactive Node Badges (Smart Z-Index order)
                val sortedBands = bands.sortedBy { it.id == selectedBandId }
                for (band in sortedBands) {
                    val normX = (log10(band.frequencyHz) - minFreqLog) / (maxFreqLog - minFreqLog)
                    val normY = (maxDb - band.gainDb) / (maxDb - minDb)

                    val nx = normX * canvasW
                    val ny = normY * canvasH

                    val isSelected = band.id == selectedBandId
                    val nodeRadius = if (isSelected) 18f else 14f

                    // Outer halo for selected node
                    if (isSelected) {
                        drawCircle(
                            color = band.displayColor.copy(alpha = 0.35f),
                            radius = nodeRadius + 12f,
                            center = Offset(nx, ny)
                        )
                    }

                    // Main Node Circle
                    drawCircle(
                        color = if (band.enabled) band.displayColor else Color.Gray,
                        radius = nodeRadius,
                        center = Offset(nx, ny)
                    )

                    // Node White Border Ring
                    drawCircle(
                        color = Color.White,
                        radius = nodeRadius,
                        center = Offset(nx, ny),
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Target Name Badge (Top Left overlay)
            if (targetName != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Target: $targetName",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFD8B4FE),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Digital Clipping Guard Readout (Top Right overlay)
            AnimatedVisibility(
                visible = isClipping,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF3B30))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Peak: +${String.format("%.1f", peakGainDb)} dB",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

private fun findNearestBand(
    offset: Offset,
    width: Float,
    height: Float,
    bands: List<EqBand>,
    minFreqLog: Float,
    maxFreqLog: Float,
    minDb: Float,
    maxDb: Float
): EqBand? {
    var nearest: EqBand? = null
    var minDistance = 80.0f

    for (band in bands) {
        val normX = (log10(band.frequencyHz) - minFreqLog) / (maxFreqLog - minFreqLog)
        val normY = (maxDb - band.gainDb) / (maxDb - minDb)

        val bx = normX * width
        val by = normY * height

        val dx = offset.x - bx
        val dy = offset.y - by
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

        if (dist < minDistance) {
            minDistance = dist
            nearest = band
        }
    }
    return nearest
}
