package com.huawei.ascend.examples.workmate.connector;

import com.huawei.ascend.examples.workmate.config.WorkmateOAuthProperties;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthCallbackRequest;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodeCompleteRequest;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodePollResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthDeviceCodeStartResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthRedirectStartResponse;
import com.huawei.ascend.examples.workmate.connector.dto.OAuthTokenRequest;
import com.huawei.ascend.examples.workmate.mcp.McpConnectorResolver;
import com.huawei.ascend.examples.workmate.office.ConnectorDefinition;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {

    private static final int SESSION_TTL_SECONDS = 600;

    private final OAuthSessionStore sessionStore;
    private final ConnectorCredentialStore credentialStore;
    private final McpConnectorResolver connectorResolver;
    private final WorkmateOAuthProperties oauthProperties;
    private final SecureRandom random = new SecureRandom();

    public OAuthService(
            OAuthSessionStore sessionStore,
            ConnectorCredentialStore credentialStore,
            McpConnectorResolver connectorResolver,
            WorkmateOAuthProperties oauthProperties) {
        this.sessionStore = sessionStore;
        this.credentialStore = credentialStore;
        this.connectorResolver = connectorResolver;
        this.oauthProperties = oauthProperties;
    }

    public OAuthRedirectStartResponse startRedirect(String connectorId) {
        requireAuthConnector(connectorId);
        if (!oauthProperties.mockEnabled()) {
            throw new OAuthMockDisabledException();
        }
        String state = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);
        sessionStore.createRedirectSession(connectorId, state, expiresAt);
        String authorizeUrl = "/oauth/mock-authorize?state=" + state + "&connector=" + connectorId;
        return new OAuthRedirectStartResponse(authorizeUrl, state, SESSION_TTL_SECONDS);
    }

    public OAuthCompletion completeRedirect(OAuthCallbackRequest request) {
        OAuthSessionStore.OAuthSession session = sessionStore
                .findByRedirectState(request.state())
                .orElseThrow(() -> new IllegalArgumentException("Invalid OAuth state"));
        if (session.status() != OAuthSessionStore.SessionStatus.PENDING) {
            throw new IllegalStateException("OAuth session is not pending");
        }
        Map<String, String> headers = toAuthHeaders(request.code());
        sessionStore.complete(session.id(), headers);
        return new OAuthCompletion(session.connectorId(), headers);
    }

    public record OAuthCompletion(String connectorId, Map<String, String> headers) {}

    public OAuthDeviceCodeStartResponse startDeviceCode(String connectorId, ConnectorAuthMethod method) {
        requireAuthConnector(connectorId);
        ConnectorAuthMethod resolved = method == ConnectorAuthMethod.QR ? ConnectorAuthMethod.QR : ConnectorAuthMethod.DEVICE_CODE;
        String userCode = formatUserCode();
        String deviceCode = UUID.randomUUID().toString();
        String verificationUri = "workmate://oauth/device?code=" + userCode;
        Instant expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);
        OAuthSessionStore.OAuthSession session =
                sessionStore.createDeviceSession(connectorId, resolved, userCode, deviceCode, verificationUri, expiresAt);
        return new OAuthDeviceCodeStartResponse(
                session.id(),
                userCode,
                deviceCode,
                verificationUri,
                resolved.name(),
                SESSION_TTL_SECONDS);
    }

    public OAuthDeviceCodePollResponse pollDeviceCode(String sessionId) {
        OAuthSessionStore.OAuthSession session = sessionStore
                .find(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("OAuth session not found"));
        return new OAuthDeviceCodePollResponse(
                session.status().name().toLowerCase(),
                session.connectorId(),
                session.status() == OAuthSessionStore.SessionStatus.APPROVED ? session.headers() : null);
    }

    public OAuthCompletion completeDeviceCode(String sessionId, OAuthDeviceCodeCompleteRequest request) {
        OAuthSessionStore.OAuthSession session = sessionStore
                .find(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("OAuth session not found"));
        if (session.status() != OAuthSessionStore.SessionStatus.PENDING) {
            throw new IllegalStateException("OAuth session is not pending");
        }
        Map<String, String> headers = mergeAuthRequest(request);
        sessionStore.complete(sessionId, headers);
        credentialStore.save(session.connectorId(), headers);
        return new OAuthCompletion(session.connectorId(), headers);
    }

    public Map<String, String> storeToken(String connectorId, OAuthTokenRequest request) {
        requireAuthConnector(connectorId);
        Map<String, String> headers = mergeAuthRequest(request);
        credentialStore.save(connectorId, headers);
        return headers;
    }

    public void revoke(String connectorId) {
        credentialStore.clear(connectorId);
    }

    public Optional<String> credentialMask(String connectorId) {
        return credentialStore.find(connectorId).flatMap(headers -> {
            String apiKey = headers.get("x-api-key");
            if (apiKey != null && !apiKey.isBlank()) {
                return Optional.ofNullable(CredentialMasker.mask(apiKey));
            }
            String bearer = headers.get("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                return Optional.ofNullable(CredentialMasker.mask(bearer.substring(7)));
            }
            return Optional.empty();
        });
    }

    public boolean hasCredential(String connectorId) {
        return credentialStore.find(connectorId).isPresent();
    }

    private void requireAuthConnector(String connectorId) {
        ConnectorDefinition definition = connectorResolver.catalogConnectors().stream()
                .filter(connector -> connector.id().equals(connectorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + connectorId));
        ConnectorAuthMethod method = ConnectorAuthMethod.fromDefinition(definition);
        if (method == ConnectorAuthMethod.NONE || method == ConnectorAuthMethod.CLI_LOGIN) {
            throw new IllegalArgumentException("Connector does not require auth: " + connectorId);
        }
    }

    private Map<String, String> mergeAuthRequest(OAuthDeviceCodeCompleteRequest request) {
        Map<String, String> headers = new HashMap<>();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            headers.put("x-api-key", request.apiKey().trim());
        }
        if (request.token() != null && !request.token().isBlank()) {
            headers.put("Authorization", "Bearer " + request.token().trim());
        }
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("apiKey or token required");
        }
        return headers;
    }

    private Map<String, String> mergeAuthRequest(OAuthTokenRequest request) {
        OAuthDeviceCodeCompleteRequest bridge =
                new OAuthDeviceCodeCompleteRequest(request.apiKey(), request.token());
        return mergeAuthRequest(bridge);
    }

    private Map<String, String> toAuthHeaders(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code required");
        }
        String trimmed = code.trim();
        if (trimmed.startsWith("Bearer ")) {
            return Map.of("Authorization", trimmed);
        }
        return Map.of("x-api-key", trimmed);
    }

    private String formatUserCode() {
        int value = 1000 + random.nextInt(9000);
        int suffix = 1000 + random.nextInt(9000);
        return value + "-" + suffix;
    }
}
