package com.example.model

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Filter Types supported by the Parametric Equalizer.
 */
enum class FilterType(
    val displayName: String,
    val shortName: String,
    val iconSymbol: String,
    val description: String
) {
    PEAKING("Peaking (Bell)", "PK", "∿", "Boosts or cuts a specific center frequency band."),
    LOW_SHELF("Low Shelf", "LS", "𝄀‾", "Adjusts all frequencies below the corner frequency."),
    HIGH_SHELF("High Shelf", "HS", "‾𝄀", "Adjusts all frequencies above the corner frequency."),
    LOW_PASS("Low Pass (Cut)", "LP", "╲", "Attenuates frequencies above the cutoff point."),
    HIGH_PASS("High Pass (Cut)", "HP", "╱", "Attenuates frequencies below the cutoff point."),
    NOTCH("Notch Filter", "NC", "V", "Completely cuts a very narrow frequency notch.")
}

/**
 * Individual Parametric EQ Band Configuration.
 */
data class EqBand(
    val id: Int,
    val label: String,
    val frequencyHz: Float, // 20Hz - 20000Hz
    val gainDb: Float,      // -18dB - +18dB
    val qFactor: Float,     // 0.1 - 10.0
    val filterType: FilterType = FilterType.PEAKING,
    val enabled: Boolean = true,
    val colorHex: String = "#E85A3C"
) {
    val displayColor: Color
        get() = try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            Color(0xFFE85A3C)
        }
}

/**
 * Target Reference Curves (AutoEQ / Squig.link).
 */
data class ReferenceTarget(
    val id: String,
    val name: String,
    val category: String, // e.g., "AutoEQ In-Ear", "Studio Reference", "Headphone Target"
    val points: List<Pair<Float, Float>> // Frequency Hz -> dB Delta
)

/**
 * Preset data structure for Room Database persistence.
 */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isUserCreated: Boolean = true,
    val isGraphicEq: Boolean = false,
    val preampDb: Float = 0.0f,
    val guardEnabled: Boolean = true,
    val bandDataJson: String,
    val referenceTargetId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * App Navigation Tabs for Bottom Bar.
 */
enum class AppTab(val title: String, val iconSymbol: String) {
    EQUALIZER("Equalizer", "🎛️"),
    TARGET_CURVES("Target Curves", "📊"),
    PRESETS("Presets", "📁"),
    SETTINGS("Settings", "⚙️")
}

/**
 * Theme Mode configuration.
 */
enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Accent Color Options for Hero Calibration.
 */
object HeroAccentColors {
    val DefaultOrange = "#FF5D00"
    val Cyan = "#00E5FF"
    val Pink = "#FF0055"
    val Green = "#00FF66"
    val Gold = "#FFB700"
    val Purple = "#9D00FF"

    val Options = listOf(
        DefaultOrange,
        Cyan,
        Pink,
        Green,
        Gold,
        Purple
    )
}

/**
 * Default multi-color band palette for tactile bands.
 */
object BandColors {
    val Palette = listOf(
        "#E85A3C", // Terracotta / Coral
        "#4A6FA5", // Slate Blue
        "#5B8C5A", // Sage Green
        "#D99B26", // Warm Ochre
        "#8B5CF6", // Soft Violet
        "#EC4899", // Soft Rose
        "#06B6D4", // Teal
        "#F97316"  // Warm Amber
    )

    fun getColorForIndex(index: Int): String {
        return Palette[index % Palette.size]
    }
}

/**
 * Expanded Color Palette for rich node & calibration options.
 */
object ExpandedColors {
    val Palette = listOf(
        "#FF3B30", "#EF4444", "#F87171", // Reds
        "#F97316", "#FB923C", "#FF9500", // Oranges
        "#FFCC00", "#FBBF24", "#F59E0B", // Yellows
        "#4CD964", "#10B981", "#34D399", // Greens
        "#06B6D4", "#22D3EE", "#5AC8FA", // Cyans
        "#007AFF", "#3B82F6", "#60A5FA", // Blues
        "#5856D6", "#6366F1", "#818CF8", // Indigo
        "#8B5CF6", "#A78BFA", "#9D00FF", // Purple
        "#FF2D55", "#EC4899", "#F472B6"  // Pink
    )
}

