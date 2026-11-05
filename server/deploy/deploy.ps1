# Deploy web pairing portal to Server1 (minogatvbox.duckdns.org)
# Usage: .\server\deploy\deploy.ps1 -Target ubuntu@130.61.183.21 [-Key C:\path\to\key]
param(
    [Parameter(Mandatory = $true)][string]$Target,
    [string]$Key
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
$javac = Join-Path $root '.jdk17\jdk-17.0.20.1+1\bin\javac.exe'
$out = Join-Path $root 'server\deploy\out'

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $out | Out-Null
& $javac --release 17 -encoding UTF-8 -d $out (Join-Path $root 'server\IptvMockServer.java')
Move-Item (Join-Path $out 'server\*.class') $out
Remove-Item (Join-Path $out 'server') -Recurse -Force
Copy-Item (Join-Path $root 'web\index.html'), (Join-Path $root 'web\config.json') $out
$deployFiles = 'minogatvbox.service', 'minogatvbox.nginx.conf', 'minogatvbox.nginx.ssl.conf', 'minogatvbox-common.conf', 'remote-install.sh'
foreach ($f in $deployFiles) {
    # Copy with LF line endings for Linux
    $txt = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $f)) -replace "`r`n", "`n"
    [IO.File]::WriteAllText((Join-Path $out $f), $txt, (New-Object Text.UTF8Encoding $false))
}

$sshArgs = @('-o', 'ConnectTimeout=15')
if ($Key) { $sshArgs += @('-i', $Key) }

& ssh @sshArgs $Target 'rm -rf /tmp/minogatvbox-deploy && mkdir -p /tmp/minogatvbox-deploy'
& scp @sshArgs "$out\*" "${Target}:/tmp/minogatvbox-deploy/"
& ssh @sshArgs $Target 'bash /tmp/minogatvbox-deploy/remote-install.sh'
