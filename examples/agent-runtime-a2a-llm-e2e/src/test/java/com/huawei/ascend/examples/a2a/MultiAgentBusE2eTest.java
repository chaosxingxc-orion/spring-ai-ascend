package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.huawei.ascend.bus.knowledge.CompositeKnowledgeSource;
import com.huawei.ascend.bus.knowledge.InMemoryKnowledgeSource;
import com.huawei.ascend.bus.knowledge.KnowledgeFragment;
import com.huawei.ascend.bus.knowledge.KnowledgeQuery;
import com.huawei.ascend.bus.knowledge.KnowledgeRegistry;
import com.huawei.ascend.bus.memory.MemoryEntry;
import com.huawei.ascend.bus.memory.SessionMemoryStore;
import com.huawei.ascend.bus.messaging.AgentMessage;
import com.huawei.ascend.bus.messaging.AgentMessageBus;
import com.huawei.ascend.bus.messaging.Subscription;
import com.huawei.ascend.client.A2aResponse;
import com.huawei.ascend.client.AscendA2aClient;
import com.huawei.ascend.client.SendSpec;
import com.huawei.ascend.runtime.app.LocalA2aRuntimeHost;
import com.huawei.ascend.runtime.app.RunningRuntime;
import com.huawei.ascend.runtime.app.RuntimeApp;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.Messages;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Multi-agent flow over the agent-bus capability surfaces on ONE booted
 * {@link RuntimeApp} (no LLM, no external infrastructure): an A2A message to
 * the "planner" agent makes it (a) record the conversation turn in the
 * platform {@link SessionMemoryStore}, (b) answer from a fragment seeded into
 * an {@link InMemoryKnowledgeSource}, and (c) delegate the final wording to a
 * co-hosted "worker" agent through an async request/reply over the in-process
 * {@link AgentMessageBus}, correlated by {@code correlationId}.
 *
 * <p>The A2A ingress is single-handler by design ({@code RuntimeApp.create}
 * takes one primary handler and the executor dispatches to exactly one), so
 * the worker is modelled as what it is on this plane: a co-hosted bus
 * subscriber, not a second A2A-exposed handler.
 *
 * <p>All waits are deadline-bounded futures — no sleeps: the planner awaits
 * the worker's reply inside its execution, so the A2A response returning is
 * the synchronization point for every assertion.
 *
 * <p>{@code @Isolated}: Spring Boot's logging re-initialization resets the
 * JVM-global logback LoggerContext, whose listener list is not thread-safe —
 * booting concurrently with other context-starting tests intermittently
 * crashes in LoggerContext.addListener.
 */
@Isolated
class MultiAgentBusE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REPLY_DEADLINE = Duration.ofSeconds(10);

    /**
     * No JWT ingress in this boot, so the executor attributes the run to the
     * configured default tenant — this example module's application.yaml pins
     * agent-runtime.access.a2a.default-tenant-id to sample-tenant.
     */
    private static final String TENANT = "sample-tenant";
    private static final String SESSION = "session-multi-agent";
    private static final String REQUEST_TOPIC = "planner.work.request";
    private static final String REPLY_TOPIC = "planner.work.reply";
    private static final String FACT =
            "The gold wealth product charges an annual management fee of 0.5 percent.";
    private static final String QUESTION =
            "What is the annual management fee of the gold wealth product?";

    @Test
    void plannerAnswersFromKnowledgeThroughWorkerOverTheBus() throws Exception {
        InMemoryKnowledgeSource faq = new InMemoryKnowledgeSource("faq");
        faq.seed(TENANT, FACT);
        PlannerHandler planner = new PlannerHandler(faq, new WorkerAgent());

        try (RunningRuntime runtime = RuntimeApp.create(planner).run(LocalA2aRuntimeHost.port(0));
                AscendA2aClient client = newClient(runtime.port())) {
            A2aResponse response = client.streamText(
                    SendSpec.of("planner", SESSION, "sample-user", QUESTION));

            // The answer travelled knowledge → planner → worker → reply → A2A wire.
            assertThat(response.text()).contains(FACT);

            // The async reply arrived on the reply topic, correlated to the planner's request.
            AgentMessage reply = planner.reply.get(REPLY_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(reply.fromAgentId()).isEqualTo("worker");
            assertThat(reply.correlationId()).isEqualTo(planner.requestCorrelationId.get());

            // The platform session memory holds both turns, newest first.
            SessionMemoryStore memory = planner.memorySeen.get();
            assertThat(memory).as("planner must have received the memory capability").isNotNull();
            String answer = String.valueOf(reply.payload().get("answer"));
            assertThat(memory.window(TENANT, SESSION, 10))
                    .extracting(MemoryEntry::role, MemoryEntry::text)
                    .containsExactly(tuple("assistant", answer), tuple("user", QUESTION));
        }
    }

    private static AscendA2aClient newClient(int port) {
        return AscendA2aClient.builder()
                .baseUrl("http://localhost:" + port)
                .timeout(TIMEOUT)
                .build();
    }

    /**
     * The A2A-exposed "planner" agent: records the turn in session memory,
     * retrieves the relevant knowledge fragment, then asks the worker over the
     * bus to phrase the answer and awaits the correlated reply.
     */
    private static final class PlannerHandler implements AgentRuntimeHandler {

        final AtomicReference<SessionMemoryStore> memorySeen = new AtomicReference<>();
        final AtomicReference<String> requestCorrelationId = new AtomicReference<>();
        final CompletableFuture<AgentMessage> reply = new CompletableFuture<>();

        private final InMemoryKnowledgeSource faq;
        private final WorkerAgent worker;

        PlannerHandler(InMemoryKnowledgeSource faq, WorkerAgent worker) {
            this.faq = faq;
            this.worker = worker;
        }

        @Override
        public String agentId() {
            return "planner";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String tenantId = context.getScope().tenantId();
            String sessionId = context.getScope().sessionId();
            SessionMemoryStore memory = context.getSessionMemory().orElseThrow();
            KnowledgeRegistry knowledge = context.getKnowledge().orElseThrow();
            AgentMessageBus bus = context.getMessageBus().orElseThrow();
            memorySeen.set(memory);

            String question = Messages.text(context.getMessages().get(0));
            memory.append(tenantId, sessionId, new MemoryEntry("user", question, Instant.now(), Map.of()));

            // Sources register per tenant; contribute the seeded FAQ once, then fan out.
            if (!knowledge.sources(tenantId).containsKey(faq.sourceId())) {
                knowledge.register(tenantId, faq.sourceId(), faq);
            }
            List<KnowledgeFragment> fragments = new CompositeKnowledgeSource(knowledge)
                    .retrieve(new KnowledgeQuery(tenantId, question, 1, Map.of()));
            if (fragments.isEmpty()) {
                throw new IllegalStateException("no knowledge fragment matched: " + question);
            }
            String fact = fragments.get(0).content();

            worker.attach(bus, tenantId);
            try (Subscription replySubscription = bus.subscribe(tenantId, REPLY_TOPIC, reply::complete)) {
                String correlationId = UUID.randomUUID().toString();
                requestCorrelationId.set(correlationId);
                bus.publish(new AgentMessage(UUID.randomUUID().toString(), tenantId, REQUEST_TOPIC,
                        agentId(), correlationId, null, Map.of("fact", fact), Instant.now()));

                AgentMessage workerReply = reply.get(REPLY_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
                String answer = String.valueOf(workerReply.payload().get("answer"));
                memory.append(tenantId, sessionId,
                        new MemoryEntry("assistant", answer, Instant.now(), Map.of()));
                return Stream.of(answer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted awaiting worker reply", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("worker reply not received", e);
            }
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(o -> AgentExecutionResult.completed(String.valueOf(o)));
        }
    }

    /**
     * The co-hosted "worker" agent: a bus subscriber that phrases an answer
     * around the fact it is handed and replies on the reply topic, echoing the
     * request's correlationId.
     */
    private static final class WorkerAgent {

        private final AtomicBoolean attached = new AtomicBoolean();

        void attach(AgentMessageBus bus, String tenantId) {
            if (!attached.compareAndSet(false, true)) {
                return;
            }
            // Lives for the whole runtime: the bus closes it with the context.
            bus.subscribe(tenantId, REQUEST_TOPIC, request -> bus.publish(new AgentMessage(
                    UUID.randomUUID().toString(), tenantId, REPLY_TOPIC, "worker",
                    request.correlationId(), null,
                    Map.of("answer", "According to the product knowledge base: "
                            + request.payload().get("fact")),
                    Instant.now())));
        }
    }
}
