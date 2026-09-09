param(
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$User = "postgres",
    [string]$PgDumpPath = ""
)

$ErrorActionPreference = "Stop"

$pgDumpCandidates = @(
    "pg_dump",
    "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe",
    "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe",
    "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe",
    "C:\Program Files\PostgreSQL\15\bin\pg_dump.exe",
    "C:\Program Files\PostgreSQL\14\bin\pg_dump.exe"
)

$pgDump = $null

if ($PgDumpPath -and (Test-Path $PgDumpPath)) {
    $pgDump = $PgDumpPath
}

foreach ($candidate in $pgDumpCandidates) {
    if (Test-Path $candidate) {
        $pgDump = $candidate
        break
    }

    try {
        $command = Get-Command $candidate -ErrorAction Stop
        $pgDump = $command.Source
        break
    } catch {}
}

if (-not $pgDump) {
    throw "pg_dump was not found. Check your PostgreSQL installation path."
}

Write-Host "Using pg_dump: $pgDump"

$securePassword = Read-Host "PostgreSQL password for user '$User'" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

$migrations = @(
    @{
        Database = "collabmind_chat"
        Output = "services\chat-core\src\main\resources\db\migration\V1__initial_chat_core_schema.sql"
        Service = "chat-core"
    },
    @{
        Database = "collabmind_identity"
        Output = "services\identity-service\src\main\resources\db\migration\V1__initial_identity_schema.sql"
        Service = "identity-service"
    },
    @{
        Database = "collabmind_ai"
        Output = "services\ai-orchestrator\src\main\resources\db\migration\V1__initial_ai_orchestrator_schema.sql"
        Service = "ai-orchestrator"
    }
)

foreach ($migration in $migrations) {
    New-Item -ItemType Directory -Force -Path (Split-Path $migration.Output) | Out-Null

    Write-Host ""
    Write-Host "Exporting $($migration.Service)..."

    & $pgDump `
        "--host=$HostName" `
        "--port=$Port" `
        "--username=$User" `
        "--schema-only" `
        "--no-owner" `
        "--no-privileges" `
        "--no-comments" `
        "--dbname=$($migration.Database)" |
        Where-Object {
            $_ -notmatch "^\s*--" -and
            $_ -notmatch "^\s*SET\s" -and
            $_ -notmatch "^\s*SELECT pg_catalog\.set_config" -and
            $_ -notmatch "^\s*$"
        } |
        Set-Content -Path $migration.Output

    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed for $($migration.Database)"
    }

    Write-Host "Created $($migration.Output)"
}

Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Done. Flyway migration files created."

