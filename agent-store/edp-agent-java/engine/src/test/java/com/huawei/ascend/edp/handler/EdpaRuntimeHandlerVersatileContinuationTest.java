package com.huawei.ascend.edp.handler;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdpaRuntimeHandlerVersatileContinuationTest {

    @Test
    void extractsWrappedVersatileMenuConfirmation() throws Exception {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        Map<String, Object> input = Map.of("query",
                "{\"inputs\":{\"query\":\"确认转账\",\"menu_type\":\"TRANSFER_MENU\",\"menu_confirm\":true}}");

        Map<String, Object> extracted = extract(handler, input);

        assertEquals("TRANSFER_MENU", extracted.get("menu_type"));
        assertEquals(Boolean.TRUE, extracted.get("menu_confirm"));
        assertEquals("确认转账", extracted.get("query"));
    }

    @Test
    void extractsRawVersatileMenuConfirmation() throws Exception {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        Map<String, Object> input = Map.of("query",
                "{\"query\":\"确认转账\",\"menu_type\":\"TRANSFER_MENU\",\"menu_confirm\":true}");

        Map<String, Object> extracted = extract(handler, input);

        assertEquals("TRANSFER_MENU", extracted.get("menu_type"));
        assertEquals(Boolean.TRUE, extracted.get("menu_confirm"));
    }

    @Test
    void ignoresPlainUserText() throws Exception {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        Map<String, Object> input = Map.of("query", "我要转账");

        assertNull(extract(handler, input));
    }

    @Test
    void buildsInteractiveResumeInputForVersatileToolResult() throws Exception {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        Map<String, Object> result = Map.of(
                "source", "versatile",
                "status", "completed",
                "content", "{\"status\":\"success\",\"msg\":\"转账成功\"}");

        Map<String, Object> resumeInput = resumeInput(handler, "conversation-1", "tool-call-1", result);

        assertEquals("conversation-1", resumeInput.get("conversation_id"));
        InteractiveInput interactiveInput = (InteractiveInput) resumeInput.get("query");
        String toolResult = String.valueOf(interactiveInput.getUserInputs().get("tool-call-1"));
        assertTrue(toolResult.contains("\"status\":\"completed\""));
        assertTrue(toolResult.contains("转账成功"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extract(EdpaRuntimeHandler handler, Map<String, Object> input) throws Exception {
        Method method = EdpaRuntimeHandler.class.getDeclaredMethod("extractVersatileContinuationInputs", Object.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(handler, new LinkedHashMap<>(input));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resumeInput(EdpaRuntimeHandler handler, String conversationId, String interruptId,
            Map<String, Object> result) throws Exception {
        Method method = EdpaRuntimeHandler.class.getDeclaredMethod("versatileToolResumeInput",
                String.class, String.class, Map.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(handler, conversationId, interruptId, result);
    }
}
