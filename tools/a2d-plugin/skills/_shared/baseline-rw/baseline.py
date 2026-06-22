"""baseline-rw:4+1 基线读写与校验库。

职责:把 A2D 步骤 8(基线固化)产出的 4+1 基线,作为结构化数据加载、校验、落盘。
renderer 消费这里的 dict 产出审阅 HTML;skill 层调用 load/validate/save。

数据模型(4+1 基线 yaml):

    version: <string>              # 基线版本标识,如 agent-bus-forwarding-runtime-v1
    title: <string>                # 基线标题
    status: accepted|draft|reviewed|superseded
    generated_at: <ISO date>
    scope: <string>                # 基线覆盖范围说明
    source_packets:                # 本基线合并自哪些源评审包/文档
      - <path>

    views:                         # 五视图(对应 review-packet _TEMPLATE §3)
      logical:
        summary: <string>
        risks: [<string>]
      development:
        summary: <string>
        allowed_dependencies: [<string>]
        forbidden_dependencies: [<string>]
        risks: [<string>]
      process:
        summary: <string>
        runtime_flows: [<string>]
        failure_paths: [<string>]
        risks: [<string>]
      physical:
        summary: <string>
        deployment_impact: [<string>]
        boundaries: [<string>]
        risks: [<string>]
      scenario:
        scenarios:
          - id: SC-001
            actor: <string>
            flow: <string>
            supported_by: <string>
            contract: <string>
            harness: <string>
            status: accepted|draft

    elements:                      # 元素事实表(对应 _TEMPLATE §3.1)
      - id: E-001
        view: logical|development|process|physical|scenario
        type: module|capability|state|contract|actor|deployment-plane
        name: <string>
        owner: <string>
        responsibility: <string>
        source_fact: <string>      # 引用源(评审包/文档锚点)
        status: accepted|draft

    relationships:                 # 关系事实表(对应 _TEMPLATE §3.2)
      - id: R-001
        view: logical|development|process|physical|scenario
        from: E-001                # 元素 ID
        to: E-002
        relation_type: calls|owns|reads|writes|deploys|verifies
        direction: one-way|two-way
        sync_async: sync|async|eventual|none
        contract_state: <string>
        source_fact: <string>

    accepted_facts:                # baseline-freeze 专属(_TEMPLATE §9)
      - id: AF-001
        fact: <string>
        source: <string>

    deferred:                      # 结转项(deny-by-default:必须有后续入口)
      - id: DF-001
        item: <string>
        owner: <string>
        trigger: <string>          # 何时重新打开
        next_entry: <string>       # 后续入口(版本/路径)

    release_risk:
      - id: RR-001
        risk: <string>
        level: L1|L2|L3
        mitigation: <string>
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml

VALID_VIEWS = ("logical", "development", "process", "physical", "scenario")
VALID_ELEMENT_TYPES = ("module", "capability", "state", "contract", "actor", "deployment-plane")
VALID_RELATION_TYPES = ("calls", "owns", "reads", "writes", "deploys", "verifies")
VALID_STATUS = ("accepted", "draft", "reviewed", "superseded")
VALID_DIRECTION = ("one-way", "two-way")
VALID_SYNC_ASYNC = ("sync", "async", "eventual", "none")
VALID_RISK_LEVEL = ("L1", "L2", "L3")

TOP_REQUIRED = ("version", "title", "status")
ELEMENT_REQUIRED = ("id", "view", "type", "name")
RELATION_REQUIRED = ("id", "view", "from", "to", "relation_type")
DEFERRED_REQUIRED = ("id", "item", "owner", "trigger", "next_entry")


def load(path: str | Path) -> dict:
    """加载 4+1 基线 yaml,返回 dict。不校验(调用方自行 validate)。"""
    with open(path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if not isinstance(data, dict):
        raise ValueError(f"基线文件根必须是 mapping,实际是 {type(data).__name__}: {path}")
    return data


def save(data: dict, path: str | Path) -> None:
    """把基线 dict 落盘为 yaml。落盘前先 validate,有错误则拒绝写。"""
    errors = validate(data)
    if errors:
        raise ValueError("基线校验失败,拒绝落盘:\n  - " + "\n  - ".join(errors))
    with open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(data, f, allow_unicode=True, sort_keys=False, default_flow_style=False)


def validate(data: dict) -> list[str]:
    """deny-by-default 校验。返回错误列表(空 = 通过)。

    校验项:顶层必填、枚举值、元素 ID 唯一、关系引用有效、deferred 必须有后续入口。
    """
    errors: list[str] = []

    def _enum(value, allowed, ctx):
        if value not in allowed:
            errors.append(f"{ctx}: 非法值 {value!r},允许 {allowed}")

    # 顶层必填
    for k in TOP_REQUIRED:
        if not data.get(k):
            errors.append(f"顶层缺必填字段: {k}")
    if data.get("status"):
        _enum(data["status"], VALID_STATUS, "status")

    # views
    views = data.get("views") or {}
    if not isinstance(views, dict):
        errors.append("views 必须是 mapping")
        views = {}
    for v in views:
        if v not in VALID_VIEWS:
            errors.append(f"views 含非法视图名: {v!r},允许 {VALID_VIEWS}")
    if "logical" not in views:
        errors.append("views 至少必须包含 logical 视图")

    # elements
    elements = data.get("elements") or []
    element_ids: set[str] = set()
    for i, el in enumerate(elements):
        ctx = f"elements[{i}]"
        for k in ELEMENT_REQUIRED:
            if not el.get(k):
                errors.append(f"{ctx} 缺必填字段: {k}")
        eid = el.get("id")
        if eid:
            if eid in element_ids:
                errors.append(f"{ctx}: 元素 ID 重复 {eid!r}")
            element_ids.add(eid)
        if el.get("view"):
            _enum(el["view"], VALID_VIEWS, f"{ctx}.view")
        if el.get("type"):
            _enum(el["type"], VALID_ELEMENT_TYPES, f"{ctx}.type")
        if el.get("status"):
            _enum(el["status"], VALID_STATUS, f"{ctx}.status")

    # relationships
    relations = data.get("relationships") or []
    for i, rel in enumerate(relations):
        ctx = f"relationships[{i}]"
        for k in RELATION_REQUIRED:
            if not rel.get(k):
                errors.append(f"{ctx} 缺必填字段: {k}")
        if rel.get("from") and rel["from"] not in element_ids:
            errors.append(f"{ctx}.from 引用了不存在的元素 ID: {rel['from']!r}")
        if rel.get("to") and rel["to"] not in element_ids:
            errors.append(f"{ctx}.to 引用了不存在的元素 ID: {rel['to']!r}")
        if rel.get("view"):
            _enum(rel["view"], VALID_VIEWS, f"{ctx}.view")
        if rel.get("relation_type"):
            _enum(rel["relation_type"], VALID_RELATION_TYPES, f"{ctx}.relation_type")
        if rel.get("direction"):
            _enum(rel["direction"], VALID_DIRECTION, f"{ctx}.direction")
        if rel.get("sync_async"):
            _enum(rel["sync_async"], VALID_SYNC_ASYNC, f"{ctx}.sync_async")

    # deferred:deny-by-default,每项必须有 owner + 后续入口
    deferred = data.get("deferred") or []
    for i, d in enumerate(deferred):
        ctx = f"deferred[{i}]"
        for k in DEFERRED_REQUIRED:
            if not d.get(k):
                errors.append(f"{ctx} 缺必填字段: {k}(deferred 项必须有 owner 和后续入口)")

    # release_risk
    risks = data.get("release_risk") or []
    for i, r in enumerate(risks):
        ctx = f"release_risk[{i}]"
        if not r.get("id") or not r.get("risk"):
            errors.append(f"{ctx} 缺必填字段 id/risk")
        if r.get("level"):
            _enum(r["level"], VALID_RISK_LEVEL, f"{ctx}.level")

    return errors


def _cli():
    """CLI:python baseline.py check <path>。"""
    if len(sys.argv) < 3 or sys.argv[1] != "check":
        print("用法: python baseline.py check <baseline.yaml>", file=sys.stderr)
        sys.exit(2)
    path = sys.argv[2]
    try:
        data = load(path)
    except Exception as e:
        print(f"加载失败: {e}", file=sys.stderr)
        sys.exit(1)
    errors = validate(data)
    if errors:
        print(f"✗ 校验失败({len(errors)} 项):")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    # 摘要
    n_el = len(data.get("elements") or [])
    n_rel = len(data.get("relationships") or [])
    n_df = len(data.get("deferred") or [])
    n_af = len(data.get("accepted_facts") or [])
    print(f"✓ 校验通过: {data.get('version','?')} | "
          f"元素 {n_el} | 关系 {n_rel} | accepted_facts {n_af} | deferred {n_df}")


if __name__ == "__main__":
    _cli()
