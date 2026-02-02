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
        LogManager.info("VolumeAccessibilityService connected")
        Log.d(TAG, "Service connected")
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
            
            // We only care about the ACTION_DOWN event (when key is pressed)
            // ACTION_UP will follow, we generally want to block the whole sequence if needed, 
            // but blocking DOWN is usually enough to prevent the action.
            // However, to be safe and consistent, we check both or just DOWN.
            
            val maxPercent = preferencesManager.getMaxVolumePercent()
            val maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val allowedLimit = (maxVolumeLevel * (maxPercent / 100.0)).toInt()

            Log.d(TAG, "KeyEvent: ${event.keyCode} Action: ${event.action} Vol: $currentVolume Limit: $allowedLimit")

            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                // If we are already at or above limit, BLOCK the volume up key
                if (currentVolume >= allowedLimit) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        LogManager.info("Blocking Volume UP. Current: $currentVolume, Limit: $allowedLimit")
                    }
                    return true // Consume event (Prohibit default action)
                }
            }
            // Optional: Block volume down? No, usually we want to allow lowering volume.
             
             // Extra safety: If somehow volume is strictly above limit (e.g. changed by other app), 
             // and they press ANY volume key, we might want to force it down?
             // But the service logic should handle that. The key interception is specifically to stop the "Up".
        }
        return super.onKeyEvent(event)
    }
}
