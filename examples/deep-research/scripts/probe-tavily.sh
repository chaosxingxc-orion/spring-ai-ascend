#!/bin/bash
# One-off probe to validate a Tavily API key + capture response samples for future
# stub fixture seeding. NOT production code. No jq dependency (uses python3).
#
# Usage:
#   export TAVILY_API_KEY=tvly-xxxxx
#   # network: try direct first; if behind a corporate proxy, set https_proxy
#   # examples:
#   #   export https_proxy=http://proxynj.huawei.com:8080
#   #   export https_proxy=http://user:pass@proxynj.huawei.com:8080   (URL-encode special chars)
#   bash probe-tavily.sh
#
# Optional: override output dir via OUT_DIR env var. Default /tmp/tavily-samples.

set -uo pipefail   # NOT -e: we want to keep probing even if one request fails

if [[ -z "${TAVILY_API_KEY:-}" ]]; then
  echo "ERROR: set TAVILY_API_KEY first" >&2
  exit 1
fi

PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
if ! command -v "$PY" >/dev/null 2>&1; then
  echo "ERROR: need python3 or python on PATH" >&2
  exit 1
fi

OUT_DIR="${OUT_DIR:-/tmp/tavily-samples}"
mkdir -p "$OUT_DIR"
echo "samples will be saved to: $OUT_DIR"
echo "proxy env:  http_proxy=${http_proxy:-<unset>}  https_proxy=${https_proxy:-<unset>}"
echo ""

probe() {
  local label="$1"
  local body_json="$2"
  local out_file="$OUT_DIR/${label}.json"

  # Ensure file exists so later head / python don't ENOENT on curl failure.
  : > "$out_file"

  echo "================================================================"
  echo "[$label]"
  echo "  request: $body_json"

  local http_code
  http_code=$(curl -sS -o "$out_file" -w "%{http_code}" \
    --connect-timeout 10 --max-time 60 \
    -X POST https://api.tavily.com/search \
    -H "Content-Type: application/json" \
    --data "$body_json" 2>>"$OUT_DIR/curl-errors.log") || http_code="curl-failed-$?"

  echo "  http:    $http_code"
  echo "  saved:   $out_file ($(wc -c <"$out_file") bytes)"

  if [[ "$http_code" == "200" ]]; then
    echo "  --- response field probe ---"
    "$PY" - "$out_file" <<'PYEOF'
import json, sys
try:
    with open(sys.argv[1], encoding='utf-8') as f:
        data = json.load(f)
except Exception as e:
    print(f"  parse-error: {e}")
    sys.exit(0)
print(f"  top-level keys:       {', '.join(data.keys())}")
results = data.get("results", [])
print(f"  results count:        {len(results)}")
if results:
    r = results[0]
    print(f"  result[0] keys:       {', '.join(r.keys())}")
    print(f"  result[0].url:        {r.get('url', 'MISSING')}")
    print(f"  result[0].title:      {r.get('title', 'MISSING')[:80]}")
    print(f"  result[0].score:      {r.get('score', 'MISSING')}")
    content = r.get('content') or ''
    print(f"  result[0].content len:{len(content)}")
    raw = r.get('raw_content') or ''
    print(f"  result[0].raw_content len:{len(raw)}")
print(f"  has answer:           {'answer' in data}")
if 'answer' in data:
    ans = data['answer'] or ''
    print(f"  answer (first 200):   {ans[:200]}")
print(f"  response_time:        {data.get('response_time', 'n/a')}")
PYEOF
  else
    echo "  --- body (first 400 chars) ---"
    if [[ -s "$out_file" ]]; then
      head -c 400 "$out_file"
      echo ""
    else
      echo "  (empty - curl wrote nothing; see $OUT_DIR/curl-errors.log)"
    fi
  fi
  echo ""
}

KEY="$TAVILY_API_KEY"

# 1. Basic depth, our actual research-style query (Chinese)
probe "01-basic-zh" "$(cat <<EOF
{"api_key":"$KEY","query":"国内大模型 API 定价对比 火山方舟 阿里百炼 智谱 Kimi DeepSeek 2026","search_depth":"basic","max_results":10,"include_answer":false}
EOF
)"

# 2. Advanced depth, same query — compare quality vs. extra credit cost
probe "02-advanced-zh" "$(cat <<EOF
{"api_key":"$KEY","query":"国内大模型 API 定价对比 火山方舟 阿里百炼 智谱 Kimi DeepSeek 2026","search_depth":"advanced","max_results":10,"include_answer":false}
EOF
)"

# 3. Domain-filtered: verify include_domains works (topology §3.2 假设可用)
probe "03-include-volcengine" "$(cat <<EOF
{"api_key":"$KEY","query":"豆包大模型 API 定价","search_depth":"basic","max_results":10,"include_answer":false,"include_domains":["volcengine.com"]}
EOF
)"

# 4. include_answer=true — see Tavily's LLM-synthesized answer
probe "04-with-answer" "$(cat <<EOF
{"api_key":"$KEY","query":"DeepSeek V3 API pricing per million tokens","search_depth":"basic","max_results":5,"include_answer":true}
EOF
)"

# 5. English equivalent — see if 中文 query 是否退化
probe "05-english" "$(cat <<EOF
{"api_key":"$KEY","query":"China LLM API pricing comparison 2026 Baidu Alibaba Zhipu Moonshot","search_depth":"basic","max_results":5,"include_answer":false}
EOF
)"

echo "================================================================"
echo "Done. Samples in $OUT_DIR"
echo ""
echo "Inspect raw JSON:"
echo "  ls -la $OUT_DIR"
echo "  $PY -m json.tool $OUT_DIR/01-basic-zh.json | less"
echo ""
echo "Compare basic vs advanced URLs:"
echo "  diff <($PY -c 'import json,sys;[print(r[\"url\"]) for r in json.load(open(\"$OUT_DIR/01-basic-zh.json\"))[\"results\"]]') \\"
echo "       <($PY -c 'import json,sys;[print(r[\"url\"]) for r in json.load(open(\"$OUT_DIR/02-advanced-zh.json\"))[\"results\"]]')"