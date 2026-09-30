<#
.SYNOPSIS
  Dựng, ký và đăng một bản AGVN Player lên GitHub Releases, để điện thoại tự cập nhật qua
  "Cài đặt > Cập nhật ứng dụng".

.DESCRIPTION
  Chạy trong C:\AGVN\ap (PowerShell). Cần: khoá ký (keystore.properties) và GitHub CLI đã đăng nhập
  (winget install GitHub.cli, rồi gh auth login một lần). Commit đang dựng phải đã push lên GitHub.

  Bản thử nghiệm (mặc định) vào release "beta": chỉ máy bật "Nhận bản thử nghiệm" mới thấy.
  Bản chính thức (-Stable) tạo release v<phiên bản>: mọi máy đều thấy.
  Mã phiên bản (versionCode) tự tăng hơn mọi bản đã đăng, vì Android chỉ cài đè bản có mã lớn hơn.

.EXAMPLE
  .\tools\agvn\dang-ban-cap-nhat.ps1 -Notes "Sửa nút ? và chuột phải"
.EXAMPLE
  .\tools\agvn\dang-ban-cap-nhat.ps1 -Stable -Version 0.1.3 -NotesFile docs\agvn\release\ghi-chu-phat-hanh-v0.1.3.txt
#>
param(
    [switch]$Stable,
    [string]$Version,
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
if ($Stable -and ($Version -notmatch '^\d+\.\d+\.\d+$')) { Stop-With "Bản chính thức cần -Version dạng 0.1.3" }
if ($NotesFile) { $Notes = (Get-Content -Encoding UTF8 -Raw -ErrorAction Stop $NotesFile).Trim() }

$commit = (git rev-parse HEAD).Trim()
if (-not $DryRun -and -not (git branch -r --contains $commit)) { Stop-With "Commit $commit chưa được push lên GitHub." }
if (git status --porcelain --untracked-files=no) { Write-Host "Chú ý: có thay đổi chưa commit, bản dựng sẽ gồm cả chúng." -ForegroundColor Yellow }

# Version: one more than anything already published, so every phone can install it over its current version
$props = Get-Content gradle.properties
$baseCode = [int](($props | Select-String '^AGVN_VERSION_CODE=(\d+)').Matches[0].Groups[1].Value)
$baseName = ($props | Select-String '^AGVN_VERSION_NAME=(.+)$').Matches[0].Groups[1].Value.Trim()
$published = @((Get-PublishedCode "$Releases/latest/download/agvn-update.txt"), (Get-PublishedCode "$Releases/download/beta/agvn-update.txt"), $baseCode)
$code = [int](($published | Measure-Object -Maximum).Maximum) + 1
if ($Stable) { $name = $Version; $tag = "v$Version" } else { $name = "$baseName-beta.$code"; $tag = "beta" }
if (-not $Notes) { $Notes = if ($Stable) { "AGVN Player $name" } else { "Bản thử nghiệm $name" } }
Write-Host "Dựng AGVN Player $name (mã $code) cho release '$tag'..." -ForegroundColor Cyan

$env:AGVN_VERSION_CODE = "$code"
$env:AGVN_VERSION_NAME = $name
if (-not $env:AGVN_KEYSTORE_PROPS) { $env:AGVN_KEYSTORE_PROPS = $Keystore }
& .\gradlew.bat testReleaseUnitTest assembleRelease --no-daemon "-PagvnRequireReleaseSigning=true"
if ($LASTEXITCODE -ne 0) { Stop-With "Dựng APK thất bại (xem lỗi ở trên)." }

$out = "build\agvn-update"
New-Item -ItemType Directory -Force $out -ErrorAction Stop | Out-Null
$apkName = "AGVN-Player-$name.apk"
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

if ($Stable) {
    gh release create $tag $apk $manifest --repo $Repo --target $commit --title "AGVN Player $name" --notes-file $notesFile --latest
    if ($LASTEXITCODE -ne 0) { Stop-With "Không tạo được release $tag." }
} else {
    cmd /c "gh release view beta --repo $Repo >nul 2>&1"
    if ($LASTEXITCODE -ne 0) {
        gh release create beta --repo $Repo --prerelease --target $commit --title "AGVN Player bản thử nghiệm" --notes "Bản thử nghiệm."
        if ($LASTEXITCODE -ne 0) { Stop-With "Không tạo được release beta." }
    }
    $old = @(gh release view beta --repo $Repo --json assets --jq ".assets[].name") | Where-Object { $_ -like "AGVN-Player-*.apk" -and $_ -ne $apkName }
    # APK first, then the file that points to it, so phones never see a missing APK; old test APKs go last
    gh release upload beta $apk --repo $Repo --clobber
    if ($LASTEXITCODE -ne 0) { Stop-With "Không tải được APK lên release beta." }
    gh release upload beta $manifest --repo $Repo --clobber
    if ($LASTEXITCODE -ne 0) { Stop-With "Không tải được agvn-update.txt lên release beta." }
    foreach ($asset in $old) { gh release delete-asset beta $asset --repo $Repo --yes }
    [IO.File]::WriteAllText((Resolve-Path $out).Path + "\ghi-chu.txt", "Bản thử nghiệm $name (commit $commit).`n`n$Notes", $utf8)
    gh release edit beta --repo $Repo --notes-file $notesFile
}
Write-Host "Xong. Trên điện thoại: Cài đặt > Cập nhật ứng dụng > Kiểm tra cập nhật." -ForegroundColor Green
if ($Stable) { Write-Host "Nhớ ghi AGVN_VERSION_CODE=$code và AGVN_VERSION_NAME=$name vào gradle.properties rồi commit." -ForegroundColor Yellow }
