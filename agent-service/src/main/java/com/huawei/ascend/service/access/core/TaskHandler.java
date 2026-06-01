package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessIntent;

import java.util.concurrent.CompletionStage;

/**
 * Inbound port from the access layer to the rest of the service.
 *
 * <p>The access layer pre-allocates the {@code taskId} and binds the reply
 * (egress) channel for it <em>before</em> calling this handler, so a fully
 * synchronous runtime can deliver output during the call. Implementations turn
 * the intent into a task on task-centric-control using the supplied id.
 */
public interface TaskHandler {
    CompletionStage<AccessAcceptedResponse> runTask(AccessIntent intent, String taskId);
}



