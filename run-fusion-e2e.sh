#!/usr/bin/env bash
# =============================================================================
# run-fusion-e2e.sh — spring-ai-ascend 融合真 LLM e2e（4 业务场景）
#
# 用法（key 从 settings 文件读取，不回显）：
#   bash run-fusion-e2e.sh
#   bash run-fusion-e2e.sh <test-method>  # 跑单个测试
#
# 环境变量（从 settings-deepseekv4.json 自动注入）：
#   OPENJIUWEN_API_KEY  — deepseek API key
#   OPENJIUWEN_BASE_URL — https://api.deepseek.com
#   OPENJIUWEN_MODEL    — deepseek-v4-pro
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

# 从 settings 读 key（不回显值）
SETTINGS="$HOME/.claude/settings-deepseekv4.json"
LLM_KEY=$(python3 -c "import json; print(json.load(open('$SETTINGS'))['env']['ANTHROPIC_AUTH_TOKEN'])")

export OPENJIUWEN_API_KEY="$LLM_KEY"
export OPENJIUWEN_BASE_URL="https://api.deepseek.com"
export OPENJIUWEN_MODEL="deepseek-v4-pro"

TEST_CLASS="com.huawei.ascend.runtime.engine.alpha.RealLlmFusionE2eTest"
TEST_FILTER="${1:-}"

echo "============================================"
echo "spring-ai-ascend 融合真 LLM e2e（轮13）"
echo "============================================"
echo "model : $OPENJIUWEN_MODEL"
echo "base  : $OPENJIUWEN_BASE_URL"
echo "class : $TEST_CLASS"
if [ -n "$TEST_FILTER" ]; then
    echo "method: $TEST_FILTER"
fi
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

if [ -n "$TEST_FILTER" ]; then
    ./mvnw -pl agent-runtime test \
        -Dtest="${TEST_CLASS}#${TEST_FILTER}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        2>&1 | grep -E "Tests run:|Failures:|fusion-claims|fusion-adversary|fusion-rootcause|fusion-planning|BUILD|完成|OK"
else
    ./mvnw -pl agent-runtime test \
        -Dtest="${TEST_CLASS}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        2>&1 | grep -E "Tests run:|Failures:|fusion-claims|fusion-adversary|fusion-rootcause|fusion-planning|BUILD|完成|OK"
fi

echo ""
echo "Done."
