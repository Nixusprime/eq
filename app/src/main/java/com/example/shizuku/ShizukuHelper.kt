package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku Binder & Session Hook Manager.
 * Features Binder availability pinging, permission request listeners,
 * dumpsys media.audio_flinger media session extraction, and fallback to Session ID 0.
 */
object ShizukuHelper {

    private val _isShizukuConnected = MutableStateFlow(false)
    val isShizukuConnected: StateFlow<Boolean> = _isShizukuConnected.asStateFlow()

    private val _shizukuPermissionGranted = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = _shizukuPermissionGranted.asStateFlow()

    private val _activeMediaSessions = MutableStateFlow<List<Int>>(listOf(0)) // Session ID 0 is Global Output
    val activeMediaSessions: StateFlow<List<Int>> = _activeMediaSessions.asStateFlow()

    private const val REQUEST_CODE_SHIZUKU = 10085

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

    /**
     * Checks if Shizuku binder is active and running.
     */
    fun pingBinder(): Boolean {
        var alive = false
        try {
            val clazz = Class.forName("moe.shizuku.api.Shizuku")
            val method = clazz.getMethod("pingBinder")
            alive = (method.invoke(null) as? Boolean) ?: false
        } catch (e: Throwable) {
            try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getMethod("pingBinder")
                alive = (method.invoke(null) as? Boolean) ?: false
            } catch (e2: Throwable) {
                alive = false
            }
        }
        _isShizukuConnected.value = alive
        if (alive) {
            checkShizukuPermission()
        }
        return alive
    }

    /**
     * Checks if app has Shizuku permission.
     */
    fun checkShizukuPermission(): Boolean {
        var granted = false
        try {
            val clazz = Class.forName("moe.shizuku.api.Shizuku")
            val method = clazz.getMethod("checkSelfPermission")
            val result = (method.invoke(null) as? Int) ?: -1
            granted = (result == PackageManager.PERMISSION_GRANTED)
        } catch (e: Throwable) {
            try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getMethod("checkSelfPermission")
                val result = (method.invoke(null) as? Int) ?: -1
                granted = (result == PackageManager.PERMISSION_GRANTED)
            } catch (e2: Throwable) {
                granted = false
            }
        }
        _shizukuPermissionGranted.value = granted
        if (granted) {
            extractActiveMediaSessions()
        }
        return granted
    }

    /**
     * Triggers Shizuku permission request dialog.
     */
    fun requestShizukuPermission(onResult: (Boolean) -> Unit = {}) {
        if (!pingBinder()) {
            _shizukuPermissionGranted.value = false
            onResult(false)
            return
        }

        try {
            registerPermissionListener(onResult)
            val clazz = Class.forName("moe.shizuku.api.Shizuku")
            val method = clazz.getMethod("requestPermission", Int::class.javaPrimitiveType)
            method.invoke(null, REQUEST_CODE_SHIZUKU)
        } catch (e: Throwable) {
            try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getMethod("requestPermission", Int::class.javaPrimitiveType)
                method.invoke(null, REQUEST_CODE_SHIZUKU)
            } catch (e2: Throwable) {
                onResult(false)
            }
        }
    }

    private fun registerPermissionListener(onResult: (Boolean) -> Unit) {
        try {
            val listenerClass = Class.forName("moe.shizuku.api.Shizuku\$OnRequestPermissionResultListener")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onRequestPermissionResult") {
                    val requestCode = args[0] as Int
                    val grantResult = args[1] as Int
                    if (requestCode == REQUEST_CODE_SHIZUKU) {
                        val granted = (grantResult == PackageManager.PERMISSION_GRANTED)
                        _shizukuPermissionGranted.value = granted
                        if (granted) {
                            extractActiveMediaSessions()
                        }
                        onResult(granted)
                    }
                }
                null
            }
            val shizukuClass = Class.forName("moe.shizuku.api.Shizuku")
            val addMethod = shizukuClass.getMethod("addRequestPermissionResultListener", listenerClass)
            addMethod.invoke(null, proxy)
        } catch (e: Throwable) {
            try {
                val listenerClass = Class.forName("rikka.shizuku.Shizuku\$OnRequestPermissionResultListener")
                val proxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onRequestPermissionResult") {
                        val requestCode = args[0] as Int
                        val grantResult = args[1] as Int
                        if (requestCode == REQUEST_CODE_SHIZUKU) {
                            val granted = (grantResult == PackageManager.PERMISSION_GRANTED)
                            _shizukuPermissionGranted.value = granted
                            if (granted) {
                                extractActiveMediaSessions()
                            }
                            onResult(granted)
                        }
                    }
                    null
                }
                val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
                val addMethod = shizukuClass.getMethod("addRequestPermissionResultListener", listenerClass)
                addMethod.invoke(null, proxy)
            } catch (e2: Throwable) {
                // Ignore if listener registration unavailable
            }
        }
    }

    /**
     * Executes dumpsys media.audio_flinger via Shizuku process to extract active media session IDs.
     * Falls back to Global Session ID 0 if unavailable or empty.
     */
    fun extractActiveMediaSessions(): List<Int> {
        val foundSessions = mutableListOf<Int>()
        foundSessions.add(0) // Always include Global Session ID 0

        try {
            val shizukuClass = Class.forName("moe.shizuku.api.Shizuku")
            val newProcessMethod = shizukuClass.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val cmd = arrayOf("dumpsys", "media.audio_flinger")
            val process = newProcessMethod.invoke(null, cmd, null, null) as? Process
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
            // Fallback to reading system dumpsys if accessible or keep session 0
        }

        _activeMediaSessions.value = foundSessions.distinct()
        return _activeMediaSessions.value
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
