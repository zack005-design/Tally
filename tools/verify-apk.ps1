param(
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-fA-F:]{64,95}$')][string]$ExpectedCertificateSha256,
    [Parameter(Mandatory)][ValidateRange(1,2100000000)][int]$ExpectedVersionCode,
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$ExpectedPackage = 'com.smartexpense'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$fingerprint = $ExpectedCertificateSha256.Replace(':','').ToLowerInvariant()
if ($fingerprint -notmatch '^[0-9a-f]{64}$') { throw 'Expected certificate must be a SHA-256 fingerprint.' }
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { throw 'Set ANDROID_HOME or pass -SdkRoot.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK build tools are missing.' }
$signer = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$signature = & $signer verify --verbose --print-certs $apkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$signatureText = $signature -join "`n"
$signers = [regex]::Matches($signatureText, '(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\s*$')
if ($signers.Count -ne 1 -or $signers[0].Groups[1].Value.ToLowerInvariant() -ne $fingerprint) {
    throw 'APK signing certificate does not match the trusted release identity.'
}
if ($signatureText -match '(?im)^Signer #\d+ certificate DN:.*CN=Android Debug(?:,|$)') {
    throw 'Debug signing certificates are not allowed for distribution.'
}
$badging = & $aapt dump badging $apkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK metadata.' }
$metadata = $badging -join "`n"
$package = [regex]::Match($metadata, "(?m)^package: name='([^']+)' versionCode='(\d+)'")
if (-not $package.Success -or $package.Groups[1].Value -ne $ExpectedPackage -or [int]$package.Groups[2].Value -ne $ExpectedVersionCode) {
    throw 'APK package or version code does not match the intended release.'
}
if ($metadata -match '(?m)^application-debuggable') { throw 'Debuggable APKs are not allowed for distribution.' }
if ($metadata -match "uses-permission[^\r\n]*name='android.permission.INTERNET'") { throw 'Unexpected Internet permission.' }
$manifest = & $aapt dump xmltree $apkPath AndroidManifest.xml 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK manifest.' }
if (($manifest -join "`n") -notmatch 'android:allowBackup\([^)]*\)=\(type 0x12\)0x0\b') {
    throw 'APK must explicitly disable Android automatic backup.'
}
$sha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
# Write a checksum only after every verification passes.
"$sha256  $([IO.Path]::GetFileName($apkPath))" | Set-Content -LiteralPath "$apkPath.sha256" -Encoding ascii
[pscustomobject]@{ Apk=$apkPath; Package=$ExpectedPackage; VersionCode=$ExpectedVersionCode; CertificateSha256=$fingerprint; Sha256=$sha256 }
