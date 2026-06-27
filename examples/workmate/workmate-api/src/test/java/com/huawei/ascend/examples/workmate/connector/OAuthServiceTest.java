package com.huawei.ascend.examples.workmate.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOAuthProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthCallbackRequest;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodeCompleteRequest;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodeStartResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthRedirectStartResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthTokenRequest;
import com.huawei.ascend.examples.workmate.mcp.McpConnectorResolver;
import com.huawei.ascend.examples.workmate.office.ConnectorRegistry;
import com.huawei.ascend.examples.workmate.office.McpJsonConfigMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuthServiceTest {

    private OAuthService oauthService;
    private ConnectorCredentialStore credentialStore;
    private McpConnectorResolver resolver;
    private Path officeDir;

    @BeforeEach
    void setUp() throws Exception {
        Path dataDir = Files.createTempDirectory("oauth-service-test-");
        officeDir = Files.createTempDirectory("oauth-office-");
        credentialStore = new ConnectorCredentialStore(
                new WorkmateDataProperties(dataDir.toString()),
                new ObjectMapper());
        WorkmateMcpProperties mcpProperties = mcpProperties();
        ConnectorRegistry registry =
                new ConnectorRegistry(new WorkmateOfficeProperties(officeDir.toString()), new McpJsonConfigMapper(new ObjectMapper()));
        resolver = new McpConnectorResolver(mcpProperties, registry);
        oauthService = new OAuthService(
                new OAuthSessionStore(),
                credentialStore,
                resolver,
                oauthProperties(true));
    }

    private static WorkmateOAuthProperties oauthProperties(boolean mockEnabled) {
        WorkmateOAuthProperties properties = new WorkmateOAuthProperties();
        properties.setMockEnabled(mockEnabled);
        return properties;
    }

    private static WorkmateMcpProperties mcpProperties() {
        return new WorkmateMcpProperties(
                true,
                60,
                50,
                3,
                500,
                List.of(
                        new WorkmateMcpProperties.McpServerConfig(
                                "qieman",
                                true,
                                "streamable-http",
                                null,
                                List.of(),
                                "https://stargate.yingmi.com/mcp/v2",
                                null,
                                Map.of("x-api-key", ""),
                                List.of(),
                                Map.of(),
                                0L),
                        new WorkmateMcpProperties.McpServerConfig(
                                "docs-fs",
                                true,
                                "stdio",
                                "npx",
                                List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                                null,
                                null,
                                Map.of(),
                                List.of(),
                                Map.of(),
                                0L)));
    }

    @Test
    void redirectFlowBlockedWhenMockDisabled() {
        OAuthService disabledMock = new OAuthService(
                new OAuthSessionStore(),
                credentialStore,
                resolver,
                oauthProperties(false));
        assertThatThrownBy(() -> disabledMock.startRedirect("qieman"))
                .isInstanceOf(OAuthMockDisabledException.class);
    }

    @Test
    void deviceCodeFlowStoresMaskedCredential() {
        OAuthDeviceCodeStartResponse start = oauthService.startDeviceCode("qieman", ConnectorAuthMethod.DEVICE_CODE);
        assertThat(start.userCode()).contains("-");

        oauthService.completeDeviceCode(start.sessionId(), new OAuthDeviceCodeCompleteRequest("secret-key-1234", null));

        assertThat(oauthService.hasCredential("qieman")).isTrue();
        assertThat(oauthService.credentialMask("qieman")).contains("****1234");
    }

    @Test
    void redirectFlowCompletesWithCode() {
        OAuthRedirectStartResponse start = oauthService.startRedirect("qieman");
        OAuthService.OAuthCompletion completion = oauthService.completeRedirect(
                new OAuthCallbackRequest(start.state(), "redirect-key-9999"));
        assertThat(completion.connectorId()).isEqualTo("qieman");
        assertThat(completion.headers()).containsEntry("x-api-key", "redirect-key-9999");
    }

    @Test
    void tokenDialogStoresBearerToken() {
        oauthService.storeToken("qieman", new OAuthTokenRequest(null, "opaque-token-42"));
        assertThat(oauthService.credentialMask("qieman")).contains("****n-42");
    }

    @Test
    void revokeClearsCredential() {
        oauthService.storeToken("qieman", new OAuthTokenRequest("abc12345", null));
        oauthService.revoke("qieman");
        assertThat(oauthService.hasCredential("qieman")).isFalse();
    }

    @Test
    void rejectsNonAuthConnector() {
        assertThatThrownBy(() -> oauthService.startRedirect("docs-fs"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
