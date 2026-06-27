package com.huawei.ascend.examples.workmate.question;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class QuestionControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @Autowired
    private UserQuestionService questionService;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-question");

    }

    @Test
    void listsPendingAndAnswersViaRest() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String toolName = WorkmateToolIds.askUserQuestion(sessionId);
        QuestionGate.PendingQuestion pending = questionService.register(
                sessionId,
                "task-rest",
                toolName,
                "Pick one",
                List.of("A", "B"),
                false,
                false);

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/pending-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionId").value(pending.id().toString()))
                .andExpect(jsonPath("$[0].question").value("Pick one"))
                .andExpect(jsonPath("$[0].options[0]").value("A"));

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/" + pending.id() + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":[\"A\"],\"skip\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selections[0]").value("A"))
                .andExpect(jsonPath("$.skipped").value(false));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/pending-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void skipViaRest() throws Exception {
        UUID sessionId = UUID.randomUUID();
        QuestionGate.PendingQuestion pending = questionService.register(
                sessionId,
                "task-rest",
                WorkmateToolIds.askUserQuestion(sessionId),
                "Optional?",
                List.of(),
                true,
                false);

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/" + pending.id() + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skip\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/pending-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
