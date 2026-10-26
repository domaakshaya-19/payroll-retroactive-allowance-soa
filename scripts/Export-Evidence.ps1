param([string]$EvidenceDirectory='docs/evidence',[DateTime]$BuildStartedUtc=[DateTime]::MinValue,[int]$BuildExitCode=0)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try{
    New-Item -ItemType Directory -Force $EvidenceDirectory|Out-Null
    $suites=@();$testCases=@()
    Get-ChildItem -Recurse -Filter 'TEST-*.xml' | Where-Object {$_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' -and $_.LastWriteTimeUtc -ge $BuildStartedUtc.ToUniversalTime()} | ForEach-Object {
        [xml]$xml=Get-Content -Raw -LiteralPath $_.FullName
        $module=Split-Path (Split-Path (Split-Path $_.DirectoryName -Parent) -Parent) -Leaf
        $suite=$xml.testsuite
        $suites+=[ordered]@{module=$module;name=[string]$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped}
        foreach($case in $suite.testcase){$result=if($case.failure){'FAIL'}elseif($case.error){'ERROR'}elseif($case.skipped){'SKIPPED'}else{'PASS'};$testCases+=[ordered]@{module=$module;class=[string]$case.classname;name=[string]$case.name;result=$result}}
    }
    $tests=0;$failures=0;$errors=0;$skipped=0
    foreach($suite in $suites){$tests+=$suite.tests;$failures+=$suite.failures;$errors+=$suite.errors;$skipped+=$suite.skipped}
    $expected=@('contracts','auth-service','allowance-rule-service','pay-period-service','retro-adjustment-service','api-gateway','verification')
    $observed=@($suites|ForEach-Object {$_.module}|Select-Object -Unique)
    $missing=@($expected|Where-Object {$_ -notin $observed})
    $summary=[ordered]@{measuredAt=[DateTime]::UtcNow.ToString('o');buildStartedUtc=$BuildStartedUtc.ToUniversalTime().ToString('o');buildExitCode=$BuildExitCode;tests=$tests;passed=$tests-$failures-$errors-$skipped;failures=$failures;errors=$errors;skipped=$skipped;modulesNotExecuted=$missing;suites=$suites;testCases=$testCases}
    $summary|ConvertTo-Json -Depth 12|Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'automated-tests.json') -Encoding utf8
    foreach($name in @('load-test.json','system-facts.json')){
        $source=Join-Path $root "verification/target/evidence/$name"
        if((Test-Path $source) -and (Get-Item $source).LastWriteTimeUtc -ge $BuildStartedUtc.ToUniversalTime()){Copy-Item -LiteralPath $source -Destination (Join-Path $EvidenceDirectory $name) -Force}
    }
    $markdown=@('# Measured verification results','',('Measured at UTC: '+$summary.measuredAt),'',"Normal build exit code: $BuildExitCode",'',"Executed **$tests** tests: **$($summary.passed)** passed, **$failures** failures, **$errors** errors, **$skipped** skipped.",'','| Module | Tests | Failures | Errors | Skipped |','|---|---:|---:|---:|---:|')
    foreach($group in ($suites|Group-Object module)){
        $gt=0;$gf=0;$ge=0;$gs=0;foreach($suite in $group.Group){$gt+=$suite.tests;$gf+=$suite.failures;$ge+=$suite.errors;$gs+=$suite.skipped}
        $markdown+="| $($group.Name) | $gt | $gf | $ge | $gs |"
    }
    $loadPath=Join-Path $EvidenceDirectory 'load-test.json'
    if(Test-Path $loadPath){$load=Get-Content -Raw $loadPath|ConvertFrom-Json;$markdown+=@('','## Measured load',"$($load.requestCount) authenticated HTTPS GET requests at concurrency $($load.concurrency); p50 **$($load.p50Ms) ms**, p95 **$($load.p95Ms) ms**; errors **$($load.errors)**, error rate **$($load.errorRatePercent)%**.")}
    $negativePath=Join-Path $EvidenceDirectory 'negative-contract.json'
    if(Test-Path $negativePath){$negative=Get-Content -Raw $negativePath|ConvertFrom-Json;$markdown+=@('','## Deliberate incompatible contract',"Separate negative run: $($negative.tests) test, $($negative.failures) expected failure, $($negative.errors) errors, $($negative.skipped) skipped; confirmed=$($negative.expectedFailureConfirmed). This failure is not included in normal-build counts.")}
    $markdown+=@('','## Execution boundaries','System evidence uses real packaged app JVMs, Eureka, HTTPS, signed JWTs, a real embedded KRaft broker and isolated H2 stores. It is not a mocked-controller system test. Docker Desktop and remote GitHub CI execution are not claimed. Raw logs, private keys and tokens are excluded from this report.')
    $markdown|Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'RESULTS.md') -Encoding utf8
    Write-Output "Executed tests=$tests passed=$($summary.passed) failures=$failures errors=$errors skipped=$skipped; build exit=$BuildExitCode"
    if($missing.Count -gt 0){Write-Output ('Modules without current-run test reports: '+($missing -join ', '))}
    if($BuildExitCode -eq 0 -and ($failures -gt 0 -or $errors -gt 0 -or $skipped -gt 0 -or $missing.Count -gt 0)){throw 'A green, complete, zero-skip verification was expected but reports disagree.'}
}finally{Pop-Location}
