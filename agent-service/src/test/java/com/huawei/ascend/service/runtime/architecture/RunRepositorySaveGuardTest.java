package com.huawei.ascend.service.runtime.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunRepositorySaveGuardTest {

    @Test
    void productionRunRepositorySaveCallsAreCreateOnlyOutsideRepositoryImplementation() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        if (root.endsWith("agent-service")) {
            root = root.getParent();
        }
        Path mainJava = root.resolve("agent-service/src/main/java");
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(mainJava)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectSaveViolations(mainJava, path, violations));
        }

        assertThat(violations).isEmpty();
    }

    private static void collectSaveViolations(Path mainJava, Path path, List<String> violations) {
        String relative = mainJava.relativize(path).toString().replace('\\', '/');
        if (relative.endsWith("runtime/orchestration/inmemory/InMemoryRunRegistry.java")) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.contains("repository.save(") && !line.contains("runs.save(")) {
                    continue;
                }
                if (isAllowedCreateOnlySave(relative, line)) {
                    continue;
                }
                violations.add(relative + ":" + (i + 1) + " " + line);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isAllowedCreateOnlySave(String relative, String line) {
        if (relative.endsWith("platform/web/runs/RunController.java")) {
            return line.equals("Run saved = repository.save(run);");
        }
        if (relative.endsWith("runtime/orchestration/inmemory/SyncOrchestrator.java")) {
            return line.contains("runs.save(createRun(") || line.startsWith("runs.save(new Run(");
        }
        return false;
    }
}
