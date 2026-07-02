param()

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$ProtoRoot = (Resolve-Path (Join-Path $ProjectDir "..\im-proto\proto")).Path
$OutDir = Join-Path $ProjectDir "lib\core\proto\generated"

if (!(Get-Command protoc -ErrorAction SilentlyContinue)) {
  throw "protoc was not found. Install it first, for example: choco install protoc -y"
}

$PubCacheBin = Join-Path $env:LOCALAPPDATA "Pub\Cache\bin"
$ProtocGenDart = Join-Path $PubCacheBin "protoc-gen-dart.bat"
if (!(Test-Path $ProtocGenDart)) {
  Write-Host "Installing protoc_plugin..."
  dart pub global activate protoc_plugin
}
if (!(Test-Path $ProtocGenDart)) {
  throw "protoc-gen-dart was not found at $ProtocGenDart"
}

if (Test-Path $OutDir) {
  Remove-Item $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$ProtoFiles = @(
  "ws/frame.proto",
  "body/messages.proto",
  "body/call.proto",
  "common/content.proto",
  "common/enums.proto",
  "common/error.proto"
)

Write-Host "Generating Dart protobuf bindings into $OutDir"
$ProtocArgs = @(
  "--plugin=protoc-gen-dart=$ProtocGenDart",
  "--proto_path=$ProtoRoot",
  "--dart_out=$OutDir"
) + $ProtoFiles

& protoc @ProtocArgs
if ($LASTEXITCODE -ne 0) {
  throw "protoc failed with exit code $LASTEXITCODE"
}

$FrameOutput = Join-Path $OutDir "ws\frame.pb.dart"
if (!(Test-Path $FrameOutput)) {
  throw "Expected protobuf output was not generated: $FrameOutput"
}

Get-ChildItem $OutDir -Recurse -Filter "*.dart" |
  Sort-Object FullName |
  ForEach-Object { Write-Host $_.FullName }
