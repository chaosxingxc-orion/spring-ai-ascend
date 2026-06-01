package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.ReplyChannel;

public interface EgressAdapter {
    ReplyChannel channel();

    void deliver(EgressBinding binding, NotificationFrame frame);
}


