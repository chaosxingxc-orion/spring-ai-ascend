#!/usr/bin/env bash
# Strip the "Skeleton — populated in rc21 Wave 2" blockquote from each
# phase contract once Wave 2 populates the tables.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

for f in docs/governance/contracts/*.md; do
  [[ -f "$f" ]] || continue
  # Use awk to drop the blockquote starting with "> **Skeleton" through the
  # first blank line that follows.
  awk '
    BEGIN { in_block = 0; just_dropped = 0 }
    /^> \*\*Skeleton/ { in_block = 1; just_dropped = 1; next }
    in_block && /^>/ { next }
    in_block && /^[[:space:]]*$/ { in_block = 0; next }
    in_block { in_block = 0 }
    { print }
  ' "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"
  echo "STRIPPED $f"
done
