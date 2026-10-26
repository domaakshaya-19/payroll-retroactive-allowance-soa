param(
    [Parameter(Mandatory=$true)][ValidateSet('auth-service','allowance-rule-service','pay-period-service','retro-adjustment-service','discovery-server','api-gateway')][string]$Service,
    [int]$Port=0
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if ($env:JAVA_HOME) { $env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH }
if ($Port -eq 0) { $Port=@{'auth-service'=8084;'allowance-rule-service'=8081;'pay-period-service'=8082;'retro-adjustment-service'=8083;'discovery-server'=8761;'api-gateway'=8080}[$Service] }
$taskProcessId=Get-Content -LiteralPath (Join-Path $projectRoot ".local/$Service-$Port.pid")
& java --add-modules jdk.attach --source 17 (Join-Path $PSScriptRoot 'StopLocal.java') $taskProcessId $Service $Port
if ($LASTEXITCODE -ne 0) { throw 'Graceful shutdown failed; inspect the service log' }
