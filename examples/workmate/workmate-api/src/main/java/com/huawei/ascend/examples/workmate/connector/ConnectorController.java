package com.huawei.ascend.examples.workmate.connector;

import com.huawei.ascend.examples.workmate.connector.dto.ConnectConnectorRequest;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorAuthProfileResponse;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthCallbackRequest;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodeCompleteRequest;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodePollResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodeStartResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthRedirectStartResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthTokenRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;
    private final OAuthService oauthService;

    public ConnectorController(ConnectorService connectorService, OAuthService oauthService) {
        this.connectorService = connectorService;
        this.oauthService = oauthService;
    }

    @GetMapping
    public List<ConnectorResponse> listConnectors() {
        return connectorService.listConnectors();
    }

    @GetMapping("/{id}")
    public ConnectorResponse getConnector(@PathVariable String id) {
        return connectorService.getConnector(id);
    }

    @GetMapping("/{id}/auth")
    public ConnectorAuthProfileResponse authProfile(@PathVariable String id) {
        return connectorService.authProfile(id);
    }

    @PostMapping("/{id}/connect")
    public ConnectorResponse connect(@PathVariable String id, @RequestBody(required = false) ConnectConnectorRequest request) {
        ConnectConnectorRequest body = request == null ? new ConnectConnectorRequest(null, null) : request;
        return connectorService.connect(id, body);
    }

    @PostMapping("/{id}/disconnect")
    public ConnectorResponse disconnect(@PathVariable String id) {
        return connectorService.disconnect(id);
    }

    @PostMapping("/{id}/reconnect")
    public ConnectorResponse reconnect(@PathVariable String id) {
        return connectorService.reconnect(id);
    }

    @PostMapping("/{id}/revoke")
    public ConnectorResponse revoke(@PathVariable String id) {
        return connectorService.revoke(id);
    }

    @PostMapping("/{id}/oauth/redirect/start")
    public OAuthRedirectStartResponse startRedirect(@PathVariable String id) {
        return oauthService.startRedirect(id);
    }

    @PostMapping("/oauth/callback")
    public ConnectorResponse oauthCallback(@RequestBody OAuthCallbackRequest request) {
        OAuthService.OAuthCompletion completion = oauthService.completeRedirect(request);
        return connectorService.connectWithOAuthHeaders(completion.connectorId(), completion.headers());
    }

    @PostMapping("/{id}/oauth/device-code/start")
    public OAuthDeviceCodeStartResponse startDeviceCode(
            @PathVariable String id,
            @RequestParam(required = false) String method) {
        ConnectorAuthMethod authMethod = "QR".equalsIgnoreCase(method)
                ? ConnectorAuthMethod.QR
                : ConnectorAuthMethod.DEVICE_CODE;
        return oauthService.startDeviceCode(id, authMethod);
    }

    @GetMapping("/oauth/device-code/{sessionId}/poll")
    public OAuthDeviceCodePollResponse pollDeviceCode(@PathVariable String sessionId) {
        return oauthService.pollDeviceCode(sessionId);
    }

    @PostMapping("/oauth/device-code/{sessionId}/complete")
    public ConnectorResponse completeDeviceCode(
            @PathVariable String sessionId,
            @RequestBody OAuthDeviceCodeCompleteRequest request) {
        OAuthService.OAuthCompletion completion = oauthService.completeDeviceCode(sessionId, request);
        return connectorService.connectWithOAuthHeaders(completion.connectorId(), completion.headers());
    }

    @PostMapping("/{id}/oauth/token")
    public ConnectorResponse storeToken(@PathVariable String id, @RequestBody OAuthTokenRequest request) {
        Map<String, String> headers = oauthService.storeToken(id, request);
        return connectorService.connectWithOAuthHeaders(id, headers);
    }
}
