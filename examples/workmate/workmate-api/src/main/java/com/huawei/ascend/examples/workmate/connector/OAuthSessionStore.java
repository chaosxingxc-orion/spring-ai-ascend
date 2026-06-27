package com.huawei.ascend.examples.workmate.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class OAuthSessionStore {

    enum SessionStatus {
        PENDING,
        APPROVED,
        DENIED,
        EXPIRED
    }

    record OAuthSession(
            String id,
            String connectorId,
            ConnectorAuthMethod method,
            String userCode,
            String deviceCode,
            String verificationUri,
            String redirectState,
            SessionStatus status,
            Instant expiresAt,
            Map<String, String> headers) {
    }

    private final ConcurrentHashMap<String, OAuthSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> redirectStateToSession = new ConcurrentHashMap<>();

    OAuthSession createDeviceSession(
            String connectorId,
            ConnectorAuthMethod method,
            String userCode,
            String deviceCode,
            String verificationUri,
            Instant expiresAt) {
        String id = UUID.randomUUID().toString();
        OAuthSession session = new OAuthSession(
                id,
                connectorId,
                method,
                userCode,
                deviceCode,
                verificationUri,
                null,
                SessionStatus.PENDING,
                expiresAt,
                Map.of());
        sessions.put(id, session);
        return session;
    }

    OAuthSession createRedirectSession(String connectorId, String redirectState, Instant expiresAt) {
        String id = UUID.randomUUID().toString();
        OAuthSession session = new OAuthSession(
                id,
                connectorId,
                ConnectorAuthMethod.REDIRECT,
                null,
                null,
                null,
                redirectState,
                SessionStatus.PENDING,
                expiresAt,
                Map.of());
        sessions.put(id, session);
        redirectStateToSession.put(redirectState, id);
        return session;
    }

    Optional<OAuthSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(this::expireIfNeeded);
    }

    Optional<OAuthSession> findByRedirectState(String state) {
        String sessionId = redirectStateToSession.get(state);
        if (sessionId == null) {
            return Optional.empty();
        }
        return find(sessionId);
    }

    OAuthSession complete(String sessionId, Map<String, String> headers) {
        OAuthSession current = sessions.get(sessionId);
        if (current == null) {
            throw new IllegalArgumentException("OAuth session not found: " + sessionId);
        }
        OAuthSession updated = new OAuthSession(
                current.id(),
                current.connectorId(),
                current.method(),
                current.userCode(),
                current.deviceCode(),
                current.verificationUri(),
                current.redirectState(),
                SessionStatus.APPROVED,
                current.expiresAt(),
                Map.copyOf(headers));
        sessions.put(sessionId, updated);
        if (current.redirectState() != null) {
            redirectStateToSession.remove(current.redirectState());
        }
        return updated;
    }

    void deny(String sessionId) {
        OAuthSession current = sessions.get(sessionId);
        if (current == null) {
            return;
        }
        sessions.put(
                sessionId,
                new OAuthSession(
                        current.id(),
                        current.connectorId(),
                        current.method(),
                        current.userCode(),
                        current.deviceCode(),
                        current.verificationUri(),
                        current.redirectState(),
                        SessionStatus.DENIED,
                        current.expiresAt(),
                        current.headers()));
    }

    void remove(String sessionId) {
        OAuthSession removed = sessions.remove(sessionId);
        if (removed != null && removed.redirectState() != null) {
            redirectStateToSession.remove(removed.redirectState());
        }
    }

    private OAuthSession expireIfNeeded(OAuthSession session) {
        if (session.status() != SessionStatus.PENDING || !Instant.now().isAfter(session.expiresAt())) {
            return session;
        }
        OAuthSession expired = new OAuthSession(
                session.id(),
                session.connectorId(),
                session.method(),
                session.userCode(),
                session.deviceCode(),
                session.verificationUri(),
                session.redirectState(),
                SessionStatus.EXPIRED,
                session.expiresAt(),
                session.headers());
        sessions.put(session.id(), expired);
        return expired;
    }
}
