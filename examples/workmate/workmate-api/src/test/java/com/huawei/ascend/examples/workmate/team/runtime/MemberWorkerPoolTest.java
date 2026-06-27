package com.huawei.ascend.examples.workmate.team.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.backend.MemberBackend;
import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunContext;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunResult;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MemberWorkerPoolTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static ExecuteRequest dummyRequest() {
        WorkmateSession session = new WorkmateSession(
                UUID.randomUUID(), "t", "/tmp", SessionStatus.CREATED, "team", PermissionMode.CRAFT);
        return new ExecuteRequest(
                session, "m", "sub", "exp", null, null, new java.util.concurrent.atomic.AtomicBoolean(true),
                false, false, false);
    }

    private MemberBackendRegistry registryReturning(AtomicInteger runCount) {
        MemberBackend fake = new MemberBackend() {
            @Override
            public String kind() {
                return "fake";
            }

            @Override
            public boolean supports(MemberDescriptor member) {
                return true;
            }

            @Override
            public MemberRunResult run(MemberRunContext context) {
                runCount.incrementAndGet();
                return MemberRunResult.ok("done");
            }
        };
        return new MemberBackendRegistry(List.of(fake));
    }

    @Test
    void runsMemberOnRoutedMessageAndAutoReawakens() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger();
        MemberBackendRegistry registry = registryReturning(runCount);
        MemberWorkerPool pool = new MemberWorkerPool("run-1", "lead", registry, executor);

        CopyOnWriteArrayList<String> bodies = new CopyOnWriteArrayList<>();
        LinkedBlockingQueue<MemberRunResult> completed = new LinkedBlockingQueue<>();
        MemberRunContextFactory factory = (member, combinedBody, inbound) -> {
            bodies.add(combinedBody);
            return new MemberRunContext(member, dummyRequest());
        };
        MemberWorkerListener listener = new MemberWorkerListener() {
            @Override
            public void onCompleted(MemberDescriptor member, List<MailboxMessage> inbound, MemberRunResult result) {
                completed.add(result);
            }
        };
        MemberWorker worker = pool.addMember(
                MemberDescriptor.of("designer", "Designer", "designer-expert", null), factory, listener);

        pool.route("lead", "@designer", "task one");
        assertThat(completed.poll(3, TimeUnit.SECONDS)).isNotNull();

        // Idle member auto-reawakens on a new message.
        pool.route("lead", "@designer", "task two");
        assertThat(completed.poll(3, TimeUnit.SECONDS)).isNotNull();

        assertThat(runCount.get()).isEqualTo(2);
        assertThat(bodies).containsExactly("task one", "task two");
        assertThat(worker.member().memberId()).isEqualTo("designer");
    }

    @Test
    void fireRouteRoutesMemberOutputBackToLeaderInbox() throws InterruptedException {
        MemberBackend producing = new MemberBackend() {
            @Override
            public String kind() {
                return "producing";
            }

            @Override
            public boolean supports(MemberDescriptor member) {
                return true;
            }

            @Override
            public MemberRunResult run(MemberRunContext context) {
                return MemberRunResult.ok("designed the hero");
            }
        };
        MemberWorkerPool pool = new MemberWorkerPool("run-5", "__lead__", new MemberBackendRegistry(List.of(producing)), executor);
        LinkedBlockingQueue<MemberRunResult> completed = new LinkedBlockingQueue<>();
        pool.addMember(
                MemberDescriptor.of("designer", "Designer", "designer-expert", null),
                (member, combinedBody, inbound) -> new MemberRunContext(member, dummyRequest()),
                new MemberWorkerListener() {
                    @Override
                    public void onCompleted(MemberDescriptor member, List<MailboxMessage> inbound, MemberRunResult result) {
                        completed.add(result);
                    }
                });

        // Fire-and-continue dispatch (no correlation): the member's output must be routed back to
        // the leader's inbox so the bridge can re-awaken the leader with it.
        pool.route("__lead__", "@designer", "design the hero");
        assertThat(completed.poll(3, TimeUnit.SECONDS)).isNotNull();

        List<MailboxMessage> leaderMail = pool.mailbox().drainUnread("__lead__");
        assertThat(leaderMail).hasSize(1);
        assertThat(leaderMail.get(0).from()).isEqualTo("designer");
        assertThat(leaderMail.get(0).body()).contains("designed the hero");
    }

    @Test
    void dispatchAndAwaitDoesNotRouteReplyToLeaderInbox() {
        MemberBackend echo = new MemberBackend() {
            @Override
            public String kind() {
                return "echo";
            }

            @Override
            public boolean supports(MemberDescriptor member) {
                return true;
            }

            @Override
            public MemberRunResult run(MemberRunContext context) {
                return MemberRunResult.ok("member-output");
            }
        };
        MemberWorkerPool pool = new MemberWorkerPool("run-6", "__lead__", new MemberBackendRegistry(List.of(echo)), executor);
        pool.addMember(
                MemberDescriptor.of("designer", "Designer", "designer-expert", null),
                (member, combinedBody, inbound) -> new MemberRunContext(member, dummyRequest()),
                MemberWorkerListener.NOOP);

        MemberRunResult result =
                pool.dispatchAndAwait("__lead__", "designer", "do the work", java.time.Duration.ofSeconds(3));

        // Correlated synchronous dispatch returns inline and must NOT also enqueue to the leader.
        assertThat(result.assistantText()).isEqualTo("member-output");
        assertThat(pool.mailbox().drainUnread("__lead__")).isEmpty();
    }

    @Test
    void dispatchAndAwaitReturnsBackendResultSynchronously() {
        MemberBackend echo = new MemberBackend() {
            @Override
            public String kind() {
                return "echo";
            }

            @Override
            public boolean supports(MemberDescriptor member) {
                return true;
            }

            @Override
            public MemberRunResult run(MemberRunContext context) {
                return MemberRunResult.ok("member-output");
            }
        };
        MemberBackendRegistry registry = new MemberBackendRegistry(List.of(echo));
        MemberWorkerPool pool = new MemberWorkerPool("run-3", "__lead__", registry, executor);
        pool.addMember(
                MemberDescriptor.of("designer", "Designer", "designer-expert", null),
                (member, combinedBody, inbound) -> new MemberRunContext(member, dummyRequest()),
                MemberWorkerListener.NOOP);

        MemberRunResult result =
                pool.dispatchAndAwait("__lead__", "designer", "do the work", java.time.Duration.ofSeconds(3));

        assertThat(result.failed()).isFalse();
        assertThat(result.assistantText()).isEqualTo("member-output");
    }

    @Test
    void dispatchAndAwaitFailsWhenNoWorkerRegistered() {
        MemberBackendRegistry registry = registryReturning(new AtomicInteger());
        MemberWorkerPool pool = new MemberWorkerPool("run-4", "__lead__", registry, executor);

        MemberRunResult result =
                pool.dispatchAndAwait("__lead__", "ghost", "hi", java.time.Duration.ofSeconds(1));

        assertThat(result.failed()).isTrue();
        assertThat(result.errorMessage()).contains("No worker registered");
    }

    @Test
    void spuriousSignalDoesNotPauseOrRunBackend() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger();
        MemberBackendRegistry registry = registryReturning(runCount);
        MemberWorkerPool pool = new MemberWorkerPool("run-spurious", "lead", registry, executor);

        CopyOnWriteArrayList<MemberRuntimeState> stateChanges = new CopyOnWriteArrayList<>();
        MemberWorker worker = pool.addMember(
                MemberDescriptor.of("designer", "Designer", "designer-expert", null),
                (member, combinedBody, inbound) -> new MemberRunContext(member, dummyRequest()),
                new MemberWorkerListener() {
                    @Override
                    public void onStateChanged(MemberDescriptor member, MemberRuntimeState state) {
                        stateChanges.add(state);
                    }
                });

        worker.signal();
        Thread.sleep(300);

        assertThat(runCount.get()).isZero();
        assertThat(stateChanges).doesNotContain(MemberRuntimeState.PAUSED);
        assertThat(worker.state()).isEqualTo(MemberRuntimeState.READY);
    }

    @Test
    void stoppedWorkerDoesNotRun() throws InterruptedException {
        AtomicInteger runCount = new AtomicInteger();
        MemberBackendRegistry registry = registryReturning(runCount);
        MemberWorkerPool pool = new MemberWorkerPool("run-2", "lead", registry, executor);

        MemberWorker worker = pool.addMember(
                MemberDescriptor.of("writer", "Writer", "writer-expert", null),
                (member, combinedBody, inbound) -> new MemberRunContext(member, dummyRequest()),
                MemberWorkerListener.NOOP);
        worker.stop();

        pool.route("lead", "@writer", "ignored");
        Thread.sleep(300);

        assertThat(runCount.get()).isZero();
        assertThat(worker.state()).isEqualTo(MemberRuntimeState.STOPPED);
    }
}
