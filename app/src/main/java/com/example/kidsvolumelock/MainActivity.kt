package com.example.kidsvolumelock

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
        
        // Auto-restart service if it was enabled before app was closed
        if (prefs.isServiceEnabled()) {
            startVolumeService()
            scheduleServiceCheckWorker()
        }
        
        refreshServiceStatus()
    }

    private fun startVolumeService() {
        val serviceIntent = Intent(this, VolumeLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        LogManager.info("MainActivity: Starting VolumeLockService")
    }

    private fun scheduleServiceCheckWorker() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Run even if low battery
                .build()

            val workRequest = androidx.work.PeriodicWorkRequest.Builder(
                ServiceCheckWorker::class.java,
                15, java.util.concurrent.TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "VolumeLockWatchdog",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            LogManager.info("MainActivity: Scheduled ServiceCheckWorker (Watchdog)")
        } catch (e: Exception) {
            LogManager.error("MainActivity: Failed to schedule worker", e)
        }
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
        if (enable) {
            LogManager.info("User starting volume lock service")
            startVolumeService()
            scheduleServiceCheckWorker()
        } else {
            LogManager.info("User stopping volume lock service")
            val intent = Intent(this, VolumeLockService::class.java)
            stopService(intent)
            // Cancel watchdog when user manually stops service
            androidx.work.WorkManager.getInstance(this).cancelUniqueWork("VolumeLockWatchdog")
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
    }
}
