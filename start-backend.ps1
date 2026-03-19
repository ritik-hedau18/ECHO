# Helper script to launch all ECHO backend microservices

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "      ECHO - Starting Services" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Start Docker Containers
Write-Host "1. Starting backing Docker containers (Postgres, MongoDB, Redis, Kafka, Zipkin)..." -ForegroundColor Yellow
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to start docker containers. Make sure Docker Desktop is running!" -ForegroundColor Red
    Exit
}

# 2. Build the Maven parent package
Write-Host "2. Building Maven projects..." -ForegroundColor Yellow
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
    Exit
}

# 3. Launch discovery-server first and let it initialize
Write-Host "3. Launching Service Discovery Server..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl discovery-server spring-boot:run" -Title "ECHO - Discovery Server"
Write-Host "Waiting 12 seconds for Discovery Server to bootstrap..." -ForegroundColor Yellow
Start-Sleep -Seconds 12

# 4. Launch all other services
Write-Host "4. Launching other services..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl api-gateway spring-boot:run" -Title "ECHO - API Gateway"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl user-service spring-boot:run" -Title "ECHO - User Service"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl chat-service spring-boot:run" -Title "ECHO - Chat Service"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl message-store-service spring-boot:run" -Title "ECHO - Message Store Service"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl notification-service spring-boot:run" -Title "ECHO - Notification Service"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot'; mvn -pl audit-log-service spring-boot:run" -Title "ECHO - Audit Log Service"

Write-Host "=========================================" -ForegroundColor Green
Write-Host " All services started! Check new shell windows." -ForegroundColor Green
Write-Host " Next, open a new shell and start the frontend:" -ForegroundColor Green
Write-Host " cd frontend; npm run dev" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
