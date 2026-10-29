# Builds everything needed to share EduTrack and puts it in release\:
#   EduTrack-0.1.0.msi   Windows installer (jpackage + WiX 3.x)
#   EduTrack.mcpb        Claude Desktop extension that opens the installed app
#   INSTALL.txt          guide for the person who receives the files
# Needs JDK 23+, Maven, WiX Toolset 3.x (candle.exe/light.exe on the PATH) and Node.js (npx).
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$release = Join-Path $root 'release'

if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    $portableWix = Join-Path $env:LOCALAPPDATA 'Programs\WiX314'
    if (Test-Path (Join-Path $portableWix 'candle.exe')) { $env:Path += ";$portableWix" }
    else { throw 'WiX Toolset 3.x was not found. Put candle.exe and light.exe on the PATH.' }
}

Write-Host 'Building the MSI...'
mvn -q -f (Join-Path $root 'pom.xml') -P installer -pl edutrack-desktop -am package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'The MSI build failed.' }

Write-Host 'Packing the Claude Desktop extension...'
$extension = Join-Path $root 'claude-extension'
Copy-Item (Join-Path $root 'edutrack-desktop\src\main\resources\icons\app-icon-256.png') (Join-Path $extension 'icon.png') -Force
npx -y @anthropic-ai/mcpb pack $extension (Join-Path $extension 'EduTrack.mcpb')
if ($LASTEXITCODE -ne 0) { throw 'Packing the extension failed.' }

New-Item -ItemType Directory -Force $release | Out-Null
Copy-Item (Join-Path $root 'edutrack-desktop\target\installer\*.msi') $release -Force
Move-Item (Join-Path $extension 'EduTrack.mcpb') $release -Force
Copy-Item (Join-Path $PSScriptRoot 'INSTALL.txt') $release -Force
Get-ChildItem $release | Format-Table Name, @{ Name = 'MB'; Expression = { [math]::Round($_.Length / 1MB, 1) } }
