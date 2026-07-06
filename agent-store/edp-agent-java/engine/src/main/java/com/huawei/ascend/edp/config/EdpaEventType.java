package com.huawei.ascend.edp.config;

/**
 * EDPA 思维链事件类型枚举（event 唯一来源）。
 *
 * <p>设计文档 §7.1 定义的事件流契约。所有 Rail 发射事件必须引用此枚举，
 * 禁止在代码中使用字符串字面量作为事件类型。</p>
 *
 * <p>事件配对规则：</p>
 * <ul>
 *   <li>conversation_start ↔ conversation_end（会话级，1:1）</li>
 *   <li>request_start（无配对，用户请求已接收）</li>
 *   <li>planning_start（无配对，开始规划）</li>
 *   <li>think_start ↔ think_end（每轮 LLM 推理，1:1）</li>
 *   <li>final_answer_start ↔ final_answer_end（最终回答，1:1）</li>
 *   <li>tool_start ↔ tool_end（业务工具，1:1）</li>
 *   <li>tool_status（工具执行中间状态，可多次，无配对）</li>
 *   <li>todolist_start ↔ todolist_end（任务列表快照，1:N item）</li>
 *   <li>todo_start ↔ todo_end（单个任务执行，按状态转移）</li>
 *   <li>todo_status（任务执行中间状态，可多次，无配对）</li>
 *   <li>interrupt_start ↔ interrupt_end（用户中断，跨轮配对）</li>
 *   <li>error_event（异常，无配对）</li>
 * </ul>
 * <p>共 22 种事件类型（含 todo_status / tool_status 中间状态事件，暂不发射但预留枚举位）。</p>
 */
public enum EdpaEventType {

    // 会话生命周期
    CONVERSATION_START("conversation_start"),
    CONVERSATION_END("conversation_end"),

    // 用户请求接收（beforeInvoke，conversation_start 之后）
    REQUEST_START("request_start"),

    // 规划开始（todo_create 前或首轮 think 前）
    PLANNING_START("planning_start"),

    // 思维链（每轮 LLM 推理一对）
    THINK_START("think_start"),
    THINK_CHUNK("think_chunk"),
    THINK_END("think_end"),

    // 最终回答
    FINAL_ANSWER_START("final_answer_start"),
    FINAL_ANSWER_CHUNK("final_answer_chunk"),
    FINAL_ANSWER_END("final_answer_end"),

    // 业务工具
    TOOL_START("tool_start"),
    TOOL_STATUS("tool_status"),
    TOOL_END("tool_end"),

    // 任务列表快照（逐条）
    TODOLIST_START("todolist_start"),
    TODOLIST_ITEM("todolist_item"),
    TODOLIST_END("todolist_end"),

    // 单个任务执行
    TODO_START("todo_start"),
    TODO_STATUS("todo_status"),
    TODO_END("todo_end"),

    // 用户中断（跨轮配对）
    INTERRUPT_START("interrupt_start"),
    INTERRUPT_END("interrupt_end"),

    // 异常（无配对）
    ERROR_EVENT("error_event");

    private final String wireName;

    EdpaEventType(String wireName) {
        this.wireName = wireName;
    }

    /** 事件在线缆上的字符串标识（写入 SSE payload 的 event 字段）。 */
    public String wireName() {
        return wireName;
    }
}
