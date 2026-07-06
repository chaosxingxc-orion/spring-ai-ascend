"""Two-level workflow matcher: context routes first, then query routes."""
from __future__ import annotations

import re
from typing import Any

from .loader import WorkflowStore


def _truthy(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        return value.strip().lower() in ("true", "1", "yes", "y")
    return False


def _match_context_rule(inputs: dict[str, Any], rule: dict[str, Any]) -> bool:
    field = rule.get("field", "")
    value = inputs.get(field)

    if "equals" in rule:
        return str(value or "") == str(rule["equals"])
    if rule.get("truthy") is True:
        return _truthy(value)
    if rule.get("falsy") is True:
        return not _truthy(value)
    if "regex" in rule:
        flags = 0
        if str(rule.get("flags", "")).lower().find("i") >= 0:
            flags |= re.IGNORECASE
        return bool(re.search(rule["regex"], str(value or ""), flags))
    return False


def _match_context(inputs: dict[str, Any], wf: dict[str, Any]) -> bool:
    rules = wf.get("match", {}).get("rules", [])
    if not rules:
        return False
    return all(_match_context_rule(inputs, rule) for rule in rules)


def _match_query_rule(query: str, rule: dict[str, Any]) -> bool:
    kind = rule.get("kind", "")
    compact = re.sub(r"\s+", "", query)

    if kind == "exclude_all":
        patterns = rule.get("patterns", [])
        return not all(p in query for p in patterns)

    if kind == "regex":
        flags = 0
        flag_str = str(rule.get("flags", ""))
        if "i" in flag_str.lower():
            flags |= re.IGNORECASE
        pattern = rule.get("pattern", "")
        target = compact if rule.get("on_compact") else query
        return bool(re.search(pattern, target, flags))

    if kind == "keywords":
        any_kw = rule.get("any", [])
        case_insensitive = rule.get("case_insensitive", False)
        haystack = query.lower() if case_insensitive else query
        for kw in any_kw:
            needle = kw.lower() if case_insensitive else kw
            if needle in haystack:
                return True
        return False

    if kind == "keywords_all":
        case_insensitive = rule.get("case_insensitive", False)
        haystack = query.lower() if case_insensitive else query
        for kw in rule.get("all", []):
            needle = kw.lower() if case_insensitive else kw
            if needle not in haystack:
                return False
        return True

    return False


def _match_query(query: str, wf: dict[str, Any]) -> bool:
    rules = wf.get("match", {}).get("rules", [])
    if not rules:
        return False
    for rule in rules:
        if not _match_query_rule(query, rule):
            return False
    return True


class WorkflowMatcher:
    def __init__(self, store: WorkflowStore) -> None:
        self.store = store

    def resolve(self, inputs: dict[str, Any]) -> dict[str, Any]:
        query = str(inputs.get("query", "") or "")
        workflows = self.store.list_workflows()

        context_wfs = [w for w in workflows if w.get("match", {}).get("type") == "context"]
        for wf in context_wfs:
            if _match_context(inputs, wf):
                return wf

        query_wfs = [w for w in workflows if w.get("match", {}).get("type") == "query"]
        for wf in query_wfs:
            if _match_query(query, wf):
                return wf

        return self.store.get_default()
