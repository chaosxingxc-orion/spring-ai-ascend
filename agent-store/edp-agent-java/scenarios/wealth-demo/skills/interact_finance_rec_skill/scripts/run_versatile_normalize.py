"""run_versatile_normalize：call_versatile 返回归一化脚本。

设计说明
--------
本脚本作为 call_versatile 的 query_response_analysis_scripts 参数，
由 VersatileInterruptRail 在低码平台返回后自动执行。

职责：
  1. 将 business_data（低码平台原始返回）转换为标准产品列表格式
  2. 透传 history_info，确保跨 Skill（首次推荐→交互式推荐）状态不断裂

输入（SKILL_INPUT）：
  {
    "business_data": {
      "bankCardNumber": "6605",
      "productList": "[{'productCode': 'XLT1801', ...}]"
    },
    "history_info": ["P001", ...]
  }

输出：
  {
    "products": [{"productCode": ..., "productName": ..., ...}, ...],
    "bankCardNumber": "6605",
    "total": 3,
    "history_info": ["P001", "P002", ...]
  }
"""

from __future__ import annotations

import ast
import json
import os
from typing import Any, Dict, List

from log_utils import log_info, log_warning, log_stdout


def parse_product_list_str(raw: Any) -> List[Dict[str, Any]]:
    """解析 productList 字段，兼容三种格式：
    - Python repr 字符串（单引号，生产环境实际格式）
    - JSON 字符串（双引号）
    - list 对象（已解析）
    """
    if isinstance(raw, list):
        return [item for item in raw if isinstance(item, dict)]
    if not isinstance(raw, str):
        return []
    s = raw.strip()
    if not s:
        return []

    try:
        result = ast.literal_eval(s)
        if isinstance(result, list):
            return [item for item in result if isinstance(item, dict)]
    except Exception:
        pass

    try:
        result = json.loads(s)
        if isinstance(result, list):
            return [item for item in result if isinstance(item, dict)]
    except Exception:
        pass

    log_warning(f"parse_product_list_str: 无法解析 productList，原始值（前100字）: {s[:100]}")
    return []


def normalize_versatile_result(
    business_data: Any,
    history_info: List[str] | None = None,
) -> Dict[str, Any]:
    """将低码平台 business_data 归一化为标准结果 dict。

    Args:
        business_data: 低码平台 product_filter 工作流的原始返回
        history_info: 从 session.state 注入的历史已推荐产品编码列表

    Returns:
        归一化结果 dict，包含 products, bankCardNumber, total, history_info
    """
    if not isinstance(business_data, dict):
        log_warning(f"normalize_versatile_result: business_data 不是 dict（类型={type(business_data).__name__}），返回空结果")
        return {
            "products": [], "bankCardNumber": "", "total": 0,
            "history_info": history_info or [],
        }

    products = parse_product_list_str(business_data.get("productList", []))
    bank_card_number = str(business_data.get("bankCardNumber", ""))

    prev_codes = list(history_info or [])
    for p in products:
        code = str(p.get("productCode") or p.get("product_code") or p.get("prodCode") or "")
        if code and code not in prev_codes:
            prev_codes.append(code)

    result = {
        "products": products,
        "bankCardNumber": bank_card_number,
        "total": len(products),
        "history_info": prev_codes,
    }
    log_info(
        f"normalize_versatile_result: total={result['total']}, "
        f"bankCardNumber={result['bankCardNumber']}, "
        f"history_info_count={len(result['history_info'])}"
    )
    return result


if __name__ == "__main__":
    """沙箱入口：从 SKILL_INPUT 环境变量读取 JSON，归一化后输出 JSON 到 stdout。"""
    params = json.loads(os.environ.get("SKILL_INPUT", "{}"))
    business_data = params.get("business_data", {})
    history_info = params.get("history_info", [])

    # ---- 调试打印 ----
    log_info(f"[run_versatile_normalize enter] business_data type: {type(business_data).__name__}")
    log_info(f"[run_versatile_normalize enter] bankCardNumber: {business_data.get('bankCardNumber', '') if isinstance(business_data, dict) else 'N/A'}")
    log_info(f"[run_versatile_normalize enter] history_info: {history_info}")
    # ---- 调试打印结束 ----

    normalized = normalize_versatile_result(
        business_data,
        history_info=history_info,
    )

    # ---- 结果打印 ----
    log_info(f"[run_versatile_normalize result] total: {normalized['total']}")
    log_info(f"[run_versatile_normalize result] bankCardNumber: {normalized['bankCardNumber']}")
    log_info(f"[run_versatile_normalize result] productCode list: {[p.get('productCode') for p in normalized['products']]}")
    log_info(f"[run_versatile_normalize result] history_info: {normalized['history_info']}")
    # ---- 结果打印结束 ----

    products = normalized.get("products", [])
    bank_card = normalized.get("bankCardNumber", "")
    # 三路分支：
    # 1. products 非空 + bankCardNumber 为空 → ui_notice → product_recommend_no_card
    # 2. products 非空 + bankCardNumber 非空 → success → product_recommend_success
    # 3. products 为空               → failure → product_recommend_empty
    if products and not bank_card:
        status = "success"
        normalized["ui_notice"] = {
            "event": "interrupt_start",
            "key": "product_recommend_no_card",
        }
    elif products and bank_card:
        status = "success"
    else:
        status = "failure"

    log_stdout(json.dumps([status, normalized], ensure_ascii=False))
