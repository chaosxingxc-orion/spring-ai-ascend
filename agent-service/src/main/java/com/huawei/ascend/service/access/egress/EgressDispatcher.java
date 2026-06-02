package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.ReplyChannel;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public final class EgressDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EgressDispatcher.class);

    private final EgressQueueRegistry registry;
    private final Map<ReplyChannel, EgressAdapter> adapters;
    private final Scheduler scheduler;
    private final ConcurrentHashMap<Key, Disposable> running = new ConcurrentHashMap<>();

    public EgressDispatcher(EgressQueueRegistry registry, Collection<EgressAdapter> adapters, Executor executor) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.adapters = new EnumMap<>(ReplyChannel.class);
        for (EgressAdapter adapter : Objects.requireNonNull(adapters, "adapters")) {
            this.adapters.put(adapter.channel(), adapter);
        }
        this.scheduler = Schedulers.fromExecutor(Objects.requireNonNull(executor, "executor"));
    }

    public void start(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding);
        running.computeIfAbsent(key, ignored -> registry.getOrCreate(binding).stream()
                .publishOn(scheduler)
                .doOnNext(frame -> deliver(binding, frame))
                .doOnError(failure -> {
                    registry.remove(binding.tenantId(), binding.sessionId());
                    LOGGER.error(
                            "Egress dispatcher stopped after delivery failure, tenantId={}, sessionId={}",
                            binding.tenantId(),
                            binding.sessionId(),
                            failure);
                })
                .doFinally(signalType -> running.remove(key))
                .subscribe());
    }

    public void stop(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Disposable subscription = running.remove(Key.from(binding));
        if (subscription != null) {
            subscription.dispose();
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
            registry.remove(binding.tenantId(), binding.sessionId());
        }
    }

    private record Key(String tenantId, String sessionId) {
        static Key from(EgressBinding binding) {
            return new Key(binding.tenantId(), binding.sessionId());
        }
    }
}
