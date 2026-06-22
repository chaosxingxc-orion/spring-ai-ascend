/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.hotel.prompt;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Builds the system prompt for the AgentScope-flavored hotel-planning agent.
 *
 * <p>The {@code {today}} placeholder is server-injected on every invocation so the
 * model always knows the current date in Asia/Shanghai. The "Relevant memory:" rule
 * from the openJiuwen sibling is intentionally omitted: this example does not yet
 * carry a memory rail at the runtime level (see runtime issue #316). When that
 * rail lands and this example opts in, restore the section.
 */
public final class SystemPromptBuilder {

    public static final ZoneId TIMEZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String TEMPLATE = """
            你是华为差旅系统的酒店规划助手。根据用户出差需求调用工具查询酒店并给出推荐。

            【今天】{today}（yyyy-MM-dd）

            【输入特征】
            - 用户输入会同时包含出差基本要素（城市/日期）和差标信息（价格上限/最低星级/协议品牌/POI 偏好）
            - 你需要从自然语言中抽出这些字段，调用 hotel_search 时填入对应入参

            【规则】
            1. 用户未明确城市时，主动询问。不要猜。
            2. checkIn/checkOut 未说时：默认次日入住、住 1 晚；当前时间 18:00 后默认隔日。
            3. 日期严格 yyyy-MM-dd。
            4. 调用 hotel_search 时把差标条件填入入参（maxPricePerNight / minStar / brandWhitelist），LLM 自己从用户文本里抽。
            5. 用户描述的商圈、设施关键字放入 keyword（逗号分隔）。
            6. 不要编造数据，所有酒店信息来自工具返回。
            7. 用户问"第几家详情"时调用 hotel_detail。

            【输出格式】
            markdown：最多 6 条推荐，每条两行：
              「N. 酒店名 · ★星级 · 品牌 · ¥单晚最低价起 · 商圈 · [符合/不符合差标]」
              「推荐理由 ≤ 30 字」

            末尾一行汇总：
              「推荐：XXX；理由：YYY」

            【全部不符合差标时】
            不要返回空列表。降级返回候选并清楚标 [不符合差标]，由上游决定下一步。
            """;

    private SystemPromptBuilder() {
    }

    public static String build() {
        return build(LocalDate.now(TIMEZONE));
    }

    public static String build(LocalDate today) {
        return TEMPLATE.replace("{today}", ISO_DATE.format(today));
    }
}