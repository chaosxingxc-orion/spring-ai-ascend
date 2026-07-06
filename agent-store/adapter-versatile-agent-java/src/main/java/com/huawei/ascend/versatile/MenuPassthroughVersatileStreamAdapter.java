package com.huawei.ascend.versatile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.versatile.VersatileProperties;
import com.huawei.ascend.runtime.engine.versatile.VersatileStreamAdapter;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在框架 {@link VersatileStreamAdapter} 之上做"菜单节点全维度透传"的薄包装。
 *
 * <p>背景：框架 {@code VersatileStreamAdapter#handleMessage} 对每个 message 节点
 * 只取 {@code data.text} 一个字段发出，会丢弃 {@code menu_type}/{@code node_type}/
 * {@code node_name} 等兄弟字段。这与老 Python 项目
 * {@code response_wrapper.wrap_workflow_event}（把整个节点 {@code data} 原样塞进
 * {@code custom_rsp_data.data}）不一致，导致前端 A2A 模式拿不到 {@code menu_type}、
 * 无法渲染菜单卡片。</p>
 *
 * <p>本类只做一件事：在把原始 SSE 行交给框架解析之前，对**带 {@code menu_type} 的
 * message 行**，把 {@code data.text} 改写成"该节点整段 {@code data} 的 JSON 快照"。
 * 于是框架照常以 {@code Target.USER} 透传出去的文本就是全维度 JSON，前端解析回
 * {@code custom_rsp_data.data} 即与 Python 维度对齐。其余所有行原样委托父类，
 * 普通流式文本、结果节点回灌 LLM、无 End 节点中断等行为完全不变。</p>
 */
public class MenuPassthroughVersatileStreamAdapter extends VersatileStreamAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MenuPassthroughVersatileStreamAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MenuPassthroughVersatileStreamAdapter(VersatileProperties properties) {
        super(properties);
    }

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        return super.adapt(rawResults.map(MenuPassthroughVersatileStreamAdapter::rewriteMenuLine));
    }

    /**
     * 对带 {@code menu_type} 的 message 行，把 {@code data.text} 替换为整段 {@code data}
     * 的 JSON 快照；其余行原样返回。任何解析失败都安全回退为原始行。
     */
    @SuppressWarnings("unchecked")
    private static Object rewriteMenuLine(Object raw) {
        if (raw == null) {
            return null;
        }
        String line = String.valueOf(raw);
        String prefix = "";
        String json = line.strip();
        if (json.startsWith("data:")) {
            prefix = "data:";
            json = json.substring(5).strip();
        }
        if (json.isEmpty() || json.charAt(0) != '{') {
            return raw;
        }
        try {
            Map<String, Object> frame = MAPPER.readValue(json, Map.class);
            if (!"message".equals(frame.get("event"))) {
                return raw;
            }
            if (!(frame.get("data") instanceof Map<?, ?> dataRaw)) {
                return raw;
            }
            Map<String, Object> data = (Map<String, Object>) dataRaw;
            Object menuType = data.get("menu_type");
            if (menuType == null || String.valueOf(menuType).isBlank()) {
                return raw;
            }
            // 先快照原始 data（含其原始 text），再把 text 覆盖为该快照 JSON。
            String fullDataJson = MAPPER.writeValueAsString(data);
            data.put("text", fullDataJson);
            LOG.info("versatile menu node passthrough: menu_type={} carried full data ({} chars)",
                    menuType, fullDataJson.length());
            return prefix + MAPPER.writeValueAsString(frame);
        } catch (Exception e) {
            return raw;
        }
    }
}
