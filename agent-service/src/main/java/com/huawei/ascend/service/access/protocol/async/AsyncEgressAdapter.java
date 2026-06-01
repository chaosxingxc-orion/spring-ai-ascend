package com.huawei.ascend.service.access.protocol.async;

import com.huawei.ascend.service.access.egress.EgressAdapter;
import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.access.model.ReplyChannel;
import java.util.Objects;

public final class AsyncEgressAdapter implements EgressAdapter {

    private final AsyncOutputSink outputSink;

    public AsyncEgressAdapter(AsyncOutputSink outputSink) {
        this.outputSink = Objects.requireNonNull(outputSink, "outputSink");
    }

    @Override
    public ReplyChannel channel() {
        return ReplyChannel.ASYNC;
    }

    @Override
    public void deliver(EgressBinding binding, NotificationFrame frame) {
        outputSink.send(binding, frame);
    }
}
