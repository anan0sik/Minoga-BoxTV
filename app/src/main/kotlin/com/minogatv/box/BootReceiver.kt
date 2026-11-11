package com.minogatv.box

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Starts [MainActivity] automatically when the Android TV box / stick boots up,
 * if the user has enabled "Автозапуск приложения с запуском устройства" in Settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_PRESENT &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val isAutoStartEnabled = WakeUpAutoStartService.isAutoStartEnabled(context)
        if (!isAutoStartEnabled) {
            return
        }

        // Start persistent wake-up listener service so waking from standby also launches the app
        WakeUpAutoStartService.syncServiceState(context)

        if (action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            WakeUpAutoStartService.launchMainActivity(context, action)
        }
    }

    private fun isBootAutoStartEnabled(context: Context): Boolean {
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
}

