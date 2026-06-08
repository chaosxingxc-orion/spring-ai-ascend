package com.huawei.ascend.runtime.access.api;

import com.huawei.ascend.runtime.access.AgentNotification;

public interface NotificationPort {
    void notify(AgentNotification notification);
}



