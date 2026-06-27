package com.huawei.ascend.examples.workmate.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamBlackboardServiceTest {

  @TempDir
  Path workspace;

  private final TeamBlackboardService service = new TeamBlackboardService();

  @Test
  void initializesAndAppendsBlackboard() throws Exception {
    TeamBlackboardService.MemoryUpdate init =
        service.initialize(workspace, "run-1", "content-review-team", "write intro");
    assertThat(init.relativePath()).isEqualTo("team/run-1/blackboard.md");
    assertThat(init.action()).isEqualTo("init");
    assertThat(init.version()).isEqualTo(1L);

    Path file = workspace.resolve(init.relativePath());
    assertThat(Files.isRegularFile(file)).isTrue();
    Path meta = workspace.resolve("team/run-1/blackboard.meta.json");
    assertThat(Files.isRegularFile(meta)).isTrue();
    String afterInit = Files.readString(file);
    assertThat(afterInit).contains("write intro");

    TeamBlackboardService.MemoryUpdate append =
        service.append(workspace, "run-1", "Generator", "draft body");
    assertThat(append.action()).isEqualTo("append");
    assertThat(append.preview()).contains("draft body");
    assertThat(append.version()).isEqualTo(2L);

    String full = service.read(workspace, "run-1");
    assertThat(full).contains("Generator").contains("draft body");

    String truncated = service.readForPrompt(workspace, "run-1", 20);
    assertThat(truncated).endsWith("…(blackboard truncated for prompt)");

    var payload = service.memoryPayload("run-1", append);
    assertThat(payload.get("path")).isEqualTo("team/run-1/blackboard.md");
    assertThat(payload.get("parentRunId")).isEqualTo("run-1");
    assertThat(payload.get("version")).isEqualTo(2L);
  }

  @Test
  void appendLockedSkipsDuplicateContent() {
    service.initialize(workspace, "run-2", "team", "task");
    service.append(workspace, "run-2", "A", "unique finding");
    assertThat(service.containsContent(workspace, "run-2", "unique finding")).isTrue();
    assertThat(service.containsContent(workspace, "run-2", "other")).isFalse();
  }
}
