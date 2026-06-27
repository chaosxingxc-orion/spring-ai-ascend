package com.huawei.ascend.examples.workmate.connector;
import com.huawei.ascend.examples.workmate.support.WorkmateIntegrationTestBase;
import com.huawei.ascend.examples.workmate.support.WorkmateTestPaths;
import com.huawei.ascend.examples.workmate.support.WorkmateTestProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Verifies {@code workmate.oauth.mock-enabled=false} removes mock page and blocks redirect start. */
class OAuthMockDisabledControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @Autowired
    private ApplicationContext applicationContext;

        @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "workmate-oauth-off");
        WorkmateTestProperties.registerOAuthMockEnabled(registry, false);
        WorkmateTestProperties.registerOfficeRoot(registry);
        WorkmateTestProperties.registerMcpEnabled(registry, true);
        registry.add("WORKMATE_MCP_QIEMAN_ENABLED", () -> "false");
    }

    @Test
    void mockAuthorizeControllerNotRegistered() {
        assertThatThrownBy(() -> applicationContext.getBean(OAuthMockController.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void redirectStartBlocked() throws Exception {
        mockMvc.perform(post("/api/v1/connectors/qieman/oauth/redirect/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
