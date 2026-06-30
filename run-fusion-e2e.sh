#!/usr/bin/env bash
# =============================================================================
# run-fusion-e2e.sh — spring-ai-ascend 融合真 LLM e2e
#
# 用法（key 从 settings 文件读取，不回显）：
#   bash run-fusion-e2e.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

# 从 settings 读 key（不回显值）
SETTINGS="$HOME/.claude/settings-deepseekv4.json"
LLM_KEY=$(python3 -c "import json; print(json.load(open('$SETTINGS'))['env']['ANTHROPIC_AUTH_TOKEN'])")

export OPENJIUWEN_API_KEY="$LLM_KEY"
export OPENJIUWEN_BASE_URL="https://api.deepseek.com"
export OPENJIUWEN_MODEL="deepseek-v4-pro"

echo "============================================"
echo "spring-ai-ascend 融合真 LLM e2e"
echo "============================================"
echo "model : $OPENJIUWEN_MODEL"
echo "base  : $OPENJIUWEN_BASE_URL"
echo ""

# smoke test
echo "[smoke] testing connectivity..."
SMOKE=$(curl -s -w "\n%{http_code}" "$OPENJIUWEN_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $OPENJIUWEN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"'"$OPENJIUWEN_MODEL"'","messages":[{"role":"user","content":"ping"}],"max_tokens":5}' 2>/dev/null || true)
SMOKE_CODE=$(echo "$SMOKE" | tail -1)
case "$SMOKE_CODE" in
  200) echo "[smoke] OK (HTTP $SMOKE_CODE)" ;;
  *)   echo "[smoke] connectivity issue (HTTP $SMOKE_CODE), continuing anyway..." ;;
esac

echo ""
echo "[test] running RealLlmFusionE2eTest..."
./mvnw -pl agent-runtime test \
    -Dtest="com.huawei.ascend.runtime.engine.alpha.RealLlmFusionE2eTest" \
    -Dsurefire.failIfNoSpecifiedTests=false \
    2>&1 | grep -E "Tests run:|Failures:|fusion-e2e|fusion-multi|BUILD|完成|OK"

echo ""
echo "Done."
