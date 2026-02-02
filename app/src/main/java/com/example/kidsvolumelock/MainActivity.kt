package com.example.kidsvolumelock

import android.accessibilityservice.AccessibilityService

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import android.provider.Settings
import android.text.TextUtils
import android.content.ComponentName
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import com.example.kidsvolumelock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager(this)
        
        // Initialize logging system
        LogManager.init(this)
        LogManager.info("MainActivity onCreate")

        setupUI()
        
        // Auto-restart service if it was enabled before app was closed, BUT only if Accessibility is NOT active
        if (prefs.isServiceEnabled() && !isAccessibilityServiceEnabled(VolumeAccessibilityService::class.java)) {
            val serviceIntent = Intent(this, VolumeLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            LogManager.info("Auto-restarted VolumeLockService on app launch")
        } else {
             // Ensure legacy service is stopped if we are in Accessibility mode
             stopService(Intent(this, VolumeLockService::class.java))
             prefs.setServiceEnabled(false)
        }
        
        refreshServiceStatus()
    }

    private fun setupUI() {
        // Seekbar
        val currentMax = prefs.getMaxVolumePercent()
        binding.seekBarMaxVolume.progress = currentMax
        binding.tvMaxVolValue.text = getString(R.string.text_current_limit, currentMax)

        binding.seekBarMaxVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvMaxVolValue.text = getString(R.string.text_current_limit, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                   val newProgress = it.progress
                   saveVolume(newProgress)
                   binding.tvMaxVolValue.text = getString(R.string.text_current_limit, newProgress)
                }
            }
        })

        // Buttons
        binding.btnToggleService.setOnClickListener {
            val isEnabled = prefs.isServiceEnabled()
            toggleService(!isEnabled)
        }

        // Hide PIN button completely
        binding.btnSetPin.visibility = View.GONE
        binding.btnSetPin.setOnClickListener(null)
        
        // View Logs button
        binding.btnViewLogs.setOnClickListener {
            LogManager.info("User opened log viewer")
            val intent = Intent(this, LogViewerActivity::class.java)
            startActivity(intent)
        }

        // Check Accessibility Service
        checkAccessibilityPermission()

        // Ask for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= 33) { // Android 13
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                LogManager.info("Requesting POST_NOTIFICATIONS permission")
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun saveVolume(percent: Int) {
        prefs.setMaxVolumePercent(percent)
        LogManager.info("User changed volume limit to $percent%")
        Toast.makeText(this, "Límite guardado: $percent%", Toast.LENGTH_SHORT).show()
    }

    private fun toggleService(enable: Boolean) {
        prefs.setServiceEnabled(enable)
        val intent = Intent(this, VolumeLockService::class.java)
        if (enable) {
            LogManager.info("User starting volume lock service")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            LogManager.info("User stopping volume lock service")
            stopService(intent)
        }
        refreshServiceStatus()
    }

    private fun refreshServiceStatus() {
        if (prefs.isServiceEnabled()) {
            binding.tvServiceStatus.text = getString(R.string.status_running)
            binding.btnToggleService.text = getString(R.string.btn_stop_service)
        } else {
            binding.tvServiceStatus.text = getString(R.string.status_stopped)
            binding.btnToggleService.text = getString(R.string.btn_start_service)
        }
    }
    
    override fun onResume() {
        super.onResume()
        LogManager.info("MainActivity onResume")
        refreshServiceStatus()
        checkAccessibilityPermission()
    }

    private fun checkAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled(VolumeAccessibilityService::class.java)) {
            // Show a toast or update UI to warn user
             Toast.makeText(this, "¡Activa Accesibilidad para Bloqueo Estricto!", Toast.LENGTH_LONG).show()
             // Optionally valid to open settings directly or show a button
             // For now, let's open settings if it's the first run or make it easy
             // But to avoid annoyance, maybe just a button in the UI? 
             // Let's add a button logic or re-purpose the setPin button or add a new one?
             // Since SetPin is hidden, let's just use a Toast for now and maybe open settings if user clicks something?
             // Actually, let's auto-open settings if it's critical, or ask via dialog.
             // Given user request simplicity, let's just toast and maybe add a logic.
             // Better: binding.btnSetPin could be "Enable Strict Mode"
             binding.btnSetPin.visibility = View.VISIBLE
             binding.btnSetPin.text = "Activar Bloqueo Total"
             binding.btnSetPin.setOnClickListener {
                 val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                 startActivity(intent)
             }
        } else {
             binding.btnSetPin.visibility = View.GONE
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(this, service)
        val enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName)
                return true
        }
        return false
    }
}
