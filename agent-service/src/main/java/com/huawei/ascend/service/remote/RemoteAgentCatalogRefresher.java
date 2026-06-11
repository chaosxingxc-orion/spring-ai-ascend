package com.huawei.ascend.service.remote;

import com.huawei.ascend.runtime.engine.spi.RemoteAgentCatalogPort;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.SmartLifecycle;

/** Polls the catalog so remote runtimes that boot later (or restart) become callable without a redeploy. */
public final class RemoteAgentCatalogRefresher implements SmartLifecycle {

    private final RemoteAgentCatalogPort catalog;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();

    RemoteAgentCatalogRefresher(RemoteAgentCatalogPort catalog, ExecutorService executor) {
        this.catalog = catalog;
        this.executor = executor;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.execute(this::run);
        }
    }

    void refreshOnce() {
        catalog.refreshPending();
    }

    private void run() {
        while (running.get()) {
            refreshOnce();
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.set(false);
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
