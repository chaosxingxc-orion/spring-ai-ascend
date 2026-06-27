package com.huawei.ascend.examples.workmate.office;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

/** Shared setup/helpers for Studio controller integration tests. */
public final class StudioControllerTestSupport {

    private StudioControllerTestSupport() {
    }

    public static WorkmateTestPaths registerStudio(DynamicPropertyRegistry registry, String dbName)
            throws IOException {
        WorkmateTestPaths paths = WorkmateTestProperties.registerBaseline(registry, dbName);
        WorkmateTestProperties.registerStudioEnabled(registry, true);
        WorkmateTestProperties.registerOfficeRoot(registry);
        return paths;
    }

    public static void resetDrafts(MockMvc mockMvc, Path dataDir) throws Exception {
        Path draftsRoot = dataDir.resolve("office-drafts");
        if (Files.exists(draftsRoot)) {
            try (var walk = Files.walk(draftsRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup between tests
                    }
                });
            }
        }
        mockMvc.perform(post("/api/v1/studio/reload"));
    }

    public static byte[] buildAgentZip(String yaml, String prompt) throws Exception {
        try (var baos = new java.io.ByteArrayOutputStream();
                var zip = new java.util.zip.ZipOutputStream(baos)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("expert.yaml"));
            zip.write(yaml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("prompt.md"));
            zip.write(prompt.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return baos.toByteArray();
        }
    }
}
