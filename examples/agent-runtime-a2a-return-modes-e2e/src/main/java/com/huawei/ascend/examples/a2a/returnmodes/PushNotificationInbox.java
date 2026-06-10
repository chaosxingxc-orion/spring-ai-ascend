package com.huawei.ascend.examples.a2a.returnmodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationInbox {
    private final Object monitor = new Object();
    private final List<String> payloads = new ArrayList<>();
    private CountDownLatch latch = new CountDownLatch(1);

    public void reset() {
        synchronized (monitor) {
            payloads.clear();
            latch = new CountDownLatch(1);
        }
    }

    public void record(String payload) {
        synchronized (monitor) {
            payloads.add(payload);
            latch.countDown();
            monitor.notifyAll();
        }
    }

    public List<String> payloads() {
        synchronized (monitor) {
            return List.copyOf(payloads);
        }
    }

    public boolean awaitFirst(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch current;
        synchronized (monitor) {
            current = latch;
        }
        return current.await(timeout, unit);
    }

    public boolean awaitPayload(Predicate<String> predicate, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        synchronized (monitor) {
            while (payloads.stream().noneMatch(predicate)) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(monitor, remainingNanos);
            }
            return true;
        }
    }
}
