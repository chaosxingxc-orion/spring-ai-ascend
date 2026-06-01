package com.huawei.ascend.service.sessionmanage.store.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.service.sessionmanage.model.Session;

import java.io.UncheckedIOException;
import java.util.Objects;

public final class JacksonSessionCodec implements SessionCodec {

    private final ObjectMapper objectMapper;

    public JacksonSessionCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String encode(Session session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public Session decode(String value) {
        try {
            return objectMapper.readValue(value, Session.class);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
