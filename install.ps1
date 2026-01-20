# Script rápido de instalación (asume que la APK ya está construida)
# Uso: .\install.ps1

$adbPath = ".\platform-tools\adb.exe"

Write-Host "=== Instalación Rápida ===" -ForegroundColor Cyan

# Verificar tablet
$devices = & $adbPath devices
if (-not ($devices -match "device$")) {
    Write-Host "✗ No se detectó tablet. Conéctala y permite USB debugging." -ForegroundColor Red
    exit 1
}

# Desinstalar e instalar
Write-Host "Desinstalando versión anterior..." -ForegroundColor Yellow
& $adbPath uninstall com.example.kidsvolumelock 2>&1 | Out-Null

Write-Host "Instalando APK..." -ForegroundColor Yellow
$result = & $adbPath install .\app-debug-with-logs.apk 2>&1

if ($result -match "Success") {
    Write-Host "✓ Instalada exitosamente" -ForegroundColor Green
} else {
    Write-Host "✗ Error: $result" -ForegroundColor Red
}
