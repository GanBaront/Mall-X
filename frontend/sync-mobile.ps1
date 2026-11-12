# Sync mobile/src (CLI project, browser H5 preview) -> mobile-hbx (HBuilderX project for emulator/real device).
# Usage: powershell -ExecutionPolicy Bypass -File sync-mobile.ps1
# NOTE: kept ASCII-only because PowerShell 5.1 mis-reads UTF-8 (no BOM) literals as ANSI.

$root = $PSScriptRoot
$src = Join-Path $root 'uniapp\mobile\src'
$dst = Join-Path $root 'uniapp\mobile-hbx'

if (-not (Test-Path $src)) {
    Write-Host "Source not found: $src" -ForegroundColor Red
    exit 1
}

# /MIR mirror sync (extra files in target are removed); exclude node_modules and HBuilderX runtime dirs
robocopy $src $dst /MIR /XD node_modules unpackage .hbuilderx /NFL /NDL /NJH /NJS

# robocopy exit code < 8 means success
if ($LASTEXITCODE -lt 8) {
    Write-Host 'SYNC OK: mobile/src -> mobile-hbx' -ForegroundColor Green
    exit 0
} else {
    Write-Host "SYNC FAILED, robocopy exit: $LASTEXITCODE" -ForegroundColor Red
    exit 1
}
