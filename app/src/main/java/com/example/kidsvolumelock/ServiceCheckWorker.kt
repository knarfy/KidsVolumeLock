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
                
                // Comprobación adicional: ¿ya está corriendo?
                // Nota: ActivityManager.getRunningServices está deprecated pero sigue siendo útil para servicios propios en este contexto simple
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                         applicationContext.startForegroundService(serviceIntent)
                    } else {
                         applicationContext.startService(serviceIntent)
                    }
                    LogManager.info("ServiceCheckWorker: ✅ Intento de inicio del servicio enviado")
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                        LogManager.warning("ServiceCheckWorker: ⚠️ No se pudo iniciar el servicio (Restricción Android 12+). Se reintentará en la próxima ejecución.")
                    } else {
                        LogManager.error("ServiceCheckWorker: ❌ Error al iniciar el servicio", e)
                        return Result.retry() // Pedir a WorkManager que reintente más tarde
                    }
                }
            } else {
                LogManager.info("ServiceCheckWorker: ⏸️ Servicio deshabilitado en preferencias")
            }
            
            return Result.success()
        } catch (e: Exception) {
            LogManager.error("ServiceCheckWorker: ❌ Error fatal en doWork", e)
            return Result.failure()
        }
    }
}
