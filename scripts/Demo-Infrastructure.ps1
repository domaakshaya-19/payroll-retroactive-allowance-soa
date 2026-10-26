param([string]$BaseUrl='https://localhost:8080',[string]$EurekaUrl='https://localhost:8761',[string]$OutputPath='docs/evidence/manual-infrastructure.json')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$password=if($env:CLERK_PASSWORD){$env:CLERK_PASSWORD}else{'Clerk@123'}
$token=(Invoke-RestMethod "$BaseUrl/api/auth/token" -Method Post -ContentType application/json -Body (@{username='clerk';password=$password}|ConvertTo-Json)).accessToken
$headers=@{Authorization="Bearer $token"}
$basic=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:EUREKA_USER+':'+$env:EUREKA_PASSWORD))
$registry=Invoke-RestMethod "$EurekaUrl/eureka/apps" -Headers @{Authorization="Basic $basic";Accept='application/json'}
$instances=@($registry.applications.application|ForEach-Object {$_.instance})
$names=@($instances|ForEach-Object {$_.app}|Sort-Object -Unique)
if($names.Count -ne 6 -or @($instances|Where-Object app -eq 'RETRO-ADJUSTMENT-SERVICE').Count -ne 2){throw 'Expected six Eureka application names and two Retro instances.'}
$observed=@();foreach($n in 1..8){$response=Invoke-WebRequest "$BaseUrl/api/retro-adjustments/employees/EMP001" -Headers $headers;$observed+=$response.Headers['X-Payroll-Instance']}
if(@($observed|Sort-Object -Unique).Count -ne 2){throw 'Gateway did not select both registered Retro instances.'}
$stopped=$false
try{
    & (Join-Path $PSScriptRoot 'Stop-Local.ps1') -Service retro-adjustment-service -Port 8083;$stopped=$true
    $success=0;foreach($n in 1..8){$response=Invoke-WebRequest "$BaseUrl/api/retro-adjustments/employees/EMP001" -Headers $headers;if([int]$response.StatusCode -ne 200){throw 'Read failed after stopping one instance'};$success++}
    $report=[ordered]@{measuredAt=[DateTime]::UtcNow.ToString('o');eurekaApplicationNames=$names;retroInstancesBeforeStop=2;gatewayObservedInstances=@($observed|Sort-Object -Unique);stoppedPort=8083;requestsAfterStop=8;successfulRequestsAfterStop=$success}
    New-Item -ItemType Directory -Force (Split-Path $OutputPath -Parent)|Out-Null
    $report|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $OutputPath -Encoding utf8
    $report|ConvertTo-Json -Depth 6
}finally{if($stopped){& (Join-Path $PSScriptRoot 'Start-Local.ps1') -Services retro-adjustment-service -RetroPort 8083 -RetroCluster}}
