package com.huawei.ascend.service.platform.auth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Single construction path for {@link JwtDecoder} (Rule 6).
 *
 * <p>Two beans are conditionally registered:
 * <ul>
 *   <li>{@link #jwtDecoderFromJwks(AuthProperties, ObjectProvider)} — when
 *       {@code app.auth.jwks-uri} is set. Backed by a JWKS endpoint via
 *       {@code NimbusJwtDecoder.withJwkSetUri(...)}. Gated on the JWKS URI (the
 *       value the decoder actually requires) rather than on {@code issuer},
 *       which is also a legitimate setting for the dev-local-mode decoder's
 *       issuer-claim validation; gating on {@code issuer} would wrongly register
 *       this JWKS bean (calling {@code withJwkSetUri(null)}) for the
 *       dev-local-mode + issuer combination.</li>
 *   <li>{@link #jwtDecoderFromDevJwk(AuthProperties, ObjectProvider)} — when
 *       {@code app.auth.dev-local-mode=true}. Backed by a static RSA public key
 *       from classpath {@code auth/dev-jwk.json}.
 *       {@link com.huawei.ascend.service.platform.posture.PostureBootGuard} (ADR-0058)
 *       aborts startup if active outside {@code app.posture=dev}.</li>
 * </ul>
 *
 * <p>Both paths share the same validator chain (signature is enforced by Nimbus;
 * timestamp/issuer/audience by validators set on the decoder). The chain is wrapped
 * with a {@code MeterRegistry}-backed counter that increments
 * {@code springai_ascend_auth_failure_total{reason}} on every failure.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E9, #E11.
 */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwtDecoderConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.auth", name = "jwks-uri")
    public JwtDecoder jwtDecoderFromJwks(AuthProperties auth, ObjectProvider<MeterRegistry> meters) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(auth.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(buildValidator(auth, meters.getIfAvailable(), "jwks"));
        LOG.info("JwtDecoder wired from JWKS issuer={} audience={} cacheTtl={}",
                auth.issuer(), auth.audience(), auth.jwksCacheTtl());
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth", name = "dev-local-mode", havingValue = "true")
    public JwtDecoder jwtDecoderFromDevJwk(AuthProperties auth, ObjectProvider<MeterRegistry> meters) {
        try (InputStream in = new ClassPathResource("auth/dev-jwk.json").getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JWK jwk = JWKSet.parse(json).getKeys().get(0);
            if (!(jwk instanceof RSAKey rsa)) {
                throw new IllegalStateException("auth/dev-jwk.json must contain an RSA key");
            }
            RSAPublicKey publicKey = rsa.toRSAPublicKey();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(buildValidator(auth, meters.getIfAvailable(), "dev_local"));
            LOG.warn("JwtDecoder wired from dev-local-mode fixture (auth/dev-jwk.json). "
                    + "This must never be enabled outside posture=dev; PostureBootGuard enforces.");
            return decoder;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load dev-jwk.json fixture", e);
        }
    }

    static OAuth2TokenValidator<Jwt> buildValidator(AuthProperties auth, MeterRegistry registry, String source) {
        OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator(auth.clockSkew());
        OAuth2TokenValidator<Jwt> issuerCheck = (auth.issuer() == null)
                ? token -> OAuth2TokenValidatorResult.success()
                : JwtValidators.createDefaultWithIssuer(auth.issuer());
        OAuth2TokenValidator<Jwt> audCheck = (auth.audience() == null)
                ? token -> OAuth2TokenValidatorResult.success()
                : new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(auth.audience()));
        OAuth2TokenValidator<Jwt> chain = new DelegatingOAuth2TokenValidator<>(timestamp, issuerCheck, audCheck);
        return new CountingValidator(chain, registry, source);
    }

    /**
     * Decorates an inner {@link OAuth2TokenValidator} and increments
     * {@code springai_ascend_auth_failure_total{reason,source}} on every failure
     * outcome. {@code reason} is derived from the first OAuth2 error code.
     * No-op when {@code registry} is null (test contexts without Micrometer).
     */
    static final class CountingValidator implements OAuth2TokenValidator<Jwt> {
        private final OAuth2TokenValidator<Jwt> inner;
        private final MeterRegistry registry;
        private final String source;

        CountingValidator(OAuth2TokenValidator<Jwt> inner, MeterRegistry registry, String source) {
            this.inner = inner;
            this.registry = registry;
            this.source = source;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            OAuth2TokenValidatorResult result = inner.validate(token);
            if (result.hasErrors() && registry != null) {
                String reason = result.getErrors().stream()
                        .findFirst()
                        .map(e -> e.getErrorCode() == null || e.getErrorCode().isBlank()
                                ? "unknown" : e.getErrorCode())
                        .orElse("unknown");
                Counter.builder("springai_ascend_auth_failure_total")
                        .description("Count of JWT validations that failed at the decoder.")
                        .tag("reason", reason)
                        .tag("source", source)
                        .register(registry)
                        .increment();
            }
            return result;
        }
    }
}
