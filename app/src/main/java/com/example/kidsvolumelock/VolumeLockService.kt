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
import android.view.KeyEvent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class VolumeLockService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioManager: AudioManager
    private var volumeCorrections = 0
    private var isMonitoring = false
    private var mediaSession: MediaSessionCompat? = null

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
            
            // Initialize MediaSession for volume key interception
            setupMediaSession()
            
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Service created with MediaSession")
            LogManager.info("VolumeLockService onCreate - Service created with MediaSession")
        } catch (e: Exception) {
            LogManager.error("VolumeLockService onCreate failed", e)
            throw e
        }
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSessionCompat(this, TAG).apply {
                // Set playback state to allow media button events
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                        .build()
                )
                
                // Set callback to intercept volume key events
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                        val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_VOLUME_UP -> {
                                    return handleVolumeUp()
                                }
                                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                    // Allow volume down always
                                    return false
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                })
                
                // Activate the session
                isActive = true
            }
            LogManager.info("MediaSession initialized and activated")
        } catch (e: Exception) {
            LogManager.error("Failed to setup MediaSession", e)
        }
    }

    private fun handleVolumeUp(): Boolean {
        try {
            val maxPercent = preferencesManager.getMaxVolumePercent()
            val maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val allowedLimit = (maxVolumeLevel * (maxPercent / 100.0)).toInt()

            // Preventive blocking: block if at or above limit-1
            if (currentVolume >= allowedLimit - 1) {
                Log.d(TAG, "Blocking VOLUME_UP: current=$currentVolume, limit=$allowedLimit")
                LogManager.info("MediaSession blocked VOLUME_UP: current=$currentVolume, limit=$allowedLimit")
                
                // Active clamp if somehow above limit
                if (currentVolume > allowedLimit) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, allowedLimit, 0)
                    volumeCorrections++
                    LogManager.warning("Forced volume down to $allowedLimit from $currentVolume")
                }
                
                return true // Consume the event (block it)
            }
            
            // Allow the volume up
            return false
        } catch (e: Exception) {
            LogManager.error("Error in handleVolumeUp", e)
            return false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            try {
                isMonitoring = true
                volumeCorrections = 0
                
                // Register BroadcastReceiver as fallback for volume changes
                val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                registerReceiver(volumeReceiver, filter)
                
                val maxPercent = preferencesManager.getMaxVolumePercent()
                Log.d(TAG, "Volume monitoring started using MediaSession + BroadcastReceiver")
                LogManager.info("VolumeLockService started - Using MediaSession with limit $maxPercent%")
                
                // Do initial check
                checkAndEnforceVolumeLimit()
            } catch (e: Exception) {
                LogManager.error("VolumeLockService onStartCommand failed", e)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            // Release MediaSession
            mediaSession?.release()
            mediaSession = null
            
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
