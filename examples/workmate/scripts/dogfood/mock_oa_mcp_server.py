#!/usr/bin/env python3
"""Minimal stdio MCP server for WorkMate dogfood — mock internal OA with submit_credit_memo."""

import json
import sys

PROTOCOL_VERSION = "2024-11-05"
SERVER_INFO = {"name": "workmate-mock-oa", "version": "0.1.0"}

TOOLS = [
    {
        "name": "submit_credit_memo",
        "description": "Submit a credit memo to the internal OA approval workflow.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "description": "Business operation label, e.g. 提交授信审批",
                },
                "companyName": {"type": "string", "description": "Enterprise name"},
                "customerName": {"type": "string", "description": "Customer name"},
                "creditAmount": {"type": "string", "description": "Credit limit amount"},
            },
            "required": ["companyName", "creditAmount"],
        },
    }
]


def write_message(msg: dict) -> None:
    body = json.dumps(msg, ensure_ascii=False)
    encoded = body.encode("utf-8")
    header = f"Content-Length: {len(encoded)}\r\n\r\n"
    sys.stdout.buffer.write(header.encode("ascii"))
    sys.stdout.buffer.write(encoded)
    sys.stdout.buffer.flush()


def read_message() -> dict | None:
    stdin = sys.stdin.buffer
    headers: dict[str, str] = {}
    while True:
        line = stdin.readline()
        if not line:
            return None
        if line in (b"\r\n", b"\n"):
            break
        if b":" in line:
            key, value = line.decode("ascii", errors="replace").split(":", 1)
            headers[key.strip().lower()] = value.strip()
    length = int(headers.get("content-length", "0") or "0")
    if length <= 0:
        return None
    raw = stdin.read(length)
    if not raw:
        return None
    return json.loads(raw.decode("utf-8"))


def ok(id_: object, result: object) -> dict:
    return {"jsonrpc": "2.0", "id": id_, "result": result}


def tool_result_text(text: str, is_error: bool = False) -> dict:
    return {
        "content": [{"type": "text", "text": text}],
        "isError": is_error,
    }


def handle(msg: dict) -> None:
    method = msg.get("method")
    id_ = msg.get("id")
    params = msg.get("params") or {}

    if method == "initialize":
        write_message(
            ok(
                id_,
                {
                    "protocolVersion": PROTOCOL_VERSION,
                    "capabilities": {"tools": {"listChanged": False}},
                    "serverInfo": SERVER_INFO,
                },
            )
        )
        return

    if method == "notifications/initialized":
        return

    if method == "tools/list":
        write_message(ok(id_, {"tools": TOOLS}))
        return

    if method == "tools/call":
        name = params.get("name")
        arguments = params.get("arguments") or {}
        if name != "submit_credit_memo":
            write_message(
                ok(id_, tool_result_text(f"Unknown tool: {name}", is_error=True))
            )
            return
        company = arguments.get("companyName") or arguments.get("customerName") or "未知企业"
        amount = arguments.get("creditAmount") or arguments.get("amount") or "未知额度"
        write_message(
            ok(
                id_,
                tool_result_text(
                    f"OA mock accepted credit memo for {company} amount {amount}"
                ),
            )
        )
        return

    if id_ is not None:
        write_message(
            {
                "jsonrpc": "2.0",
                "id": id_,
                "error": {"code": -32601, "message": f"Method not found: {method}"},
            }
        )


def main() -> None:
    while True:
        msg = read_message()
        if msg is None:
            break
        handle(msg)


if __name__ == "__main__":
    main()
