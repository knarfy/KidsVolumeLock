package com.example.kidsvolumelock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class VolumeLockService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var volumeCorrections = 0

    companion object {
        const val CHANNEL_ID = "VolumeLockChannel"
        const val NOTIFICATION_ID = 1
        const val POLLING_INTERVAL_MS = 200L
        private const val TAG = "VolumeLockService"
    }

    private val volumeCheckRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                checkAndEnforceVolumeLimit()
                handler.postDelayed(this, POLLING_INTERVAL_MS)
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
        if (!isRunning) {
            try {
                isRunning = true
                volumeCorrections = 0
                // Start periodic volume checking
                handler.post(volumeCheckRunnable)
                val maxPercent = preferencesManager.getMaxVolumePercent()
                Log.d(TAG, "Volume monitoring started with ${POLLING_INTERVAL_MS}ms interval")
                LogManager.info("VolumeLockService started - Monitoring at ${POLLING_INTERVAL_MS}ms with limit $maxPercent%")
            } catch (e: Exception) {
                LogManager.error("VolumeLockService onStartCommand failed", e)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            isRunning = false
            handler.removeCallbacks(volumeCheckRunnable)
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
