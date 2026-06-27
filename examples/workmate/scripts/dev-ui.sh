#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/workmate-ui"

if [[ ! -d node_modules ]]; then
  npm install
fi

npm run dev -- --port 5174
