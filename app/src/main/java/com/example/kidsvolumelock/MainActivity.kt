package com.example.kidsvolumelock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kidsvolumelock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager(this)

        setupUI()
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
    }

    private fun saveVolume(percent: Int) {
        prefs.setMaxVolumePercent(percent)
        Toast.makeText(this, "Límite guardado: $percent%", Toast.LENGTH_SHORT).show()
    }

    private fun toggleService(enable: Boolean) {
        prefs.setServiceEnabled(enable)
        val intent = Intent(this, VolumeLockService::class.java)
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
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
}
