package com.example.kidsvolumelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            try {
                LogManager.init(context)
                LogManager.info("BootReceiver triggered - Device booted")
                
                val prefs = PreferencesManager(context)
                if (prefs.isServiceEnabled()) {
                    val serviceIntent = Intent(context, VolumeLockService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                        LogManager.info("BootReceiver: Starting VolumeLockService (foreground)")
                    } else {
                        context.startService(serviceIntent)
                        LogManager.info("BootReceiver: Starting VolumeLockService")
                    }
                } else {
                    LogManager.info("BootReceiver: Service not enabled, skipping auto-start")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error in onReceive", e)
                try {
                    LogManager.error("BootReceiver error", e)
                } catch (logError: Exception) {
                    // Ignore logging errors
                }
            }
        }
    }
}
