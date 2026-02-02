package com.example.kidsvolumelock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log

class VolumeLockService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioManager: AudioManager
    private var volumeCorrections = 0
    private var isMonitoring = false

    companion object {
        const val CHANNEL_ID = "VolumeLockChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "VolumeLockService"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                LogManager.info("Volume change detected by BroadcastReceiver")
                checkAndEnforceVolumeLimit()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            preferencesManager = PreferencesManager(this)
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Service created")
            LogManager.info("VolumeLockService onCreate - Service created successfully")
        } catch (e: Exception) {
            LogManager.error("VolumeLockService onCreate failed", e)
            throw e
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // [FIX] Suicide Pill: If Accessibility Service is enabled, this service MUST die to prevent conflict/oscillation.
        if (isAccessibilityServiceEnabled()) {
             LogManager.info("VolumeLockService: Accessibility Service detected. Stopping self to avoid conflict.")
             stopSelf()
             return START_NOT_STICKY
        }

        if (!isMonitoring) {
            try {
                isMonitoring = true
                volumeCorrections = 0
                
                // Register BroadcastReceiver to listen for volume changes
                val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                registerReceiver(volumeReceiver, filter)
                
                val maxPercent = preferencesManager.getMaxVolumePercent()
                Log.d(TAG, "Volume monitoring started using BroadcastReceiver")
                LogManager.info("VolumeLockService started - Using BroadcastReceiver with limit $maxPercent%")
                
                // Do initial check
                checkAndEnforceVolumeLimit()
            } catch (e: Exception) {
                LogManager.error("VolumeLockService onStartCommand failed", e)
                // If we fail to start monitoring, we should probably stop the service or try again?
                // For now, logging error.
            }
        }
        return START_STICKY
    }

    // Checking if our Accessibility Service is enabled
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = android.content.ComponentName(this, VolumeAccessibilityService::class.java)
        val enabledServicesSetting = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName)
                return true
        }
        return false
    }

    override fun onDestroy() {
        try {
            if (isMonitoring) {
                unregisterReceiver(volumeReceiver)
                isMonitoring = false
            }
            
            Log.d(TAG, "Service destroyed, monitoring stopped")
            LogManager.info("VolumeLockService destroyed - Total corrections: $volumeCorrections")
        } catch (e: Exception) {
            LogManager.error("VolumeLockService onDestroy error", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            CHANNEL_ID
        } else {
            ""
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun checkAndEnforceVolumeLimit() {
        try {
            val maxPercent = preferencesManager.getMaxVolumePercent()
            val maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            // Calculate allowed limit
            val allowedLimitCapped = (maxVolumeLevel * (maxPercent / 100.0)).toInt()
            
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            // Log less frequently or only on violation? 
            // For debugging we want to see it, but we can check if it exceeds first to reduce noise if needed.
            // LogManager.info("Checking volume: current=$currentVolume, allowed=$allowedLimitCapped, max=$maxVolumeLevel, limit=$maxPercent%")

            if (currentVolume > allowedLimitCapped) {
                // Force volume down
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, allowedLimitCapped, 0)
                volumeCorrections++
                val logMsg = "Volume corrected #$volumeCorrections: $currentVolume -> $allowedLimitCapped (max:$maxVolumeLevel, limit:$maxPercent%)"
                Log.d(TAG, logMsg)
                LogManager.warning(logMsg)
            }
        } catch (e: Exception) {
            LogManager.error("Error in checkAndEnforceVolumeLimit", e)
        }
    }
}
