$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
Push-Location $workspaceRoot
try {
    & .\gradlew.bat :app:testDebugUnitTest --console=plain
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
