param([string]$BaseUrl='https://localhost:8080')
$ErrorActionPreference='Stop'
$password=if($env:CLERK_PASSWORD){$env:CLERK_PASSWORD}else{'Clerk@123'}
$token=(Invoke-RestMethod "$BaseUrl/api/auth/token" -Method Post -ContentType application/json -Body (@{username='clerk';password=$password}|ConvertTo-Json)).accessToken
$headers=@{Authorization="Bearer $token"}
$rules=Invoke-RestMethod "$BaseUrl/api/allowances/employees/EMP001" -Headers $headers
$original=@($rules|Where-Object {$_.allowanceType -eq 'HOUSING' -and $_.ruleVersion -eq 1})
if($original.Count -eq 0){Invoke-RestMethod "$BaseUrl/api/allowances/employees/EMP001" -Headers $headers -Method Post -ContentType application/json -Body '{"allowanceType":"HOUSING","monthlyAmount":2000.00,"effectiveFrom":"2026-08-01"}'|Out-Null}
elseif([decimal]$original[0].monthlyAmount -ne 2000){throw 'Existing version 1 differs from the synthetic seed. It will not be overwritten.'}
foreach($month in @(8,9,10)){
    $start=[DateTime]::new(2026,$month,1);$end=$start.AddMonths(1).AddDays(-1)
    $periods=Invoke-RestMethod "$BaseUrl/api/pay-periods/employees/EMP001" -Headers $headers
    $matches=@($periods|Where-Object {([DateTime]$_.startsOn).Date -eq $start.Date})
    if($matches.Count -eq 0){$period=Invoke-RestMethod "$BaseUrl/api/pay-periods/employees/EMP001" -Headers $headers -Method Post -ContentType application/json -Body (@{startsOn=$start.ToString('yyyy-MM-dd');endsOn=$end.ToString('yyyy-MM-dd')}|ConvertTo-Json)}else{$period=$matches[0]}
    if($month -lt 10 -and $period.status -eq 'OPEN'){Invoke-RestMethod "$BaseUrl/api/pay-periods/employees/EMP001/$($period.id)/issue" -Headers $headers -Method Post -ContentType application/json -Body '{"allowances":[{"allowanceType":"HOUSING","ruleVersion":1,"originalAmount":2000.00}]}'|Out-Null}
    if($month -eq 10 -and $period.status -ne 'OPEN'){throw 'Existing October period is not OPEN; seed will not rewrite payroll history.'}
}
$history=Invoke-RestMethod "$BaseUrl/api/pay-periods/employees/EMP001/snapshots" -Headers $headers
if(@($history).Count -ne 2 -or @($history|Where-Object {[decimal]$_.originalAmount -ne 2000}).Count -gt 0){throw 'Historical seed differs; no issued statement was overwritten.'}
Write-Output 'Seed verified: EMP001 HOUSING version 1 = 2000; Aug/Sep 2026 CLOSED and issued; October OPEN.'
