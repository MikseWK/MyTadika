# MyTadika — One-command dev launcher
# Usage: .\start.ps1

$root = $PSScriptRoot

Write-Host "Starting MyTadika..." -ForegroundColor Cyan

# Backend — Spring Boot on port 8080 with local profile (loads application-local.properties).
# It also serves the frontend directly (Maven copies ../frontend into the classpath's static/
# folder at build time) — there is no separate frontend server/port.
$backendCmd = "Write-Host 'MyTadika Backend + Frontend (Spring Boot :8080)' -ForegroundColor Green; Set-Location '$root\backend'; `$env:SPRING_PROFILES_ACTIVE='local'; .\mvnw spring-boot:run"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $backendCmd)

# Short pause so backend window opens first
Start-Sleep -Seconds 2

# AI microservice — FastAPI on port 8001 (health advice predictions)
$aiPython = "C:\Users\Wai Kit Liew\AppData\Local\Programs\Python\Python312\python.exe"
$aiCmd = "Write-Host 'MyTadika AI Service (FastAPI :8001)' -ForegroundColor Magenta; Set-Location '$root\AI\api'; & '$aiPython' main.py"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $aiCmd)

Write-Host ""
Write-Host "Two windows launched:" -ForegroundColor Cyan
Write-Host "  Backend + Frontend -> http://localhost:8080/login.html" -ForegroundColor Green
Write-Host "  AI                 -> http://localhost:8001/health" -ForegroundColor Magenta
Write-Host ""
Write-Host "To stop: close the two terminal windows." -ForegroundColor Gray
