# Script para lanzar los microservicios de VoicePay
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"

Write-Host "Iniciando microservicios..." -ForegroundColor Cyan

# Iniciar User Service
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; ./mvnw spring-boot:run -pl user-service" -WindowStyle Normal
Write-Host "-> User Service iniciado (Puerto 8080)" -ForegroundColor Green

# Iniciar Payment Service
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; ./mvnw spring-boot:run -pl payment-service" -WindowStyle Normal
Write-Host "-> Payment Service iniciado (Puerto 8081)" -ForegroundColor Green

# Iniciar IVR Service
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; ./mvnw spring-boot:run -pl ivr-service" -WindowStyle Normal
Write-Host "-> IVR Service iniciado (Puerto 8082)" -ForegroundColor Green

# Iniciar Gateway Service
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; ./mvnw spring-boot:run -pl gateway-service" -WindowStyle Normal
Write-Host "-> Gateway Service iniciado (Puerto 9000)" -ForegroundColor Green

Write-Host "`n¡Todos los servicios están arrancando en ventanas separadas!" -ForegroundColor Yellow
