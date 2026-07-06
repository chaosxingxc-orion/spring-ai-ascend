"""Stateful and dynamic response hooks migrated from mock_workflow_server_v6."""
from __future__ import annotations

import ast
import json
import logging
import os
import re
from datetime import datetime
from typing import Any, Optional

logger = logging.getLogger("mock_versatile.hooks")

_transfer_counters: dict[str, int] = {}
_balance_states: dict[str, dict[str, Any]] = {}

MOCK_PRODUCT_FILTER: dict[str, Any] = {
    "bankCardNumber": "6605",
    "productList": (
        "[{'productCode': 'XLT1801', 'productName': '工银理财「添利宝」净值型理财产品(XLT1801)', "
        "'productType': '固定收益类', 'profitValue': '3.2%', 'riskLevel': 'R1'}, "
        "{'productCode': 'WM002', 'productName': '稳健增长理财计划(WM002)', 'productType': '混合类', "
        "'profitValue': '4.5%', 'riskLevel': 'R1'}, "
        "{'productCode': 'JJ003', 'productName': '进取型权益理财(JJ003)', 'productType': '权益类', "
        "'profitValue': '6.8%', 'riskLevel': 'R1'}]"
    ),
}


def get_initial_balances() -> tuple[float, float]:
    licai = float(os.environ.get("MOCK_LICAI_BALANCE", "1000.0"))
    chuxu = float(os.environ.get("MOCK_CHUXU_BALANCE", "125680.5"))
    return licai, chuxu


def get_or_create_balance_state(
    session_key: str,
    licai_tail: str = "6605",
    chuxu_tail: str = "3344",
) -> dict[str, Any]:
    same_card_mode = os.environ.get("MOCK_SAME_CARD_MODE", "false").lower() == "true"
    if same_card_mode:
        chuxu_tail = licai_tail

    if session_key not in _balance_states:
        licai_balance, chuxu_balance = get_initial_balances()
        _balance_states[session_key] = {
            "licai_balance": licai_balance,
            "chuxu_balance": chuxu_balance,
            "licai_tail": licai_tail,
            "chuxu_tail": chuxu_tail,
        }
    return _balance_states[session_key]


def update_balance_after_transfer(session_key: str, transfer_amount: float) -> None:
    if session_key not in _balance_states:
        return
    state = _balance_states[session_key]
    state["chuxu_balance"] = max(0, state["chuxu_balance"] - transfer_amount)
    state["licai_balance"] = state["licai_balance"] + transfer_amount


def get_transfer_amount_for_session(
    session_key: str,
    requested_amount: float,
) -> tuple[float, bool, str]:
    amounts_config = os.environ.get("MOCK_TRANSFER_AMOUNTS", "")
    transfer_mode = os.environ.get("MOCK_TRANSFER_MODE", "cycle").lower()

    if not amounts_config:
        return requested_amount, True, ""

    amounts = [float(x.strip()) for x in amounts_config.split(",") if x.strip()]
    if not amounts:
        return requested_amount, True, ""

    counter = _transfer_counters.get(session_key, 0)

    if counter >= len(amounts):
        if transfer_mode == "cycle":
            idx = counter % len(amounts)
        elif transfer_mode == "last":
            idx = len(amounts) - 1
        elif transfer_mode == "full":
            _transfer_counters[session_key] = counter + 1
            return requested_amount, True, ""
        elif transfer_mode == "fail":
            _transfer_counters[session_key] = counter + 1
            return 0.0, False, "转账次数超限"
        else:
            idx = len(amounts) - 1
    else:
        idx = counter

    actual_amount = amounts[idx]
    _transfer_counters[session_key] = counter + 1
    return actual_amount, True, ""


def reset_transfer_counter(session_key: str | None = None) -> None:
    if session_key:
        _transfer_counters.pop(session_key, None)
        _balance_states.pop(session_key, None)
    else:
        _transfer_counters.clear()
        _balance_states.clear()


def _mock_cny_balance_with_thousands(amount: float) -> str:
    return f"{amount:,.2f}"


def _build_mock_fund_products() -> list[dict]:
    products: list[dict] = []
    risk_levels = ["R1", "R2", "R3", "R4", "R5"]
    holding_periods = [3, 12, 30]
    min_amounts = [1000, 10000, 100000]
    type_by_risk = {
        "R1": "货币型",
        "R2": "债券型",
        "R3": "混合型",
        "R4": "股票型",
        "R5": "QDII",
    }
    base_yield_by_risk = {"R1": 2.1, "R2": 3.2, "R3": 5.2, "R4": 8.6, "R5": 12.0}
    idx = 1
    for rl in risk_levels:
        for hp in holding_periods:
            for ma in min_amounts:
                code = f"FUND{idx:03d}"
                products.append(
                    {
                        "productCode": code,
                        "productName": f"基金推荐产品-{rl}-{hp}M-{ma}元({code})",
                        "productType": type_by_risk[rl],
                        "profitValue": (
                            f"{(base_yield_by_risk[rl] + (hp / 100) + (0.05 if ma == 1000 else (0.15 if ma == 10000 else 0.25))):.2f}%"
                        ),
                        "riskLevel": rl,
                        "holdingPeriodMonths": hp,
                        "minAmount": ma,
                    }
                )
                idx += 1
    return products


MOCK_FUND_PRODUCTS = _build_mock_fund_products()


def _parse_fund_constraints_from_query(query: str) -> dict[str, Any]:
    q = (query or "").strip()
    risk = None
    m = re.search(r"R([1-5])(?![0-9])", q, flags=re.IGNORECASE)
    if m:
        risk = f"R{m.group(1)}"

    term = None
    if "短期" in q:
        term = "短期"
    elif "中期" in q:
        term = "中期"
    elif "长期" in q:
        term = "长期"

    amount = None
    m2 = re.search(r"(\d+)\s*元", q)
    if m2:
        try:
            amount = int(m2.group(1))
        except Exception:
            amount = None

    return {"risk_level": risk, "invest_term": term, "invest_amount": amount}


def _filter_funds_for_query(query: str) -> list[dict]:
    c = _parse_fund_constraints_from_query(query)
    risk = c.get("risk_level")
    term = c.get("invest_term")
    amount = c.get("invest_amount")

    allowed_holding: Optional[set[int]] = None
    if term == "短期":
        allowed_holding = {3}
    elif term == "中期":
        allowed_holding = {12}
    elif term == "长期":
        allowed_holding = {30}

    filtered: list[dict] = []
    for p in MOCK_FUND_PRODUCTS:
        if risk and p.get("riskLevel") != risk:
            continue
        if allowed_holding and p.get("holdingPeriodMonths") not in allowed_holding:
            continue
        if isinstance(amount, int):
            ma = p.get("minAmount")
            if isinstance(ma, int) and ma > amount:
                continue
        filtered.append(p)
    return filtered


def wealth_product_filter_json(ctx: dict[str, Any]) -> str:
    return json.dumps(MOCK_PRODUCT_FILTER, ensure_ascii=False, separators=(",", ":"))


def fund_product_filter_json(ctx: dict[str, Any]) -> str:
    query = str(ctx.get("query", "") or "")
    filtered = _filter_funds_for_query(query)
    payload = {
        "bankCardNumber": "6605",
        "funds": filtered,
        "products": filtered,
        "productList": str(filtered),
    }
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def product_buy_response_json(ctx: dict[str, Any]) -> str:
    query = str(ctx.get("query", "") or "")
    product_code_match = re.search(r"产品代码：([^，,\s]+)", query)
    product_name_match = re.search(r"产品名称：([^，]+)", query)
    amount_match = re.search(r"金额：(\d+(?:\.\d+)?)元", query)

    product_code = product_code_match.group(1) if product_code_match else "25G5793A"
    product_name = (
        product_name_match.group(1)
        if product_name_match
        else "工银理财·核心优选目标止盈策略固收增强封闭产品（25G5793A)"
    )
    buy_amount = (
        f"{float(amount_match.group(1)):.2f}元"
        if amount_match
        else "55550.00元"
    )

    buy_success = os.environ.get("MOCK_PRODUCT_BUY_SUCCESS", "true").lower() == "true"
    buy_status = "1" if buy_success else "购买理财失败"
    fail_cause = "" if buy_success else "余额不足或风控拦截"

    buy_data = {
        "productBuyResponse": {
            "productCode": product_code,
            "productName": product_name,
            "buyAmount": buy_amount,
            "buyStatus": buy_status,
            "failCause": fail_cause,
        }
    }
    return json.dumps(buy_data, ensure_ascii=False, separators=(",", ":"))


def balance_business_result(ctx: dict[str, Any]) -> dict[str, Any]:
    """v6 production-style balance payload (emitted as raw SSE frame)."""
    query = str(ctx.get("query", "") or "")
    conversation_id = str(ctx.get("conversation_id", "") or "default")
    tail_match = re.search(r"尾号为?(\d{4})", query)
    requested_tail = tail_match.group(1) if tail_match else ""
    same_card_mode = os.environ.get("MOCK_BALANCE_SAME_CARD", "false").lower() == "true"

    balance_state = get_or_create_balance_state(conversation_id)

    if requested_tail:
        card_tail = requested_tail
        if requested_tail == balance_state.get("licai_tail", "6605"):
            cny_balance = balance_state["licai_balance"]
        elif requested_tail == balance_state.get("chuxu_tail", "3344"):
            cny_balance = balance_state["chuxu_balance"]
        else:
            cny_balance = 50000.0
    else:
        card_tail = balance_state.get("chuxu_tail", "3344")
        cny_balance = balance_state["chuxu_balance"]
        if same_card_mode:
            card_tail = balance_state.get("licai_tail", "6605")
            cny_balance = balance_state["licai_balance"]

    balance_display = _mock_cny_balance_with_thousands(cny_balance)

    return {
        "SPTRANSRETCODE": "LLMU0002",
        "bankCardBalanceList": [
            {
                "bankCardNumber": f"6222****{card_tail}",
                "queryStatus": "成功",
                "currencyBalanceList": [
                    {"currencyCode": "CNY", "balance": balance_display}
                ],
            }
        ],
        "responseData": [
            {
                "type": "1",
                "answer": "已为您查询账户余额",
                "readme": "已为您查询账户余额",
                "pageData": "",
            },
            {
                "type": "7",
                "answer": "",
                "readme": "",
                "pageData": {
                    "id": "queryBalance",
                    "bankBalanceData": [
                        {
                            "layouttype": "1",
                            "actionFun_click": {
                                "menu": {
                                    "param": "returnFlag=3",
                                    "needLogin": "false",
                                    "menuId": "account_1",
                                }
                            },
                            "actionType_click": "menu",
                            "balanceList": [
                                {
                                    "balanceTitle": {
                                        "titleValueColor": "C3B9A1",
                                        "titleValue": "人民币余额",
                                        "type": "text",
                                        "actionFun_click": "",
                                        "actionType_click": "",
                                    },
                                    "balance": {
                                        "titleValueColor": "F4E1B3",
                                        "titleValue": balance_display,
                                        "type": "text",
                                        "actionFun_click": "",
                                        "actionType_click": "",
                                    },
                                },
                                {
                                    "balanceTitle": {
                                        "titleValueColor": "C3B9A1",
                                        "titleValue": "人民币可用余额",
                                        "type": "text",
                                        "actionFun_click": "",
                                        "actionType_click": "",
                                    },
                                    "balance": {
                                        "titleValueColor": "F4E1B3",
                                        "titleValue": balance_display,
                                        "type": "text",
                                        "actionFun_click": "",
                                        "actionType_click": "",
                                    },
                                },
                            ],
                        }
                    ],
                },
            },
        ],
    }


def balance_business_result_json(ctx: dict[str, Any]) -> str:
    """v6 balance payload as QA result-node text (8191 adapter GXZQAResponseNode contract)."""
    return json.dumps(balance_business_result(ctx), ensure_ascii=False, separators=(",", ":"))


def balance_simple_qa_json(ctx: dict[str, Any]) -> str:
    """Interrupt-style simplified balance QA."""
    conversation_id = str(ctx.get("conversation_id", "") or "default")
    balance_state = get_or_create_balance_state(conversation_id)
    return json.dumps(
        {
            "status": "success",
            "card_no": balance_state.get("chuxu_tail", "3344"),
            "balance": balance_state["chuxu_balance"],
        },
        ensure_ascii=False,
    )


def transfer_response_json(ctx: dict[str, Any]) -> str:
    query = str(ctx.get("query", "") or "")
    conversation_id = str(ctx.get("conversation_id", "") or "default")
    amount_match = re.search(r"转账(\d+(?:\.\d+)?)元", query)
    requested_amount = float(amount_match.group(1)) if amount_match else 1000.0

    actual, success, fail_cause = get_transfer_amount_for_session(conversation_id, requested_amount)

    payer_m = re.search(r"从尾号(\d+)的卡转账", query)
    payee_m = re.search(r"元到尾号为(\d+)的卡", query)
    payer_tail = (payer_m.group(1) if payer_m else "1234")[-4:].zfill(4)
    payee_tail = (payee_m.group(1) if payee_m else "5678")[-4:].zfill(4)

    if success:
        update_balance_after_transfer(conversation_id, actual)
        transfer_status = "success"
        transfer_amount_str = f"{actual:.2f}".rstrip("0").rstrip(".")
    else:
        transfer_status = "fail"
        transfer_amount_str = "0"

    transfer_data = {
        "transferStatus": transfer_status,
        "payerCardNumber": f"328393893832{payer_tail}",
        "payeeCardNumber": f"389288102902{payee_tail}",
        "transferAmount": transfer_amount_str,
        "failCause": fail_cause if not success else "",
    }
    return json.dumps(transfer_data, ensure_ascii=False, separators=(",", ":"))


def transfer_confirmed_simple_json(ctx: dict[str, Any]) -> str:
    conversation_id = str(ctx.get("conversation_id", "") or "default")
    if ctx.get("config", {}).get("features", {}).get("stateful_transfer", True):
        _, success, _ = get_transfer_amount_for_session(conversation_id, 1000.0)
        if success:
            update_balance_after_transfer(conversation_id, 1000.0)
    return json.dumps({"status": "success", "msg": "转账成功"}, ensure_ascii=False)


def default_error_qa_json(ctx: dict[str, Any]) -> str:
    query = str(ctx.get("query", "") or "")
    return json.dumps(
        {"error": "未知工作流类型", "query": query},
        ensure_ascii=False,
        separators=(",", ":"),
    )


def default_answer_qa_json(ctx: dict[str, Any]) -> str:
    query = str(ctx.get("query", "") or "")
    return json.dumps(
        {"status": "success", "answer": f"已为您完成处理（mock 兜底）：{query}"},
        ensure_ascii=False,
    )


HOOK_REGISTRY: dict[str, Any] = {
    "wealth_product_filter_json": wealth_product_filter_json,
    "fund_product_filter_json": fund_product_filter_json,
    "product_buy_response_json": product_buy_response_json,
    "balance_business_result": balance_business_result,
    "balance_business_result_json": balance_business_result_json,
    "balance_simple_qa_json": balance_simple_qa_json,
    "transfer_response_json": transfer_response_json,
    "transfer_confirmed_simple_json": transfer_confirmed_simple_json,
    "default_error_qa_json": default_error_qa_json,
    "default_answer_qa_json": default_answer_qa_json,
}


def call_hook(name: str, ctx: dict[str, Any]) -> Any:
    fn = HOOK_REGISTRY.get(name)
    if fn is None:
        raise KeyError(f"unknown hook: {name}")
    return fn(ctx)


def get_transfer_counters() -> dict[str, int]:
    return dict(_transfer_counters)


def get_balance_states() -> dict[str, dict[str, Any]]:
    return {
        k: {
            "licai_balance": v["licai_balance"],
            "chuxu_balance": v["chuxu_balance"],
            "licai_tail": v["licai_tail"],
            "chuxu_tail": v["chuxu_tail"],
        }
        for k, v in _balance_states.items()
    }


def count_product_list_entries(raw: Any) -> int:
    if isinstance(raw, list):
        return sum(1 for item in raw if isinstance(item, dict))
    if not isinstance(raw, str):
        return 0
    s = raw.strip()
    if not s:
        return 0
    try:
        parsed = ast.literal_eval(s)
        if isinstance(parsed, list):
            return sum(1 for item in parsed if isinstance(item, dict))
    except Exception:
        pass
    try:
        parsed = json.loads(s)
        if isinstance(parsed, list):
            return sum(1 for item in parsed if isinstance(item, dict))
    except Exception:
        pass
    return 0
