param([string]$EvidenceDirectory='docs/evidence')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    New-Item -ItemType Directory -Force .local,$EvidenceDirectory|Out-Null
    $log=Join-Path $root '.local/negative-contract.log'
    $preference=$ErrorActionPreference;$ErrorActionPreference='Continue'
    & mvn -B -ntp -Pcontract-negative -pl allowance-rule-service -am '-Dtest=AllowanceWorkflowTest#producerMatchesPublishedConsumerSchema' '-Dsurefire.failIfNoSpecifiedTests=false' test *> $log
    $status=$LASTEXITCODE
    $ErrorActionPreference=$preference
    if($status -eq 0){throw 'The deliberately incompatible required-field change was not detected.'}
    [xml]$report=Get-Content -Raw allowance-rule-service/target/surefire-reports/TEST-com.example.payroll.AllowanceWorkflowTest.xml
    $failureText=[string]$report.testsuite.testcase.failure.InnerText
    if([int]$report.testsuite.tests -ne 1 -or [int]$report.testsuite.failures -ne 1 -or [int]$report.testsuite.errors -ne 0 -or $failureText -notmatch 'newAmount' -or $failureText -notmatch 'correctedAmount') {
        throw "Negative run failed for an unexpected reason. Inspect $log"
    }
    $evidence=[ordered]@{measuredAt=[DateTime]::UtcNow.ToString('o');command='mvn -Pcontract-negative -pl allowance-rule-service -am -Dtest=AllowanceWorkflowTest#producerMatchesPublishedConsumerSchema -Dsurefire.failIfNoSpecifiedTests=false test';mutation='Required field newAmount renamed to incompatible correctedAmount, in test payload only';exitCode=$status;tests=1;failures=1;errors=0;skipped=0;expectedFailureConfirmed=$true}
    $evidence|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'negative-contract.json') -Encoding utf8
    Write-Output 'Confirmed deliberate contract failure: 1 test, 1 expected failure, 0 errors, 0 skipped. Run normal verification next.'
}finally{Pop-Location}
