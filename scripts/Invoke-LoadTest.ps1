param([string]$BaseUrl='https://localhost:8080',[int]$Requests=50,[int]$Concurrency=5,[string]$OutputPath='docs/evidence/manual-load-test.json')
$ErrorActionPreference='Stop'
if($Requests -lt 1 -or $Concurrency -lt 1){throw 'Request count and concurrency must be positive.'}
Add-Type -AssemblyName System.Net.Http
$password=if($env:CLERK_PASSWORD){$env:CLERK_PASSWORD}else{'Clerk@123'}
$token=(Invoke-RestMethod "$BaseUrl/api/auth/token" -Method Post -ContentType application/json -Body (@{username='clerk';password=$password}|ConvertTo-Json)).accessToken
$client=[Net.Http.HttpClient]::new();$client.Timeout=[TimeSpan]::FromSeconds(15)
$pool=[RunspaceFactory]::CreateRunspacePool(1,$Concurrency);$pool.Open();$jobs=@()
$work={param($client,$uri,$token)
    $watch=[Diagnostics.Stopwatch]::StartNew();$status=0
    try{$request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,$uri);$request.Headers.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token);$response=$client.SendAsync($request).GetAwaiter().GetResult();$response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()|Out-Null;$status=[int]$response.StatusCode;$response.Dispose();$request.Dispose()}catch{$status=0}
    $watch.Stop();[pscustomobject]@{milliseconds=$watch.Elapsed.TotalMilliseconds;status=$status}
}
try{
    foreach($number in 1..$Requests){$shell=[PowerShell]::Create();$shell.RunspacePool=$pool;$shell.AddScript($work).AddArgument($client).AddArgument("$BaseUrl/api/retro-adjustments/employees/EMP001").AddArgument($token)|Out-Null;$jobs+=@{shell=$shell;handle=$shell.BeginInvoke()}}
    $samples=@();foreach($job in $jobs){$samples+=@($job.shell.EndInvoke($job.handle));$job.shell.Dispose()}
    $latencies=@($samples|ForEach-Object {$_.milliseconds}|Sort-Object);$errors=@($samples|Where-Object {$_.status -ne 200}).Count
    $result=[ordered]@{measuredAt=[DateTime]::UtcNow.ToString('o');requestCount=$samples.Count;concurrency=$Concurrency;p50Ms=$latencies[[Math]::Ceiling(.50*$samples.Count)-1];p95Ms=$latencies[[Math]::Ceiling(.95*$samples.Count)-1];errors=$errors;errorRatePercent=100.0*$errors/$samples.Count;transport='HTTPS Gateway with locally issued clerk JWT'}
    New-Item -ItemType Directory -Force (Split-Path $OutputPath -Parent)|Out-Null
    $result|ConvertTo-Json|Set-Content -LiteralPath $OutputPath -Encoding utf8
    $result|ConvertTo-Json
    if($samples.Count -ne $Requests -or $errors -gt 0){throw 'Load test did not meet the zero-error verification requirement; measured failures are retained in the report.'}
}finally{$pool.Close();$pool.Dispose();$client.Dispose()}
