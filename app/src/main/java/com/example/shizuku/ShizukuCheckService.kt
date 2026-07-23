package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShizukuStatusState(
    val isRunning: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val activeSessions: List<Int> = listOf(0),
    val statusMessage: String = "Not Connected"
)

/**
 * Robust Shizuku IPC Check Service.
 * Safely monitors 'shizuku_running' state via IPC and exposes
 * a reactive StateFlow hook for real-time UI permission dashboard updates.
 */
object ShizukuCheckService {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _statusState = MutableStateFlow(ShizukuStatusState())
    val statusState: StateFlow<ShizukuStatusState> = _statusState.asStateFlow()

    private const val REQUEST_CODE_SHIZUKU = 10085
    @Volatile private var isMonitoringStarted = false

    fun startMonitoring() {
        if (isMonitoringStarted) return
        isMonitoringStarted = true

        try {
            Shizuku.addBinderReceivedListenerSticky {
                try { updateStateSafely() } catch (e: Throwable) {}
            }
        } catch (e: Throwable) {}

        try {
            Shizuku.addBinderDeadListener {
                _statusState.value = ShizukuStatusState(
                    isRunning = false,
                    isPermissionGranted = false,
                    activeSessions = listOf(0),
                    statusMessage = "Binder Disconnected"
                )
            }
        } catch (e: Throwable) {}

        try {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == REQUEST_CODE_SHIZUKU) {
                    val granted = (grantResult == PackageManager.PERMISSION_GRANTED)
                    val sessions = if (granted) extractActiveSessionsSafely() else listOf(0)
                    _statusState.value = _statusState.value.copy(
                        isPermissionGranted = granted,
                        activeSessions = sessions,
                        statusMessage = if (granted) "Granted & Hooked" else "Permission Denied"
                    )
                }
            }
        } catch (e: Throwable) {}

        // IPC polling thread for guaranteed real-time updates
        serviceScope.launch {
            while (isActive) {
                try {
                    updateStateSafely()
                } catch (e: Throwable) {}
                delay(2500)
            }
        }
    }

    /**
     * Safely queries Shizuku IPC binder and permission state without throwing.
     */
    fun updateStateSafely(): ShizukuStatusState {
        var running = false
        var granted = false

        try {
            running = Shizuku.pingBinder()
            if (running) {
                granted = (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            }
        } catch (e: Throwable) {
            running = false
            granted = false
        }

        val sessions = if (running && granted) extractActiveSessionsSafely() else listOf(0)
        val msg = when {
            !running -> "Service Disconnected"
            !granted -> "Service Running (Permission Required)"
            else -> "Connected & Hooked (${sessions.size} Stream${if (sessions.size > 1) "s" else ""})"
        }

        val newState = ShizukuStatusState(
            isRunning = running,
            isPermissionGranted = granted,
            activeSessions = sessions,
            statusMessage = msg
        )
        _statusState.value = newState
        return newState
    }

    fun requestPermissionSafely(onResult: (Boolean) -> Unit = {}) {
        val safeResult: (Boolean) -> Unit = { res ->
            mainHandler.post { onResult(res) }
        }

        try {
            val state = updateStateSafely()
            if (!state.isRunning) {
                safeResult(false)
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                updateStateSafely()
                safeResult(true)
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            }
        } catch (e: Throwable) {
            safeResult(false)
        }
    }

    fun extractActiveSessionsSafely(): List<Int> {
        val foundSessions = mutableListOf<Int>()
        foundSessions.add(0)

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val cmd = arrayOf("dumpsys", "media.audio_flinger")
            val process = method.invoke(null, cmd, null, null) as? Process
            process?.let { proc ->
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                val sessionRegex = Regex("""session(?:Id)?\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
                while (reader.readLine().also { line = it } != null) {
                    val match = sessionRegex.find(line!!)
                    match?.groupValues?.get(1)?.toIntOrNull()?.let { sessionId ->
                        if (sessionId > 0 && !foundSessions.contains(sessionId)) {
                            foundSessions.add(sessionId)
                        }
                    }
                }
                proc.destroy()
            }
        } catch (e: Throwable) {
            // Ignore dumpsys read errors
        }

        return foundSessions.distinct()
    }
}
