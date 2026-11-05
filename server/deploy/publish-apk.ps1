# Publish the Android APK to Server1 so it can be downloaded from https://minogatvbox.duckdns.org
# Usage: .\server\deploy\publish-apk.ps1 -Target ubuntu@130.61.183.21 [-Key C:\path\to\key] [-Apk path\to.apk]
param(
    [Parameter(Mandatory = $true)][string]$Target,
    [string]$Key,
    [string]$Apk
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
if (-not $Apk) { $Apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk' }
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk (run .\gradlew.bat assembleDebug first)" }

# Read version from app/build.gradle.kts
$gradle = Get-Content (Join-Path $root 'app\build.gradle.kts') -Raw
$versionName = if ($gradle -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { '1.0.0' }
$versionCode = if ($gradle -match 'versionCode\s*=\s*(\d+)') { [int]$Matches[1] } else { 1 }

$file = Get-Item $Apk
$sha = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLower()
$out = Join-Path $root 'server\deploy\out-apk'
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $out | Out-Null
Copy-Item $Apk (Join-Path $out 'MinogaTVBox.apk')
$info = [ordered]@{
    versionName = $versionName
    versionCode = $versionCode
    sizeBytes   = $file.Length
    sha256      = $sha
    updated     = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    url         = '/download/MinogaTVBox.apk'
}
[IO.File]::WriteAllText((Join-Path $out 'version.json'), ($info | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))

$sshArgs = @('-o', 'ConnectTimeout=15')
if ($Key) { $sshArgs += @('-i', $Key) }

& ssh @sshArgs $Target 'mkdir -p /tmp/minogatvbox-apk'
& scp @sshArgs "$out\MinogaTVBox.apk" "$out\version.json" "${Target}:/tmp/minogatvbox-apk/"
& ssh @sshArgs $Target 'sudo mkdir -p /var/www/minogatvbox/download && sudo mv /tmp/minogatvbox-apk/MinogaTVBox.apk /tmp/minogatvbox-apk/version.json /var/www/minogatvbox/download/ && sudo chown -R www-data:www-data /var/www/minogatvbox && ls -la /var/www/minogatvbox/download'
Write-Output "Published v$versionName ($([math]::Round($file.Length / 1MB, 1)) MB) sha256=$sha"

