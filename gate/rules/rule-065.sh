#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 65 — module_metadata_pom_dep_parity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 65 — module_metadata_pom_dep_parity (enforcer E95, G2 prevention)
#
# For each <module>/module-metadata.yaml, every com.huawei.ascend sibling
# artifact declared in <module>/pom.xml's <dependencies> MUST appear in
# allowed_dependencies of the metadata. Catches drift where a developer
# adds a dep to the pom but forgets to update the metadata declaration.
# ---------------------------------------------------------------------------
_r65_fail=0
while IFS= read -r _r65_meta; do
  [[ -z "$_r65_meta" ]] && continue
  _r65_mod_dir="$(dirname "$_r65_meta")"
  _r65_pom="${_r65_mod_dir}/pom.xml"
  [[ -f "$_r65_pom" ]] || continue
  # Extract com.huawei.ascend sibling deps from pom — only inside <dependency> blocks
  # (excludes the <parent> block at top, which would otherwise be a false positive).
  # Skip <dependencyManagement> block — those are managed versions for downstream
  # modules (BoM-style), not direct compile-time deps of the current module.
  _r65_pom_deps=$(awk '
    /<dependencyManagement>/ { in_mgmt=1; next }
    /<\/dependencyManagement>/ { in_mgmt=0; next }
    !in_mgmt && /<dependency>/ { in_dep=1; want=0; next }
    /<\/dependency>/ { in_dep=0; want=0; next }
    in_dep && /<groupId>ascend\.springai<\/groupId>/ { want=1; next }
    in_dep && want && /<artifactId>/ {
      gsub(/^[[:space:]]*<artifactId>/, "")
      gsub(/<\/artifactId>.*/, "")
      print
      want=0
    }
  ' "$_r65_pom" | sort -u)
  # Extract allowed_dependencies block entries from metadata
  _r65_meta_allowed=$(awk '/^allowed_dependencies:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r65_meta" | sort -u)
  while IFS= read -r _r65_dep; do
    [[ -z "$_r65_dep" ]] && continue
    if ! echo "$_r65_meta_allowed" | grep -qxF "$_r65_dep"; then
      fail_rule "module_metadata_pom_dep_parity" "$_r65_pom declares dependency on '$_r65_dep' (com.huawei.ascend sibling) but $_r65_meta allowed_dependencies does not list it (G2 prevention)"
      _r65_fail=1
    fi
  done <<< "$_r65_pom_deps"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r65_fail -eq 0 ]]; then pass_rule "module_metadata_pom_dep_parity"; fi

# ---------------------------------------------------------------------------
