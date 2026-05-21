#!/usr/bin/env bash
# Flip docs/governance/contracts/*.md `status: skeleton` to `status: active`
# after Wave 2 populates the Active Rules tables. Idempotent.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

for f in docs/governance/contracts/*.md; do
  [[ -f "$f" ]] || continue
  if grep -q '^status: skeleton$' "$f"; then
    sed -i 's/^status: skeleton$/status: active/' "$f"
    echo "FLIPPED $f"
  else
    echo "OK      $f (not skeleton)"
  fi
done

echo "---"
grep -H '^status:' docs/governance/contracts/*.md
