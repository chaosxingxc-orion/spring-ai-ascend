package com.huawei.ascend.runtime.access.output;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public final class OutputChannel {

    private final List<RuntimeOutput> history = new CopyOnWriteArrayList<>();
    private final Sinks.Many<RuntimeOutput> sink = Sinks.many().replay().all();
    private boolean closed;

    public Flux<RuntimeOutput> stream() {
        return sink.asFlux();
    }

    public synchronized void write(RuntimeOutput output) {
        RuntimeOutput requiredOutput = Objects.requireNonNull(output, "output");
        if (closed) {
            throw new IllegalStateException("Output channel is closed");
        }
        sink.tryEmitNext(requiredOutput).orThrow();
        history.add(requiredOutput);
        if (requiredOutput.terminal()) {
            closed = true;
            sink.tryEmitComplete().orThrow();
        }
    }

    public List<RuntimeOutput> history() {
        return List.copyOf(history);
    }
}
