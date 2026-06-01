package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.ReplyChannel;
import com.huawei.ascend.service.queue.TaskQueue;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EgressDispatcher {

    private static final long IDLE_BACKOFF_MILLIS = 5L;
    private static final Logger LOGGER = LoggerFactory.getLogger(EgressDispatcher.class);

    private final EgressQueueRegistry registry;
    private final Map<ReplyChannel, EgressAdapter> adapters;
    private final Executor executor;
    private final ConcurrentHashMap<Key, Boolean> running = new ConcurrentHashMap<>();

    public EgressDispatcher(EgressQueueRegistry registry, Collection<EgressAdapter> adapters, Executor executor) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.adapters = new EnumMap<>(ReplyChannel.class);
        for (EgressAdapter adapter : Objects.requireNonNull(adapters, "adapters")) {
            this.adapters.put(adapter.channel(), adapter);
        }
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void start(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding);
        if (running.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        CompletableFuture.runAsync(() -> dispatchLoop(binding, key), executor)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        LOGGER.error(
                                "Egress dispatcher stopped after delivery failure, tenantId={}, sessionId={}, taskId={}",
                                binding.tenantId(),
                                binding.sessionId(),
                                binding.taskId(),
                                failure);
                    }
                });
    }

    public void stop(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        running.remove(Key.from(binding));
    }

    private void dispatchLoop(EgressBinding binding, Key key) {
        try {
            while (running.containsKey(key)) {
                Optional<TaskQueue<NotificationFrame>> queue =
                        registry.find(binding.tenantId(), binding.sessionId(), binding.taskId());
                if (queue.isEmpty()) {
                    stop(binding);
                    return;
                }
                Optional<NotificationFrame> frame = queue.get().poll();
                if (frame.isPresent()) {
                    deliver(binding, frame.get());
                } else {
                    Thread.sleep(IDLE_BACKOFF_MILLIS);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            registry.remove(binding.tenantId(), binding.sessionId(), binding.taskId());
            throw ex;
        } finally {
            running.remove(key);
        }
    }

    private void deliver(EgressBinding binding, NotificationFrame frame) {
        EgressAdapter adapter = adapters.get(binding.replyChannel());
        if (adapter == null) {
            throw new EgressDeliveryException("No egress adapter for channel " + binding.replyChannel());
        }
        adapter.deliver(binding, frame);
        if (frame.terminal()) {
            stop(binding);
            registry.remove(binding.tenantId(), binding.sessionId(), binding.taskId());
        }
    }

    private record Key(String tenantId, String sessionId, String taskId) {
        static Key from(EgressBinding binding) {
            return new Key(binding.tenantId(), binding.sessionId(), binding.taskId());
        }
    }
}


