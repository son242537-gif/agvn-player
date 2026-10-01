<#
.SYNOPSIS
  Dựng, ký và đăng một bản AGVN Player lên GitHub Releases. Điện thoại nhận bản đó ở
  "Cài đặt > Cập nhật ứng dụng > Kiểm tra cập nhật" (và tự kiểm tra mỗi ngày).

.DESCRIPTION
  Chạy trong C:\AGVN\ap (PowerShell). Cần: khoá ký (keystore.properties) và GitHub CLI đã đăng nhập
  (winget install GitHub.cli, rồi gh auth login một lần). Commit đang dựng phải đã push lên GitHub.

  Một kênh duy nhất: mọi máy nhận cùng một bản.
  - Phiên bản lấy từ gradle.properties (AGVN_VERSION_CODE, AGVN_VERSION_NAME). Mã phải lớn hơn bản đang đăng,
    vì Android chỉ cài đè bản có mã lớn hơn.
  - Ghi chú lấy từ docs\agvn\release\ghi-chu-phat-hanh-v<phiên bản>.txt, hoặc từ -Notes / -NotesFile.

  Windows mặc định không cho chạy file .ps1, nên gọi qua "powershell -ExecutionPolicy Bypass -File" (chỉ cho lần chạy đó).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\agvn\dang-ban-cap-nhat.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\agvn\dang-ban-cap-nhat.ps1 -DryRun
#>
param(
    [string]$Notes = "",
    [string]$NotesFile,
    [string]$Keystore = "C:\AGVN\Keys\keystore.properties",
    [switch]$DryRun
)
# Errors are checked one by one: with "Stop", Windows PowerShell 5.1 turns any stderr line of git/gh into a failure
$ErrorActionPreference = "Continue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Repo = "son242537-gif/agvn-player"
$Releases = "https://github.com/$Repo/releases"

function Stop-With($message) { Write-Host "LỖI: $message" -ForegroundColor Red; exit 1 }

function Get-PublishedCode($url) {
    try {
        $content = (Invoke-WebRequest -UseBasicParsing -Uri $url -ErrorAction Stop).Content
        if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
        foreach ($line in ($content -split "`r?`n")) { if ($line -match '^versionCode=(\d+)$') { return [int]$Matches[1] } }
    } catch { }
    return 0
}

if (-not (Test-Path gradlew.bat)) { Stop-With "Hãy chạy script trong thư mục mã nguồn (C:\AGVN\ap)." }
if (-not $DryRun) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Stop-With "Chưa có GitHub CLI: winget install GitHub.cli" }
    cmd /c "gh auth status >nul 2>&1"
    if ($LASTEXITCODE -ne 0) { Stop-With "GitHub CLI chưa đăng nhập: gh auth login" }
}

$commit = (git rev-parse HEAD).Trim()
if (-not $DryRun -and -not (git branch -r --contains $commit)) { Stop-With "Commit $commit chưa được push lên GitHub." }
if (git status --porcelain --untracked-files=no) { Write-Host "Chú ý: có thay đổi chưa commit, bản dựng sẽ gồm cả chúng." -ForegroundColor Yellow }

# Version: the one in gradle.properties, so a build made with gradlew by hand has the same code and installs over it
$props = Get-Content gradle.properties
$code = [int](($props | Select-String '^AGVN_VERSION_CODE=(\d+)').Matches[0].Groups[1].Value)
$name = ($props | Select-String '^AGVN_VERSION_NAME=(.+)$').Matches[0].Groups[1].Value.Trim()
if ($name -notmatch '^\d+\.\d+\.\d+$') { Stop-With "AGVN_VERSION_NAME trong gradle.properties phải có dạng 0.1.3." }
$tag = "v$name"
if (-not $DryRun) {
    $published = Get-PublishedCode "$Releases/latest/download/agvn-update.txt"
    if ($code -le $published) {
        Stop-With "Bản $name (mã $code) không mới hơn bản đang đăng (mã $published). Cần tăng AGVN_VERSION_CODE và AGVN_VERSION_NAME trong gradle.properties."
    }
    cmd /c "gh release view $tag --repo $Repo >nul 2>&1"
    if ($LASTEXITCODE -eq 0) { Stop-With "Release $tag đã có trên GitHub. Cần tăng phiên bản trong gradle.properties." }
}
$defaultNotes = "docs\agvn\release\ghi-chu-phat-hanh-$tag.txt"
if ($NotesFile) { $Notes = (Get-Content -Encoding UTF8 -Raw -ErrorAction Stop $NotesFile).Trim() }
elseif (-not $Notes -and (Test-Path $defaultNotes)) { $Notes = (Get-Content -Encoding UTF8 -Raw $defaultNotes).Trim() }
if (-not $Notes) { $Notes = "AGVN Player $name" }
Write-Host "Dựng AGVN Player $name (mã $code) cho release $tag..." -ForegroundColor Cyan

$env:AGVN_VERSION_CODE = "$code"
$env:AGVN_VERSION_NAME = $name
if (-not $env:AGVN_KEYSTORE_PROPS) { $env:AGVN_KEYSTORE_PROPS = $Keystore }
& .\gradlew.bat testReleaseUnitTest assembleRelease --no-daemon "-PagvnRequireReleaseSigning=true"
if ($LASTEXITCODE -ne 0) { Stop-With "Dựng APK thất bại (xem lỗi ở trên)." }

$out = "build\agvn-update"
New-Item -ItemType Directory -Force $out -ErrorAction Stop | Out-Null
# One name in every release: releases/latest/download/AGVN-Player.apk is then a fixed link to the newest APK
$apkName = "AGVN-Player.apk"
$apk = Join-Path $out $apkName
Copy-Item app\build\outputs\apk\release\app-release.apk $apk -Force -ErrorAction Stop
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $apk).Length
$lines = @("# AGVN Player: thông tin bản cập nhật cho mục Cập nhật ứng dụng", "versionCode=$code", "versionName=$name",
    "apk=$Releases/download/$tag/$apkName", "size=$size", "sha256=$hash")
foreach ($line in ($Notes -split "`r?`n")) { if ($line.Trim()) { $lines += "notes=$($line.Trim())" } }
$manifest = Join-Path $out "agvn-update.txt"
$notesFile = Join-Path $out "ghi-chu.txt"
$utf8 = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllLines((Resolve-Path $out).Path + "\agvn-update.txt", [string[]]$lines, $utf8)
[IO.File]::WriteAllText((Resolve-Path $out).Path + "\ghi-chu.txt", $Notes, $utf8)
Write-Host "APK: $apk ($([math]::Round($size / 1MB)) MB)"
if ($DryRun) { Write-Host "Chạy thử (-DryRun): không đăng lên GitHub."; exit 0 }

# A draft first, published once both files are in: phones never see agvn-update.txt pointing to a missing APK
gh release create $tag $apk $manifest --repo $Repo --target $commit --title "AGVN Player $name" --notes-file $notesFile --draft
if ($LASTEXITCODE -ne 0) { Stop-With "Không tạo được release $tag." }
gh release edit $tag --repo $Repo --draft=false --latest
if ($LASTEXITCODE -ne 0) { Stop-With "Đã tải lên nhưng chưa công bố được release ${tag}: mở trang Releases trên GitHub và bấm Publish." }
Write-Host "Xong: AGVN Player $name đã đăng. Trên điện thoại: Cài đặt > Cập nhật ứng dụng > Kiểm tra cập nhật." -ForegroundColor Green
Write-Host "Muốn cài bằng cáp thì dùng đúng APK này: adb install -r $apk"
