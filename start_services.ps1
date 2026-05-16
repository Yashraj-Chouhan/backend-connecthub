$services = @(
    "eureka-server",
    "auth-service",
    "room-service",
    "message-service",
    "notification-service",
    "presence-service",
    "translation-service",
    "websocket-service",
    "payment-service",
    "gateway-service"
)

$servicePorts = @{
    "eureka-server" = 9000
    "auth-service" = 9002
    "room-service" = 9003
    "message-service" = 9004
    "notification-service" = 9007
    "presence-service" = 9012
    "translation-service" = 9013
    "websocket-service" = 9014
    "payment-service" = 9015
    "gateway-service" = 8080
}

$coreServiceApps = @(
    "AUTH-SERVICE",
    "ROOM-SERVICE",
    "MESSAGE-SERVICE",
    "NOTIFICATION-SERVICE",
    "PRESENCE-SERVICE",
    "WEBSOCKET-SERVICE"
)

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $backendRoot
$frontendRoot = Join-Path $projectRoot "frontend"

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        $connected = $async.AsyncWaitHandle.WaitOne(1000, $false)
        if ($connected -and $client.Connected) {
            $client.EndConnect($async) | Out-Null
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

function Wait-ForTcpPort {
    param(
        [string]$HostName,
        [int]$Port,
        [int]$TimeoutSeconds = 90,
        [string]$Label = "${HostName}:$Port"
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -HostName $HostName -Port $Port) {
            Write-Host "$Label is accepting TCP connections."
            return $true
        }
        Start-Sleep -Seconds 2
    }

    Write-Warning "$Label did not open within $TimeoutSeconds seconds."
    return $false
}

function Wait-ForHttpSuccess {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 90,
        [string]$Label = $Url,
        [hashtable]$Headers = @{}
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -Headers $Headers -TimeoutSec 5 -ErrorAction Stop | Out-Null
            Write-Host "$Label is responding over HTTP."
            return $true
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    Write-Warning "$Label did not return a successful HTTP response within $TimeoutSeconds seconds."
    return $false
}

function Get-RegisteredEurekaApps {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:9000/eureka/apps" -Headers @{ Accept = "application/json" } -TimeoutSec 5 -ErrorAction Stop
        $applications = $response.applications.application
        if ($null -eq $applications) {
            return @()
        }
        if ($applications -is [System.Array]) {
            return @($applications | ForEach-Object { [string]$_.name })
        }
        return @([string]$applications.name)
    } catch {
        return @()
    }
}

function Wait-ForEurekaApps {
    param(
        [string[]]$ExpectedApps,
        [int]$TimeoutSeconds = 180,
        [string]$Label = "services"
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $registered = @(Get-RegisteredEurekaApps)
        $missing = @($ExpectedApps | Where-Object { $registered -notcontains $_ })
        if ($missing.Count -eq 0) {
            Write-Host "Eureka has registered the expected ${Label}: $($ExpectedApps -join ', ')"
            return $true
        }

        Write-Host "Waiting for Eureka to register ${Label}: $($missing -join ', ')"
        Start-Sleep -Seconds 3
    }

    Write-Warning "Timed out waiting for Eureka to register $Label."
    return $false
}

function Start-InfraIfAvailable {
    $dockerCli = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCli) {
        Write-Host "Docker CLI not found. Continuing with local fallbacks."
        return
    }

    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Docker daemon is not running. Continuing with local fallbacks."
        return
    }

    Write-Host "Starting Kafka, Redis, and MySQL containers..."
    docker compose up -d zookeeper kafka redis mysql | Out-Host
}

function Start-ServiceProcess {
    param([string]$ServiceName)

    Write-Host "Booting $ServiceName..."

    $envLines = @(
        '$env:SPRING_CLOUD_COMPATIBILITY_VERIFIER_ENABLED=''false''',
        '$env:MAVEN_OPTS=''-Dspring-boot.run.fork=false''',
        '$env:EUREKA_CLIENT_REGISTRY_FETCH_INTERVAL_SECONDS=''5''',
        '$env:EUREKA_CLIENT_INITIAL_INSTANCE_INFO_REPLICATION_INTERVAL_SECONDS=''5''',
        '$env:EUREKA_CLIENT_INSTANCE_INFO_REPLICATION_INTERVAL_SECONDS=''5'''
    )

    if ($ServiceName -in @("auth-service", "message-service", "websocket-service") -and -not $kafkaAvailable) {
        $envLines += '$env:SPRING_KAFKA_LISTENER_AUTO_STARTUP=''false'''
    }

    if ($ServiceName -eq "presence-service" -and -not $redisAvailable) {
        Write-Host "Presence service will start without Redis connectivity until Redis becomes available."
    }

    $joinedEnv = [string]::Join("; ", $envLines)
    $serviceDir = Join-Path $backendRoot $ServiceName
    $cmd = "-NoExit -Command `"Set-Location '$serviceDir'; title $ServiceName; $joinedEnv; .\mvnw.cmd --% -Dmaven.test.skip=true spring-boot:run`""
    Start-Process powershell -ArgumentList $cmd
}

Write-Host "Stopping existing ConnectHub processes on known service ports to prevent port conflicts..."
$portsToReset = 5173, 8080, 9000, 9001, 9002, 9003, 9004, 9007, 9012, 9013, 9014, 9015
foreach ($port in $portsToReset) {
    try {
        Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique |
            ForEach-Object {
                if ($_ -and $_ -gt 0) {
                    Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
                }
            }
    } catch {
        # Nothing is listening on this port yet.
    }
}

Start-InfraIfAvailable

$kafkaAvailable = Test-TcpPort -HostName "127.0.0.1" -Port 9092
$redisAvailable = Test-TcpPort -HostName "127.0.0.1" -Port 6379
$mysqlAvailable = Test-TcpPort -HostName "127.0.0.1" -Port 3306

Write-Host "Infrastructure status: Kafka=$kafkaAvailable Redis=$redisAvailable MySQL=$mysqlAvailable"
Write-Host "Starting ConnectHub microservices..."

Start-ServiceProcess -ServiceName "eureka-server"

if (-not (Wait-ForTcpPort -HostName "127.0.0.1" -Port $servicePorts["eureka-server"] -TimeoutSeconds 120 -Label "Eureka server")) {
    throw "Eureka server did not start on port $($servicePorts["eureka-server"])."
}

if (-not (Wait-ForHttpSuccess -Url "http://127.0.0.1:9000/eureka/apps" -Headers @{ Accept = "application/json" } -TimeoutSeconds 120 -Label "Eureka registry")) {
    throw "Eureka registry endpoint did not become ready in time."
}

$backendServices = $services | Where-Object { $_ -notin @("eureka-server", "gateway-service") }
foreach ($svc in $backendServices) {
    Start-ServiceProcess -ServiceName $svc
    Start-Sleep -Seconds 3
}

if (-not (Wait-ForEurekaApps -ExpectedApps $coreServiceApps -TimeoutSeconds 180 -Label "core services")) {
    Write-Warning "Starting gateway even though some core services are still missing from Eureka."
}

Start-ServiceProcess -ServiceName "gateway-service"

if (-not (Wait-ForTcpPort -HostName "127.0.0.1" -Port $servicePorts["gateway-service"] -TimeoutSeconds 120 -Label "Gateway service")) {
    throw "Gateway service did not start on port $($servicePorts["gateway-service"])."
}

if (-not (Wait-ForHttpSuccess -Url "http://127.0.0.1:8080/actuator/health" -TimeoutSeconds 120 -Label "Gateway health endpoint")) {
    Write-Warning "Gateway health endpoint did not become ready in time. Continuing to start the frontend."
}

if (Test-Path $frontendRoot) {
    Write-Host "Starting frontend dev server..."
    $frontendCmd = "-NoExit -Command `"Set-Location '$frontendRoot'; title connecthub-frontend; npm run dev`""
    Start-Process powershell -ArgumentList $frontendCmd
} else {
    Write-Host "Frontend workspace not found at $frontendRoot"
}

Write-Host "ConnectHub startup sequence launched. The gateway now waits for Eureka registration so the frontend should stop hitting transient service-unavailable responses on first load."
