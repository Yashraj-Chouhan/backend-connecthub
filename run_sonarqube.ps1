param(
    [string]$Token,
    [string]$SonarHostUrl = "http://localhost:9030",
    [int]$StartupTimeoutSeconds = 300,
    [switch]$NoClean,
    [switch]$StartOnly,
    [ValidateSet("aggregate", "services", "both")]
    [string]$Mode = "aggregate"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptRoot

function Get-LocalTokenCachePath {
    $localAppData = [Environment]::GetFolderPath("LocalApplicationData")
    return Join-Path $localAppData "ConnectHub\sonar-token.txt"
}

function Get-WorkspaceTokenCachePath {
    return Join-Path $scriptRoot ".sonar-token.local"
}

function Get-BasicAuthHeader {
    param(
        [string]$Username,
        [string]$Password
    )

    $raw = "{0}:{1}" -f $Username, $Password
    $bytes = [System.Text.Encoding]::ASCII.GetBytes($raw)
    return @{
        Authorization = "Basic " + [Convert]::ToBase64String($bytes)
    }
}

function Get-TokenAuthHeader {
    param([string]$ResolvedToken)

    $raw = "{0}:" -f $ResolvedToken
    $bytes = [System.Text.Encoding]::ASCII.GetBytes($raw)
    return @{
        Authorization = "Basic " + [Convert]::ToBase64String($bytes)
    }
}

function Wait-ForSonarQube {
    param(
        [string]$HostUrl,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastStatus = $null

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -UseBasicParsing -Uri "$HostUrl/api/system/status" -TimeoutSec 10
            if ($response.status -ne $lastStatus) {
                Write-Host "SonarQube status: $($response.status)"
                $lastStatus = $response.status
            }
            if ($response.status -eq "UP") {
                return
            }
        } catch {
            if ($lastStatus -ne "STARTING") {
                Write-Host "Waiting for SonarQube to become reachable..."
                $lastStatus = "STARTING"
            }
        }

        Start-Sleep -Seconds 5
    }

    throw "SonarQube did not reach UP within $TimeoutSeconds seconds."
}

function Resolve-SonarToken {
    param(
        [string]$ProvidedToken,
        [string]$HostUrl
    )

    if ($ProvidedToken) {
        return $ProvidedToken.Trim()
    }

    foreach ($envVar in @("SONAR_TOKEN", "SONAR_LOGIN")) {
        $value = [Environment]::GetEnvironmentVariable($envVar)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            Write-Host "Using Sonar token from environment variable $envVar."
            return $value.Trim()
        }
    }

    $workspaceTokenPath = Get-WorkspaceTokenCachePath
    if (Test-Path $workspaceTokenPath) {
        $workspaceToken = Get-Content $workspaceTokenPath -Raw
        if (-not [string]::IsNullOrWhiteSpace($workspaceToken)) {
            Write-Host "Using Sonar token from the workspace cache."
            return $workspaceToken.Trim()
        }
    }

    $tokenCachePath = Get-LocalTokenCachePath
    if (Test-Path $tokenCachePath) {
        try {
            $secureToken = Get-Content $tokenCachePath -Raw | ConvertTo-SecureString
            $tokenHandle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
            try {
                $cachedToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenHandle)
            } finally {
                [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenHandle)
            }

            if (-not [string]::IsNullOrWhiteSpace($cachedToken)) {
                Write-Host "Using Sonar token from the local encrypted cache."
                return $cachedToken.Trim()
            }
        } catch {
            Write-Host "Ignoring unreadable Sonar token cache at $tokenCachePath."
        }
    }

    $defaultAuthHeader = Get-BasicAuthHeader -Username "admin" -Password "admin"

    try {
        $validation = Invoke-RestMethod -UseBasicParsing -Headers $defaultAuthHeader `
            -Uri "$HostUrl/api/authentication/validate" -TimeoutSec 10

        if ($validation.valid) {
            $tokenName = "local-scan-" + (Get-Date -Format "yyyyMMddHHmmss")
            $generated = Invoke-RestMethod -Method Post -UseBasicParsing -Headers $defaultAuthHeader `
                -Uri "$HostUrl/api/user_tokens/generate?name=$tokenName" -TimeoutSec 10

            if (-not [string]::IsNullOrWhiteSpace($generated.token)) {
                Write-Host "Generated a temporary Sonar token using the default admin/admin login."
                return $generated.token.Trim()
            }
        }
    } catch {
        # Fall through to the guidance below when default admin bootstrap is not available.
    }

    throw "No SonarQube token is available. Pass -Token <token> or set SONAR_TOKEN first."
}

function Resolve-MavenExecutable {
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) {
        return $maven.Source
    }

    $wrapper = Join-Path $scriptRoot "auth-service\mvnw.cmd"
    if (Test-Path $wrapper) {
        return $wrapper
    }

    throw "Neither mvn nor auth-service\\mvnw.cmd is available on this machine."
}

function Get-ServiceModules {
    $pomPath = Join-Path $scriptRoot "pom.xml"
    [xml]$pom = Get-Content $pomPath
    $modules = Select-Xml -Xml $pom -XPath "//*[local-name()='project']/*[local-name()='modules']/*[local-name()='module']"
    return $modules | ForEach-Object { $_.Node.InnerText.Trim() } | Where-Object { $_ }
}

function Invoke-MavenScan {
    param(
        [string]$PomPath,
        [string[]]$AdditionalArgs = @()
    )

    $mavenArgs = @("-f", $PomPath)

    if (-not $NoClean) {
        $mavenArgs += "clean"
    }

    $mavenArgs += @(
        "verify",
        "sonar:sonar",
        "-Dsonar.host.url=$SonarHostUrl",
        "-Dsonar.login=$resolvedToken"
    )

    if ($AdditionalArgs.Count -gt 0) {
        $mavenArgs += $AdditionalArgs
    }

    & $mavenExecutable @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maven SonarQube analysis failed for $PomPath."
    }
}

function Show-QualityGate {
    param(
        [string]$ProjectKey,
        [string]$DisplayName
    )

    try {
        $qualityGate = Invoke-RestMethod -UseBasicParsing -Headers (Get-TokenAuthHeader -ResolvedToken $resolvedToken) `
            -Uri "$SonarHostUrl/api/qualitygates/project_status?projectKey=$ProjectKey" -TimeoutSec 20
        if ($qualityGate.projectStatus.status) {
            Write-Host "$DisplayName quality gate: $($qualityGate.projectStatus.status)"
        }
    } catch {
        Write-Host "$DisplayName analysis finished, but the quality gate status could not be read yet."
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI is not installed or not on PATH."
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker is installed but the daemon is not running."
}

Write-Host "Starting SonarQube containers..."
& docker compose --profile quality up -d sonarqube sonarqube-db
if ($LASTEXITCODE -ne 0) {
    throw "Could not start the SonarQube containers."
}

Wait-ForSonarQube -HostUrl $SonarHostUrl -TimeoutSeconds $StartupTimeoutSeconds

if ($StartOnly) {
    Write-Host "SonarQube is ready at $SonarHostUrl"
    return
}

$resolvedToken = Resolve-SonarToken -ProvidedToken $Token -HostUrl $SonarHostUrl
$mavenExecutable = Resolve-MavenExecutable

if ($Mode -in @("services", "both")) {
    $serviceModules = Get-ServiceModules
    foreach ($service in $serviceModules) {
        $servicePom = Join-Path $scriptRoot "$service\pom.xml"
        $projectKey = "ConnectHub-$service"
        $projectName = "ConnectHub-$service"
        $coveragePath = Join-Path $scriptRoot "$service\target\site\jacoco\jacoco.xml"

        Write-Host "Running SonarQube analysis for service $service..."
        Invoke-MavenScan -PomPath $servicePom -AdditionalArgs @(
            "-Dsonar.projectKey=$projectKey",
            "-Dsonar.projectName=$projectName",
            "-Dsonar.coverage.jacoco.xmlReportPaths=$coveragePath",
            "-Dsonar.exclusions=**/dto/**,**/entity/**,**/config/**,**/*Application.java"
        )

        Show-QualityGate -ProjectKey $projectKey -DisplayName $service
        Write-Host "Dashboard: $SonarHostUrl/dashboard?id=$projectKey"
    }
}

if ($Mode -in @("aggregate", "both")) {
    Write-Host "Running SonarQube analysis for the combined backend project..."
    Invoke-MavenScan -PomPath (Join-Path $scriptRoot "pom.xml")
    Show-QualityGate -ProjectKey "ConnectHub-Backend" -DisplayName "ConnectHub-Backend"
    Write-Host "Dashboard: $SonarHostUrl/dashboard?id=ConnectHub-Backend"
}
