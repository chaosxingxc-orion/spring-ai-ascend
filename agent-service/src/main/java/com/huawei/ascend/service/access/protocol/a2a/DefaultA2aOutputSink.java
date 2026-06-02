package com.huawei.ascend.service.access.protocol.a2a;

import com.huawei.ascend.service.access.model.EgressBinding;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;

public final class DefaultA2aOutputSink implements A2aOutputSink {

    private final A2aOutputRegistry outputRegistry;
    private final HttpClient httpClient;

    public DefaultA2aOutputSink(A2aOutputRegistry outputRegistry) {
        this(outputRegistry, HttpClient.newHttpClient());
    }

    DefaultA2aOutputSink(A2aOutputRegistry outputRegistry, HttpClient httpClient) {
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public void send(EgressBinding binding, A2aOutput output) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(output, "output");
        A2aOutputHandle handle = new A2aOutputHandle(binding.tenantId(), binding.sessionId(), output.taskId());
        outputRegistry.append(handle, output);
        if ("PUSH_NOTIFICATION".equals(binding.deliveryMode())) {
            pushNotification(binding, output);
        }
    }

    private void pushNotification(EgressBinding binding, A2aOutput output) {
        if (binding.targetRef() == null || binding.targetRef().isBlank()) {
            throw new IllegalArgumentException("Missing A2A push notification target URL");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(binding.targetRef()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(output.event())));
            Object token = binding.attributes().get("pushNotificationToken");
            if (token != null && !token.toString().isBlank()) {
                builder.header("X-A2A-Notification-Token", token.toString());
            }
            Object authScheme = binding.attributes().get("pushNotificationAuthScheme");
            Object authCredentials = binding.attributes().get("pushNotificationAuthCredentials");
            if (authScheme != null && authCredentials != null
                    && !authScheme.toString().isBlank()
                    && !authCredentials.toString().isBlank()) {
                builder.header("Authorization", authScheme + " " + authCredentials);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("A2A push notification returned HTTP " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to send A2A push notification", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending A2A push notification", ex);
        } catch (org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize A2A push notification event", ex);
        }
    }
}


