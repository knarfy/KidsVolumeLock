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
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class VolumeLockService : Service() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private lateinit var sharedPreferences: SharedPreferences
    private var volumeCorrections = 0
    private var isMonitoring = false
    private var isVolumeReceiverRegistered = false
    private var isScreenReceiverRegistered = false
    private var isScreenReceiverRegistered = false
    private var lastVolumeChangeTimestamp = 0L
    private var cachedMaxVolumePercent = 50 // Default safe value

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == PreferencesManager.KEY_MAX_VOLUME_PERCENT) {
            val newPercent = sharedPreferences.getInt(key, 50)
            cachedMaxVolumePercent = newPercent
            LogManager.info("🔄 Service updated cached max volume: $newPercent%")
            // Re-check immediately upon setting change
            checkAndEnforceVolumeLimit()
        }
    }

    companion object {
        const val CHANNEL_ID = "VolumeLockChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "VolumeLockService"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                // Filter by stream type!
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                
                // Only act if it's STREAM_MUSIC or if the extra is missing (safety)
                if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                    val timestamp = System.currentTimeMillis()
                    val timeSinceLastChange = if (lastVolumeChangeTimestamp > 0) {
                        timestamp - lastVolumeChangeTimestamp
                    } else {
                        0L
                    }
                    lastVolumeChangeTimestamp = timestamp
                    
                    // LogManager.info("📢 Volume change detected (stream=$streamType, ${timeSinceLastChange}ms since last)")
                    checkAndEnforceVolumeLimit()
                } else {
                     // LogManager.info("🔇 Ignored volume change for stream $streamType")
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogManager.warning("🔒 SCREEN_OFF detected - Screen locked")
                    logServiceState("after SCREEN_OFF")
                }
                Intent.ACTION_SCREEN_ON -> {
                    LogManager.warning("🔓 SCREEN_ON detected - Screen turned on")
                    logServiceState("after SCREEN_ON")
                }
                Intent.ACTION_USER_PRESENT -> {
                    LogManager.warning("👤 USER_PRESENT detected - User unlocked device")
                    logServiceState("after USER_PRESENT")
                    // Re-register volume receiver as a safety measure
                    ensureVolumeReceiverRegistered()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            preferencesManager = PreferencesManager(this)
            // Access raw prefs to register listener
            sharedPreferences = getSharedPreferences("kids_volume_prefs", Context.MODE_PRIVATE)
            
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Service created")
            LogManager.info("✅ VolumeLockService onCreate - Service created successfully")
            logServiceState("onCreate")
        } catch (e: Exception) {
            LogManager.error("❌ VolumeLockService onCreate failed", e)
            throw e
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.info("🚀 onStartCommand called - flags=$flags, startId=$startId, isMonitoring=$isMonitoring")
        
        if (!isMonitoring) {
            try {
                isMonitoring = true
                volumeCorrections = 0
                lastVolumeChangeTimestamp = 0L
                
                // Register BroadcastReceiver for volume changes
                registerVolumeReceiver()
                
                // Register BroadcastReceiver for screen events
                registerScreenReceiver()
                
                // Register Preference Listener
                sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
                
                // Load initial value
                cachedMaxVolumePercent = preferencesManager.getMaxVolumePercent()
                
                Log.d(TAG, "Volume monitoring started using BroadcastReceiver")
                LogManager.info("✅ VolumeLockService started - Using BroadcastReceiver with limit $cachedMaxVolumePercent%")
                logServiceState("onStartCommand")
                
                // Do initial check
                checkAndEnforceVolumeLimit()
            } catch (e: Exception) {
                LogManager.error("❌ VolumeLockService onStartCommand failed", e)
            }
        } else {
            LogManager.info("⚠️ onStartCommand called but already monitoring - ensuring receivers are registered")
            ensureVolumeReceiverRegistered()
            ensureScreenReceiverRegistered()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            LogManager.warning("💀 onDestroy called - Service is being destroyed")
            logServiceState("onDestroy")
            
            if (isMonitoring) {
                unregisterVolumeReceiver()
                unregisterScreenReceiver()
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
                isMonitoring = false
            }
            
            Log.d(TAG, "Service destroyed, monitoring stopped")
            LogManager.info("❌ VolumeLockService destroyed - Total corrections: $volumeCorrections")
        } catch (e: Exception) {
            LogManager.error("❌ VolumeLockService onDestroy error", e)
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        LogManager.warning("⚠️ onTaskRemoved called - App task removed from recents")
        logServiceState("onTaskRemoved")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        LogManager.warning("⚠️ onLowMemory called - System is low on memory")
        logServiceState("onLowMemory")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LogManager.warning("⚠️ onTrimMemory called - level=$level")
        logServiceState("onTrimMemory")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun registerVolumeReceiver() {
        try {
            if (!isVolumeReceiverRegistered) {
                val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                registerReceiver(volumeReceiver, filter)
                isVolumeReceiverRegistered = true
                LogManager.info("✅ Volume BroadcastReceiver registered")
            }
        } catch (e: Exception) {
            LogManager.error("❌ Failed to register volume receiver", e)
        }
    }

    private fun unregisterVolumeReceiver() {
        try {
            if (isVolumeReceiverRegistered) {
                unregisterReceiver(volumeReceiver)
                isVolumeReceiverRegistered = false
                LogManager.info("❌ Volume BroadcastReceiver unregistered")
            }
        } catch (e: Exception) {
            LogManager.error("❌ Failed to unregister volume receiver", e)
        }
    }

    private fun registerScreenReceiver() {
        try {
            if (!isScreenReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                registerReceiver(screenReceiver, filter)
                isScreenReceiverRegistered = true
                LogManager.info("✅ Screen BroadcastReceiver registered")
            }
        } catch (e: Exception) {
            LogManager.error("❌ Failed to register screen receiver", e)
        }
    }

    private fun unregisterScreenReceiver() {
        try {
            if (isScreenReceiverRegistered) {
                unregisterReceiver(screenReceiver)
                isScreenReceiverRegistered = false
                LogManager.info("❌ Screen BroadcastReceiver unregistered")
            }
        } catch (e: Exception) {
            LogManager.error("❌ Failed to unregister screen receiver", e)
        }
    }

    private fun ensureVolumeReceiverRegistered() {
        if (!isVolumeReceiverRegistered) {
            LogManager.warning("⚠️ Volume receiver was not registered! Re-registering...")
            registerVolumeReceiver()
        } else {
            LogManager.info("✅ Volume receiver is still registered")
        }
    }

    private fun ensureScreenReceiverRegistered() {
        if (!isScreenReceiverRegistered) {
            LogManager.warning("⚠️ Screen receiver was not registered! Re-registering...")
            registerScreenReceiver()
        }
    }

    private fun logServiceState(context: String) {
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
        
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        LogManager.info("""
            📊 Service State ($context):
            - isMonitoring: $isMonitoring
            - volumeReceiverRegistered: $isVolumeReceiverRegistered
            - screenReceiverRegistered: $isScreenReceiverRegistered
            - screenOn: $isScreenOn
            - currentVolume: $currentVolume/$maxVolume
            - corrections: $volumeCorrections
        """.trimIndent())
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
        // Debounce/Rate limit checks?
        val startTime = System.currentTimeMillis()
        
        try {
            // Use cached value instead of reading from disk/IPC every time
            val maxPercent = cachedMaxVolumePercent
            val maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val allowedLimitCapped = (maxVolumeLevel * (maxPercent / 100.0)).toInt()
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            // LogManager.info("🔍 Checking volume: current=$currentVolume, allowed=$allowedLimitCapped, max=$maxVolumeLevel, limit=$maxPercent%")

            if (currentVolume > allowedLimitCapped) {
                val beforeCorrection = System.currentTimeMillis()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, allowedLimitCapped, 0)
                val afterCorrection = System.currentTimeMillis()
                val correctionTime = afterCorrection - beforeCorrection
                val totalTime = afterCorrection - startTime
                
                volumeCorrections++
                val logMsg = "⚠️ Volume corrected #$volumeCorrections: $currentVolume -> $allowedLimitCapped (max:$maxVolumeLevel, limit:$maxPercent%) [correction took ${correctionTime}ms, total ${totalTime}ms]"
                Log.d(TAG, logMsg)
                LogManager.warning(logMsg)
            } else {
                // DON'T LOG ON SUCCESS TO AVOID SPAM
                // val totalTime = System.currentTimeMillis() - startTime
                // LogManager.info("✅ Volume OK: $currentVolume <= $allowedLimitCapped [check took ${totalTime}ms]")
            }
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - startTime
            LogManager.error("❌ Error in checkAndEnforceVolumeLimit [took ${totalTime}ms]", e)
        }
    }
}
