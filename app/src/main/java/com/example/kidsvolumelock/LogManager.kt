package com.example.kidsvolumelock

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages persistent logging to a file for debugging purposes.
 * Thread-safe and includes automatic log rotation.
 */
object LogManager {
    private const val LOG_FILE_NAME = "kidsvolumelock.log"
    private const val MAX_LOG_SIZE_BYTES = 500 * 1024 // 500KB
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    @Volatile
    private var context: Context? = null
    
    fun init(ctx: Context) {
        context = ctx.applicationContext
        logSystemInfo()
    }
    
    private fun getLogFile(): File? {
        return context?.let { File(it.filesDir, LOG_FILE_NAME) }
    }
    
    /**
     * Log system information at startup
     */
    private fun logSystemInfo() {
        info("=== SYSTEM INFO ===")
        info("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        info("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        info("App Started")
        info("===================")
    }
    
    /**
     * Write a log entry with INFO level
     */
    @Synchronized
    fun info(message: String) {
        writeLog("INFO", message)
    }
    
    /**
     * Write a log entry with WARNING level
     */
    @Synchronized
    fun warning(message: String) {
        writeLog("WARNING", message)
    }
    
    /**
     * Write a log entry with ERROR level
     */
    @Synchronized
    fun error(message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        writeLog("ERROR", fullMessage)
    }
    
    /**
     * Internal method to write to log file
     */
    private fun writeLog(level: String, message: String) {
        try {
            val logFile = getLogFile() ?: return
            
            // Check file size and rotate if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                rotateLog(logFile)
            }
            
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] [$level] $message\n"
            
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            // Can't log errors in the logger, so print to logcat
            android.util.Log.e("LogManager", "Failed to write log", e)
        }
    }
    
    /**
     * Rotate log file when it gets too large
     */
    private fun rotateLog(logFile: File) {
        try {
            val backupFile = File(logFile.parent, "$LOG_FILE_NAME.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            // If rotation fails, just delete the file
            logFile.delete()
        }
    }
    
    /**
     * Read all logs from file
     */
    fun readLogs(): String {
        return try {
            val logFile = getLogFile()
            if (logFile?.exists() == true) {
                logFile.readText()
            } else {
                "No logs available yet."
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }
    
    /**
     * Clear all logs
     */
    @Synchronized
    fun clearLogs() {
        try {
            getLogFile()?.delete()
            info("=== LOGS CLEARED ===")
        } catch (e: Exception) {
            android.util.Log.e("LogManager", "Failed to clear logs", e)
        }
    }
}
