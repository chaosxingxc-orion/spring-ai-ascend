#!/usr/bin/env python3
"""Tiny OpenAI-compatible test double for Mem0 local smoke tests.

The Mem0 REST server needs an embedding provider even when the Java sample only
validates MemoryProvider plumbing. This server returns deterministic embeddings
and an empty chat-completion payload so the local Mem0 stack can run without a
real model provider.
"""

from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path.endswith("/models"):
            self._send({"object": "list", "data": [{"id": "fake-embedding"}]})
            return
        self._send({"ok": True, "path": self.path})

    def do_POST(self) -> None:
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length:
            self.rfile.read(content_length)
        if self.path.endswith("/embeddings"):
            self._send({
                "object": "list",
                "model": "fake-embedding",
                "data": [{"object": "embedding", "index": 0, "embedding": [0.001] * 1536}],
            })
            return
        if self.path.endswith("/chat/completions"):
            self._send({
                "id": "fake-chat",
                "object": "chat.completion",
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": "[]"},
                    "finish_reason": "stop",
                }],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            })
            return
        self._send({"ok": True, "path": self.path})

    def log_message(self, format: str, *args: object) -> None:
        return

    def _send(self, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=18080)
    args = parser.parse_args()
    HTTPServer((args.host, args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
