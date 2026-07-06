"""run_interact_finance_rec_skill：交互式多轮理财产品推荐归一化脚本。

设计说明
--------
本脚本在沙箱 subprocess 中独立运行，不依赖 framework import。
通过环境变量 SKILL_INPUT 接收 JSON 输入，归一化后输出 JSON 到 stdout。

MCP 先行架构下的核心流程：
  1. 解析 SKILL_INPUT → 提取 mcp_products_data, business_data
  2. mcp_products_data 有数据 → 优先使用 MCP 产品列表
  3. mcp_products_data 为空 → fallback 到 business_data 中的 productList
  4. 历史去重（防御性二次去重，MCP 先行阶段已去重过）
  5. bankCardNumber 从 business_data 获取（来自低码平台 product_filter 工作流）
  6. 兜底场景输出策略：is_first_recommend=True 时 0→error、1→1个、2+→2个
  7. 输出 JSON → print(json.dumps(result))

Rail 通过 sys_op.shell().execute_cmd() 调用本脚本完成归一化。
"""

from __future__ import annotations

import ast
import json
import os
from typing import Any, Dict, List, Optional

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


def merge_recommend_params(
    history_params: Dict[str, Any],
    mcp_params: Dict[str, Any],
) -> Dict[str, Any]:
    """推荐参数增量覆盖：history_recommend_params 为基础，mcp_params 有效值覆盖。

    规则：
    - history_recommend_params 提供基础参数
    - mcp_params 中非空、非 None 的值覆盖 history 中的同名参数
    - mcp_params 中新增的参数直接加入
    - history 中被覆盖前的值不保留
    """
    merged = dict(history_params) if history_params else {}
    for key, value in mcp_params.items():
        if value is not None and value != "":
            merged[key] = value
    return merged


def deduplicate_products(
    products: List[Dict[str, Any]],
    history_codes: List[str],
) -> List[Dict[str, Any]]:
    """历史去重：过滤掉 history_product_codes 中已推荐的产品编码。

    产品编码字段名可能是 productCode、product_code 或 prodCode，兼容三种命名。
    """
    if not history_codes:
        return products

    history_set = set(history_codes)
    result = []
    for product in products:
        code = str(product.get("productCode") or product.get("product_code") or product.get("prodCode") or "")
        if code not in history_set:
            result.append(product)
    return result


def collect_product_codes(products: List[Dict[str, Any]], history_codes: List[str]) -> List[str]:
    """收集本轮推荐的产品编码，追加到历史列表并进行去重。

    确保每轮推荐的所有产品编码都被保存，不遗漏，同时进行去重处理。
    """
    # 使用 set 进行去重，保持原有顺序
    all_codes = list(dict.fromkeys(history_codes))

    for product in products:
        code = str(product.get("productCode") or product.get("product_code") or product.get("prodCode") or "")
        if code and code not in all_codes:
            all_codes.append(code)

    return all_codes


MCP_MAX_RETURN_COUNT = 2

# 输入中缺少的字段将以此默认值填充
DEFAULT_MCP_PARAMS = {
    "pageMark": "20",           # 20=全部理财列表, 21=活钱管理, 22=低波稳健, 23=收益进阶
    "beginIndex": "0",          # 从第几支产品开始
    "queryPlatFlag": "0",       # 0=非套数平台, 1=查询平台, 2=养老金入口
    # 筛选参数
    "filterConsignment": "0",   # 产品销售渠道: 0=不筛选, 1=自营, 2=代销
    "filterLowestBuyAmt": "0",  # 最低购买金额: 0=不筛选, 1=0-1万, 2=1-5万, 3=5-50万, 4=50-500万, 5=500万以上
    "filterRiskLevel": "0",     # 风险等级: 0=不筛选, 1-5=R1-R5
    "filterProdType": "0",      # 产品类型: 0=不筛选, 1=固定收益类, 2=商品及金融衍生品类, 3=混合类, 4=传统产品, 5=结构性存款
    "filterCurrtype": "0",      # 产品币种: 0=不筛选, 1=人民币, 2=美元, 3=其他币种
    "filterStatus": "1",        # 产品状态: 0=不筛选, 1=在售, 2=暂无额度
    "filterOrganization": "0",  # 代销机构: 0=不筛选
    "filterFinanceChoice": "0", # 工银研选: 0=不筛选, 1=筛选工银研选
    "filterCurrentProd": "0",   # 现金管理产品: 0=不筛选, 1=筛选现金管理产品
    "filterDayProd": "0",       # 日开产品: 0=不筛选, 1=筛选日开产品
    "filter06FixProd": "0",     # 0-6个月期限: 0=不筛选, 1=筛选
    "filter612FixProd": "0",    # 6-12个月期限: 0=不筛选, 1=筛选
    "filter12FixProd": "0",     # 12个月以上期限: 0=不筛选, 1=筛选
    "QueryFundStatus": "1",     # 在售状态: 0=不筛选, 1=只展示在售, 2=只展示非在售
    "sortType": "0",            # 排序类型: 0=默认, 1=近1月年化, 2=近3月年化, 3=近6月年化, 4=近1年年化, 5=成立以来年化, 6=七日年化
}


def build_mcp_call_params(
    merged_params: Dict[str, Any],
    mcp_required_params: Dict[str, Any],
) -> Dict[str, Any]:
    """构建 MCP tools/call 的 arguments 参数。

    合并默认筛选参数、LLM 传入的筛选参数和必输参数。
    优先级：必输参数 > LLM 传入参数 > 默认参数。
    wap_grayFlag 仅用于灰度路由选 URL，不传给 MCP 接口。
    """
    # 1. 以默认参数为基础
    call_params = dict(DEFAULT_MCP_PARAMS)

    # 2. LLM 传入的筛选参数覆盖默认值（只接受 DEFAULT_MCP_PARAMS 中定义的筛选字段）
    for key, value in merged_params.items():
        if key in DEFAULT_MCP_PARAMS:
            if value is not None and str(value).strip() not in ("", "null", "None"):
                call_params[key] = str(value)

    # 3. 必输参数优先级最高，不可被筛选参数覆盖
    for key, value in mcp_required_params.items():
        if key == "wap_grayFlag":
            continue
        if value is not None and value != "":
            call_params[key] = value

    return call_params


# versatile 调用 query 构建所需的 filter_data 字段名列表
_FILTER_DATA_FIELDS = [
    "beginIndex",
    "filter06FixProd",
    "filter12FixProd",
    "filter612FixProd",
    "filterConsignment",
    "filterCurrentProd",
    "filterCurrtype",
    "filterDayProd",
    "filterFinanceChoice",
    "filterLowestBuyAmt",
    "filterOrganization",
    "filterProdType",
    "filterRiskLevel",
    "filterMaxYield",
    "filterMinYield",
    "filterStatus",
    "pageMark",
    "QueryFundStatus",
    "queryplatFlag",
    "sortType",
]


def build_versatile_query(
    products: List[Dict[str, Any]],
    params: Optional[Dict[str, Any]] = None,
) -> str:
    """构建 versatile 调用 query 字符串。

    格式：理财二次选品购买：{"productListJsonData":"<产品列表JSON>","filter_data":"<筛选参数JSON>"}

    此字符串用于后续 call_versatile 工具的 query 参数，
    以调用低码平台 product_filter 工作流获取 bankCardNumber 等补充信息。

    Args:
        products: MCP 返回的产品列表（去重后）
        params: 合并后的 MCP 筛选参数

    Returns:
        格式化的 query 字符串
    """

    products_json = json.dumps(products, ensure_ascii=False)

    filter_data = {}	 
    if params:	 
        for field in _FILTER_DATA_FIELDS:	 
            default_value = "20" if field == "pageMark" else "0"	 
            value = params.get(field, default_value)	 
            filter_data[field] = str(value) if value is not None else default_value 
    else: 
        for field in _FILTER_DATA_FIELDS: 
            filter_data[field] = "20" if field == "pageMark" else "0"

    data = {
        "productListJsonData": products_json,	 
         "filter_data": json.dumps(filter_data, ensure_ascii=False),
    }
    return f'理财二次选品购买：{json.dumps(data, ensure_ascii=False)}'


def normalize_interact_finance_rec_result(skill_input: Dict[str, Any]) -> Dict[str, Any]:
    """交互式多轮理财产品推荐归一化主函数。

    MCP 先行架构：优先使用 mcp_products_data（由 MCPInterruptRail 沙箱执行后注入），
    fallback 到 business_data.productList（向后兼容 MCP 未先行的场景）。

    兜底场景输出策略（is_first_recommend=True）：
    - 0 个产品 → error + 提示"没有符合您要求的理财产品"
    - 1 个产品 → 输出 1 个
    - 2 个及以上 → 截取前 2 个

    Args:
        skill_input: SKILL_INPUT JSON（包含 business_data, mcp_products_data,
                     history_info, is_first_recommend 等）

    Returns:
        归一化结果 dict，包含 products, bankCardNumber, total,
        history_params, history_info
    """
    business_data = skill_input.get("business_data", {})
    mcp_products_data = skill_input.get("mcp_products_data")
    history_product_codes = skill_input.get("history_info", [])

    if mcp_products_data is not None:
        products = mcp_products_data.get("products", [])
        log_info(f"normalize: 使用 mcp_products_data 中的产品列表，数量={len(products)}")
    else:
        log_info("normalize: mcp_products_data 为空，fallback 到 business_data 中的 productList")
        products = parse_product_list_str(business_data.get("productList", []))

    products = deduplicate_products(products, history_product_codes)

    if len(products) > MCP_MAX_RETURN_COUNT:
        log_info(f"normalize: MCP 返回 {len(products)} 个产品，截断为 {MCP_MAX_RETURN_COUNT} 个")
        products = products[:MCP_MAX_RETURN_COUNT]

    bank_card_number = str(business_data.get("bankCardNumber", ""))

    history_params = skill_input.get("history_params", {})
    mcp_products_history_params = mcp_products_data.get("history_params", {}) if mcp_products_data else {}
    if mcp_products_history_params:
        updated_history_params = mcp_products_history_params
    else:
        updated_history_params = dict(history_params)

    mcp_history_codes = mcp_products_data.get("history_info", []) if mcp_products_data else []
    if mcp_history_codes:
        # 合并 MCP 返回的历史编码和当前产品编码，确保不遗漏
        updated_history_codes = collect_product_codes(products, mcp_history_codes)
    else:
        updated_history_codes = collect_product_codes(products, history_product_codes)

    is_first_recommend = skill_input.get("is_first_recommend", False)
    mcp_error = mcp_products_data.get("mcp_error") if mcp_products_data else None

    result = {
        "products": products,
        "bankCardNumber": bank_card_number,
        "total": len(products),
        "history_params": updated_history_params,
        "history_info": updated_history_codes,
    }

    if len(products) == 0:
        if mcp_error:
            result["error"] = "mcp_timeout"
            result["message"] = "暂时无法获取理财产品信息，请稍后再试。"
        elif is_first_recommend:
            result["error"] = "no_products"
            result["message"] = "没有符合您要求的理财产品，请重新描述需求"
    elif is_first_recommend and len(products) > 2:
        result["original_total"] = len(products)
        products = products[:2]
        result["products"] = products
        result["total"] = 2

    log_info(
        f"normalize: total={result['total']}, bankCardNumber={result['bankCardNumber']}, "
        f"history_info_count={len(result['history_info'])}, is_first_recommend={is_first_recommend}"
    )
    return result


if __name__ == "__main__":
    """沙箱入口：从 SKILL_INPUT 环境变量读取 JSON，归一化后输出 JSON 到 stdout。"""
    skill_input = json.loads(os.environ.get("SKILL_INPUT", "{}"))
    normalized = normalize_interact_finance_rec_result(skill_input)
    log_stdout(json.dumps(normalized, ensure_ascii=False))
