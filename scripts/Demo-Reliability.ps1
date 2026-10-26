
param(
    [string]$BaseUrl = 'https://localhost:8080',
    [string]$OutputPath = 'docs/evidence/manual-reliability.json'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root

try {
    New-Item -ItemType Directory -Force .local | Out-Null

    # Login
    $password = if ($env:CLERK_PASSWORD) {
        $env:CLERK_PASSWORD
    } else {
        'Clerk@123'
    }

    $login = @{
        username = 'clerk'
        password = $password
    } | ConvertTo-Json

    $token = (Invoke-RestMethod `
        "$BaseUrl/api/auth/token" `
        -Method Post `
        -ContentType application/json `
        -Body $login).accessToken

    $headers = @{
        Authorization = "Bearer $token"
    }

    $base = "$BaseUrl/api/retro-adjustments/employees/EMP001"

    function Get-Adjustments {
        $response = Invoke-RestMethod $base -Headers $headers

        if ($null -eq $response) {
            return ,@()
        }

        return ,@($response)
    }

    function Publish-Line([string]$value) {
        $value | docker compose exec -T kafka `
            /opt/kafka/bin/kafka-console-producer.sh `
            --bootstrap-server localhost:9092 `
            --topic soa.s08.t04.events `
            --property parse.key=true `
            --property 'key.separator=|' | Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw 'Kafka producer failed.'
        }
    }

    # Correct Kafka offset handling.
    # A partition with END=0 and LAG=- is empty, not behind.
    function Wait-Consumed {
        for ($attempt = 0; $attempt -lt 60; $attempt++) {

            $lines = docker compose exec -T kafka `
                /opt/kafka/bin/kafka-consumer-groups.sh `
                --bootstrap-server localhost:9092 `
                --describe `
                --group retro-backdate-v1 `
                2> .local/group-status.log

            if ($LASTEXITCODE -eq 0) {
                $rows = @(
                    $lines | Where-Object {
                        $_ -match '^\s*retro-backdate-v1\s+soa\.s08\.t04\.events\s+'
                    }
                )

                $pending = @(
                    foreach ($row in $rows) {
                        $columns = @($row.Trim() -split '\s+')

                        if ($columns.Count -lt 6) {
                            $row
                            continue
                        }

                        $current = $columns[3]
                        $end = $columns[4]
                        $lag = $columns[5]

                        $caughtUp = (
                            ($lag -eq '0' -and $current -eq $end) -or
                            ($lag -eq '-' -and $end -eq '0')
                        )

                        if (-not $caughtUp) {
                            $row
                        }
                    }
                )

                if ($rows.Count -eq 3 -and $pending.Count -eq 0) {
                    Write-Host 'Kafka consumer caught up.' -ForegroundColor Green
                    return
                }
            }

            Start-Sleep -Seconds 1
        }

        throw 'Kafka consumer offsets did not catch up.'
    }

    # Get the existing completed workflow
    $all = Invoke-RestMethod "$base/workflows" -Headers $headers

    $workflow = $all |
        Where-Object {
            $_.status -eq 'COMPLETED' -and
            $_.newVersion -eq 2
        } |
        Select-Object -First 1

    if (-not $workflow) {
        throw 'Completed version-2 workflow not found.'
    }

    # Retrieve the original Kafka event.
    # Convert to JSON only if API returns an object.
    $eventResponse = Invoke-RestMethod `
        "$base/workflows/$($workflow.eventId)/event" `
        -Headers $headers

    if ($eventResponse -is [string]) {
        $json = $eventResponse.Trim()
    } else {
        $json = $eventResponse | ConvertTo-Json -Depth 20 -Compress
    }

    $eventData = $json | ConvertFrom-Json

    if ($eventData.eventId -ne $workflow.eventId) {
        throw 'Event ID mismatch.'
    }

    # Verify existing records before replay
    $before = Get-Adjustments

    Write-Host "Adjustments before replay: $($before.Count)" -ForegroundColor Cyan

    if ($before.Count -ne 2) {
        throw "Expected 2 adjustments; found $($before.Count)."
    }

    $beforeIds = @($before | ForEach-Object { [string]$_.id } | Sort-Object)

    # Duplicate delivery
    $line = $workflow.aggregateId + '|' + $json

    Write-Host 'Publishing duplicate Kafka events...' -ForegroundColor Cyan

    Publish-Line $line
    Publish-Line $line

    Wait-Consumed

    $afterDuplicate = Get-Adjustments

    Write-Host "Adjustments after duplicate: $($afterDuplicate.Count)" -ForegroundColor Cyan

    $duplicateIds = @(
        $afterDuplicate | ForEach-Object { [string]$_.id } | Sort-Object
    )

    if ($afterDuplicate.Count -ne 2 -or
        ($duplicateIds -join ',') -ne ($beforeIds -join ',')) {
        throw 'Duplicate delivery changed adjustment records.'
    }

    # Before changing to two-instance cluster mode, ensure the
    # Retro database migration/readiness has been verified.
    #
    # The initial service uses embedded file-backed H2.
    # Two instances require a shared Retro-owned H2 server.
    # Switching must not create a separate empty database.
    #
    # First prove replay across a restart of the existing
    # single-instance service, preserving its same database.

    Write-Host 'Restarting existing Retro consumer...' -ForegroundColor Cyan

    & (Join-Path $PSScriptRoot 'Stop-Local.ps1') `
        -Service retro-adjustment-service -Port 8083

    & (Join-Path $PSScriptRoot 'Start-Local.ps1') `
        -Services retro-adjustment-service -RetroInstances 1

    # Wait for actual authenticated API readiness
    $ready = $false

    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $check = Get-Adjustments

            if ($check.Count -eq 2) {
                $ready = $true
                break
            }
        }
        catch {
            # Service might still be registering with Eureka.
        }

        Start-Sleep -Seconds 2
    }

    if (-not $ready) {
        throw 'Retro service did not recover with its original data.'
    }

    Write-Host 'Retro consumer restarted and existing data recovered.' -ForegroundColor Green

    Publish-Line $line
    Wait-Consumed

    $afterRestart = Get-Adjustments

    Write-Host "Adjustments after restart: $($afterRestart.Count)" -ForegroundColor Cyan

    $restartIds = @(
        $afterRestart | ForEach-Object { [string]$_.id } | Sort-Object
    )

    if ($afterRestart.Count -ne 2 -or
        ($restartIds -join ',') -ne ($beforeIds -join ',')) {
        throw 'Restart/replay changed business-effect records.'
    }

    # Invalid event -> Dead Letter Topic
    Write-Host 'Publishing invalid schema-version event...' -ForegroundColor Cyan

    $bad = $json | ConvertFrom-Json
    $bad.schemaVersion = 99
    $bad.eventId = [guid]::NewGuid().ToString()

    $badJson = $bad | ConvertTo-Json -Depth 20 -Compress

    Publish-Line ($workflow.aggregateId + '|' + $badJson)

    $dead = docker compose exec -T kafka `
        /opt/kafka/bin/kafka-console-consumer.sh `
        --bootstrap-server localhost:9092 `
        --topic soa.s08.t04.events.DLT `
        --from-beginning `
        --timeout-ms 15000 `
        --property print.key=true `
        --property print.headers=true `
        2> .local/dlt-inspection.log

    $matching = @(
        $dead | Where-Object {
            $_ -match [regex]::Escape($bad.eventId)
        }
    )

    if ($matching.Count -eq 0 -or
        ($matching -join ' ') -notmatch 'UNSUPPORTED_SCHEMA_VERSION') {
        throw 'Invalid event was not confirmed in the DLT.'
    }

    # Confirm records are unchanged after DLT test
    $final = Get-Adjustments

    if ($final.Count -ne 2) {
        throw 'Final adjustment count changed unexpectedly.'
    }

    $report = [ordered]@{
        measuredAt = [DateTime]::UtcNow.ToString('o')
        sameEventAdditionalDeliveries = 2
        adjustmentsBefore = $before.Count
        adjustmentsAfterDuplicate = $afterDuplicate.Count
        consumerRestarted = $true
        adjustmentsAfterRestartReplay = $afterRestart.Count
        invalidEventObservedInDlt = $true
        dltErrorCode = 'UNSUPPORTED_SCHEMA_VERSION'
        result = 'PASSED'
    }

    New-Item -ItemType Directory -Force `
        (Split-Path $OutputPath -Parent) | Out-Null

    $report |
        ConvertTo-Json |
        Set-Content -LiteralPath $OutputPath -Encoding utf8

    Write-Host 'Kafka reliability verification completed.' -ForegroundColor Green

    $report | ConvertTo-Json
}
finally {
    Pop-Location
}
