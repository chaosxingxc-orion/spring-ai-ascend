#!/usr/bin/env bash
# Formal release transaction validator.
#
# This is a standalone release-readiness gate. It is intentionally separate
# from the canonical architecture-sync gate until the architecture team chooses
# to promote it into a numbered Gate Rule and re-baseline the rule manifest.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${GATE_PYTHON_BIN:-}"

if [[ -z "$PYTHON_BIN" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
  else
    echo "FAIL: formal_release_transaction -- neither python3 nor python found on PATH"
    echo "GATE: FAIL"
    exit 1
  fi
fi

exec "$PYTHON_BIN" "$ROOT_DIR/gate/lib/check_formal_release_transaction.py" --root "$ROOT_DIR" "$@"
