# Windows variant of fetch-dataset.sh. Downloads the public ORCAS dataset
# (Microsoft's Open Resource for Click Analysis in Search) and stages it at
# backend/data/orcas.tsv.gz for the backend to load.
#
# Each TSV row is "<queryId>\t<query>\t<docId>\t<url>" — one observed click;
# the loader aggregates rows per query into a popularity count.
#
# Usage: powershell -File scripts/fetch-dataset.ps1
$ErrorActionPreference = "Stop"

$url = "https://msmarco.z22.web.core.windows.net/msmarcoranking/orcas.tsv.gz"

$destDir = Join-Path $PSScriptRoot "..\backend\data"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$out = Join-Path $destDir "orcas.tsv.gz"

Write-Host "Downloading ORCAS from $url"
Write-Host "(~330 MB compressed, ~18M rows; this can take a while)"
Invoke-WebRequest -Uri $url -OutFile $out
Write-Host "Saved -> $out"
Write-Host "Backend reads it on next start (gzip is read directly)."
