param(
    [string[]]$Services = @('discovery-server','auth-service','allowance-rule-service','pay-period-service','retro-adjustment-service','api-gateway'),
    [ValidateSet(1,2)][int]$RetroInstances=1,
    [ValidateSet(8083,8085)][int]$RetroPort=8083,
    [switch]$RetroCluster
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH }
if ($RetroInstances -eq 2) { $RetroCluster=$true }
if ($RetroCluster -and 'retro-adjustment-service' -in $Services) {
    & java --source 17 (Join-Path $PSScriptRoot 'StartRetroStore.java') $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Retro store startup failed' }
}
$knownServices = @('discovery-server','auth-service','allowance-rule-service','pay-period-service','retro-adjustment-service','api-gateway')
$required = @('RETRO_SERVICE_PASSWORD','TLS_KEYSTORE','TLS_KEYSTORE_PASSWORD','EUREKA_USER','EUREKA_PASSWORD','PAYROLL_TRUSTSTORE','JWT_PUBLIC_KEY','JWT_PRIVATE_KEY','CLERK_PASSWORD','APPROVER_PASSWORD','ALLOWANCE_DB_USER','ALLOWANCE_DB_PASSWORD','PAY_PERIOD_DB_USER','PAY_PERIOD_DB_PASSWORD','RETRO_DB_USER','RETRO_DB_PASSWORD')
foreach ($name in $required) {
    if (-not [Environment]::GetEnvironmentVariable($name,'Process')) { throw "Missing $name. Dot-source Initialize-Local.ps1 first." }
}
foreach ($service in $Services) {
    if ($service -notin $knownServices) { throw "Unknown service: $service" }
    $jar = Join-Path $projectRoot "$service/target/$service-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { throw "Build first: mvn verify ($jar missing)" }
    $ports = if ($service -eq 'retro-adjustment-service') { if ($RetroInstances -eq 2) { @(8083,8085) } else { @($RetroPort) } } else { @(@{'auth-service'=8084;'allowance-rule-service'=8081;'pay-period-service'=8082;'discovery-server'=8761;'api-gateway'=8080}[$service]) }
    foreach ($servicePort in $ports) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = (Get-Command java).Source
    $info.WorkingDirectory = $projectRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    # Each process inherits only its own sensitive credentials.
    $serviceSecrets = @('RETRO_SERVICE_PASSWORD','JWT_PRIVATE_KEY','CLERK_PASSWORD','APPROVER_PASSWORD','ALLOWANCE_DB_USER','ALLOWANCE_DB_PASSWORD','PAY_PERIOD_DB_USER','PAY_PERIOD_DB_PASSWORD','RETRO_DB_USER','RETRO_DB_PASSWORD')
    $keep = switch ($service) {
        'auth-service' { @('RETRO_SERVICE_PASSWORD','JWT_PRIVATE_KEY','CLERK_PASSWORD','APPROVER_PASSWORD') }
        'allowance-rule-service' { @('ALLOWANCE_DB_USER','ALLOWANCE_DB_PASSWORD') }
        'pay-period-service' { @('PAY_PERIOD_DB_USER','PAY_PERIOD_DB_PASSWORD') }
        'retro-adjustment-service' { @('RETRO_SERVICE_PASSWORD','RETRO_DB_USER','RETRO_DB_PASSWORD') }
        default { @() }
    }
    foreach ($name in $serviceSecrets) { if ($name -notin $keep) { $info.EnvironmentVariables.Remove($name) } }
    $logName = if ($service -eq 'retro-adjustment-service') { "$service-$servicePort" } else { $service }
    $log = Join-Path $projectRoot ".local/$logName.log"
    $trust = $env:PAYROLL_TRUSTSTORE
    $info.Arguments = '-Djavax.net.ssl.trustStore="' + $trust + '" -Djavax.net.ssl.trustStorePassword=changeit -jar "' + $jar + '" --logging.file.name="' + $log + '" --server.port=' + $servicePort + ' --spring.application.admin.enabled=true'
    if ($service -eq 'retro-adjustment-service' -and $RetroCluster) { $info.Arguments += ' --spring.profiles.active=cluster' }
    $process = [System.Diagnostics.Process]::Start($info)
    Set-Content -LiteralPath (Join-Path $projectRoot ".local/$service-$servicePort.pid") -Value $process.Id -Encoding ascii
    Write-Host "$service port=$servicePort PID=$($process.Id) log=$log"
    }
}
