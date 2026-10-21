param(
    [Parameter(Mandatory=$true)][string]$AdbPath,
    [Parameter(Mandatory=$true)][DateTimeOffset]$Since,
    [string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot '../docs/screenshots/all-screens'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$screens = @()
$folders = @('/sdcard/Download/tally-exact-reference', '/sdcard/Android/data/com.smartexpense.reference/files/all-screens')
foreach ($folder in $folders) {
    $stamps = & $AdbPath -s $Serial shell stat -c '%n:%Y' "$folder/*.png" 2>$null
    foreach ($stamp in $stamps) {
        if ($stamp -notmatch '^(.*/([a-z0-9-]+\.png)):(\d+)\s*$') { continue }
        $remote = $Matches[1]; $name = $Matches[2]; $epoch = [long]$Matches[3]
        if ($epoch -lt $Since.ToUnixTimeSeconds()) { continue }
        $target = Join-Path $destination $name
        & $AdbPath -s $Serial pull $remote $target | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not collect $name" }
        $screens += [pscustomobject]@{
            name = $name
            title = [Globalization.CultureInfo]::InvariantCulture.TextInfo.ToTitleCase($name.Replace('.png','').Replace('-',' '))
            capturedUtc = [DateTimeOffset]::FromUnixTimeSeconds($epoch).ToString('o')
            sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        }
    }
}
$screens = @($screens | Sort-Object name -Unique)
$json = ConvertTo-Json -InputObject $screens -Depth 3
Set-Content -LiteralPath (Join-Path $destination 'manifest.json') -Value $json -Encoding utf8
Set-Content -LiteralPath (Join-Path $destination 'screens.js') -Value "window.tallyScreens = $json;" -Encoding utf8
Write-Output "Collected $($screens.Count) fresh screen captures."
