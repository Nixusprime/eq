package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shizuku Helper delegating to ShizukuCheckService for robust IPC monitoring.
 */
object ShizukuHelper {

    val isShizukuConnected: StateFlow<Boolean>
        get() = MutableStateFlow(ShizukuCheckService.statusState.value.isRunning).asStateFlow()

    val shizukuPermissionGranted: StateFlow<Boolean>
        get() = MutableStateFlow(ShizukuCheckService.statusState.value.isPermissionGranted).asStateFlow()

    val activeMediaSessions: StateFlow<List<Int>>
        get() = MutableStateFlow(ShizukuCheckService.statusState.value.activeSessions).asStateFlow()

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                context.packageManager.getPackageInfo("moe.shizuku.api", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun pingBinder(): Boolean {
        return ShizukuCheckService.updateStateSafely().isRunning
    }

    fun checkShizukuPermission(): Boolean {
        return ShizukuCheckService.updateStateSafely().isPermissionGranted
    }

    fun requestShizukuPermission(onResult: (Boolean) -> Unit = {}) {
        ShizukuCheckService.requestPermissionSafely(onResult)
    }

    fun extractActiveMediaSessions(): List<Int> {
        return ShizukuCheckService.extractActiveSessionsSafely()
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
