package com.huawei.ascend.runtime.access.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.spec.StreamingEventKind;

/**
 * Normalises a serialized A2A JSON-RPC response tree onto the on-the-wire shape
 * the A2A spec expects.
 *
 * <p>Split out of {@code A2aJsonRpcHandler}. Two distinct wire concerns live
 * here: streaming results carry a protobuf {@code StreamingEventKind} that must
 * be rendered via the protobuf JSON printer; non-streaming results carry proto
 * enum constants (TASK_STATE_*, ROLE_*) and implicit part kinds that must be
 * lowered to their A2A string forms, with null fields stripped.
 */
final class A2aResponseMapper {

    private final ObjectMapper objectMapper;

    A2aResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Rewrites {@code json} (the serialized {@code response}) in place onto the
     * A2A wire shape: streaming responses are re-rendered from protobuf and have
     * null fields removed; all others have their enum/kind values normalized.
     */
    void normalizeWire(Object response, JsonNode json)
            throws JsonProcessingException, InvalidProtocolBufferException {
        if (normalizeStreamingResponseResult(response, json)) {
            removeNullFields(json);
        } else {
            normalizeA2aWireValues(json);
        }
    }

    private boolean normalizeStreamingResponseResult(Object response, JsonNode json)
            throws JsonProcessingException, InvalidProtocolBufferException {
        if (!(response instanceof SendStreamingMessageResponse streamingResponse)
                || !(json instanceof ObjectNode object)
                || !(streamingResponse.getResult() instanceof StreamingEventKind streamingEvent)) {
            return false;
        }
        String streamResponseJson = com.google.protobuf.util.JsonFormat.printer()
                .omittingInsignificantWhitespace()
                .print(ProtoUtils.ToProto.streamResponse(streamingEvent));
        object.set("result", objectMapper.readTree(streamResponseJson));
        return true;
    }

    private void normalizeA2aWireValues(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ArrayNode array) {
            array.forEach(this::normalizeA2aWireValues);
            return;
        }
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        normalizeTaskState(object);
        normalizeMessageRole(object);
        normalizePartKind(object);
        object.elements().forEachRemaining(this::normalizeA2aWireValues);
        removeNullFields(object);
    }

    private void removeNullFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node instanceof ArrayNode array) {
            array.forEach(this::removeNullFields);
            return;
        }
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        object.elements().forEachRemaining(this::removeNullFields);
        List<String> nullFields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue() == null || field.getValue().isNull()) {
                nullFields.add(field.getKey());
            }
        }
        nullFields.forEach(object::remove);
    }

    private void normalizeTaskState(ObjectNode object) {
        JsonNode state = object.get("state");
        if (state == null || !state.isTextual()) {
            return;
        }
        String value = switch (state.asText()) {
            case "TASK_STATE_SUBMITTED" -> "submitted";
            case "TASK_STATE_WORKING" -> "working";
            case "TASK_STATE_INPUT_REQUIRED" -> "input-required";
            case "TASK_STATE_AUTH_REQUIRED" -> "auth-required";
            case "TASK_STATE_COMPLETED" -> "completed";
            case "TASK_STATE_CANCELED" -> "canceled";
            case "TASK_STATE_FAILED" -> "failed";
            case "TASK_STATE_REJECTED" -> "rejected";
            default -> null;
        };
        if (value != null) {
            object.put("state", value);
        }
    }

    private void normalizeMessageRole(ObjectNode object) {
        JsonNode role = object.get("role");
        if (role == null || !role.isTextual()) {
            return;
        }
        String value = switch (role.asText()) {
            case "ROLE_AGENT" -> "agent";
            case "ROLE_USER" -> "user";
            default -> null;
        };
        if (value != null) {
            object.put("role", value);
        }
    }

    private void normalizePartKind(ObjectNode object) {
        if (object.has("kind")) {
            return;
        }
        if (object.has("text")) {
            object.put("kind", "text");
        } else if (object.has("file")) {
            object.put("kind", "file");
        } else if (object.has("data")) {
            object.put("kind", "data");
        }
    }
}
