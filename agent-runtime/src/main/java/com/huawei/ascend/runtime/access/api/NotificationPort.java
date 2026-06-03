package com.huawei.ascend.service.access.api;

import com.huawei.ascend.service.access.model.AgentNotification;

public interface NotificationPort {
    void notify(AgentNotification notification);
}



