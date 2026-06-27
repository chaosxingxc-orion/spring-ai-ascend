package com.huawei.ascend.examples.workmate.office;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class WelcomeControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-welcome");
        WorkmateTestProperties.registerOfficeRoot(registry);
    }

    @Test
    void loadsWelcomeFromOfficeYaml() throws Exception {
        mockMvc.perform(get("/api/v1/welcome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hero.title").value("WorkMate"))
                .andExpect(jsonPath("$.hero.headline").value("WorkMate，我帮你"))
                .andExpect(jsonPath("$.bestPractices.enabled").value(false))
                .andExpect(jsonPath("$.marketFeatured.enabled").value(false))
                .andExpect(jsonPath("$.marketFeatured.playbooks").isEmpty())
                .andExpect(jsonPath("$.scenes[?(@.id=='working')].chips[0].label").value("文档处理"));
    }

    @Test
    void listsPlaybooksByPlacement() throws Exception {
        mockMvc.perform(get("/api/v1/playbooks").param("placement", "market-featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='investment-analysis')]").exists());

        mockMvc.perform(get("/api/v1/playbooks").param("placement", "home-best-practice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void playbookRegistryLoadsOfficeFiles() {
        PlaybookRegistry registry = new PlaybookRegistry(
                new com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties(WorkmateTestPaths.officeRoot()),
                new OfficeImportPaths(new com.huawei.ascend.examples.workmate.config.WorkmateDataProperties(paths.data().toString())));
        assertThat(registry.listByPlacement("home-best-practice")).hasSize(3);
        assertThat(registry.listByPlacement("market-featured")).hasSize(4);
    }
}
