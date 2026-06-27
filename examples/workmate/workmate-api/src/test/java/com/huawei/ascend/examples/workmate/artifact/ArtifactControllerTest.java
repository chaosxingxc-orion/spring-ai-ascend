package com.huawei.ascend.examples.workmate.artifact;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

class ArtifactControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-w6");

    }

    @Test
    void listsAndReadsWorkspaceArtifacts() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Artifacts\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Path workspace = paths.workspace().resolve(sessionId);
        Files.writeString(workspace.resolve("hello.md"), "Hello from WorkMate");

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("hello.md"))
                .andExpect(jsonPath("$[0].name").value("hello.md"))
                .andExpect(jsonPath("$[0].mime").value("text/markdown"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/files")
                        .param("path", "hello.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello from WorkMate"))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void listsWorkspaceDirectoryEntries() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Tree\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Path workspace = paths.workspace().resolve(sessionId);
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/main.md"), "# Main");
        Files.writeString(workspace.resolve("readme.txt"), "readme");

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/workspace/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("dir"))
                .andExpect(jsonPath("$[0].name").value("src"))
                .andExpect(jsonPath("$[1].type").value("file"))
                .andExpect(jsonPath("$[1].name").value("readme.txt"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/workspace/entries")
                        .param("path", "src"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("src/main.md"))
                .andExpect(jsonPath("$[0].mime").value("text/markdown"));
    }

    @Test
    void readMissingFileReturns404() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/files")
                        .param("path", "missing.md"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesHtmlPreviewWithRelativeAssets() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Preview\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Path workspace = paths.workspace().resolve(sessionId);
        Files.createDirectories(workspace.resolve("site"));
        Files.writeString(workspace.resolve("site/index.html"), "<html><body>Hello</body></html>");
        Files.writeString(workspace.resolve("site/style.css"), "body { color: red; }");

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/preview/site/index.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("<html><body>Hello</body></html>"));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/preview/site/style.css"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("body { color: red; }"));

        Files.writeString(workspace.resolve("readme.md"), "# readme");
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/preview/readme.md"))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchWorkspaceFilesByQuery() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        Path workspace = paths.workspace().resolve(sessionId);
        Files.writeString(workspace.resolve("summary.md"), "# Summary");
        Files.writeString(workspace.resolve("notes.txt"), "notes");

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/workspace/search")
                        .param("q", "summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("summary.md"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
