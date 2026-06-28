# MyTadika — One-command dev launcher
# Usage: .\start.ps1

$root = $PSScriptRoot

Write-Host "Starting MyTadika..." -ForegroundColor Cyan

# Backend — Spring Boot on port 8080 with local profile (loads application-local.properties)
$backendCmd = "Write-Host 'MyTadika Backend (Spring Boot :8080)' -ForegroundColor Green; Set-Location '$root\mytadika-backend'; `$env:SPRING_PROFILES_ACTIVE='local'; .\mvnw spring-boot:run"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $backendCmd)

# Short pause so backend window opens first
Start-Sleep -Seconds 2

# Frontend — static file server on port 3000
$frontendCmd = "Write-Host 'MyTadika Frontend (static :3000)' -ForegroundColor Yellow; Set-Location '$root\mytadika-frontend'; npx serve . --listen 3000 --no-clipboard"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $frontendCmd)

Write-Host ""
Write-Host "Two windows launched:" -ForegroundColor Cyan
Write-Host "  Backend  -> http://localhost:8080" -ForegroundColor Green
Write-Host "  Frontend -> http://localhost:3000/pages/login.html" -ForegroundColor Yellow
Write-Host ""
Write-Host "To stop: close the two terminal windows." -ForegroundColor Gray
