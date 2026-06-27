package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficeArtifactContractTest {

    @Test
    void parsesOfficePaths() {
        var meta = OfficeArtifactContract.parseRelativePath("office/prd-write/abc/outputs/draft.md");
        assertThat(meta).isPresent();
        assertThat(meta.get().capability()).isEqualTo("prd-write");
        assertThat(meta.get().taskId()).isEqualTo("abc");
        assertThat(meta.get().zone()).isEqualTo("outputs");
    }

    @Test
    void rejectsWritesToInputs() {
        assertThat(OfficeArtifactContract.validateAgentWritePath("office/prd-write/s1/inputs/foo.pdf"))
                .isPresent();
        assertThat(OfficeArtifactContract.validateAgentWritePath("office/prd-write/s1/outputs/draft.md"))
                .isEmpty();
    }

    @Test
    void sessionTaskRootIsStableAcrossExperts() {
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertThat(OfficeArtifactContract.sessionTaskRoot(sessionId))
                .isEqualTo("office/tasks/00000000-0000-0000-0000-000000000001");
    }

    @Test
    void detectsOfficeCapableExpertByTag() {
        ExpertDefinition expert = new ExpertDefinition(
                "prd-writer",
                "n",
                "d",
                "agent",
                "prompt",
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
        assertThat(OfficeArtifactContract.isOfficeCapable(expert)).isTrue();
        assertThat(OfficeArtifactContract.taskRoot(expert, UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isEqualTo("office/prd-write/00000000-0000-0000-0000-000000000001");
    }
}
