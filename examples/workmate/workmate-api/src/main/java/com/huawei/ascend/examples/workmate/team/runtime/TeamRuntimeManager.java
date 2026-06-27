package com.huawei.ascend.examples.workmate.team.runtime;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring-managed registry of concurrent team runtimes, keyed by team run id.
 *
 * <p>Owns a shared virtual-thread executor so each member worker gets its own lightweight thread.
 * Creating a runtime per team run keeps mailboxes and workers isolated across concurrent sessions.</p>
 */
@Component
public class TeamRuntimeManager {

    private static final Logger LOG = LoggerFactory.getLogger(TeamRuntimeManager.class);

    private final MemberBackendRegistry backendRegistry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, MemberWorkerPool> pools = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRun = new ConcurrentHashMap<>();
    private final Map<String, TeamRunRegistration> sessionRegistrations = new ConcurrentHashMap<>();

    public TeamRuntimeManager(MemberBackendRegistry backendRegistry) {
        this.backendRegistry = backendRegistry;
    }

    /** Create (or return the existing) worker pool for a team run, indexed by session for @member routing. */
    public MemberWorkerPool runtimeFor(String teamRunId, String leaderId, String sessionId) {
        return runtimeFor(teamRunId, leaderId, sessionId, List.of());
    }

    public MemberWorkerPool runtimeFor(
            String teamRunId, String leaderId, String sessionId, List<TeamMemberDefinition> members) {
        MemberWorkerPool pool = pools.computeIfAbsent(
                teamRunId, id -> new MemberWorkerPool(id, leaderId, backendRegistry, executor));
        if (sessionId != null && !sessionId.isBlank()) {
            sessionToRun.put(sessionId, teamRunId);
            if (members != null && !members.isEmpty()) {
                sessionRegistrations.put(
                        sessionId,
                        new TeamRunRegistration(UUID.fromString(sessionId), teamRunId, List.copyOf(members)));
            }
        }
        return pool;
    }

    public MemberWorkerPool find(String teamRunId) {
        return pools.get(teamRunId);
    }

    /** Find the active worker pool for a session (used by the @member bypass API). */
    public MemberWorkerPool findBySession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        String teamRunId = sessionToRun.get(sessionId);
        return teamRunId == null ? null : pools.get(teamRunId);
    }

    public TeamRunRegistration registrationForSession(String sessionId) {
        return sessionId == null ? null : sessionRegistrations.get(sessionId);
    }

    /** Tear down and forget a team run's runtime. */
    public void release(String teamRunId) {
        MemberWorkerPool pool = pools.remove(teamRunId);
        if (pool != null) {
            pool.shutdown();
        }
        sessionToRun.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(teamRunId)) {
                sessionRegistrations.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down TeamRuntimeManager ({} active pools)", pools.size());
        pools.values().forEach(MemberWorkerPool::shutdown);
        pools.clear();
        sessionRegistrations.clear();
        executor.shutdown();
    }
}
