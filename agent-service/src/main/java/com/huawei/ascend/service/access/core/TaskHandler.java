package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessCancelCommand;
import com.huawei.ascend.service.access.model.ReplyContext;
import com.huawei.ascend.service.schema.AgentRequest;

import java.util.concurrent.CompletionStage;

/**
 * Inbound port from the access layer to the rest of the service.
 *
 * <p>The handler coordinates the L1 reply binding with task control. Task
 * control owns task identity; access binds egress after task control returns
 * the task id and before dispatch starts.
 */
public interface TaskHandler {
    CompletionStage<AccessAcceptedResponse> run(AgentRequest request, ReplyContext reply);

    CompletionStage<AccessAcceptedResponse> resume(AgentRequest request, ReplyContext reply);

    CompletionStage<AccessAcceptedResponse> cancel(AccessCancelCommand command);
}

