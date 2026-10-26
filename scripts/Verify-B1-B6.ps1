param([switch]$IncludeContractBreak,[string]$EvidenceDirectory='docs/evidence')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try{
    New-Item -ItemType Directory -Force .local,$EvidenceDirectory|Out-Null
    if($IncludeContractBreak){& (Join-Path $PSScriptRoot 'Test-Contract-Break.ps1') -EvidenceDirectory $EvidenceDirectory}
    $started=[DateTime]::UtcNow
    $preference=$ErrorActionPreference;$ErrorActionPreference='Continue'
    & mvn -B -ntp verify *> .local/b1-b6-build.log
    $status=$LASTEXITCODE
    $ErrorActionPreference=$preference
    & (Join-Path $PSScriptRoot 'Export-Evidence.ps1') -EvidenceDirectory $EvidenceDirectory -BuildStartedUtc $started -BuildExitCode $status
    if($status -ne 0){throw 'Normal verification failed. No failed tests were excluded; inspect .local/b1-b6-build.log and current Surefire reports.'}
    Write-Output 'Normal root build passed. Sanitized evidence exported; keys, tokens and raw application logs remain under ignored target/.local directories.'
}finally{Pop-Location}
