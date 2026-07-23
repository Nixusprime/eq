package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalTactileColors
import kotlin.math.abs

/**
 * Raised Tactile Clay Container Card with soft diffuse shadow and 24dp-32dp radius.
 */
@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    val tactileColors = LocalTactileColors.current
    val shape = RoundedCornerShape(cornerRadius)

    val cardModifier = modifier
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .shadow(
            elevation = 6.dp,
            shape = shape,
            ambientColor = tactileColors.shadowColor,
            spotColor = tactileColors.shadowColor
        )
        .clip(shape)
        .background(tactileColors.cardBackground)
        .border(1.dp, borderColor, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onClick
                )
            } else Modifier
        )

    Box(modifier = cardModifier) {
        content()
    }
}

/**
 * Vertical Fader in Indented Groove Track with physical 3D thumb handle.
 */
@Composable
fun VerticalIndentedFader(
    value: Float, // Normalized 0.0f .. 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 24.dp,
    thumbHeight: Dp = 28.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    zeroCrossingNormalized: Float? = 0.5f, // Optional 0dB snap point
    testTag: String = "vertical_fader"
) {
    val tactileColors = LocalTactileColors.current
    val view = LocalView.current
    var isDragging by remember { androidx.compose.runtime.mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(testTag)
            .width(trackWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        // Indented Track Groove
        Box(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(tactileColors.indentedTrack)
                .border(
                    1.dp,
                    Color.Black.copy(alpha = 0.12f),
                    RoundedCornerShape(5.dp)
                )
        ) {
            // Fill level up to thumb position
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0.01f, 1.0f))
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Baseline indicator tick if zeroCrossing is set
            if (zeroCrossingNormalized != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset(y = (-((1.0f - zeroCrossingNormalized) * 100)).dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                )
            }
        }

        // Draggable Thumb handle
        BoxWithConstraintsLayout(
            modifier = Modifier.fillMaxSize(),
            value = value,
            thumbHeight = thumbHeight,
            onDragStateChange = { isDragging = it },
            onValueChange = { newValue ->
                val prevVal = value
                val clamped = newValue.coerceIn(0.0f, 1.0f)
                // Contextual haptic feedback on zero baseline crossing
                if (zeroCrossingNormalized != null) {
                    if ((prevVal < zeroCrossingNormalized && clamped >= zeroCrossingNormalized) ||
                        (prevVal > zeroCrossingNormalized && clamped <= zeroCrossingNormalized)
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                }
                // Add incremental tick haptics
                val prevTick = (prevVal * 20f).toInt()
                val newTick = (clamped * 20f).toInt()
                if (prevTick != newTick) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onValueChange(clamped)
            }
        ) {
            // Physical Thumb Handle
            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .height(thumbHeight)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                tactileColors.cardBackground,
                                tactileColors.indentedTrack
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        if (isDragging) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Grip ridges
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.width(12.dp).height(2.dp).background(accentColor))
                    Box(modifier = Modifier.width(12.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                    Box(modifier = Modifier.width(12.dp).height(2.dp).background(accentColor))
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsLayout(
    modifier: Modifier,
    value: Float,
    thumbHeight: Dp,
    onValueChange: (Float) -> Unit,
    onDragStateChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val usableRangePx = (totalHeightPx - thumbHeightPx).coerceAtLeast(1.0f)

        // Calculate Y offset from top (0 = top, 1 = bottom)
        val thumbOffsetPx = (1.0f - value) * usableRangePx

        val draggableState = rememberDraggableState { deltaPx ->
            // In Compose vertical drag: UP is negative deltaPx, DOWN is positive deltaPx
            // Adding deltaPx to thumbOffsetPx moves offset up when dragging up (-delta) and down when dragging down (+delta)
            val newOffsetPx = (thumbOffsetPx + deltaPx).coerceIn(0.0f, usableRangePx)
            val newValue = 1.0f - (newOffsetPx / usableRangePx)
            onValueChange(newValue)
        }

        Box(
            modifier = Modifier
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { onDragStateChange?.invoke(true) },
                    onDragStopped = { onDragStateChange?.invoke(false) }
                )
        ) {
            content()
        }
    }
}

/**
 * Padded Numeric Readout Pill.
 */
@Composable
fun ReadoutPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    val tactileColors = LocalTactileColors.current
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (active) accentColor.copy(alpha = 0.12f) else tactileColors.indentedTrack)
            .border(
                1.dp,
                if (active) accentColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        )
    }
}

/**
 * Tactile Toggle Chip (ON / OFF).
 */
@Composable
fun TactileBypassChip(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val tactileColors = LocalTactileColors.current
    val view = LocalView.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) accentColor else tactileColors.indentedTrack)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle(!enabled)
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (enabled) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Stereo Peak VU Meter (L & R vertical LED bars).
 */
@Composable
fun DualStereoVuMeter(
    leftPeak: Float, // 0.0f .. 1.0f
    rightPeak: Float,
    modifier: Modifier = Modifier
) {
    val tactileColors = LocalTactileColors.current

    val animL by animateFloatAsState(
        targetValue = leftPeak.coerceIn(0.0f, 1.0f),
        animationSpec = tween(durationMillis = 80), label = "vuL"
    )
    val animR by animateFloatAsState(
        targetValue = rightPeak.coerceIn(0.0f, 1.0f),
        animationSpec = tween(durationMillis = 80), label = "vuR"
    )

    Row(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(tactileColors.indentedTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // Left Channel Bar
        SingleVuChannelBar(level = animL, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(3.dp))
        // Right Channel Bar
        SingleVuChannelBar(level = animR, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SingleVuChannelBar(
    level: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.15f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(level.coerceIn(0.02f, 1.0f))
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF3B30), // Red Peak
                            Color(0xFFFFCC00), // Yellow Warning
                            Color(0xFF34C759)  // Green Normal
                        )
                    )
                )
        )
    }
}
