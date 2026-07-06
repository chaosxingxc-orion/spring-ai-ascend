"""
versatile_main.py — 可配置 Mock Versatile 子 agent 上游。

启动：
    python versatile_main.py

工作流端点：
    POST http://127.0.0.1:30001/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}

意图定义位于 workflows/*.json，匹配规则 + 输出帧序列均可增删改。
"""
from __future__ import annotations

import logging
import os
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

_ROOT = Path(__file__).resolve().parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

try:
    from dotenv import load_dotenv
except ImportError:
    def load_dotenv(*args: object, **kwargs: object) -> bool:
        return False
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse, StreamingResponse

from engine.hooks import (
    count_product_list_entries,
    get_balance_states,
    get_initial_balances,
    get_transfer_counters,
    MOCK_PRODUCT_FILTER,
    reset_transfer_counter,
)
from engine.loader import WorkflowStore, load_server_config
from engine.matcher import WorkflowMatcher
from engine.streamer import stream_workflow

_env_path = Path(__file__).resolve().parent.parent / ".env"
if _env_path.exists():
    load_dotenv(dotenv_path=_env_path, override=False)
else:
    load_dotenv(override=False)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("mock_versatile")

SERVER_CONFIG = load_server_config()
STORE = WorkflowStore(SERVER_CONFIG)
MATCHER = WorkflowMatcher(STORE)

app = FastAPI(
    title="Mock Versatile Workflow",
    description="JSON-driven mock Versatile sub-agent upstream",
    version="1.0.0",
)


def extract_inputs(body: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(body, dict):
        return {}
    custom_data = body.get("custom_data")
    if isinstance(custom_data, dict):
        inputs = custom_data.get("inputs")
        if isinstance(inputs, dict):
            return inputs
    inputs = body.get("inputs")
    if isinstance(inputs, dict):
        return inputs
    input_obj = body.get("input")
    if isinstance(input_obj, dict):
        return input_obj
    if body.get("query"):
        return {"query": body.get("query", "")}
    return body


def build_context(inputs: dict[str, Any], conversation_id: str) -> dict[str, Any]:
    return {
        "inputs": inputs,
        "query": str(inputs.get("query", "") or ""),
        "conversation_id": conversation_id,
        "menu_type": str(inputs.get("menu_type", "") or ""),
        "menu_confirm": inputs.get("menu_confirm"),
        "config": STORE.server_config,
    }


def validate_cookie(cookie: str) -> tuple[bool, str]:
    if not cookie:
        return False, "Cookie header is required"
    if "AGENT_SID=" not in cookie:
        return False, "AGENT_SID is required in Cookie"
    match = re.search(r"AGENT_SID=([^;]+)", cookie)
    if not match:
        return False, "Invalid AGENT_SID format"
    return True, ""


@app.get("/health")
async def health() -> dict[str, Any]:
    licai, chuxu = get_initial_balances()
    cfg = STORE.server_config
    return {
        "status": "healthy",
        "service": "versatile_main",
        "version": "1.0.0",
        "workflows_loaded": list(STORE.workflows.keys()),
        "load_errors": STORE.load_errors,
        "config": {
            "sse_format": cfg.get("sse_format"),
            "result_node": cfg.get("result_node"),
            "features": cfg.get("features"),
            "balance_delay_seconds": int(os.environ.get("MOCK_BALANCE_DELAY_SECONDS", "0")),
            "transfer_amounts_sequence": os.environ.get("MOCK_TRANSFER_AMOUNTS", ""),
            "transfer_mode": os.environ.get("MOCK_TRANSFER_MODE", "cycle"),
            "initial_licai_balance": licai,
            "initial_chuxu_balance": chuxu,
        },
        "transfer_counters": get_transfer_counters(),
        "balance_states": get_balance_states(),
        "product_list_count": count_product_list_entries(MOCK_PRODUCT_FILTER.get("productList")),
        "timestamp": datetime.now().isoformat(),
    }


@app.post("/admin/reload")
async def reload_workflows() -> JSONResponse:
    STORE.reload()
    return JSONResponse(
        {
            "status": "ok",
            "workflows_loaded": list(STORE.workflows.keys()),
            "load_errors": STORE.load_errors,
        }
    )


@app.post("/reset_transfer_counter")
async def reset_counter_endpoint(session_key: str | None = None) -> JSONResponse:
    reset_transfer_counter(session_key)
    return JSONResponse(
        {
            "status": "ok",
            "message": f"转账计数器已重置: {session_key or 'all'}",
            "current_counters": get_transfer_counters(),
        }
    )


@app.post("/v1/chat/{conversation_id}")
async def mock_workflow_legacy(conversation_id: str, request: Request) -> StreamingResponse:
    return await _handle_workflow(conversation_id, request)


@app.post("/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}", response_model=None)
async def mock_workflow_new(
    workflow_id: str,
    conversation_id: str,
    request: Request,
    cookie: str | None = Header(None, alias="Cookie"),
) -> StreamingResponse | JSONResponse:
    skip_auth = os.environ.get("MOCK_SKIP_COOKIE_AUTH", "true").lower() == "true"
    if not skip_auth and SERVER_CONFIG.get("features", {}).get("cookie_auth"):
        is_valid, error_msg = validate_cookie(cookie or "")
        if not is_valid:
            raise HTTPException(status_code=401, detail={"error": "Unauthorized", "message": error_msg})

    logger.info(
        "workflow request: workflow_id=%s conversation_id=%s",
        workflow_id,
        conversation_id,
    )
    return await _handle_workflow(conversation_id, request)


async def _handle_workflow(conversation_id: str, request: Request) -> StreamingResponse:
    try:
        body = await request.json()
    except Exception:
        body = {}

    inputs = extract_inputs(body if isinstance(body, dict) else {})
    ctx = build_context(inputs, conversation_id)
    workflow = MATCHER.resolve(inputs)

    logger.info(
        "route: workflow=%s query=%r menu_type=%r menu_confirm=%s",
        workflow.get("id"),
        ctx["query"][:80],
        ctx["menu_type"],
        ctx.get("menu_confirm"),
    )

    generator = stream_workflow(workflow, ctx, STORE.server_config)
    return StreamingResponse(generator, media_type="text/event-stream")


if __name__ == "__main__":
    import uvicorn

    port = int(os.environ.get("MOCK_SERVER_PORT", SERVER_CONFIG.get("port", 30001)))
    host = os.environ.get("MOCK_SERVER_HOST", SERVER_CONFIG.get("host", "127.0.0.1"))

    print("=" * 60)
    print("Mock Versatile Workflow (versatile_main.py) 启动中...")
    print(f"服务地址: http://{host}:{port}")
    print(f"健康检查: http://{host}:{port}/health")
    print(f"热加载: POST http://{host}:{port}/admin/reload")
    print(f"工作流端点: http://{host}:{port}/v1/0/agent-manager/workflows/{{workflow_id}}/conversations/{{conversation_id}}")
    print(f"已加载意图: {', '.join(STORE.workflows.keys())}")
    if STORE.load_errors:
        print(f"加载错误: {STORE.load_errors}")
    print("=" * 60)

    uvicorn.run(app, host=host, port=port, log_level="info")
