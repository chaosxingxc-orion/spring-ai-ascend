"""冒烟测试:baseline-rw + renderer 端到端。

双兼容:可直接 `python test_smoke.py` 跑,也可被 pytest 收集(函数名 test_ 前缀)。
覆盖:加载、deny-by-default 校验(正反例)、渲染无 Jinja 泄漏、关键标记存在。
"""
from __future__ import annotations

import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_PLUGIN = _HERE.parent
sys.path.insert(0, str(_PLUGIN / "skills" / "_shared" / "baseline-rw"))
sys.path.insert(0, str(_PLUGIN / "skills" / "_shared" / "renderer"))

import baseline  # noqa: E402
import render as renderer  # noqa: E402

SAMPLE = _PLUGIN / "examples" / "agent-bus-forwarding-baseline.yaml"
DELTA = _PLUGIN / "examples" / "agent-bus-a2a-connpool-update.yaml"


def test_load_sample():
    data = baseline.load(SAMPLE)
    assert data["version"] == "agent-bus-forwarding-runtime-v1"
    assert len(data["elements"]) == 15
    assert len(data["relationships"]) == 12


def test_validate_sample_passes():
    data = baseline.load(SAMPLE)
    errors = baseline.validate(data)
    assert errors == [], "样例基线应通过校验,实际错误:\n  - " + "\n  - ".join(errors)


def test_deny_by_default_deferred_needs_next_entry():
    """deferred 缺 next_entry 必须被拒——本工具最硬的一条线。"""
    data = baseline.load(SAMPLE)
    data["deferred"].append({"id": "DF-BAD", "item": "无后续入口的结转", "owner": "x", "trigger": "y"})
    errors = baseline.validate(data)
    assert any("next_entry" in e for e in errors), "应报告缺 next_entry"


def test_deny_by_default_relation_dangling_ref():
    """关系引用不存在的元素 ID 必须被拒。"""
    data = baseline.load(SAMPLE)
    data["relationships"].append(
        {"id": "R-BAD", "view": "logical", "from": "E-999", "to": "E-001", "relation_type": "calls"}
    )
    errors = baseline.validate(data)
    assert any("E-999" in e for e in errors), "应报告悬空引用"


def test_deny_by_default_bad_enum():
    """非法枚举值必须被拒。"""
    data = baseline.load(SAMPLE)
    data["status"] = "frozen"  # 非法
    errors = baseline.validate(data)
    assert any("status" in e for e in errors)


def test_render_no_jinja_leak():
    """渲染产物不得残留 Jinja 标签。"""
    data = baseline.load(SAMPLE)
    html = renderer.render(data, mode="full")
    for token in ("{{", "}}", "{%", "%}"):
        assert token not in html, f"HTML 残留 Jinja 标签 {token!r}"


def test_render_has_key_markers():
    data = baseline.load(SAMPLE)
    html = renderer.render(data, mode="full")
    assert "<h1>agent-bus 转发运行时 4+1 基线</h1>" in html
    assert "逻辑视图" in html and "场景视图" in html
    assert "DF-001" in html  # deferred 表
    assert "RR-003" in html  # risk 表


def test_save_roundtrip_rejects_invalid():
    """save 必须先 validate,坏数据拒绝落盘。"""
    import tempfile

    data = baseline.load(SAMPLE)
    data["version"] = ""  # 顶层缺必填
    try:
        with tempfile.NamedTemporaryFile(suffix=".yaml", delete=False) as f:
            tmp = f.name
        baseline.save(data, tmp)
        raise AssertionError("save 应拒绝坏数据")
    except ValueError:
        pass  # 预期


def test_change_enum_validation():
    """非法 change 值必须被拒。"""
    data = baseline.load(SAMPLE)
    data["elements"][0]["change"] = "created"  # 非法
    errors = baseline.validate(data)
    assert any("change" in e for e in errors)


def test_validate_delta_sample_passes():
    errors = baseline.validate(baseline.load(DELTA))
    assert errors == [], "delta 样例应通过校验:\n  - " + "\n  - ".join(errors)


def test_change_counts():
    counts = baseline.change_counts(baseline.load(DELTA))
    assert counts["added"] == 3      # E-016 + R-013 + AF-011
    assert counts["modified"] == 3   # E-007 + E-008 + R-004
    assert counts["removed"] == 0
    assert counts["unchanged"] == 3  # E-001 + E-006 + E-014


def test_render_delta_filters_and_colors():
    """delta 模式:变更摘要计数 + 只详列变更项 + change 着色 + 无泄漏。"""
    data = baseline.load(DELTA)
    html = renderer.render(data, mode="delta")
    assert "chip-added" in html and "chip-modified" in html  # 着色类
    assert "E-016" in html   # added 元素出现
    assert "R-013" in html   # added 关系出现
    assert "AF-011" in html  # added 拟接受事实出现
    for token in ("{{", "}}", "{%", "%}"):
        assert token not in html


def test_diagrams_missing_path_rejected():
    """diagrams 项缺 path 必须被拒。"""
    data = baseline.load(SAMPLE)
    data["diagrams"] = [{"caption": "无路径"}]
    errors = baseline.validate(data)
    assert any("diagrams" in e and "path" in e for e in errors)


def test_render_diagrams_source_fallback():
    """full 渲染含 diagrams 区;本地无 SVG 时走源码块分支(显示渲染命令)。"""
    data = baseline.load(SAMPLE)
    html = renderer.render(data, mode="full", base_dir=SAMPLE.parent)
    assert "架构图" in html
    assert "l0-logical.puml" in html
    assert "plantuml -tsvg" in html  # 源码块分支的渲染命令提示


def test_render_diagrams_missing_file():
    """引用不存在的图源 → missing 提示,不崩。"""
    data = baseline.load(SAMPLE)
    data["diagrams"] = [{"path": "does/not/exist.puml", "caption": "假图"}]
    html = renderer.render(data, mode="full", base_dir=SAMPLE.parent)
    assert "找不到图源" in html


def _run_all():
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    failed = 0
    for fn in fns:
        try:
            fn()
            print(f"  ✓ {fn.__name__}")
        except Exception as e:
            failed += 1
            print(f"  ✗ {fn.__name__}: {e}")
    print(f"\n{'✓ 全部通过' if failed == 0 else f'✗ {failed} 项失败'} ({len(fns)} 项)")
    return failed


if __name__ == "__main__":
    sys.exit(1 if _run_all() else 0)
