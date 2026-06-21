#!/usr/bin/env bash
# Download the public ORCAS dataset (Microsoft's Open Resource for Click
# Analysis in Search) and stage it for the backend.
#
# Each TSV row is "<queryId>\t<query>\t<docId>\t<url>" — one observed click.
# The loader aggregates rows per query into a popularity count. The full dump is
# ~10M unique queries (well over the 100k minimum); app.dataset.max-rows caps
# how many rows are read so memory stays bounded.
#
# Usage: scripts/fetch-dataset.sh
set -euo pipefail

URL="https://msmarco.z22.web.core.windows.net/msmarcoranking/orcas.tsv.gz"

DEST_DIR="$(cd "$(dirname "$0")/../backend" && pwd)/data"
mkdir -p "$DEST_DIR"
OUT="${DEST_DIR}/orcas.tsv.gz"

echo "Downloading ORCAS from ${URL}"
echo "(~330 MB compressed, ~18M rows; this can take a while)"
curl -fL --retry 3 -o "$OUT" "$URL"
echo "Saved $(du -h "$OUT" | cut -f1) -> $OUT"
echo "Backend reads it on next start (gzip is read directly)."
