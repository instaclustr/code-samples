#!/usr/bin/env bash
# Regenerate PNG exports from Mermaid (.mmd) sources.
# Requires: npx (Node.js). Uses @mermaid-js/mermaid-cli.
set -euo pipefail
cd "$(dirname "$0")"
CONFIG="${CONFIG:-mermaid-export-config.json}"
MMCC="@mermaid-js/mermaid-cli@11.4.0"
for f in *.mmd; do
  base="${f%.mmd}"
  echo "Exporting $base ..."
  npx --yes "$MMCC" -i "$f" -o "${base}.png" -b white -s 2 -c "$CONFIG"
  npx --yes "$MMCC" -i "$f" -o "${base}-word.png" -b white -s 4 -c "$CONFIG"
done
echo "Done. See README.md for *-word.png usage (Word/print)."
