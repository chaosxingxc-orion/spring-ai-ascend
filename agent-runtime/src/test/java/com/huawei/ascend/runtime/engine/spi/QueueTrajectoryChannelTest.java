package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QueueTrajectoryChannelTest {

    private static TrajectoryEvent event(long seq) {
        return new TrajectoryEvent(seq, Kind.PROGRESS, 0L, null, "task", "span", null, "tenant", "ctx", "task", "obj", "name",
                null, null, null, null, null, null, null, "1");
    }

    @Test
    @Timeout(5)
    void drainsPublishedEventsInOrderThenStopsAtClose() throws Exception {
        QueueTrajectoryChannel channel = new QueueTrajectoryChannel();
        List<TrajectoryEvent> drained = new ArrayList<>();
        Thread consumer = new Thread(() -> channel.drain().forEach(drained::add));
        consumer.start();

        channel.publish(event(0));
        channel.publish(event(1));
        channel.publish(event(2));
        channel.close();

        consumer.join(3000);
        assertThat(drained).extracting(TrajectoryEvent::seq).containsExactly(0L, 1L, 2L);
    }

    @Test
    @Timeout(5)
    void publishAfterCloseIsIgnoredAndDrainTerminates() {
        QueueTrajectoryChannel channel = new QueueTrajectoryChannel();
        channel.close();
        channel.publish(event(9));
        assertThat(channel.drain()).isEmpty();
    }

    @Test
    void noopChannelDrainsEmpty() {
        TrajectoryChannel channel = TrajectoryChannel.NOOP;
        channel.publish(event(1));
        channel.close();
        assertThat(channel.drain()).isEmpty();
    }
}
