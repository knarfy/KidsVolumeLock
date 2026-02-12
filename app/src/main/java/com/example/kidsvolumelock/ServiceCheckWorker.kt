package com.example.kidsvolumelock

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.core.content.ContextCompat

class ServiceCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        LogManager.init(applicationContext)
        LogManager.info("ServiceCheckWorker: 🕵️ Ejecutando comprobación periódica...")

        try {
            val prefs = PreferencesManager(applicationContext)
            
            // Solo intentamos arrancar si el servicio está habilitado
            if (prefs.isServiceEnabled()) {
                val serviceIntent = Intent(applicationContext, VolumeLockService::class.java)
                
                // Usamos un try-catch específico para Android 12+ (S)
                try {
                    // ContextCompat maneja la compatibilidad de versiones, pero no la excepción específica de S
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                         applicationContext.startForegroundService(serviceIntent)
                    } else {
                         applicationContext.startService(serviceIntent)
                    }
                    LogManager.info("ServiceCheckWorker: ✅ Intento de inicio del servicio enviado")
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                        LogManager.warning("ServiceCheckWorker: ⚠️ No se pudo iniciar el servicio desde el worker (Restricción Android 12+)")
                    } else {
                        LogManager.error("ServiceCheckWorker: ❌ Error al iniciar el servicio", e)
                        return Result.failure()
                    }
                }
            } else {
                LogManager.info("ServiceCheckWorker: ⏸️ Servicio deshabilitado en preferencias, no se requiere acción")
            }
            
            return Result.success()
        } catch (e: Exception) {
            LogManager.error("ServiceCheckWorker: ❌ Error fatal en doWork", e)
            return Result.failure()
        }
    }
}
