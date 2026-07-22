package com.example.data

import com.example.model.EqBand
import com.example.model.FilterType
import com.example.model.PresetEntity
import com.example.model.ReferenceTarget
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class EqRepository(private val presetDao: PresetDao) {

    val allPresets: Flow<List<PresetEntity>> = presetDao.getAllPresets()

    suspend fun savePreset(preset: PresetEntity): Long {
        return presetDao.insertPreset(preset)
    }

    suspend fun deletePreset(id: Long) {
        presetDao.deletePresetById(id)
    }

    /**
     * Pre-populated AutoEQ and Reference Target Database.
     */
    val builtInReferenceTargets: List<ReferenceTarget> = listOf(
        ReferenceTarget(
            id = "harman_in_ear_2019",
            name = "Harman In-Ear Target 2019",
            category = "AutoEQ Standard",
            points = listOf(
                20f to 6.5f, 50f to 5.8f, 100f to 4.2f, 200f to 1.8f,
                500f to 0.0f, 1000f to 0.0f, 2500f to 7.5f, 5000f to 4.0f,
                8000f to -2.0f, 10000f to 1.5f, 16000f to -3.0f
            )
        ),
        ReferenceTarget(
            id = "harman_over_ear_2018",
            name = "Harman Over-Ear Target 2018",
            category = "AutoEQ Standard",
            points = listOf(
                20f to 5.0f, 50f to 4.5f, 100f to 3.2f, 200f to 1.2f,
                500f to 0.0f, 1000f to 0.0f, 3000f to 8.2f, 6000f to 2.0f,
                9000f to 1.0f, 12000f to -2.5f, 20000f to -5.0f
            )
        ),
        ReferenceTarget(
            id = "sennheiser_hd600_target",
            name = "Sennheiser HD600 Target",
            category = "Headphone Reference",
            points = listOf(
                20f to -4.0f, 50f to -2.0f, 100f to -0.5f, 300f to 0.0f,
                1000f to 0.0f, 3000f to 3.5f, 6000f to -1.0f, 10000f to -2.0f,
                15000f to -4.5f
            )
        ),
        ReferenceTarget(
            id = "sony_wh1000xm4_target",
            name = "Sony WH-1000XM4 Flat Corrected",
            category = "Wireless ANC",
            points = listOf(
                20f to -3.5f, 60f to -2.5f, 120f to -1.5f, 400f to 0.0f,
                1000f to 0.0f, 2500f to 2.5f, 5000f to 1.0f, 8000f to 3.0f
            )
        ),
        ReferenceTarget(
            id = "apple_airpods_max_target",
            name = "Apple AirPods Max Target",
            category = "AutoEQ Target",
            points = listOf(
                20f to 3.0f, 60f to 2.5f, 150f to 1.0f, 500f to 0.0f,
                1000f to 0.0f, 2700f to 5.5f, 6000f to 1.5f, 12000f to -1.0f
            )
        ),
        ReferenceTarget(
            id = "ief_neutral_target",
            name = "IEF Neutral Target (Crinacle)",
            category = "Studio Reference",
            points = listOf(
                20f to 0.0f, 100f to 0.0f, 500f to 0.0f, 1000f to 0.0f,
                2800f to 7.0f, 5000f to 2.0f, 8000f to 0.0f, 15000f to -2.0f
            )
        )
    )

    /**
     * Built-in Factory Presets for Parametric EQ.
     */
    val builtInParametricPresets: List<Pair<String, List<EqBand>>> = listOf(
        "Deep Bass Warmth" to listOf(
            EqBand(1, "#1", 45f, 4.5f, 0.71f, FilterType.LOW_SHELF, true, "#E85A3C"),
            EqBand(2, "#2", 180f, 2.0f, 1.2f, FilterType.PEAKING, true, "#4A6FA5"),
            EqBand(3, "#3", 1000f, -1.0f, 1.0f, FilterType.PEAKING, true, "#5B8C5A"),
            EqBand(4, "#4", 3500f, 1.5f, 1.4f, FilterType.PEAKING, true, "#D99B26"),
            EqBand(5, "#5", 8500f, 3.0f, 0.8f, FilterType.HIGH_SHELF, true, "#8B5CF6")
        ),
        "Vocal & Acoustic Clarity" to listOf(
            EqBand(1, "#1", 80f, -2.5f, 0.8f, FilterType.HIGH_PASS, true, "#E85A3C"),
            EqBand(2, "#2", 300f, -1.5f, 1.0f, FilterType.PEAKING, true, "#4A6FA5"),
            EqBand(3, "#3", 1200f, 2.5f, 1.2f, FilterType.PEAKING, true, "#5B8C5A"),
            EqBand(4, "#4", 4200f, 3.5f, 1.5f, FilterType.PEAKING, true, "#D99B26"),
            EqBand(5, "#5", 10000f, 2.0f, 0.7f, FilterType.HIGH_SHELF, true, "#8B5CF6")
        ),
        "Harman 2019 Match" to listOf(
            EqBand(1, "#1", 35f, 5.5f, 0.6f, FilterType.LOW_SHELF, true, "#E85A3C"),
            EqBand(2, "#2", 220f, -1.2f, 1.1f, FilterType.PEAKING, true, "#4A6FA5"),
            EqBand(3, "#3", 1500f, 1.0f, 1.0f, FilterType.PEAKING, true, "#5B8C5A"),
            EqBand(4, "#4", 3000f, 4.0f, 1.6f, FilterType.PEAKING, true, "#D99B26"),
            EqBand(5, "#5", 6500f, -2.0f, 2.0f, FilterType.PEAKING, true, "#8B5CF6")
        ),
        "Studio Flat Reference" to listOf(
            EqBand(1, "#1", 60f, 0.0f, 0.71f, FilterType.PEAKING, true, "#E85A3C"),
            EqBand(2, "#2", 250f, 0.0f, 1.0f, FilterType.PEAKING, true, "#4A6FA5"),
            EqBand(3, "#3", 1000f, 0.0f, 1.0f, FilterType.PEAKING, true, "#5B8C5A"),
            EqBand(4, "#4", 4000f, 0.0f, 1.0f, FilterType.PEAKING, true, "#D99B26"),
            EqBand(5, "#5", 12000f, 0.0f, 0.71f, FilterType.PEAKING, true, "#8B5CF6")
        )
    )

    companion object {
        fun serializeBandsToJson(bands: List<EqBand>): String {
            val array = JSONArray()
            for (b in bands) {
                val obj = JSONObject()
                obj.put("id", b.id)
                obj.put("label", b.label)
                obj.put("freq", b.frequencyHz)
                obj.put("gain", b.gainDb)
                obj.put("q", b.qFactor)
                obj.put("type", b.filterType.name)
                obj.put("enabled", b.enabled)
                obj.put("color", b.colorHex)
                array.put(obj)
            }
            return array.toString()
        }

        fun deserializeBandsFromJson(jsonStr: String): List<EqBand> {
            val list = mutableListOf<EqBand>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val filterType = try {
                        FilterType.valueOf(obj.optString("type", "PEAKING"))
                    } catch (e: Exception) {
                        FilterType.PEAKING
                    }
                    list.add(
                        EqBand(
                            id = obj.optInt("id", i + 1),
                            label = obj.optString("label", "#${i + 1}"),
                            frequencyHz = obj.optDouble("freq", 1000.0).toFloat(),
                            gainDb = obj.optDouble("gain", 0.0).toFloat(),
                            qFactor = obj.optDouble("q", 1.0).toFloat(),
                            filterType = filterType,
                            enabled = obj.optBoolean("enabled", true),
                            colorHex = obj.optString("color", "#E85A3C")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }

        /**
         * Export Equalizer configuration as Parametric EQ CSV format.
         */
        fun exportToCsv(bands: List<EqBand>, preampDb: Float): String {
            val sb = StringBuilder()
            sb.append("# StudioEQ Parametric Export\n")
            sb.append("# Preamp: ").append(preampDb).append(" dB\n")
            sb.append("Filter,Type,Frequency (Hz),Gain (dB),Q Factor,Enabled\n")
            for (b in bands) {
                sb.append("${b.label},${b.filterType.shortName},${b.frequencyHz},${b.gainDb},${b.qFactor},${if (b.enabled) "ON" else "OFF"}\n")
            }
            return sb.toString()
        }
    }
}
