#!/usr/bin/env bash
# Regenerate PNG/SVG exports from Mermaid (.mmd) sources.
set -euo pipefail
cd "$(dirname "$0")"
CONFIG="${CONFIG:-mermaid-export-config.json}"
MMCC="@mermaid-js/mermaid-cli@11.4.0"
for f in *.mmd; do
  base="${f%.mmd}"
  echo "Exporting $base ..."
  npx --yes "$MMCC" -i "$f" -o "${base}.svg" -b white -c "$CONFIG"
  npx --yes "$MMCC" -i "$f" -o "${base}.png" -b white -s 2 -c "$CONFIG"
  npx --yes "$MMCC" -i "$f" -o "${base}-word.png" -b white -s 4 -c "$CONFIG"
done
echo "Done."
