package com.example.kidsvolumelock

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.kidsvolumelock.databinding.ActivityLogViewerBinding

class LogViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadLogs()
    }

    private fun setupUI() {
        binding.btnRefreshLogs.setOnClickListener {
            loadLogs()
            Toast.makeText(this, getString(R.string.logs_refreshed), Toast.LENGTH_SHORT).show()
        }

        binding.btnClearLogs.setOnClickListener {
            showClearLogsConfirmation()
        }

        binding.btnShareLogs.setOnClickListener {
            shareLogs()
        }
    }

    private fun loadLogs() {
        val logs = LogManager.readLogs()
        binding.tvLogContent.text = logs
        
        // Scroll to bottom to show latest logs
        binding.scrollViewLogs.post {
            binding.scrollViewLogs.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun showClearLogsConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_clear_logs_title))
            .setMessage(getString(R.string.confirm_clear_logs_message))
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                LogManager.clearLogs()
                loadLogs()
                Toast.makeText(this, getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun shareLogs() {
        val logs = LogManager.readLogs()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_logs_subject))
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_logs_title)))
    }
}
