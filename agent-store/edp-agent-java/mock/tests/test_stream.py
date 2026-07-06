"""Integration smoke tests for versatile_main streaming."""
from __future__ import annotations

import asyncio
import json
import sys
import unittest
from pathlib import Path

_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from engine.loader import WorkflowStore
from engine.matcher import WorkflowMatcher
from engine.streamer import stream_workflow


async def collect_sse(workflow_id: str, query: str, conversation_id: str = "test-conv") -> list[dict]:
    store = WorkflowStore()
    matcher = WorkflowMatcher(store)
    inputs = {"query": query}
    ctx = {
        "inputs": inputs,
        "query": query,
        "conversation_id": conversation_id,
        "menu_type": "",
        "menu_confirm": None,
        "config": store.server_config,
    }
    wf = matcher.resolve(inputs)
    assert wf["id"] == workflow_id, f"expected {workflow_id}, got {wf['id']}"

    chunks: list[dict] = []
    async for line in stream_workflow(wf, ctx, store.server_config):
        if line.startswith("data: "):
            chunks.append(json.loads(line[6:].strip()))
    return chunks


class StreamSmokeTests(unittest.TestCase):
    def test_wealth_recommend_has_qa_product_list(self) -> None:
        frames = asyncio.run(collect_sse("wealth_recommend", "帮我推荐几款稳健型理财产品"))
        qa_frames = [f for f in frames if f.get("node_type") == "QA"]
        self.assertTrue(qa_frames)
        payload = json.loads(qa_frames[0]["text"])
        self.assertIn("productList", payload)
        self.assertIn("bankCardNumber", payload)

    def test_wealth_recommend_ends_with_end(self) -> None:
        frames = asyncio.run(collect_sse("wealth_recommend", "帮我推荐理财产品"))
        self.assertEqual(frames[-1].get("node_type"), "End")

    def test_transfer_round1_omits_end(self) -> None:
        frames = asyncio.run(collect_sse("transfer_round1", "转账1000元"))
        self.assertEqual(frames[0].get("menu_type"), "TRANSFER_MENU")
        self.assertFalse(any(f.get("node_type") == "End" for f in frames))

    def test_balance_query_qa_result_node_for_adapter(self) -> None:
        frames = asyncio.run(collect_sse("balance_query", "查询尾号为6605的卡的余额"))
        qa_frames = [f for f in frames if f.get("node_type") == "QA"]
        self.assertTrue(qa_frames, "balance query must emit QA result frame for 8191 adapter")
        self.assertEqual(qa_frames[-1].get("node_name"), "GXZQAResponseNode")
        payload = json.loads(qa_frames[-1]["text"])
        self.assertIn("bankCardBalanceList", payload)
        self.assertEqual(frames[-1].get("node_type"), "End")


if __name__ == "__main__":
    unittest.main()
