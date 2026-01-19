package com.example.kidsvolumelock

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
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
                   val oldProgress = prefs.getMaxVolumePercent()
                   
                   // Callback to execute if PIN is correct or not needed
                   // We need to re-apply the new progress visually + save it
                   val onAuthSuccess = {
                       it.progress = newProgress
                       saveVolume(newProgress)
                       binding.tvMaxVolValue.text = getString(R.string.text_current_limit, newProgress)
                   }

                   val neededAuth = checkPinIfNeeded(onAuthSuccess)
                   
                   if (!neededAuth) {
                       // Dialog shown. Revert visual to old value temporarily
                       it.progress = oldProgress
                       binding.tvMaxVolValue.text = getString(R.string.text_current_limit, oldProgress)
                   }
                   // If neededAuth is true (no PIN), onAuthSuccess was already called inside checkPinIfNeeded
                }
            }
        })

        // Buttons
        binding.btnToggleService.setOnClickListener {
            val isEnabled = prefs.isServiceEnabled()
            if (isEnabled) {
                // To Stop, require PIN
                checkPinIfNeeded {
                    toggleService(false)
                }
            } else {
                // To Start, no PIN needed
                toggleService(true)
            }
        }

        binding.btnSetPin.setOnClickListener {
            showSetPinDialog()
        }
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

    /**
     * Checks PIN if one is set. If verified, executes action.
     * Returns true if immediate execution occurred (no pin), false if async dialog shows.
     */
    private fun checkPinIfNeeded(onSuccess: () -> Unit): Boolean {
        val storedPin = prefs.getPin()
        if (storedPin.isNullOrEmpty()) {
            onSuccess()
            return true
        }

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = getString(R.string.pin_hint)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.enter_pin_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                if (input.text.toString() == storedPin) {
                    onSuccess()
                } else {
                    Toast.makeText(this, getString(R.string.error_pin_incorrect), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
        
        return false // Auth pending
    }

    private fun showSetPinDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = getString(R.string.pin_hint)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_pin_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                val newPin = input.text.toString()
                if (newPin.isNotEmpty()) {
                    prefs.setPin(newPin)
                    Toast.makeText(this, getString(R.string.msg_pin_set), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
