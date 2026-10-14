param([string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot),[Parameter(Mandatory=$true)][string]$OutputPath,[int]$Runs=3,[int]$Port=31995,[ValidateSet('legacy','unified')][string]$Mode='unified')
$ErrorActionPreference='Stop'
& node (Join-Path $ProjectRoot 'scripts/measure-launcher-startup.mjs') $ProjectRoot $OutputPath $Mode $Port $Runs
if($LASTEXITCODE -ne 0){throw 'Startup measurement failed'}
