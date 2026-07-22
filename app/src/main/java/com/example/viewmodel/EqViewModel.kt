package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.data.EqDatabase
import com.example.data.EqRepository
import com.example.dsp.DspEngine
import com.example.model.AppThemeMode
import com.example.model.BandColors
import com.example.model.EqBand
import com.example.model.FilterType
import com.example.model.PresetEntity
import com.example.model.ReferenceTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.model.AppTab
import com.example.model.HeroAccentColors

data class EqUiState(
    val currentTab: AppTab = AppTab.EQUALIZER,
    val bands: List<EqBand> = emptyList(),
    val preampDb: Float = 0.0f,
    val guardEnabled: Boolean = true,
    val peakAlertsEnabled: Boolean = true,
    val pureBlackOled: Boolean = false,
    val customAccentHex: String = HeroAccentColors.DefaultOrange,
    val activePresetName: String = "Deep Bass Warmth",
    val selectedBandId: Int? = 1,
    val expandedModalBandId: Int? = null,
    val activeTarget: ReferenceTarget? = null,
    val isGraphicEqMode: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val showPresetOverlay: Boolean = false
)

private data class HistorySnapshot(
    val bands: List<EqBand>,
    val preampDb: Float,
    val presetName: String
)

class EqViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EqRepository
    val audioManager: AudioPlayerManager

    private val _uiState = MutableStateFlow(EqUiState())
    val uiState: StateFlow<EqUiState> = _uiState.asStateFlow()

    // Undo / Redo Stacks
    private val undoStack = mutableListOf<HistorySnapshot>()
    private val redoStack = mutableListOf<HistorySnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Database flow of user presets
    val userPresets: StateFlow<List<PresetEntity>>

    init {
        val database = EqDatabase.getDatabase(application)
        repository = EqRepository(database.presetDao())
        audioManager = AudioPlayerManager(application)

        userPresets = repository.allPresets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Load default preset "Deep Bass Warmth"
        val defaultBands = repository.builtInParametricPresets.first().second
        _uiState.value = _uiState.value.copy(
            bands = defaultBands,
            activePresetName = repository.builtInParametricPresets.first().first,
            selectedBandId = defaultBands.firstOrNull()?.id
        )

        // Automatically sync DSP config on every UI state change (band frequency, gain, preamp, etc.)
        viewModelScope.launch {
            _uiState.collect { state ->
                audioManager.updateDspConfig(state.bands, state.preampDb)
            }
        }
    }

    private fun syncDsp() {
        audioManager.updateDspConfig(_uiState.value.bands, _uiState.value.preampDb)
    }

    // Computed total response curve
    val responseCurve: StateFlow<FloatArray> = _uiState.map { state ->
        DspEngine.computeTotalResponseCurve(state.bands, state.preampDb)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FloatArray(DspEngine.NUM_FREQ_POINTS))

    // Computed target curve array
    val targetCurveArray: StateFlow<FloatArray?> = _uiState.map { state ->
        DspEngine.computeTargetCurveArray(state.activeTarget)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Computed peak gain
    val peakGainDb: StateFlow<Float> = responseCurve.map { curve ->
        DspEngine.computePeakOutputGainDb(curve)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0f)

    private fun pushHistorySnapshot() {
        val current = _uiState.value
        undoStack.add(HistorySnapshot(current.bands, current.preampDb, current.activePresetName))
        if (undoStack.size > 25) undoStack.removeAt(0)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val last = undoStack.removeAt(undoStack.lastIndex)
        val current = _uiState.value
        redoStack.add(HistorySnapshot(current.bands, current.preampDb, current.activePresetName))

        _uiState.value = current.copy(
            bands = last.bands,
            preampDb = last.preampDb,
            activePresetName = last.presetName
        )
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        checkAutoGuard()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        val current = _uiState.value
        undoStack.add(HistorySnapshot(current.bands, current.preampDb, current.activePresetName))

        _uiState.value = current.copy(
            bands = next.bands,
            preampDb = next.preampDb,
            activePresetName = next.presetName
        )
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        checkAutoGuard()
    }

    fun selectBand(id: Int) {
        _uiState.value = _uiState.value.copy(selectedBandId = id)
    }

    fun openExpandModal(id: Int) {
        _uiState.value = _uiState.value.copy(selectedBandId = id, expandedModalBandId = id)
    }

    fun closeExpandModal() {
        _uiState.value = _uiState.value.copy(expandedModalBandId = null)
    }

    fun updateBand(updatedBand: EqBand) {
        pushHistorySnapshot()
        val currentBands = _uiState.value.bands.toMutableList()
        val index = currentBands.indexOfFirst { it.id == updatedBand.id }
        if (index >= 0) {
            currentBands[index] = updatedBand
            _uiState.value = _uiState.value.copy(bands = currentBands)
            checkAutoGuard()
        }
    }

    fun updateBandFreqGain(id: Int, freqHz: Float, gainDb: Float) {
        pushHistorySnapshot()
        val currentBands = _uiState.value.bands.toMutableList()
        val index = currentBands.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentBands[index] = currentBands[index].copy(frequencyHz = freqHz, gainDb = gainDb)
            _uiState.value = _uiState.value.copy(bands = currentBands)
            checkAutoGuard()
        }
    }

    fun updateBandBypass(id: Int, enabled: Boolean) {
        pushHistorySnapshot()
        val currentBands = _uiState.value.bands.toMutableList()
        val index = currentBands.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentBands[index] = currentBands[index].copy(enabled = enabled)
            _uiState.value = _uiState.value.copy(bands = currentBands)
            checkAutoGuard()
        }
    }

    fun addBand(freqHz: Float = 1000f, gainDb: Float = 0f) {
        pushHistorySnapshot()
        val currentBands = _uiState.value.bands.toMutableList()
        val newId = (currentBands.maxOfOrNull { it.id } ?: 0) + 1
        val newBand = EqBand(
            id = newId,
            label = "#$newId",
            frequencyHz = freqHz,
            gainDb = gainDb,
            qFactor = 1.0f,
            filterType = FilterType.PEAKING,
            enabled = true,
            colorHex = BandColors.getColorForIndex(newId - 1)
        )
        currentBands.add(newBand)
        _uiState.value = _uiState.value.copy(
            bands = currentBands,
            selectedBandId = newId
        )
        checkAutoGuard()
    }

    fun deleteBand(id: Int) {
        pushHistorySnapshot()
        val currentBands = _uiState.value.bands.filterNot { it.id == id }
        _uiState.value = _uiState.value.copy(
            bands = currentBands,
            selectedBandId = currentBands.firstOrNull()?.id
        )
        checkAutoGuard()
    }

    fun setPreampDb(preampDb: Float) {
        pushHistorySnapshot()
        _uiState.value = _uiState.value.copy(preampDb = preampDb)
        checkAutoGuard()
    }

    fun setGuardEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(guardEnabled = enabled)
        checkAutoGuard()
    }

    private fun checkAutoGuard() {
        if (_uiState.value.guardEnabled) {
            val currentCurve = DspEngine.computeTotalResponseCurve(_uiState.value.bands, _uiState.value.preampDb)
            val maxPeak = DspEngine.computePeakOutputGainDb(currentCurve)
            if (maxPeak > 0.0f) {
                val adjustedPreamp = (_uiState.value.preampDb - maxPeak).coerceIn(-12.0f, 12.0f)
                _uiState.value = _uiState.value.copy(preampDb = adjustedPreamp)
            }
        }
        syncDsp()
    }

    fun loadFactoryPreset(name: String, bands: List<EqBand>) {
        pushHistorySnapshot()
        _uiState.value = _uiState.value.copy(
            bands = bands,
            activePresetName = name,
            selectedBandId = bands.firstOrNull()?.id
        )
        checkAutoGuard()
    }

    fun loadUserPreset(preset: PresetEntity) {
        pushHistorySnapshot()
        val bands = EqRepository.deserializeBandsFromJson(preset.bandDataJson)
        _uiState.value = _uiState.value.copy(
            bands = bands,
            preampDb = preset.preampDb,
            guardEnabled = preset.guardEnabled,
            activePresetName = preset.name,
            selectedBandId = bands.firstOrNull()?.id
        )
        checkAutoGuard()
    }

    fun saveCurrentAsUserPreset(name: String) {
        viewModelScope.launch {
            val entity = PresetEntity(
                name = name,
                isUserCreated = true,
                isGraphicEq = _uiState.value.isGraphicEqMode,
                preampDb = _uiState.value.preampDb,
                guardEnabled = _uiState.value.guardEnabled,
                bandDataJson = EqRepository.serializeBandsToJson(_uiState.value.bands),
                referenceTargetId = _uiState.value.activeTarget?.id
            )
            repository.savePreset(entity)
            _uiState.value = _uiState.value.copy(activePresetName = name)
        }
    }

    fun selectAutoEqTarget(target: ReferenceTarget) {
        _uiState.value = _uiState.value.copy(activeTarget = target)
    }

    fun clearAutoEqTarget() {
        _uiState.value = _uiState.value.copy(activeTarget = null)
    }

    fun toggleGraphicEqMode() {
        val current = _uiState.value.isGraphicEqMode
        if (!current) {
            // Generate standard 10-band Graphic EQ frequencies (31, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k)
            val geqFreqs = listOf(31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
            val geqBands = geqFreqs.mapIndexed { idx, freq ->
                EqBand(
                    id = idx + 1,
                    label = "#${idx + 1}",
                    frequencyHz = freq,
                    gainDb = 0f,
                    qFactor = 1.41f,
                    filterType = FilterType.PEAKING,
                    enabled = true,
                    colorHex = BandColors.getColorForIndex(idx)
                )
            }
            _uiState.value = _uiState.value.copy(
                isGraphicEqMode = true,
                bands = geqBands,
                activePresetName = "10-Band Graphic EQ"
            )
        } else {
            val defaultBands = repository.builtInParametricPresets.first().second
            _uiState.value = _uiState.value.copy(
                isGraphicEqMode = false,
                bands = defaultBands,
                activePresetName = repository.builtInParametricPresets.first().first
            )
        }
        syncDsp()
    }

    fun resetAllBands() {
        pushHistorySnapshot()
        val resetBands = _uiState.value.bands.map { it.copy(gainDb = 0f, enabled = true) }
        _uiState.value = _uiState.value.copy(bands = resetBands, preampDb = 0f)
        syncDsp()
    }

    fun exportParametricCsv(): String {
        return EqRepository.exportToCsv(_uiState.value.bands, _uiState.value.preampDb)
    }

    fun setTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun setPureBlackOled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(pureBlackOled = enabled)
    }

    fun setCustomAccentHex(hex: String) {
        _uiState.value = _uiState.value.copy(customAccentHex = hex)
    }

    fun setPeakAlertsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(peakAlertsEnabled = enabled)
    }

    fun deleteUserPreset(preset: PresetEntity) {
        viewModelScope.launch {
            repository.deletePreset(preset.id)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setOverlayVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showPresetOverlay = visible)
    }

    fun getFactoryPresets(): List<Pair<String, List<EqBand>>> = repository.builtInParametricPresets
    fun getAutoEqTargets(): List<ReferenceTarget> = repository.builtInReferenceTargets
}
