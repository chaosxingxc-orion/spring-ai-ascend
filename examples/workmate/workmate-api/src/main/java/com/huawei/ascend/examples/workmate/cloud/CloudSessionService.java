package com.huawei.ascend.examples.workmate.cloud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.cloud.dto.CloudSessionHealthResponse;
import com.huawei.ascend.examples.workmate.cloud.dto.CloudSessionResponse;
import com.huawei.ascend.examples.workmate.cloud.dto.CreateCloudSessionRequest;
import com.huawei.ascend.examples.workmate.cloud.dto.SessionManifest;
import com.huawei.ascend.examples.workmate.config.WorkmateCloudProperties;
import com.huawei.ascend.examples.workmate.office.ExpertService;
import com.huawei.ascend.examples.workmate.runtime.RuntimeLifecycle;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloudSessionService {

    private final CloudSessionRepository repository;
    private final SessionManifestBuilder manifestBuilder;
    private final SandboxLifecycle sandboxLifecycle;
    private final SessionService sessionService;
    private final ExpertService expertService;
    private final WorkmateCloudProperties cloud;
    private final ObjectMapper objectMapper;
    private final RuntimeLifecycle runtimeLifecycle;

    public CloudSessionService(
            CloudSessionRepository repository,
            SessionManifestBuilder manifestBuilder,
            SandboxLifecycle sandboxLifecycle,
            SessionService sessionService,
            ExpertService expertService,
            WorkmateCloudProperties cloud,
            ObjectMapper objectMapper,
            RuntimeLifecycle runtimeLifecycle) {
        this.repository = repository;
        this.manifestBuilder = manifestBuilder;
        this.sandboxLifecycle = sandboxLifecycle;
        this.sessionService = sessionService;
        this.expertService = expertService;
        this.cloud = cloud;
        this.objectMapper = objectMapper;
        this.runtimeLifecycle = runtimeLifecycle;
    }

    @Transactional(readOnly = true)
    public List<CloudSessionResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CloudSessionResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public SessionManifest getManifest(UUID id) {
        return readManifest(require(id));
    }

    @Transactional(readOnly = true)
    public Optional<CloudSessionResponse> findByLinkedSessionId(UUID linkedSessionId) {
        return repository
                .findFirstByLinkedSessionIdAndStatusNotOrderByUpdatedAtDesc(
                        linkedSessionId, CloudSessionStatus.DESTROYED)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CloudSessionHealthResponse health(UUID id) {
        CloudSession session = require(id);
        String runtimeUrl = session.getRuntimeBaseUrl();
        if (runtimeUrl == null || runtimeUrl.isBlank()) {
            return new CloudSessionHealthResponse(
                    session.getId().toString(),
                    session.getStatus().name(),
                    null,
                    false,
                    "runtimeBaseUrl not set");
        }
        boolean healthy = runtimeLifecycle.probeHealthy(java.net.URI.create(runtimeUrl.trim()));
        String message = healthy ? "ok" : "agent card probe failed";
        return new CloudSessionHealthResponse(
                session.getId().toString(),
                session.getStatus().name(),
                runtimeUrl,
                healthy,
                message);
    }

    @Transactional
    public CloudSessionResponse create(CreateCloudSessionRequest request) {
        if (!cloud.enabled()) {
            throw new IllegalStateException("Cloud sessions disabled");
        }
        if (request.expertId() == null || request.expertId().isBlank()) {
            throw new IllegalArgumentException("expertId is required");
        }
        expertService.requireExpertDefinition(request.expertId().trim());

        UUID cloudSessionId = UUID.randomUUID();
        PermissionMode permissionMode = parsePermissionMode(request.permissionMode());

        SessionManifest manifest =
                manifestBuilder.build(cloudSessionId, request.expertId().trim(), request.title(), permissionMode);

        CloudSession entity = new CloudSession();
        entity.setId(cloudSessionId);
        entity.setExpertId(request.expertId().trim());
        entity.setTitle(manifest.metadata().title());
        entity.setStatus(CloudSessionStatus.PROVISIONING);
        entity.setManifestJson(writeManifest(manifest));

        try {
            SandboxHandle handle = sandboxLifecycle.provision(manifest);
            entity.setSandboxId(handle.sandboxId());
            entity.setRuntimeBaseUrl(handle.runtimeBaseUrl());
            entity.setStatus(CloudSessionStatus.RUNNING);

            CreateSessionResponse linked = sessionService.createSession(new CreateSessionRequest(
                    manifest.metadata().title() + " · 云",
                    null,
                    manifest.metadata().expertId(),
                    permissionMode,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null));
            entity.setLinkedSessionId(linked.session().id());
        } catch (Exception ex) {
            entity.setStatus(CloudSessionStatus.FAILED);
            entity.setLastError(truncate(ex.getMessage(), 2000));
            repository.save(entity);
            throw ex;
        }

        return toResponse(repository.save(entity));
    }

    @Transactional
    public CloudSessionResponse wake(UUID id) {
        CloudSession session = require(id);
        if (session.getStatus() == CloudSessionStatus.DESTROYED) {
            throw new IllegalArgumentException("Cloud session destroyed");
        }
        sandboxLifecycle.wake(session);
        session.setStatus(CloudSessionStatus.RUNNING);
        session.setLastError(null);
        return toResponse(repository.save(session));
    }

    @Transactional
    public CloudSessionResponse sleep(UUID id) {
        CloudSession session = require(id);
        if (session.getStatus() == CloudSessionStatus.DESTROYED) {
            throw new IllegalArgumentException("Cloud session destroyed");
        }
        sandboxLifecycle.sleep(session);
        session.setStatus(CloudSessionStatus.SLEEPING);
        return toResponse(repository.save(session));
    }

    @Transactional
    public void destroy(UUID id) {
        CloudSession session = require(id);
        if (session.getStatus() == CloudSessionStatus.DESTROYED) {
            return;
        }
        sandboxLifecycle.destroy(session);
        session.setStatus(CloudSessionStatus.DESTROYED);
        session.setDestroyedAt(Instant.now());
        repository.save(session);
    }

    private CloudSession require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Cloud session not found: " + id));
    }

    private SessionManifest readManifest(CloudSession session) {
        try {
            return objectMapper.readValue(session.getManifestJson(), SessionManifest.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid manifest JSON for " + session.getId(), ex);
        }
    }

    private String writeManifest(SessionManifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize manifest", ex);
        }
    }

    private CloudSessionResponse toResponse(CloudSession session) {
        return new CloudSessionResponse(
                session.getId(),
                session.getExpertId(),
                session.getTitle(),
                session.getStatus().name(),
                session.getRuntimeBaseUrl(),
                session.getSandboxId(),
                session.getLinkedSessionId(),
                session.getLastError(),
                formatInstant(session.getCreatedAt()),
                formatInstant(session.getUpdatedAt()),
                formatInstant(session.getDestroyedAt()));
    }

    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static PermissionMode parsePermissionMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return PermissionMode.CRAFT;
        }
        return PermissionMode.valueOf(raw.trim().toUpperCase());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
