package com.huawei.ascend.examples.workmate.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class RunEventWriter {

    private final RunEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final SessionSequenceAllocator sequenceAllocator;

    RunEventWriter(
            RunEventRepository eventRepository,
            ObjectMapper objectMapper,
            SessionSequenceAllocator sequenceAllocator) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.sequenceAllocator = sequenceAllocator;
    }

    boolean hasRunEvent(UUID sessionId, String eventName) {
        return eventRepository.existsBySessionIdAndEventName(sessionId, eventName);
    }

    List<RecordedRunEvent> listEventsAfter(UUID sessionId, int afterSeq) {
        return eventRepository.findBySessionIdAndSeqGreaterThanOrderBySeqAsc(sessionId, afterSeq).stream()
                .map(this::toRecordedEvent)
                .toList();
    }

    List<Map<String, Object>> listEventLog(UUID sessionId, int afterSeq) {
        return eventRepository.findBySessionIdAndSeqGreaterThanOrderBySeqAsc(sessionId, afterSeq).stream()
                .map(event -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("seq", event.getSeq());
                    entry.put("name", event.getEventName());
                    entry.put("data", readEventPayload(event));
                    return entry;
                })
                .toList();
    }

    RecordedRunEvent persistRunEvent(
            RunPersistenceContext context, String eventName, Map<String, Object> payload) {
        if (context == null) {
            return null;
        }
        int seq = sequenceAllocator.nextSeq(context.sessionId());
        String payloadJson = SessionMessageJson.writeJson(objectMapper, payload);
        eventRepository.save(new RunEvent(
                UUID.randomUUID(),
                context.sessionId(),
                context.runId(),
                seq,
                eventName,
                payloadJson));
        return new RecordedRunEvent(seq, eventName, payloadJson);
    }

    Map<String, Object> readEventPayload(RunEvent event) {
        String json = event.getPayloadJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, SessionMessageJson.MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    int maxSeq(UUID sessionId) {
        return eventRepository.findMaxSeq(sessionId);
    }

    List<RunEvent> findBySessionIdAndEventNameOrderBySeqAsc(UUID sessionId, String eventName) {
        return eventRepository.findBySessionIdAndEventNameOrderBySeqAsc(sessionId, eventName);
    }

    private RecordedRunEvent toRecordedEvent(RunEvent event) {
        return new RecordedRunEvent(event.getSeq(), event.getEventName(), event.getPayloadJson());
    }
}
