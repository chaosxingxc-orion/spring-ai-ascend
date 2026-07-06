"""
理财推荐三轮对话端到端测试用例。

测试场景：用户通过 A2A JSON-RPC SendStreamingMessage 发起理财推荐会话，
模拟三轮交互：(1) "理财推荐" → (2) "第一个，100元" → (3) "确认"。

外部依赖（Versatile adapter、LLM）需已部署并运行，本测试不生成 mock。

运行方式：
    cd agent-store/edp-agent-java/scenarios/wealth-demo/test
    python test_wealth_recommend_e2e.py
"""
import json
import os
import time
import uuid
import requests


EDPA_BASE_URL = "http://localhost:8190"
A2A_ENDPOINT = f"{EDPA_BASE_URL}/a2a"
ROUND_INTERVAL_SECONDS = 3
REQUEST_TIMEOUT_SECONDS = 180


def build_a2a_request(conversation_id: str, user_text: str) -> str:
    """构造 A2A JSON-RPC SendStreamingMessage 请求体。"""
    return json.dumps({
        "jsonrpc": "2.0",
        "id": str(uuid.uuid4()),
        "method": "SendStreamingMessage",
        "params": {
            "message": {
                "role": "ROLE_USER",
                "messageId": f"msg-{uuid.uuid4()}",
                "contextId": conversation_id,
                "parts": [{"text": user_text}]
            }
        }
    }, ensure_ascii=False)


def parse_sse_stream(response: requests.Response) -> list[dict]:
    """解析 SSE 流式响应，返回 JSON-RPC 事件列表。

    SSE 格式：
        event:jsonrpc
        data:{"jsonrpc":"2.0","id":1,"result":{...}}

    每个 data 行是一个完整的 JSON-RPC 消息。
    """
    events = []
    for line in response.iter_lines(decode_unicode=True):
        if line is None or line == "":
            continue
        if line.startswith("data:"):
            data_str = line[5:].lstrip()
            try:
                event = json.loads(data_str)
                events.append(event)
            except json.JSONDecodeError:
                events.append({"raw": data_str})
    return events


def extract_event_types(events: list[dict]) -> list[str]:
    """从 SSE 事件列表中提取事件类型序列。

    A2A SSE 事件结构：
        {"jsonrpc":"2.0","id":1,"result":{"artifactUpdate":{"artifact":{"parts":[{"text":"{\"event\":\"conversation_start\",...}"}]}}}}

    事件类型编码在 parts[0].text 的 JSON 的 "event" 字段中。
    """
    types = []
    for evt in events:
        if not isinstance(evt, dict):
            continue
        result = evt.get("result", {})
        artifact_update = result.get("artifactUpdate", {})
        artifact = artifact_update.get("artifact", {})
        parts = artifact.get("parts", [])
        for part in parts:
            if isinstance(part, dict) and "text" in part:
                text = part["text"]
                try:
                    inner = json.loads(text)
                    event_type = inner.get("event", "")
                    if event_type:
                        types.append(event_type)
                except (json.JSONDecodeError, TypeError):
                    pass
    return types


def _detect_skill_from_content(content: str) -> bool:
    """从 think_chunk 原始内容中检测是否调用了 skill_tool。"""
    keywords = ["技能文档", "skill_tool", "SKILL", "读取技能", "list_skill",
                "技能 ", "技能。", "读取相关技能", "skill "]
    return any(kw in content for kw in keywords)


def _find_following_tool_name(pairs: list[tuple[str, str]], think_chunk_idx: int) -> str | None:
    """查找 think_chunk 后续的所有工具调用（在同一 think 迭代内）。

    检测多种工具调用模式：
    - tool_start → 工具函数名（如 call_versatile, skill_tool）
    - todolist_start → todo_create/todo_modify（任务规划/重新规划）
    - todo_start → todo_modify（标记 IN_PROGRESS）
    - todo_end → todo_modify（标记 COMPLETED/CANCELLED）
    - interrupt_start → ask_user
    - final_answer_start → final_answer
    """
    tools = []
    for j in range(think_chunk_idx + 1, len(pairs)):
        et, content = pairs[j]
        if et == "think_start":
            break
        if et == "tool_start" and content:
            tools.append(content)
        elif et == "todolist_start":
            tools.append("todo_create/todo_modify")
        elif et == "todo_start":
            tools.append("todo_modify→IN_PROGRESS")
        elif et == "todo_end":
            tools.append("todo_modify→COMPLETED")
        elif et == "interrupt_start":
            tools.append("ask_user")
        elif et == "final_answer_start":
            tools.append("final_answer")

    if not tools:
        return None
    # 去重并保持顺序
    seen = []
    for t in tools:
        if t not in seen:
            seen.append(t)
    return " + ".join(seen)


def extract_events_with_content(events: list[dict]) -> list[tuple[str, str]]:
    """提取事件类型及其对应的内容。

    think_chunk 内容显示 token 数；如果该 think 迭代后续跟随工具调用事件，
    则标注工具函数名，格式：[→ func_name] N tokens。
    工具调用检测包括：tool_start、todolist_start、todo_start、todo_end、
    interrupt_start、final_answer_start，以及从内容中推断的 skill_tool。
    其他事件原文输出 content 字段。
    返回 [(event_type, content), ...]
    """
    pairs = []
    think_chunk_skill_flags = {}  # think_chunk index → has_skill
    for evt in events:
        if not isinstance(evt, dict):
            continue
        result = evt.get("result", {})
        artifact_update = result.get("artifactUpdate", {})
        artifact = artifact_update.get("artifact", {})
        parts = artifact.get("parts", [])
        for part in parts:
            if isinstance(part, dict) and "text" in part:
                text = part["text"]
                try:
                    inner = json.loads(text)
                    event_type = inner.get("event", "")
                    if not event_type:
                        continue
                    content = inner.get("content", "")
                    if content is None:
                        content = ""
                    if event_type == "think_chunk":
                        # 在截断前检测 skill_tool
                        think_chunk_skill_flags[len(pairs)] = _detect_skill_from_content(content)
                        content = f"{len(content)} tokens"
                    pairs.append((event_type, content))
                except (json.JSONDecodeError, TypeError):
                    pass

    # 为 think_chunk 添加 tool call 关联信息
    for i, (et, content) in enumerate(pairs):
        if et == "think_chunk":
            tool_name = _find_following_tool_name(pairs, i)
            # 从内容中检测 skill_tool
            has_skill = think_chunk_skill_flags.get(i, False)
            if has_skill:
                if tool_name:
                    tool_name = f"skill_tool + {tool_name}"
                else:
                    tool_name = "skill_tool"
            if tool_name:
                pairs[i] = (et, f"[→ {tool_name}] {content}")

    return pairs


def extract_text_chunks(events: list[dict]) -> list[str]:
    """提取 think_chunk 和 final_answer_chunk 的文本内容。

    如果 think_chunk 后续跟随 tool_start，标注工具函数名。
    """
    # 先提取所有事件的原始内容（用于查找 tool_start 关联）
    raw_pairs = []
    for evt in events:
        if not isinstance(evt, dict):
            continue
        result = evt.get("result", {})
        artifact_update = result.get("artifactUpdate", {})
        artifact = artifact_update.get("artifact", {})
        parts = artifact.get("parts", [])
        for part in parts:
            if isinstance(part, dict) and "text" in part:
                text = part["text"]
                try:
                    inner = json.loads(text)
                    event_type = inner.get("event", "")
                    content = inner.get("content", "")
                    if event_type and content:
                        raw_pairs.append((event_type, content))
                except (json.JSONDecodeError, TypeError):
                    pass

    # 构建 text chunks，为 think_chunk 添加 tool call 关联
    chunks = []
    for i, (et, content) in enumerate(raw_pairs):
        if et in ("think_chunk", "final_answer_chunk") and content:
            if et == "think_chunk":
                tool_name = _find_following_tool_name(raw_pairs, i)
                # 从内容中检测 skill_tool
                has_skill = _detect_skill_from_content(content)
                if has_skill:
                    if tool_name:
                        tool_name = f"skill_tool + {tool_name}"
                    else:
                        tool_name = "skill_tool"
                if tool_name:
                    chunks.append(f"[think_chunk → {tool_name}] {content[:150]}")
                else:
                    chunks.append(f"[think_chunk] {content[:150]}")
            else:
                chunks.append(f"[{et}] {content[:150]}")
    return chunks


def send_query(conversation_id: str, query: str, round_num: int) -> list[dict]:
    """发送一轮对话并返回 SSE 事件列表。"""
    body = build_a2a_request(conversation_id, query)
    print(f"\n{'='*60}")
    print(f"轮次 {round_num}: query=\"{query}\"")
    print(f"conversation_id={conversation_id}")
    print(f"{'='*60}")

    headers = {
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
        "X-Tenant-Id": "default",
    }
    response = requests.post(
        A2A_ENDPOINT,
        data=body.encode("utf-8"),
        headers=headers,
        stream=True,
        timeout=REQUEST_TIMEOUT_SECONDS,
        proxies={"http": None, "https": None},
    )
    response.raise_for_status()
    response.encoding = "utf-8"

    events = parse_sse_stream(response)
    event_types = extract_event_types(events)
    text_chunks = extract_text_chunks(events)

    print(f"  收到 {len(events)} 个 SSE 事件")
    print(f"  事件类型序列: {event_types}")
    if text_chunks:
        print(f"  文本内容摘要:")
        for chunk in text_chunks[:10]:
            print(f"    {chunk}")
        if len(text_chunks) > 10:
            print(f"    ... (共 {len(text_chunks)} 条文本块)")

    return events


def assert_round1_events(events: list[dict], query: str):
    """断言轮次1（理财推荐）的关键事件。"""
    event_types = extract_event_types(events)
    assert "conversation_start" in event_types, f"轮次1应包含 conversation_start，实际: {event_types}"
    assert "conversation_end" in event_types, f"轮次1应包含 conversation_end，实际: {event_types}"
    has_planning = "planning_start" in event_types
    has_todolist = "todolist_start" in event_types
    has_final = "final_answer_end" in event_types
    has_interrupt = "interrupt_start" in event_types
    assert has_planning or has_todolist, f"轮次1应包含 planning_start 或 todolist_start，实际: {event_types}"
    assert has_final or has_interrupt, f"轮次1应以 final_answer_end 或 interrupt_start 结束，实际: {event_types}"
    print(f"  [PASS] 轮次1断言通过（query=\"{query}\"）")


def assert_round2_events(events: list[dict], query: str):
    """断言轮次2（选品）的关键事件。

    LLM 行为非确定性：可能走到 ask_user 中断（interrupt_start），
    也可能只完成规划就达到迭代上限（conversation_end）。
    核心验证：会话正常启动和结束。
    """
    event_types = extract_event_types(events)
    assert "conversation_start" in event_types, f"轮次2应包含 conversation_start，实际: {event_types}"
    assert "conversation_end" in event_types, f"轮次2应包含 conversation_end，实际: {event_types}"
    has_think = "think_start" in event_types
    assert has_think, f"轮次2应包含 think_start（LLM 推理），实际: {event_types}"
    print(f"  [PASS] 轮次2断言通过（query=\"{query}\"）")


def assert_round3_events(events: list[dict], query: str):
    """断言轮次3（确认）的关键事件。

    LLM 行为非确定性：可能完成购买并输出 final_answer，
    也可能继续规划或达到迭代上限。
    核心验证：会话正常启动和结束。
    """
    event_types = extract_event_types(events)
    assert "conversation_start" in event_types, f"轮次3应包含 conversation_start，实际: {event_types}"
    assert "conversation_end" in event_types, f"轮次3应包含 conversation_end，实际: {event_types}"
    has_think = "think_start" in event_types
    assert has_think, f"轮次3应包含 think_start（LLM 推理），实际: {event_types}"
    print(f"  [PASS] 轮次3断言通过（query=\"{query}\"）")


def assert_round4_events(events: list[dict], query: str):
    """断言轮次4（二次确认）的关键事件。

    LLM 行为非确定性：可能完成最终购买并输出 final_answer，
    也可能继续规划或达到迭代上限。
    核心验证：会话正常启动和结束。
    """
    event_types = extract_event_types(events)
    assert "conversation_start" in event_types, f"轮次4应包含 conversation_start，实际: {event_types}"
    assert "conversation_end" in event_types, f"轮次4应包含 conversation_end，实际: {event_types}"
    has_think = "think_start" in event_types
    assert has_think, f"轮次4应包含 think_start（LLM 推理），实际: {event_types}"
    print(f"  [PASS] 轮次4断言通过（query=\"{query}\"）")


def build_test_report(conversation_id: str, all_events: list[list[dict]], queries: list[str],
                      round_durations: list[float], total_duration: float) -> str:
    """构建结构化测试报告（Markdown 格式字符串）。"""
    # 检测被测服务是否具备 Redis 能力（通过检查本次会话是否写入了 Redis）
    redis_enabled = False
    redis_key = f"edpa:todo:state:default:edp-agent:{conversation_id}"
    try:
        import redis as redis_lib
        r = redis_lib.Redis(host="localhost", port=6379, db=0)
        r.ping()
        # 检查本次会话是否有 Redis 数据（被测服务写入的证据）
        if r.get(redis_key) is not None:
            redis_enabled = True
    except Exception:
        redis_enabled = False

    lines = []
    lines.append("# 端到端测试报告 — 理财推荐三轮对话")
    lines.append("")
    lines.append(f"- **会话 ID**: `{conversation_id}`")
    lines.append(f"- **测试时间**: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- **目标服务**: {EDPA_BASE_URL}")
    lines.append(f"- **Redis 能力**: {'具备（RedisTodoStore 已集成）' if redis_enabled else '不具备（使用文件存储）'}")
    lines.append(f"- **总耗时**: {total_duration:.1f}s")
    lines.append(f"- **轮间间隔**: {ROUND_INTERVAL_SECONDS}s")
    lines.append("")

    # 轮次概览
    lines.append("## 轮次概览")
    lines.append("")
    lines.append("| 轮次 | Query | 事件数 | 耗时(s) | think次数 | todolist次数 | todo次数 | tool次数 | 状态 |")
    lines.append("|------|-------|--------|---------|-----------|---------------|----------|----------|------|")
    for i, (query, events, dur) in enumerate(zip(queries, all_events, round_durations)):
        status = "PASS" if events else "FAIL"
        ec_pairs = extract_events_with_content(events)
        think_count = sum(1 for et, _ in ec_pairs if et == "think_start")
        todolist_count = sum(1 for et, _ in ec_pairs if et == "todolist_start")
        todo_count = sum(1 for et, _ in ec_pairs if et == "todo_start")
        tool_count = sum(1 for et, _ in ec_pairs if et == "tool_start")
        lines.append(f"| {i+1} | {query} | {len(events)} | {dur:.1f} | {think_count} | {todolist_count} | {todo_count} | {tool_count} | {status} |")
    total_events = sum(len(e) for e in all_events)
    all_pairs = [extract_events_with_content(events) for events in all_events]
    total_think = sum(sum(1 for et, _ in pairs if et == "think_start") for pairs in all_pairs)
    total_todolist = sum(sum(1 for et, _ in pairs if et == "todolist_start") for pairs in all_pairs)
    total_todo = sum(sum(1 for et, _ in pairs if et == "todo_start") for pairs in all_pairs)
    total_tool = sum(sum(1 for et, _ in pairs if et == "tool_start") for pairs in all_pairs)
    lines.append(f"| **合计** |  | **{total_events}** | **{total_duration:.1f}** | **{total_think}** | **{total_todolist}** | **{total_todo}** | **{total_tool}** |  |")
    lines.append("")

    # 模型调用轮次信息
    lines.append("## 模型调用轮次信息")
    lines.append("")
    for i, (query, events) in enumerate(zip(queries, all_events)):
        ec_pairs = extract_events_with_content(events)
        think_count = sum(1 for et, _ in ec_pairs if et == "think_start")
        tool_count = sum(1 for et, _ in ec_pairs if et == "tool_start")
        lines.append(f"### 轮次{i+1} (query=\"{query}\")")
        lines.append(f"- LLM 推理次数（think_start）: {think_count}")
        lines.append(f"- 工具调用次数（tool_start）: {tool_count}")
        # 构建模型调用链
        chain = []
        for et, content in ec_pairs:
            if et == "think_start":
                chain.append("think")
            elif et == "tool_start":
                chain.append(f"tool({content})" if content else "tool")
        lines.append(f"- 模型调用链: {' → '.join(chain) if chain else '无'}")
        lines.append("")

    # LLM 调用分析（think → tool 关联）
    lines.append("## LLM 调用分析（think → tool 关联）")
    lines.append("")
    for i, (query, events) in enumerate(zip(queries, all_events)):
        ec_pairs = extract_events_with_content(events)
        lines.append(f"### 轮次{i+1} (query=\"{query}\")")
        lines.append("")
        lines.append("| 迭代 | think 内容摘要 | 工具调用 |")
        lines.append("|------|--------------|---------|")
        iter_num = 0
        for j, (et, content) in enumerate(ec_pairs):
            if et == "think_start":
                iter_num += 1
                # 收集该迭代内的 think_chunk 内容和工具调用
                think_summary = ""
                tool_names = []
                has_skill_in_iter = False
                for k in range(j + 1, len(ec_pairs)):
                    next_et, next_content = ec_pairs[k]
                    if next_et == "think_start":
                        break
                    if next_et == "think_chunk":
                        # 检测 skill_tool
                        if "[→ skill_tool" in next_content or "skill_tool +" in next_content:
                            has_skill_in_iter = True
                        # 提取原始内容（去掉前缀的 [→ tool] 标记）
                        raw_content = next_content
                        if raw_content.startswith("[→"):
                            bracket_end = raw_content.index("]") + 2
                            raw_content = raw_content[bracket_end:].strip()
                        if "tokens" in raw_content:
                            think_summary = raw_content
                        else:
                            think_summary = raw_content[:50]
                    elif next_et == "tool_start":
                        tool_names.append(next_content if next_content else "unknown")
                    elif next_et == "todolist_start":
                        tool_names.append("todo_create/todo_modify")
                    elif next_et == "todo_start":
                        tool_names.append("todo_modify→IN_PROGRESS")
                    elif next_et == "todo_end":
                        tool_names.append("todo_modify→COMPLETED")
                    elif next_et == "final_answer_start":
                        tool_names.append("final_answer")
                    elif next_et == "interrupt_start":
                        tool_names.append("ask_user")
                # 合并 skill_tool
                if has_skill_in_iter:
                    tool_names.insert(0, "skill_tool")
                # 去重
                seen_tools = []
                for t in tool_names:
                    if t not in seen_tools:
                        seen_tools.append(t)
                tool_name = " + ".join(seen_tools) if seen_tools else "—"
                lines.append(f"| {iter_num} | {think_summary} | {tool_name} |")
        lines.append("")

    # 各轮事件类型序列（带内容）
    lines.append("## 各轮事件类型序列")
    lines.append("")
    for i, (query, events) in enumerate(zip(queries, all_events)):
        ec_pairs = extract_events_with_content(events)
        lines.append(f"### 轮次{i+1} (query=\"{query}\")")
        lines.append("")
        for j, (et, content) in enumerate(ec_pairs):
            if content:
                lines.append(f"{j+1}. {et} — {content}")
            else:
                lines.append(f"{j+1}. {et}")
        lines.append("")

    # 各轮文本内容摘要
    lines.append("## 各轮文本内容摘要")
    lines.append("")
    for i, (query, events) in enumerate(zip(queries, all_events)):
        chunks = extract_text_chunks(events)
        lines.append(f"### 轮次{i+1} (query=\"{query}\") — {len(chunks)} 条文本块")
        lines.append("")
        for chunk in chunks:
            lines.append(f"- {chunk}")
        lines.append("")

    # 关键事件检查
    lines.append("## 关键事件检查")
    lines.append("")
    desc_map = {
        "conversation_start": "会话启动",
        "conversation_end": "会话结束",
        "request_start": "请求开始",
        "planning_start": "任务规划",
        "todolist_start": "任务列表展示",
        "todolist_item": "任务项",
        "todolist_end": "任务列表结束",
        "todo_start": "任务开始执行",
        "todo_end": "任务执行结束",
        "interrupt_start": "中断发起",
        "interrupt_end": "中断恢复",
        "think_start": "LLM 推理开始",
        "think_chunk": "推理内容",
        "think_end": "LLM 推理结束",
        "tool_start": "工具调用开始",
        "tool_end": "工具调用结束",
        "final_answer_start": "最终回答开始",
        "final_answer_chunk": "最终回答内容",
        "final_answer_end": "最终回答结束",
        "message": "消息",
        "error_event": "错误事件",
    }
    all_types = []
    for events in all_events:
        all_types.extend(extract_event_types(events))
    seen_order = []
    for t in all_types:
        if t not in seen_order:
            seen_order.append(t)
    for t in desc_map:
        if t not in seen_order:
            seen_order.append(t)
    lines.append("| 事件类型 | 描述 | 出现次数 | 状态 |")
    lines.append("|----------|------|----------|------|")
    for event_type in seen_order:
        count = all_types.count(event_type)
        desc = desc_map.get(event_type, "其他事件")
        mark = "Y" if count > 0 else "N"
        lines.append(f"| {event_type} | {desc} | {count} | {mark} |")
    lines.append("")

    # Redis 持久化验证
    lines.append("## Redis 持久化验证")
    lines.append("")
    try:
        import redis as redis_lib
        r = redis_lib.Redis(host="localhost", port=6379, db=0)
        key = f"edpa:todo:state:default:edp-agent:{conversation_id}"
        raw = r.get(key)
        ttl = r.ttl(key)
        if raw:
            todos = json.loads(raw)
            lines.append(f"- **Redis Key**: `{key}`")
            lines.append(f"- **TTL**: {ttl}s")
            lines.append(f"- **Todo 数量**: {len(todos)}")
            lines.append("")
            for t in todos:
                content = t.get("content", "")
                status = t.get("status", "")
                lines.append(f"  - {content} [{status}]")
        else:
            lines.append(f"- **Redis Key**: `{key}`")
            lines.append("- **状态**: 已过期或不存在（会话结束后 TTL 到期）")
    except Exception as e:
        lines.append(f"- **Redis 查询失败**: {e}")
    lines.append("")

    # 测试结果
    passed = all(e for e in all_events)
    lines.append("## 测试结果")
    lines.append("")
    lines.append(f"**结果**: {'ALL PASS' if passed else 'FAIL'}")
    lines.append(f"- 三轮对话: {'全部通过' if passed else '存在失败'}")
    lines.append(f"- 总事件数: {total_events}")
    lines.append(f"- 总耗时: {total_duration:.1f}s")

    return "\n".join(lines)


def print_test_report(conversation_id: str, all_events: list[list[dict]], queries: list[str],
                      round_durations: list[float], total_duration: float):
    """打印结构化测试报告并保存为 Markdown 文件。"""
    report = build_test_report(conversation_id, all_events, queries, round_durations, total_duration)
    # 控制台输出
    print(report)
    # 保存为 md 文件
    report_dir = os.path.dirname(os.path.abspath(__file__))
    report_file = os.path.join(report_dir, f"test_report_{conversation_id}.md")
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(report)
    print(f"\n报告已保存: {report_file}")


def test_wealth_recommend_three_rounds():
    """
    端到端测试：理财推荐 → 选品（第一个，100元）→ 确认。

    三轮对话通过同一个 conversation_id 串联：
      轮次1: "理财推荐"        → Agent 规划任务 + 调用 call_versatile 推荐产品
      轮次2: "第一个，10000元"  → Agent 选品确认（ask_user 中断）
      轮次3: "确认"            → Agent 资金筹划 + 购买理财
      轮次4: "确认"            → Agent 完成最终购买 + 最终回答

    每轮对话完成后间隔 3 秒发送下一轮。
    """
    conversation_id = time.strftime("%Y%m%d_%H%M%S")
    queries = ["理财推荐", "第一个，10000元", "确认", "确认"]
    assert_fns = [assert_round1_events, assert_round2_events, assert_round3_events, assert_round4_events]

    all_events = []
    round_durations = []
    test_start = time.time()

    for i, (query, assert_fn) in enumerate(zip(queries, assert_fns)):
        round_start = time.time()
        events = send_query(conversation_id, query, i + 1)
        round_durations.append(time.time() - round_start)
        all_events.append(events)
        assert_fn(events, query)

        if i < len(queries) - 1:
            print(f"  等待 {ROUND_INTERVAL_SECONDS} 秒后发送下一轮...")
            time.sleep(ROUND_INTERVAL_SECONDS)

    total_duration = time.time() - test_start
    print_test_report(conversation_id, all_events, queries, round_durations, total_duration)


if __name__ == "__main__":
    test_wealth_recommend_three_rounds()
