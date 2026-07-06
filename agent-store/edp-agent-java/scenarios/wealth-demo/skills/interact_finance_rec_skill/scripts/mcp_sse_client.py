"""MCP SSE Client：通过 MCP SDK 的 sse_client + ClientSession 连接 MCP 服务，获取理财产品列表。

设计说明
--------
本脚本在沙箱 subprocess 中独立运行，不依赖 framework import。
MCP SDK 已打入沙箱环境，可直接 import，无需 sys.path 注入。

MCP SSE 协议流程（使用 MCP SDK）：
  1. sse_client(server_url, headers) 建立 SSE 连接，返回 read_stream + write_stream
  2. ClientSession(read_stream, write_stream) 初始化会话
  3. session.call_tool("get-finance-productslist", arguments=params) 调用工具
  4. 解析 result.content 提取产品列表
  5. 25秒超时兜底（asyncio.wait_for）

灰度路由由 Java 侧 McpInterruptRail 通过环境变量注入，Python 侧零硬编码。
"""
from __future__ import annotations

import asyncio
import json
import os
import time
from typing import Any, Dict, List, Optional

from log_utils import log_info, log_error, log_warning

_mcp_sdk_available = True
try:
    from mcp import ClientSession
    from mcp.client.sse import sse_client
except ImportError:
    _mcp_sdk_available = False
    log_warning("mcp_sse_client: MCP SDK 不可用，MCP 调用将跳过")

MCP_TOOL_NAME = "get-finance-productslist"
MCP_TIMEOUT = 25

SUCCESS_CODES = ("200", 200, "000", "0", "0000", "Success", "success", "SUCCESS")


class MCPSSERequester:
    """MCP SSE 协议调用器（使用 MCP SDK）。

    通过 MCP SDK 的 sse_client + ClientSession 连接 MCP 服务，
    调用 get-finance-productslist 工具。
    MCP 调用失败时返回 None，由沙箱脚本 fallback 到 business_data 中的 productList。
    """

    def __init__(
        self,
        server_url: str,
        access_token: str,
        app_name: str,
        timeout: int = MCP_TIMEOUT,
    ):
        self._server_url = server_url.rstrip("/")
        self._access_token = access_token
        self._app_name = app_name
        self._timeout = timeout

    def call(self, params: Dict[str, Any]) -> Optional[List[Dict[str, Any]]]:
        """同步调用 MCP 服务获取产品列表（asyncio.run 包装）。

        Args:
            params: MCP 工具调用参数（筛选条件 + 必输参数）

        Returns:
            产品列表（list of dict），或 None（调用失败时）
        """
        if not _mcp_sdk_available:
            log_error("mcp_sse_client: MCP SDK 不可用，跳过 MCP 调用")
            return None

        start_time = time.time()
        log_info(
            f"mcp_sse_client: 开始 MCP SSE 请求, url={self._server_url}, tool={MCP_TOOL_NAME}"
        )

        try:
            result = asyncio.run(asyncio.wait_for(
                self._do_mcp_request(params),
                timeout=self._timeout,
            ))
            elapsed_ms = (time.time() - start_time) * 1000
            log_info(
                f"mcp_sse_client: MCP 请求成功, 产品数量={len(result) if result else 0}, 耗时={elapsed_ms:.2f}ms"
            )
            return result
        except asyncio.TimeoutError:
            elapsed_ms = (time.time() - start_time) * 1000
            log_error(f"mcp_sse_client: MCP 请求超时 ({self._timeout}s), 耗时={elapsed_ms:.2f}ms")
            return None
        except Exception as e:
            elapsed_ms = (time.time() - start_time) * 1000
            log_error(f"mcp_sse_client: MCP 请求异常: {e}, 耗时={elapsed_ms:.2f}ms")
            return None

    async def _do_mcp_request(self, params: Dict[str, Any]) -> List[Dict[str, Any]]:
        """使用 MCP SDK 的 sse_client + ClientSession 执行 MCP 调用。"""
        headers = {
            "appAccessCheckToken": self._access_token,
            "app_name": self._app_name,
            "Content-Type": "application/json",
        }

        async with sse_client(self._server_url, headers=headers) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream) as session:
                await session.initialize()
                log_info(f"mcp_sse_client: SSE 连接已建立，调用 {MCP_TOOL_NAME}...")

                result = await session.call_tool(MCP_TOOL_NAME, arguments=params)

                if not result.content:
                    raise Exception(f"{MCP_TOOL_NAME} 调用未返回内容")

                raw_text = result.content[0].text
                log_info(f"mcp_sse_client: 响应原始内容长度: {len(raw_text)} 字符")

                payload = json.loads(raw_text)
                return self._parse_payload(payload)

    def _parse_payload(self, payload: Dict[str, Any]) -> List[Dict[str, Any]]:
        """解析 MCP 返回的 payload，提取产品列表。"""
        status = payload.get("status")
        if status is not None and not (200 <= status < 300):
            error_msg = payload.get("message") or payload.get("error") or "未知错误"
            raise Exception(f"MCP HTTP 错误 [{status}]: {error_msg}")

        code = payload.get("code")
        if code is not None and code not in SUCCESS_CODES:
            error_msg = payload.get("message") or "业务处理失败"
            raise Exception(f"MCP 业务失败 [{code}]: {error_msg}")

        op_data = payload.get("opData", {})
        if isinstance(op_data, dict):
            products = op_data.get("prodList", [])
        elif isinstance(op_data, list):
            products = op_data
        else:
            products = []

        return products

    @classmethod
    def from_skill_input(cls, skill_input: Dict[str, Any]) -> Optional["MCPSSERequester"]:
        """从环境变量构造 MCPSSERequester。

        灰度路由由 Java 侧 McpInterruptRail 通过环境变量 MCP_SERVER_URL、
        MCP_ACCESS_TOKEN、MCP_APP_NAME 注入，Python 侧零硬编码。

        Args:
            skill_input: SKILL_INPUT JSON（保留兼容，不再用于连接配置提取）

        Returns:
            MCPSSERequester 实例，或 None（环境变量缺失或 SDK 不可用时）
        """
        server_url = os.environ.get("MCP_SERVER_URL", "")
        access_token = os.environ.get("MCP_ACCESS_TOKEN", "")
        app_name = os.environ.get("MCP_APP_NAME", "")

        if not server_url:
            log_error("mcp_sse_client: MCP_SERVER_URL 环境变量未设置，无法创建请求器")
            return None
        if not access_token:
            log_error("mcp_sse_client: MCP_ACCESS_TOKEN 环境变量未设置，无法创建请求器")
            return None

        return cls(
            server_url=server_url,
            access_token=access_token,
            app_name=app_name,
        )
