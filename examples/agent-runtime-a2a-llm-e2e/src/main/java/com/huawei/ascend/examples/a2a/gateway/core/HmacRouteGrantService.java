package com.huawei.ascend.examples.a2a.gateway.core;

import com.huawei.ascend.examples.a2a.gateway.api.AgentDiscoveryApi;
import com.huawei.ascend.examples.a2a.gateway.api.RouteGrantService;
import com.huawei.ascend.examples.a2a.gateway.model.GatewayErrorCode;
import com.huawei.ascend.examples.a2a.gateway.model.GrantValidationResult;
import com.huawei.ascend.examples.a2a.gateway.model.InboundA2aContext;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrant;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrantRequest;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeRoute;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacRouteGrantService implements RouteGrantService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_POLICY_VERSION = 1L;

    private final AgentDiscoveryApi discoveryApi;
    private final Clock clock;
    private final byte[] secret;

    public HmacRouteGrantService(AgentDiscoveryApi discoveryApi, String secret) {
        this(discoveryApi, Clock.systemUTC(), secret);
    }

    public HmacRouteGrantService(AgentDiscoveryApi discoveryApi, Clock clock, String secret) {
        this.discoveryApi = Objects.requireNonNull(discoveryApi, "discoveryApi");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secret = requireText(secret, "secret").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public RouteGrant resolveGrant(RouteGrantRequest request) {
        Objects.requireNonNull(request, "request");
        RuntimeRoute route = discoveryApi.resolveRoute(
                request.targetAgentId(),
                request.tenantId(),
                request.routingContext());
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(request.ttl());
        String grantId = "grant-" + UUID.randomUUID();
        Set<String> methods = Set.of(request.a2aMethod());
        RouteGrant unsigned = new RouteGrant(
                grantId,
                request.tenantId(),
                request.sourceAgentId(),
                route.agentId(),
                route.runtimeInstanceId(),
                route.a2aEndpoint(),
                methods,
                DEFAULT_POLICY_VERSION,
                issuedAt,
                expiresAt,
                "unsigned");
        return new RouteGrant(
                unsigned.grantId(),
                unsigned.tenantId(),
                unsigned.sourceAgentId(),
                unsigned.targetAgentId(),
                unsigned.targetRuntimeId(),
                unsigned.a2aEndpoint(),
                unsigned.allowedMethods(),
                unsigned.policyVersion(),
                unsigned.issuedAt(),
                unsigned.expiresAt(),
                sign(unsigned));
    }

    @Override
    public GrantValidationResult validate(RouteGrant grant, InboundA2aContext context) {
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(context, "context");
        if (grant.expiresAt().isBefore(clock.instant()) || grant.expiresAt().equals(clock.instant())) {
            return GrantValidationResult.rejected(GatewayErrorCode.ROUTE_GRANT_EXPIRED, "route grant expired");
        }
        if (!grant.tenantId().equals(context.tenantId())) {
            return GrantValidationResult.rejected(GatewayErrorCode.TENANT_FORBIDDEN, "tenant mismatch");
        }
        if (!grant.sourceAgentId().equals(context.sourceAgentId())) {
            return GrantValidationResult.rejected(GatewayErrorCode.SOURCE_AGENT_FORBIDDEN, "source agent mismatch");
        }
        if (!grant.targetAgentId().equals(context.targetAgentId())) {
            return GrantValidationResult.rejected(GatewayErrorCode.TARGET_AGENT_MISMATCH, "target agent mismatch");
        }
        if (!grant.allowedMethods().contains(context.a2aMethod())) {
            return GrantValidationResult.rejected(GatewayErrorCode.A2A_METHOD_FORBIDDEN, "A2A method is not allowed");
        }
        if (!MessageDigest.isEqual(sign(grant).getBytes(StandardCharsets.UTF_8),
                grant.signature().getBytes(StandardCharsets.UTF_8))) {
            return GrantValidationResult.rejected(GatewayErrorCode.ROUTE_GRANT_INVALID, "route grant signature invalid");
        }
        return GrantValidationResult.success();
    }

    private String sign(RouteGrant grant) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(canonicalGrant(grant).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign route grant", ex);
        }
    }

    private String canonicalGrant(RouteGrant grant) {
        return String.join("|",
                grant.grantId(),
                grant.tenantId(),
                grant.sourceAgentId(),
                grant.targetAgentId(),
                grant.targetRuntimeId().value(),
                grant.a2aEndpoint().toString(),
                String.join(",", grant.allowedMethods().stream().sorted().toList()),
                Long.toString(grant.policyVersion()),
                grant.issuedAt().toString(),
                grant.expiresAt().toString());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
