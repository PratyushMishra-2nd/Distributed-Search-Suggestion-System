# Windows variant of fetch-dataset.sh. Downloads a Wikipedia pageviews hourly
# dump and stages it at backend/data/pageviews.gz for the backend to load.
#
# Usage: powershell -File scripts/fetch-dataset.ps1 [YEAR] [MONTH] [DAY] [HOUR]
param(
  [string]$Year  = "2024",
  [string]$Month = "11",
  [string]$Day   = "01",
  [string]$Hour  = "12"
)
$ErrorActionPreference = "Stop"

$file = "pageviews-$Year$Month$Day-${Hour}0000.gz"
$url  = "https://dumps.wikimedia.org/other/pageviews/$Year/$Year-$Month/$file"

$destDir = Join-Path $PSScriptRoot "..\backend\data"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$out = Join-Path $destDir "pageviews.gz"

Write-Host "Downloading $url"
Invoke-WebRequest -Uri $url -OutFile $out
Write-Host "Saved -> $out"
Write-Host "Backend will load it on next start (gzip is read directly)."
