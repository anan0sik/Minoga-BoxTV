package com.minogatv.box

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Listens for device wake-up events ([Intent.ACTION_SCREEN_ON], [Intent.ACTION_DREAMING_STOPPED],
 * [Intent.ACTION_USER_PRESENT]) so that when "Автозапуск приложения с запуском устройства" is enabled,
 * Minoga TV automatically opens not only on cold boot, but also when the TV box / stick wakes from sleep.
 */
class WakeUpAutoStartService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastWakeLaunchAtMs: Long = 0L
    private var isReceiverRegistered: Boolean = false

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_DREAMING_STOPPED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                    if (!isAutoStartEnabled(context)) {
                        stopSelf()
                        return
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastWakeLaunchAtMs < 2500L) {
                        return
                    }
                    lastWakeLaunchAtMs = now
                    // Launch immediately and once more after 600ms in case HDMI/Launcher finishes waking up
                    launchMainActivity(context, action)
                    mainHandler.postDelayed({
                        if (isAutoStartEnabled(context)) {
                            launchMainActivity(context, "$action-delayed")
                        }
                    }, 600L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerWakeReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isAutoStartEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        registerWakeReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterWakeReceiver()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerWakeReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_DREAMING_STOPPED)
                addAction("android.intent.action.QUICKBOOT_POWERON")
                addAction("com.htc.intent.action.QUICKBOOT_POWERON")
                priority = 999
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(wakeReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(wakeReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e("WakeUpAutoStartService", "Failed to register wake receiver", e)
        }
    }

    private fun unregisterWakeReceiver() {
        if (!isReceiverRegistered) return
        try {
            unregisterReceiver(wakeReceiver)
        } catch (_: Exception) {}
        isReceiverRegistered = false
    }

    companion object {
        fun isAutoStartEnabled(context: Context): Boolean {
            runCatching {
                val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("auto_start_on_boot", false)) return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    val dpContext = context.createDeviceProtectedStorageContext()
                    val dpPrefs = dpContext.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
                    if (dpPrefs.getBoolean("auto_start_on_boot", false)) return true
                }
            }
            return false
        }

        fun syncServiceState(context: Context) {
            runCatching {
                val intent = Intent(context, WakeUpAutoStartService::class.java)
                if (isAutoStartEnabled(context)) {
                    context.startService(intent)
                } else {
                    context.stopService(intent)
                }
            }
        }

        fun launchMainActivity(context: Context, reason: String) {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("WakeUpAutoStartService", "Failed to launch MainActivity ($reason)", e)
            }
        }
    }
}

