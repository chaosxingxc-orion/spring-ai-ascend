package com.huawei.ascend.examples.workmate.cloud;

import com.huawei.ascend.examples.workmate.cloud.dto.CloudSessionHealthResponse;
import com.huawei.ascend.examples.workmate.cloud.dto.CloudSessionResponse;
import com.huawei.ascend.examples.workmate.cloud.dto.CreateCloudSessionRequest;
import com.huawei.ascend.examples.workmate.cloud.dto.SessionManifest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cloud/sessions")
public class CloudSessionController {

    private final CloudSessionService cloudSessionService;

    public CloudSessionController(CloudSessionService cloudSessionService) {
        this.cloudSessionService = cloudSessionService;
    }

    @GetMapping
    public List<CloudSessionResponse> list() {
        return cloudSessionService.list();
    }

    @GetMapping("/by-linked/{linkedSessionId}")
    public ResponseEntity<CloudSessionResponse> byLinked(@PathVariable UUID linkedSessionId) {
        return cloudSessionService
                .findByLinkedSessionId(linkedSessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public CloudSessionResponse get(@PathVariable UUID id) {
        return cloudSessionService.get(id);
    }

    @GetMapping("/{id}/manifest")
    public SessionManifest manifest(@PathVariable UUID id) {
        return cloudSessionService.getManifest(id);
    }

    @GetMapping("/{id}/health")
    public CloudSessionHealthResponse health(@PathVariable UUID id) {
        return cloudSessionService.health(id);
    }

    @PostMapping
    public CloudSessionResponse create(@Valid @RequestBody CreateCloudSessionRequest request) {
        return cloudSessionService.create(request);
    }

    @PostMapping("/{id}/wake")
    public CloudSessionResponse wake(@PathVariable UUID id) {
        return cloudSessionService.wake(id);
    }

    @PostMapping("/{id}/sleep")
    public CloudSessionResponse sleep(@PathVariable UUID id) {
        return cloudSessionService.sleep(id);
    }

    @DeleteMapping("/{id}")
    public void destroy(@PathVariable UUID id) {
        cloudSessionService.destroy(id);
    }
}
