"""SSE stream generator from workflow JSON definitions."""
from __future__ import annotations

import asyncio
import json
import logging
import os
from typing import Any, AsyncGenerator

from .hooks import call_hook
from .templates import render_dict, render_value

logger = logging.getLogger("mock_versatile.streamer")


def _format_sse(payload: dict[str, Any], sse_format: str) -> str:
    if sse_format == "wrapped":
        event = payload.pop("_event", "message")
        body = {"event": event, "data": payload}
    else:
        body = payload
    return "data: " + json.dumps(body, ensure_ascii=False, separators=(",", ":")) + "\n\n"


def _format_error_sse(code: str, message: str, sse_format: str) -> str:
    if sse_format == "wrapped":
        body = {"event": "error", "data": {"code": code, "message": message}}
    else:
        body = {"event": "error", "code": code, "message": message}
    return "data: " + json.dumps(body, ensure_ascii=False, separators=(",", ":")) + "\n\n"


def _resolve_text(frame: dict[str, Any], ctx: dict[str, Any]) -> str:
    if "text_hook" in frame:
        return str(call_hook(frame["text_hook"], ctx))
    if "text_template" in frame:
        tpl = render_value(frame["text_template"], ctx)
        if tpl.startswith("{{hooks.") and tpl.endswith("}}"):
            hook_name = tpl[len("{{hooks.") : -2]
            return str(call_hook(hook_name, ctx))
        return tpl
    if "text" in frame:
        return render_value(frame["text"], ctx)
    return ""


async def _yield_balance_delay_frames(
    ctx: dict[str, Any],
    sse_format: str,
    llm_node: str,
) -> AsyncGenerator[str, None]:
    delay_seconds = int(os.environ.get("MOCK_BALANCE_DELAY_SECONDS", "0"))
    if delay_seconds <= 0:
        return

    elapsed_wait = 0
    progress_interval = 10
    conversation_id = ctx.get("conversation_id", "")
    logger.info(
        "balance delay simulation: conversation_id=%s delay=%ss",
        conversation_id,
        delay_seconds,
    )

    while elapsed_wait < delay_seconds:
        sleep_time = min(progress_interval, delay_seconds - elapsed_wait)
        await asyncio.sleep(sleep_time)
        elapsed_wait += sleep_time
        remaining = delay_seconds - elapsed_wait
        if remaining > 0:
            progress_msg = (
                f"余额查询处理中，请稍候...（已等待 {elapsed_wait} 秒，剩余约 {remaining} 秒）"
            )
        else:
            progress_msg = f"余额查询处理完成，正在返回结果...（已等待 {elapsed_wait} 秒）"
        payload = {
            "text": progress_msg,
            "node_type": "LLM",
            "node_name": llm_node,
        }
        yield _format_sse(payload, sse_format)


async def stream_workflow(
    workflow: dict[str, Any],
    ctx: dict[str, Any],
    server_config: dict[str, Any],
) -> AsyncGenerator[str, None]:
    sse_format = server_config.get("sse_format", "raw")
    llm_node = server_config.get("llm_node", "LLMNode")
    output = workflow.get("output", {})

    error_def = output.get("error")
    if error_def:
        code = render_value(error_def.get("code", "ERROR"), ctx)
        message = render_value(error_def.get("message", ""), ctx)
        yield _format_error_sse(code, message, sse_format)
        return

    frames = output.get("frames", [])
    omit_end = bool(output.get("omit_end", False))
    run_balance_delay = bool(output.get("balance_delay", False))

    if run_balance_delay:
        async for chunk in _yield_balance_delay_frames(ctx, sse_format, llm_node):
            yield chunk

    for frame in frames:
        if frame.get("skip_if") == "not_enable_interrupt_menus":
            if not server_config.get("features", {}).get("enable_interrupt_menus", False):
                continue

        delay_ms = int(frame.get("delay_ms", 50))
        await asyncio.sleep(delay_ms / 1000.0)

        kind = frame.get("kind", "node")

        if kind == "raw_json":
            hook_name = frame.get("hook", "")
            raw_payload = call_hook(hook_name, ctx)
            if not isinstance(raw_payload, dict):
                raise TypeError(f"hook {hook_name!r} must return dict for raw_json frame")
            yield _format_sse(raw_payload, sse_format)
            continue

        rendered = render_dict(
            {k: v for k, v in frame.items() if k not in ("delay_ms", "kind", "text_hook", "text_template", "skip_if")},
            ctx,
        )

        text = _resolve_text(frame, ctx)
        if text:
            rendered["text"] = text

        if "conversation_id" not in rendered and frame.get("node_type") == "Start":
            rendered["conversation_id"] = ctx.get("conversation_id", "")

        yield _format_sse(rendered, sse_format)

    if not omit_end:
        end_name = output.get("end_node_name", "EndNode")
        end_payload = {"node_type": "End", "node_name": end_name}
        yield _format_sse(end_payload, sse_format)

    logger.info("workflow %s streamed for conversation_id=%s", workflow.get("id"), ctx.get("conversation_id"))
