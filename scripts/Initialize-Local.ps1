# Dot-source this script so generated environment variables stay in your shell.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
if ($env:JAVA_HOME) { $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH }
$localDirectory = Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force $localDirectory | Out-Null
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
# Existing values are retained; use the same DB passwords after restarting your shell.
foreach ($name in @('RETRO_SERVICE_PASSWORD','TLS_KEYSTORE_PASSWORD','EUREKA_PASSWORD','ALLOWANCE_DB_PASSWORD','PAY_PERIOD_DB_PASSWORD','RETRO_DB_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($name,'Process')) {
        [Environment]::SetEnvironmentVariable($name,(New-LocalSecret),'Process')
    }
}
# Public demo credentials only. JWT signing keys and infrastructure credentials remain random.
if (-not $env:CLERK_PASSWORD) { $env:CLERK_PASSWORD = 'Clerk@123' }
if (-not $env:APPROVER_PASSWORD) { $env:APPROVER_PASSWORD = 'Approver@123' }
$env:AUTH_DEMO_ENABLED = 'true'
$env:EUREKA_USER = 'discovery'
$env:ALLOWANCE_DB_USER = 'allowance_owner'
$env:PAY_PERIOD_DB_USER = 'period_owner'
$env:RETRO_DB_USER = 'retro_owner'
$env:CORS_ORIGINS = 'https://localhost:3000'
$env:CLERK_EMPLOYEES = 'EMP001'
$env:APPROVER_EMPLOYEES = 'EMP001'
$env:JWT_ISSUER = 'https://localhost:8084'
$env:TLS_KEYSTORE = 'file:' + ((Join-Path $localDirectory 'localhost.p12') -replace '\\','/')
$env:JWT_PRIVATE_KEY = 'file:' + ((Join-Path $localDirectory 'jwt-private.pem') -replace '\\','/')
$env:JWT_PUBLIC_KEY = 'file:' + ((Join-Path $localDirectory 'jwt-public.pem') -replace '\\','/')
$env:PAYROLL_TRUSTSTORE = Join-Path $localDirectory 'truststore.p12'
if (-not (Test-Path (Join-Path $localDirectory 'jwt-private.pem'))) {
    & java --source 17 scripts/LocalKeys.java $localDirectory
    if ($LASTEXITCODE -ne 0) { throw 'JWT key generation failed' }
}
if (-not (Test-Path (Join-Path $localDirectory 'localhost.p12'))) {
    & keytool -genkeypair -alias localhost -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -storetype PKCS12 -keystore (Join-Path $localDirectory 'localhost.p12') -storepass:env TLS_KEYSTORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'TLS certificate generation failed' }
    & keytool -exportcert -rfc -alias localhost -keystore (Join-Path $localDirectory 'localhost.p12') -storepass:env TLS_KEYSTORE_PASSWORD -file (Join-Path $localDirectory 'localhost.crt')
    if ($LASTEXITCODE -ne 0) { throw 'TLS certificate export failed' }
}
if (-not (Test-Path $env:PAYROLL_TRUSTSTORE)) {
    # Contains a PUBLIC certificate only. Its password is not a secret.
    $env:PAYROLL_PUBLIC_TRUST_PASSWORD = 'changeit'
    & keytool -importcert -noprompt -alias localhost -file (Join-Path $localDirectory 'localhost.crt') -keystore $env:PAYROLL_TRUSTSTORE -storetype PKCS12 -storepass:env PAYROLL_PUBLIC_TRUST_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Trust store creation failed' }
}
Write-Host 'Local environment initialized. Keep this shell open. Secrets were not printed or persisted.'
