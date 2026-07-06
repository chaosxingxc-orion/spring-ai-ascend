"""run_mcp_recommend：MCP 产品推荐沙箱脚本。

设计说明
--------
本脚本在沙箱 subprocess 中独立运行，不依赖 framework import。
从 run_interact_finance_rec_skill.py 提取 MCP 相关逻辑，职责单一化：
仅负责 MCP SSE 调用 + 去重。

核心流程：
  1. 解析输入参数 → 提取 mcp_params, mcp_required_params, history_info, history_params
  2. 推荐参数增量覆盖：history_params 为基础，mcp_params 有效值覆盖
  3. MCP SSE Client 调用 → 获取产品列表
  4. 历史去重（过滤 history_info）
  5. 输出 JSON（不含 bankCardNumber，bankCardNumber 由低码平台返回）

参数传入方式：
  - --arguments-json：命令行参数，JSON 字符串（推荐，execute_cmd 方式）
  - SKILL_INPUT 环境变量：fallback，兼容旧调用方式

输出格式：
  {
    "products": [...],
    "total": 3,
    "history_params": {"filterRiskLevel": "2"},
    "history_info": ["250761", "250871"],
    "versatile_query": "理财二次选品购买：{...}",
    "mcp_error": null
  }
"""

from __future__ import annotations

import argparse
import json
import os
from typing import Any, Dict, List, Optional

from log_utils import log_info, log_warning, log_stdout

from mcp_sse_client import MCPSSERequester
from run_interact_finance_rec_skill import (
     build_mcp_call_params,
     build_versatile_query,
     collect_product_codes,
     deduplicate_products,
     merge_recommend_params,
     MCP_MAX_RETURN_COUNT,
 )


def parse_mcp_required_params(mcp_required_params_str: str) -> Dict[str, str]:
    """将 mcp_required_params 字符串解析为目标格式的字典。

    从字符串中提取 clientIP, userAgent, wapSessionId, wapbCookieList, wap_grayFlag 字段。
    字符串格式为嵌套字典的字符串表示，实际数据在 custom_data.inputs 中。

    Args:
        mcp_required_params_str: 输入参数字符串，例如 "{'conversation_id': '...', 'custom_data': {...}}"

    Returns:
        包含指定字段的字典，格式: {"clientIP": "xxx", "userAgent": "xxx", "wapSessionId": "xxx",
                                     "wapbCookieList": "xxx", "wap_grayFlag": "xxx"}
    """
    result = {
        "clientIP": "",
        "userAgent": "",
        "mainClusterSessionID": "",
        "wapbCookieList": "",
        "wap_grayFlag": "",
        "ICBCWAPBNEW_NLP_VER": "beta"
    }

    if not mcp_required_params_str:
        return result

    try:
        # 处理单引号包裹的字符串，将其转换为有效的 JSON 格式
        # 使用 ast.literal_eval 安全地解析 Python 字典字符串
        import ast
        params_dict = ast.literal_eval(mcp_required_params_str)

        # 从 custom_data.inputs 中提取目标字段
        custom_data = params_dict.get("custom_data", {})
        inputs = custom_data.get("inputs", {})

        # 映射字段
        result["clientIP"] = inputs.get("clientIP", "")
        result["userAgent"] = inputs.get("userAgent", "")
        result["mainClusterSessionID"] = inputs.get("wap_sessionId", "")
        result["wapbCookieList"] = inputs.get("wapbCookieList", "")
        result["wap_grayFlag"] = inputs.get("wap_grayFlag", "")

    except (ValueError, SyntaxError, TypeError) as e:
        log_warning(f"parse_mcp_required_params: 解析参数字符串失败: {e}")

    return result


def run_mcp_recommend(skill_input: Dict[str, Any]) -> Dict[str, Any]:
    """MCP 产品推荐主函数。

    Args:
        skill_input: JSON（包含 mcp_params, mcp_required_params, history_info, history_params）

    Returns:
        MCP 推荐结果 dict，包含 products, total,
        history_params, history_info, versatile_query, mcp_error
    """
    mcp_params = skill_input.get("mcp_params", {})
    mcp_required_params_str = skill_input.get("mcp_required_params", "")
    history_info = skill_input.get("history_info", [])
    history_params = skill_input.get("history_params", {})
    mcp_required_params = parse_mcp_required_params(mcp_required_params_str)
    merged_params = merge_recommend_params(history_params, mcp_params)
    log_info(f"run_mcp_recommend: merge_recommend_params, history_params:{history_params}, current_param:{mcp_params}, final_param:{merged_params}")
    products = []
    mcp_error = None

    requester = MCPSSERequester.from_skill_input(skill_input)
    if requester is not None:
        call_params = build_mcp_call_params(merged_params, mcp_required_params)
        log_info(f"run_mcp_recommend: build_mcp_call_params, merged_params:{merged_params}, mcp_required_params:{mcp_required_params}, final_param:{call_params}")
        try:
            mcp_result = requester.call(call_params)
            if mcp_result is not None:
                products = mcp_result
            else:
                mcp_error = "MCP returned None (timeout or connection error)"
        except Exception as e:
            mcp_error = str(e)
            log_warning(f"run_mcp_recommend: MCP 调用异常: {mcp_error}")
    else:
        mcp_error = "MCPSSERequester unavailable (SDK missing or URL empty)"
        log_warning(f"run_mcp_recommend: {mcp_error}")

    log_info(f"[deduplicate before] productCode list: {[p.get('productCode') or p.get('prodCode') for p in products]}")
    products = deduplicate_products(products, history_info)
    log_info(f"[deduplicate after] productCode list: {[p.get('productCode') or p.get('prodCode') for p in products]}")

    if len(products) > MCP_MAX_RETURN_COUNT:
        log_info(f"run_mcp_recommend: MCP 返回 {len(products)} 个产品，截断为 {MCP_MAX_RETURN_COUNT} 个")
        products = products[:MCP_MAX_RETURN_COUNT]

    # MCP 调用失败或最终产品为空时，清空历史状态，使下次调用不继承上次条件
    if mcp_error or len(products) == 0:
        updated_history_params = {}
        updated_history_info = []
        versatile_query = "推荐理财产品，关键词：固收，风险等级：R2"
        log_info(
            f"run_mcp_recommend: MCP 失败或产品为空，清空历史状态。"
            f"mcp_error={mcp_error}, products_count={len(products)}"
        )
    else:
        updated_history_params = dict(merged_params)
        updated_history_info = collect_product_codes(products, history_info)
        versatile_query = build_versatile_query(products, merged_params)

    result = {
        "products": [],
        "total": len(products),
        "history_params": updated_history_params,
        "history_info": updated_history_info,
        "versatile_query": versatile_query,
        "mcp_error": mcp_error,
    }

    log_info(
        f"run_mcp_recommend: total={result['total']}, history_info_count={len(result['history_info'])}, mcp_error={mcp_error}"
    )
    # ---- 结果打印（调试阶段） ----
    log_info(f"[run_mcp_recommend result] products total: {result['total']}")
    log_info(f"[run_mcp_recommend result] history_info_list: {result['history_info']}")
    log_info(f"[run_mcp_recommend result] productCode list: {[p.get('productCode') or p.get('prodCode') for p in products]}")
    log_info(f"[run_mcp_recommend result] history_params: {json.dumps(result['history_params'], ensure_ascii=False)}")
    log_info(f"[run_mcp_recommend result] versatile_query: {result['versatile_query']}")
    log_info(f"[run_mcp_recommend result] mcp_error: {result['mcp_error']}")
    # ---- 结果打印结束 ----
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MCP 产品推荐沙箱脚本")
    parser.add_argument(
        "--arguments-json",
        type=str,
        default=None,
        help="JSON 字符串参数（优先级高于 SKILL_INPUT 环境变量）",
    )
    args = parser.parse_args()

    if args.arguments_json:
        skill_input = json.loads(args.arguments_json)
    else:
        skill_input = json.loads(os.environ.get("SKILL_INPUT", "{}"))

    result = run_mcp_recommend(skill_input)
    log_stdout(json.dumps(result, ensure_ascii=False))
