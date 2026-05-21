package com.huawei.ascend.service.platform.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Configuration for the W1 JWT validation surface (ADR-0056).
 *
 * <p>Bound from {@code app.auth.*}. Fields are nullable so {@code dev} posture
 * can boot with zero config; {@link com.huawei.ascend.service.platform.posture.PostureBootGuard}
 * (Phase F, ADR-0058) enforces that {@code research}/{@code prod} populate
 * {@code issuer}, {@code jwksUri}, and {@code audience}.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E9 (validation behaviour),
 * #E11 (dev-local-mode posture guard), #E21 (research/prod required-config gate).
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String issuer,
        String jwksUri,
        String audience,
        @NotNull Duration clockSkew,
        @NotNull Duration jwksCacheTtl,
        boolean devLocalMode
) {

    public AuthProperties {
        if (clockSkew == null) {
            clockSkew = Duration.ofSeconds(60);
        }
        if (jwksCacheTtl == null) {
            jwksCacheTtl = Duration.ofMinutes(5);
        }
    }

    /**
     * True when the property block carries the minimum surface required to wire a
     * real {@code JwtDecoder} backed by a JWKS endpoint (issuer + jwks-uri + audience
     * all non-blank). When false in {@code research}/{@code prod}, {@code PostureBootGuard}
     * aborts startup.
     */
    public boolean hasJwksConfig() {
        return StringUtils.hasText(issuer)
                && StringUtils.hasText(jwksUri)
                && StringUtils.hasText(audience);
    }

    /**
     * Cross-field constraint: {@code dev-local-mode=true} is only valid alongside the
     * {@code dev} posture marker. Enforced at startup by {@code PostureBootGuard}
     * (enforcer E11); this annotation makes the misconfiguration visible as a Bean
     * Validation failure during {@code @ConfigurationProperties} binding when the
     * combination is statically wrong (e.g. {@code dev-local-mode=true} together with
     * a real {@code jwks-uri}, which is incompatible).
     */
    @AssertTrue(message = "dev-local-mode=true is incompatible with a configured jwks-uri; "
            + "remove one or the other.")
    public boolean isDevLocalModeConsistent() {
        if (!devLocalMode) {
            return true;
        }
        return !StringUtils.hasText(jwksUri);
    }
}
