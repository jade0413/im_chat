param(
  [string]$OutputDir = "dist"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
Set-Location $ProjectDir

Write-Host "Resolving Flutter dependencies..."
flutter pub get

Write-Host "Building Windows release..."
flutter build windows --release

$ReleaseDir = Join-Path $ProjectDir "build\windows\x64\runner\Release"
if (!(Test-Path $ReleaseDir)) {
  throw "Windows release output not found: $ReleaseDir"
}

$OutputPath = Join-Path $ProjectDir $OutputDir
New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null

$VersionLine = Select-String -Path (Join-Path $ProjectDir "pubspec.yaml") -Pattern "^version:\s*(.+)$" | Select-Object -First 1
$Version = if ($VersionLine) { $VersionLine.Matches[0].Groups[1].Value.Trim() } else { "dev" }
$Version = $Version -replace "\+", "-"
$Stamp = Get-Date -Format "yyyyMMdd-HHmm"
$ZipPath = Join-Path $OutputPath "im_app-windows-$Version-$Stamp.zip"

if (Test-Path $ZipPath) {
  Remove-Item $ZipPath -Force
}

Write-Host "Packaging $ReleaseDir -> $ZipPath"
Compress-Archive -Path (Join-Path $ReleaseDir "*") -DestinationPath $ZipPath -Force

Write-Host ""
Write-Host "Done: $ZipPath"
