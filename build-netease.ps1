[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$modernTasks = @(
    ':netease-1.21.8:build'
    ':netease-1.21:build'
    ':netease-1.20.6:build'
    ':netease-1.20:build'
    ':netease-1.19.2:build'
    ':netease-1.18:build'
    ':netease-1.16:build'
)

& "$PSScriptRoot\gradlew.bat" @modernTasks '--no-daemon'
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$legacyRoot = "$PSScriptRoot\legacy\netease-forge"
& "$legacyRoot\gradlew.bat" '-p' $legacyRoot 'build' '--no-daemon'
exit $LASTEXITCODE
