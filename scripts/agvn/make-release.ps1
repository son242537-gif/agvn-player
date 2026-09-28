# AGVN Player - build a release candidate on Windows into release\v<version>\
#   $env:AGVN_KEYSTORE_PROPS = "C:\AGVN\keys\keystore.properties"   (outside the repo)
#   powershell -ExecutionPolicy Bypass -File scripts\agvn\make-release.ps1
# Same steps as make-release.sh plus a Windows Defender scan of the APK.
# -AllowDebugSigning builds a debug-signed dry run (never give that APK to customers).
param([switch]$AllowDebugSigning)
$ErrorActionPreference = "Stop"
function Log($m) { Write-Host "[AGVN Release] $m" }
function Fail($m) { Write-Host "[AGVN Release] LOI: $m" -ForegroundColor Red; exit 1 }

$root = (git rev-parse --show-toplevel).Trim()
Set-Location $root
if (-not $env:ANDROID_HOME) { Fail "chua dat ANDROID_HOME" }
$bt = Join-Path $env:ANDROID_HOME "build-tools\35.0.0"
$props = Get-Content gradle.properties
$version = (($props | Select-String '^AGVN_VERSION_NAME=') -split '=')[1].Trim()
$code = (($props | Select-String '^AGVN_VERSION_CODE=') -split '=')[1].Trim()
$out = "release\v$version"; $apkName = "AGVN-Player-v$version.apk"
Log "v$version (versionCode $code)"

$secrets = git ls-files | Select-String -Pattern '\.(jks|keystore)$|keystore\.properties$|\.tar\.zst$'
if ($secrets) { $secrets; Fail "file bi mat/luu tru lon dang nam trong git" }

$gradleArgs = @("testReleaseUnitTest", "assembleRelease", "--no-daemon")
if (-not $AllowDebugSigning) {
    if (-not $env:AGVN_KEYSTORE_PROPS -or -not (Test-Path $env:AGVN_KEYSTORE_PROPS)) { Fail "dat `$env:AGVN_KEYSTORE_PROPS tro toi keystore.properties" }
    $gradleArgs += "-PagvnRequireReleaseSigning=true"
}
& .\gradlew.bat @gradleArgs
if ($LASTEXITCODE -ne 0) { Fail "gradle build/test that bai" }
& python -m unittest discover -s tools/agvn/tests
if ($LASTEXITCODE -ne 0) { Fail "toolkit tests" }

$apk = "app\build\outputs\apk\release\app-release.apk"
$badging = & (Join-Path $bt "aapt2.exe") dump badging $apk | Out-String
if ($badging -notmatch "package: name='com.agvn.player' versionCode='$code' versionName='$([regex]::Escape($version))'") { Fail "package/version sai" }
if ($badging -notmatch "application-label:'AGVN Player'") { Fail "ten app sai" }

$certOut = & (Join-Path $bt "apksigner.bat") verify --print-certs $apk | Out-String
$cert = ([regex]::Match($certOut, 'certificate SHA-256 digest: ([0-9a-f]+)')).Groups[1].Value
if (-not $cert) { Fail "APK chua duoc ky hop le" }
$pinLine = Get-Content scripts\agvn\pins.txt | Select-String '^release_cert_sha256='
$pin = if ($pinLine) { ($pinLine -split '=')[1].Trim() } else { "" }
if (-not $AllowDebugSigning) {
    if (-not $pin) { Log "Chua co release_cert_sha256 trong pins.txt. Neu dung keystore AGVN, them dong: release_cert_sha256=$cert" }
    elseif ($pin -ne $cert) { Fail "chung chi $cert khac pin $pin (sai keystore?)" }
}

New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item $apk (Join-Path $out $apkName) -Force
$hash = (Get-FileHash (Join-Path $out $apkName) -Algorithm SHA256).Hash.ToLower()
Set-Content -Path (Join-Path $out "$apkName.sha256") -Value "$hash  $apkName" -Encoding ascii
Copy-Item docs\agvn\release\HUONG-DAN-CAI-AGVN-PLAYER.txt $out -Force
$notes = "docs\agvn\release\ghi-chu-phat-hanh-v$version.txt"
if (Test-Path $notes) { Copy-Item $notes $out -Force }

$defender = Join-Path $env:ProgramFiles "Windows Defender\MpCmdRun.exe"
if (Test-Path $defender) {
    Log "Quet Windows Defender..."
    & $defender -Scan -ScanType 3 -File (Resolve-Path (Join-Path $out $apkName)) -DisableRemediation
    if ($LASTEXITCODE -eq 0) { Log "Defender: sach" } else { Log "Defender bao nghi ngo (ma $LASTEXITCODE). Neu chi co assets/proton-9.0-arm64ec.tar.zst thi da biet, xem docs\agvn\release\defender-bao-nham.md; file khac bi bao thi dung lai kiem tra" }
} else { Log "Khong thay MpCmdRun.exe - hay quet APK bang phan mem diet virus khac" }

Log "Xong: $out"; Get-ChildItem $out
Log "Tiep theo: chay docs\agvn\rc-test-report.md tren may, roi: git tag v$version; git push origin v$version"
