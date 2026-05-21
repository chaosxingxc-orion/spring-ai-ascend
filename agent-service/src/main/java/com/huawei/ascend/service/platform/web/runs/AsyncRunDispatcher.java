package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.runtime.runs.Run;

/**
 * SPI invoked by {@link RunController} immediately after a Run is persisted in PENDING
 * state. Implementations dispatch the run for actual execution. {@link RunController}
 * always invokes this via {@code CompletableFuture.runAsync(...)} so the POST handler
 * MUST NOT block on this method, even if an implementation itself runs synchronously.
 *
 * <p>Layer-0 principle P-F (Cursor Flow): the client-to-runtime boundary is
 * non-blocking. The dispatcher's lifetime is decoupled from the HTTP response.
 *
 * <p>At W1.x the default {@link NoOpAsyncRunDispatcher} is registered; W2 supplies a
 * real orchestrator-backed implementation (ADR-0070).
 */
@FunctionalInterface
public interface AsyncRunDispatcher {

    void dispatch(Run run);
}
