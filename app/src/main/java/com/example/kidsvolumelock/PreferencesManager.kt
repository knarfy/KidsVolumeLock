package com.example.kidsvolumelock

import android.content.Context
import android.preference.PreferenceManager

/**
 * Manages simple persistent data using SharedPreferences.
 */
class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("kids_volume_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_MAX_VOLUME_PERCENT = "max_volume_percent"
        private const val KEY_PIN = "security_pin"
        private const val Key_SERVICE_ENABLED = "service_enabled_flag" // to auto-start if it was running
    }

    fun setMaxVolumePercent(percent: Int) {
        prefs.edit().putInt(KEY_MAX_VOLUME_PERCENT, percent.coerceIn(0, 100)).apply()
    }

    fun getMaxVolumePercent(): Int {
        return prefs.getInt(KEY_MAX_VOLUME_PERCENT, 50) // Default 50%
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun getPin(): String? {
        return prefs.getString(KEY_PIN, null)
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Key_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(Key_SERVICE_ENABLED, false)
    }
}
