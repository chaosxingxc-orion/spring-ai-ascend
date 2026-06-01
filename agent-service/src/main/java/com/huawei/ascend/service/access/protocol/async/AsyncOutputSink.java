package com.huawei.ascend.service.access.protocol.async;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;

public interface AsyncOutputSink {
    void send(EgressBinding binding, NotificationFrame frame);
}
