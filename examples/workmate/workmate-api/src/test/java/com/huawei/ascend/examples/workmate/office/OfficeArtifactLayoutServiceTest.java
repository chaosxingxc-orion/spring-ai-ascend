package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficeArtifactLayoutServiceTest {

    @TempDir
    Path workspace;

    @Test
    void bootstrapsInputsOutputsAndRequest() throws Exception {
        ExpertDefinition expert = new ExpertDefinition(
                "prd-writer",
                "n",
                "d",
                "agent",
                "p",
                null,
                "product",
                List.of("office"),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "prd-write",
                Map.of());
        UUID sessionId = UUID.randomUUID();
        OfficeArtifactLayoutService service = new OfficeArtifactLayoutService();

        service.bootstrapLayout(workspace, expert, sessionId);

        String root = OfficeArtifactContract.sessionTaskRoot(sessionId);
        assertThat(Files.isDirectory(workspace.resolve(root + "/inputs"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve(root + "/outputs"))).isTrue();
        assertThat(Files.exists(workspace.resolve(root + "/request.md"))).isTrue();
    }
}
