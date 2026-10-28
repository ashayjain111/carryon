# One-command local start for Windows PowerShell:  .\start-dev.ps1
# Starts MongoDB (Docker), the API and the website, each in its own window, then opens the browser.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host '1/4 Checking Docker...'
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Docker is not running. Start Docker Desktop, wait until it says "Engine running", then run this script again.' -ForegroundColor Red
    exit 1
}

Write-Host '2/4 Starting MongoDB...'
Push-Location $root
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { Write-Host 'MongoDB failed to start.' -ForegroundColor Red; Pop-Location; exit 1 }
Pop-Location

Write-Host '3/4 Starting the API (a new window opens; first start downloads dependencies)...'
# SEED_DEMO creates demo accounts: traveller@carryon.demo and sender@carryon.demo (password demo12345)
Start-Process powershell -ArgumentList '-NoExit', '-Command', "`$env:SEED_DEMO='true'; Set-Location '$root\backend'; .\mvnw spring-boot:run"

Write-Host '4/4 Starting the website (a new window opens)...'
if (-not (Test-Path "$root\frontend\node_modules")) { Push-Location "$root\frontend"; npm install; Pop-Location }
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$root\frontend'; npm run dev"

Write-Host 'Waiting for the API to come up...'
$up = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $h = Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 2
        if ($h.status -eq 'UP') { $up = $true; break }
    } catch { }
    Start-Sleep 2
}
if ($up) {
    Write-Host 'Ready: http://localhost:5173' -ForegroundColor Green
    Write-Host 'Demo logins: traveller@carryon.demo / sender@carryon.demo  (password demo12345)'
    Write-Host 'Admin login: admin@carryon.local / admin12345'
    Start-Process 'http://localhost:5173'
} else {
    Write-Host 'The API did not start within 3 minutes. Look at the API window for the error.' -ForegroundColor Red
}
