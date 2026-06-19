#!/usr/bin/env bash
# Download a public Wikipedia pageviews hourly dump and stage it for the backend.
#
# Each row is "<project> <title> <viewcount> <bytes>". One hourly English dump
# contains well over 100k titles with counts (satisfies the dataset size req).
# The backend reads ./data/pageviews(.gz) relative to its working dir.
#
# Usage:
#   scripts/fetch-dataset.sh                 # default date below
#   scripts/fetch-dataset.sh 2024 11 01 12   # YYYY MM DD HH (UTC)
set -euo pipefail

YEAR="${1:-2024}"
MONTH="${2:-11}"
DAY="${3:-01}"
HOUR="${4:-12}"

FILE="pageviews-${YEAR}${MONTH}${DAY}-${HOUR}0000.gz"
URL="https://dumps.wikimedia.org/other/pageviews/${YEAR}/${YEAR}-${MONTH}/${FILE}"

DEST_DIR="$(cd "$(dirname "$0")/../backend" && pwd)/data"
mkdir -p "$DEST_DIR"
OUT="${DEST_DIR}/pageviews.gz"

echo "Downloading ${URL}"
curl -fL --retry 3 -o "$OUT" "$URL"
echo "Saved $(du -h "$OUT" | cut -f1) -> $OUT"
echo "Backend will load it on next start (gzip is read directly)."
