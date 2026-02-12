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
                    
                    // Schedule Watchdog
                    try {
                        val constraints = androidx.work.Constraints.Builder()
                            .setRequiresBatteryNotLow(false)
                            .build()

                        val workRequest = androidx.work.PeriodicWorkRequest.Builder(
                            ServiceCheckWorker::class.java,
                            15, java.util.concurrent.TimeUnit.MINUTES
                        )
                        .setConstraints(constraints)
                        .build()

                        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "VolumeLockWatchdog",
                            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                            workRequest
                        )
                        LogManager.info("BootReceiver: Scheduled ServiceCheckWorker")
                    } catch (e: Exception) {
                        LogManager.error("BootReceiver: Failed to schedule worker", e)
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
