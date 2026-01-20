# KidsVolumeLock - Scripts de Automatización

Este directorio contiene scripts de PowerShell para automatizar el proceso de desarrollo e instalación.

## Scripts Disponibles

### 🚀 `deploy.ps1` - Deploy Completo Automatizado

**Ejecuta el ciclo completo:**
1. Verifica cambios en git
2. Hace commit y push a GitHub
3. Espera a que GitHub Actions compile
4. Descarga la APK generada
5. Desinstala versión anterior de la tablet
6. Instala la nueva versión

**Requisitos:**
- Tablet conectada por USB con USB debugging habilitado
- Git configurado
- `gh` CLI configurado (en `.\bin\gh.exe`)
- ADB disponible (en `.\platform-tools\adb.exe`)

**Uso:**
```powershell
.\deploy.ps1
```

El script te pedirá un mensaje de commit si hay cambios pendientes.

---

### ⚡ `install.ps1` - Instalación Rápida

**Solo instala la APK ya generada** en la tablet conectada.

**Uso:**
```powershell
.\install.ps1
```

Útil cuando ya tienes la APK descargada y solo quieres instalarla.

---

## Preparación de la Tablet

Para que los scripts funcionen, la tablet debe tener **USB Debugging** habilitado:

1. Ve a **Ajustes** → **Acerca del dispositivo**
2. Toca 7 veces en **Número de compilación** para activar opciones de desarrollador
3. Ve a **Ajustes** → **Opciones de desarrollador**
4. Activa **Depuración USB**
5. Conecta la tablet al PC
6. Acepta el diálogo de autorización en la tablet

## Verificar Conexión

Para verificar que la tablet está conectada:

```powershell
.\platform-tools\adb.exe devices
```

Deberías ver algo como:
```
List of devices attached
ABC123456789    device
```

## Problemas Comunes

### "No se detectó tablet"
- Asegúrate de que USB debugging está habilitado
- Verifica que el cable USB funcione para datos (no solo carga)
- Prueba con otro puerto USB
- Revoca las autorizaciones USB en la tablet y vuelve a conectar

### "Error al instalar"
- Puede que la app esté en uso, ciérrala primero
- Verifica que hay suficiente espacio en la tablet
- Intenta desinstalar manualmente desde la tablet primero

### Build falla en GitHub Actions
- Revisa los logs en: https://github.com/knarfy/KidsVolumeLock/actions
- Verifica que no haya errores de compilación

## Workflow Manual Alternativo

Si prefieres hacerlo manualmente:

```powershell
# 1. Commit y push
git add .
git commit -m "Tu mensaje"
git push origin master

# 2. Esperar build (3-5 min)

# 3. Descargar APK
.\bin\gh.exe run download <RUN_ID> --repo knarfy/KidsVolumeLock --dir .\build_output
Copy-Item .\build_output\app-debug\app-debug.apk -Destination .\app-debug-latest.apk

# 4. Instalar
.\platform-tools\adb.exe install -r .\app-debug-latest.apk
```
