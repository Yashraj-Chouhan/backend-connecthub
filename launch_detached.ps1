$services = @("eureka-server", "gateway-service", "auth-service", "room-service", "message-service", "notification-service", "presence-service", "translation-service", "websocket-service", "payment-service")

Write-Host "Terminating existing Java instances..."
Stop-Process -Name java -Force -ErrorAction SilentlyContinue

if (!(Test-Path -Path logs)) {
    New-Item -ItemType Directory -Force -Path logs
}

foreach ($svc in $services) {
    Write-Host "Starting $svc..."
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd $svc && .\mvnw.cmd spring-boot:run > ..\logs\$svc.log 2>&1" -WindowStyle Hidden
    if ($svc -eq "eureka-server") { Start-Sleep -Seconds 15 }
    else { Start-Sleep -Seconds 10 }
}
Write-Host "Started all services silently."
