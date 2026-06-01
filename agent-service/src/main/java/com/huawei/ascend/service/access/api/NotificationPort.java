package com.huawei.ascend.service.access.api;

import com.huawei.ascend.service.access.model.NotificationFrame;

public interface NotificationPort {
    void notify(NotificationFrame frame);
}



