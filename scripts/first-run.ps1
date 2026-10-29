# EduTrack first-run setup: creates the PostgreSQL role and databases and writes the local configuration.
# Run once after installing PostgreSQL 18. It asks for a new password for the "edutrack" role and then
# for the password of the "postgres" superuser (the one chosen when installing PostgreSQL).
$ErrorActionPreference = 'Stop'

$psql = Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $psql) {
    Write-Host 'PostgreSQL was not found. Install it first, for example:'
    Write-Host '    winget install --id PostgreSQL.PostgreSQL.18 --interactive'
    exit 1
}
Write-Host "Using $psql"

$secure = Read-Host 'Choose a password for the new edutrack database user' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $secure).Password
if ([string]::IsNullOrEmpty($password)) {
    Write-Host 'The password cannot be empty.'
    exit 1
}

Write-Host 'Now enter the password of the postgres superuser.'
& $psql -U postgres -v "edutrack_pw=$password" -f (Join-Path $PSScriptRoot 'setup-db.sql')
if ($LASTEXITCODE -ne 0) {
    Write-Host 'The database setup failed (see the message above). If the edutrack role already exists, only the configuration file is missing.'
    exit 1
}

$configDir = Join-Path $env:USERPROFILE '.edutrack'
New-Item -ItemType Directory -Force $configDir | Out-Null
# .properties files treat backslash as an escape character
$escaped = $password.Replace('\', '\\')
Set-Content -Path (Join-Path $configDir 'application.properties') -Value "spring.datasource.password=$escaped" -Encoding ascii
Write-Host "Done. Configuration written to $configDir\application.properties. You can start EduTrack now."
