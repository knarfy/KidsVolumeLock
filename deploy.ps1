# Script automatizado para build, descarga e instalación de KidsVolumeLock
# Uso: .\deploy.ps1

Write-Host "=== KidsVolumeLock - Deploy Automatizado ===" -ForegroundColor Cyan
Write-Host ""

# Configuración
$repoName = "knarfy/KidsVolumeLock"
$ghPath = ".\bin\gh.exe"
$adbPath = ".\platform-tools\adb.exe"

# 1. Verificar tablet conectada
Write-Host "[1/6] Verificando tablet conectada..." -ForegroundColor Yellow
$devices = & $adbPath devices
if ($devices -match "device$") {
    Write-Host "✓ Tablet detectada" -ForegroundColor Green
} else {
    Write-Host "✗ No se detectó ninguna tablet. Conecta la tablet y permite USB debugging." -ForegroundColor Red
    exit 1
}

# 2. Commit y push (si hay cambios)
Write-Host ""
Write-Host "[2/6] Verificando cambios en git..." -ForegroundColor Yellow
$status = git status --porcelain
if ($status) {
    Write-Host "Hay cambios pendientes. Haciendo commit..." -ForegroundColor Cyan
    git add .
    $commitMsg = Read-Host "Mensaje del commit (Enter para usar 'Update app')"
    if ([string]::IsNullOrWhiteSpace($commitMsg)) {
        $commitMsg = "Update app"
    }
    git commit -m $commitMsg
    Write-Host "Pushing a GitHub..." -ForegroundColor Cyan
    git push origin master
    Write-Host "✓ Push completado" -ForegroundColor Green
} else {
    Write-Host "✓ No hay cambios pendientes" -ForegroundColor Green
}

# 3. Esperar y verificar build
Write-Host ""
Write-Host "[3/6] Esperando build de GitHub Actions..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

$maxAttempts = 30
$attempt = 0
$buildComplete = $false

while ($attempt -lt $maxAttempts -and -not $buildComplete) {
    $attempt++
    Write-Host "  Intento $attempt/$maxAttempts..." -ForegroundColor Gray
    
    $runList = & $ghPath run list --repo $repoName --limit 1 2>&1
    if ($runList -match "✓") {
        $buildComplete = $true
        Write-Host "✓ Build completado exitosamente" -ForegroundColor Green
    } elseif ($runList -match "X") {
        Write-Host "✗ Build falló. Revisa GitHub Actions." -ForegroundColor Red
        exit 1
    } else {
        Start-Sleep -Seconds 10
    }
}

if (-not $buildComplete) {
    Write-Host "✗ Timeout esperando el build" -ForegroundColor Red
    exit 1
}

# 4. Obtener ID del run y descargar APK
Write-Host ""
Write-Host "[4/6] Descargando APK..." -ForegroundColor Yellow

# Seleccionar el run más reciente automáticamente
$runInfo = & $ghPath run list --repo $repoName --limit 1 --json databaseId,conclusion 2>&1 | ConvertFrom-Json
$runId = $runInfo[0].databaseId

Write-Host "  Run ID: $runId" -ForegroundColor Gray

# Limpiar y descargar
Remove-Item -Path .\build_output -Recurse -Force -ErrorAction SilentlyContinue
& $ghPath run download $runId --repo $repoName --dir .\build_output

if (Test-Path ".\build_output\app-debug\app-debug.apk") {
    Copy-Item -Path .\build_output\app-debug\app-debug.apk -Destination .\app-debug-latest.apk -Force
    $apkSize = (Get-Item .\app-debug-latest.apk).Length / 1MB
    Write-Host "✓ APK descargada ($([math]::Round($apkSize, 2)) MB)" -ForegroundColor Green
} else {
    Write-Host "✗ No se encontró la APK en los artefactos" -ForegroundColor Red
    exit 1
}

# 5. Desinstalar versión anterior
Write-Host ""
Write-Host "[5/6] Desinstalando versión anterior..." -ForegroundColor Yellow
& $adbPath uninstall com.example.kidsvolumelock 2>&1 | Out-Null
Write-Host "✓ Versión anterior desinstalada (si existía)" -ForegroundColor Green

# 6. Instalar nueva APK
Write-Host ""
Write-Host "[6/6] Instalando nueva APK en la tablet..." -ForegroundColor Yellow
$installResult = & $adbPath install .\app-debug-latest.apk 2>&1
if ($installResult -match "Success") {
    Write-Host "✓ APK instalada exitosamente" -ForegroundColor Green
    Write-Host ""
    Write-Host "=== Deploy Completado ===" -ForegroundColor Cyan
    Write-Host "La app está lista para usar en la tablet." -ForegroundColor Green
} else {
    Write-Host "✗ Error al instalar: $installResult" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "APK guardada en: .\app-debug-latest.apk" -ForegroundColor Gray
