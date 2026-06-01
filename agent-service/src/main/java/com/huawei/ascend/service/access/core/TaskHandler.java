package com.huawei.ascend.service.access.core;

import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.model.AccessIntent;

import java.util.concurrent.CompletionStage;

public interface TaskHandler {
    CompletionStage<AccessAcceptedResponse> runTask(AccessIntent intent);
}



