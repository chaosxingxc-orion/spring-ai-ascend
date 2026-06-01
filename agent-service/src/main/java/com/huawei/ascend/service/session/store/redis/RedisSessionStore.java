package com.huawei.ascend.service.session.store.redis;

import com.huawei.ascend.service.session.model.Session;
import com.huawei.ascend.service.session.model.SessionKey;
import com.huawei.ascend.service.session.store.SessionStore;
import com.huawei.ascend.service.session.store.memory.SessionNotFoundException;

import java.util.Objects;
import java.util.Optional;
import java.util.ConcurrentModificationException;
import java.util.function.UnaryOperator;

public final class RedisSessionStore implements SessionStore {

    private final RedisSessionCommands commands;
    private final SessionCodec codec;
    private final RedisSessionStoreProperties properties;

    public RedisSessionStore(
            RedisSessionCommands commands,
            SessionCodec codec,
            RedisSessionStoreProperties properties) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public Optional<Session> find(SessionKey key) {
        return commands.get(redisKey(key)).map(codec::decode);
    }

    @Override
    public Session save(Session session) {
        Objects.requireNonNull(session, "session");
        Session saved = withVersion(session, Math.max(1L, session.version()));
        commands.set(redisKey(saved.key()), codec.encode(saved), properties.ttl());
        return saved;
    }

    @Override
    public Session update(SessionKey key, UnaryOperator<Session> mutator) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mutator, "mutator");
        for (int attempt = 0; attempt < properties.maxCasRetries(); attempt++) {
            Session current = find(key).orElseThrow(() -> new SessionNotFoundException(key));
            Session mutated = withVersion(Objects.requireNonNull(mutator.apply(current), "mutated session"),
                    current.version() + 1);
            if (commands.compareAndSet(redisKey(key), current.version(), codec.encode(mutated), properties.ttl())) {
                return mutated;
            }
            Thread.onSpinWait();
        }
        throw new ConcurrentModificationException(
                "Failed to update session after " + properties.maxCasRetries() + " CAS retries: " + key);
    }

    @Override
    public boolean saveIfVersion(Session session, long expectedVersion) {
        Objects.requireNonNull(session, "session");
        Session saved = withVersion(session, expectedVersion + 1);
        return commands.compareAndSet(redisKey(session.key()), expectedVersion, codec.encode(saved), properties.ttl());
    }

    @Override
    public void remove(SessionKey key) {
        commands.delete(redisKey(key));
    }

    private String redisKey(SessionKey key) {
        return properties.keyPrefix() + key.tenantId() + ":" + key.sessionId();
    }

    private static Session withVersion(Session session, long version) {
        return new Session(
                session.tenantId(),
                session.userId(),
                session.agentId(),
                session.sessionId(),
                version,
                session.messages(),
                session.state(),
                session.tasks(),
                session.metadata(),
                session.createdAt(),
                session.updatedAt(),
                session.lastAccessedAt(),
                session.expiresAt());
    }
}
