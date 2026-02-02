package com.example.kidsvolumelock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var audioManager: AudioManager
    private val TAG = "VolumeAccessService"

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferencesManager = PreferencesManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Disable the legacy VolumeLockService to prevent conflicts/loops
        try {
            val intent = android.content.Intent(this, VolumeLockService::class.java)
            stopService(intent)
            LogManager.info("Legacy VolumeLockService stopped to avoid conflicts")
        } catch (e: Exception) {
            LogManager.error("Failed to stop legacy service", e)
        }
        
        // Explicitly set flags to ensure key filtering works
        val info = serviceInfo
        // Combine with existing flags if any, or just set what we need
        info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or 
                     android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info
        
        LogManager.info("VolumeAccessibilityService connected and flags set")
        Log.d(TAG, "Service connected with flags: ${info.flags}")
        
        // Visual confirmation for user
        // android.widget.Toast.makeText(this, "Bloqueo Estricto Activo", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, but required to override
    }

    override fun onInterrupt() {
        LogManager.info("VolumeAccessibilityService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Only handle UP and DOWN volume keys
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            
            val maxPercent = preferencesManager.getMaxVolumePercent()
            val maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val allowedLimit = (maxVolumeLevel * (maxPercent / 100.0)).toInt()

            // Aggressive Logic for "Hold to Bypass" issue:
            // If user holds volume up, the system repeats events rapidly. 
            // AudioManager might report a lagged volume (e.g. 4) while system is already processing 5.
            // FIX: If event is repeating (holding) AND we are within 1 step of limit, BLOCK IT.
            
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                var shouldBlock = false

                // [FIX] Preventive Blocking Strategy:
                // Block ANY volume up event if we're at limit-1 or above.
                // This prevents the "first event gets through" race condition.
                if (currentVolume >= allowedLimit - 1) {
                    shouldBlock = true
                    // Active clamp if somehow we're above limit
                    if (currentVolume > allowedLimit) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, allowedLimit, 0)
                        Log.d(TAG, "Active Clamp: Forced volume to $allowedLimit from $currentVolume")
                    }
                }

                if (shouldBlock) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                         Log.d(TAG, "Blocking Volume UP (Preventive). Current: $currentVolume, Limit: $allowedLimit, Repeat: ${event.repeatCount}")
                    }
                    // Prevent the event from propagating
                    return true 
                }
            }
        }
        return super.onKeyEvent(event)
    }
}
