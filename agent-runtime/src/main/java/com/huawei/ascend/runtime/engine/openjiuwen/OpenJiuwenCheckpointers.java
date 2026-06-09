package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.common.Guards;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Small factory for openJiuwen native checkpointers used by runtime samples.
 *
 * <p>The runtime does not wrap or replace openJiuwen checkpoint storage. This
 * helper only centralizes the standard setup shape: create an in-memory
 * checkpointer for local runs, keep a Redis checkpointer candidate available
 * for durable runs, and install the selected one into openJiuwen's
 * {@link CheckpointerFactory}.
 */
public final class OpenJiuwenCheckpointers {
    public static final String IN_MEMORY = "in-memory";
    public static final String REDIS = "redis";

    private OpenJiuwenCheckpointers() {
    }

    public static Checkpointer inMemory() {
        return new InMemoryCheckpointer();
    }

    public static Checkpointer redis(String redisUrl) {
        return new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", Guards.requireNonBlank(redisUrl, "redisUrl"))));
    }

    public static Checkpointer configureDefault(String checkpointerType, String redisUrl) {
        Checkpointer inMemoryCheckpointer = inMemory();
        Checkpointer redisCheckpointer = redis(redisUrl);
        Checkpointer selected = isRedis(checkpointerType) ? redisCheckpointer : inMemoryCheckpointer;
        return setDefault(selected);
    }

    public static Checkpointer setDefault(Checkpointer checkpointer) {
        Checkpointer selected = Objects.requireNonNull(checkpointer, "checkpointer");
        CheckpointerFactory.setDefaultCheckpointer(selected);
        return selected;
    }

    public static boolean isRedis(String checkpointerType) {
        return REDIS.equals(String.valueOf(checkpointerType).trim().toLowerCase(Locale.ROOT));
    }
}
