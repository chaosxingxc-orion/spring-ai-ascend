package ascend.springai.service.platform.web.runs;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Test fixture for L1 Phase L Run HTTP authenticated contract tests.
 *
 * <p>Generates one stable RSA keypair per JVM, exposes {@link #decoder()} to
 * wire a Spring-compatible {@link JwtDecoder} that recognises tokens minted by
 * {@link #mint(String, String)}. Validator chain (issuer + audience + timestamp)
 * mirrors production {@code JwtDecoderConfig.buildValidator} semantics so the
 * test decoder rejects the same shapes as production.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E37.
 */
final class JwtTestFixture {

    static final String ISSUER = "https://issuer.test";
    static final String AUDIENCE = "spring-ai-ascend";

    private static final RSAKey SIGNING_KEY = generateKey();

    private JwtTestFixture() {
    }

    static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
            OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator(Duration.ofSeconds(60));
            OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(ISSUER);
            OAuth2TokenValidator<Jwt> aud = new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD, a -> a != null && a.contains(AUDIENCE));
            nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, issuer, aud));
            return nimbus;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build test JwtDecoder", e);
        }
    }

    /**
     * Mints a Bearer-token string with the given {@code subject} and a
     * {@code tenant_id} claim. The token is valid for 5 minutes; iat is now-5s.
     */
    static String mint(String subject, String tenantId) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .issueTime(Date.from(now))
                    .notBeforeTime(Date.from(now.minusSeconds(5)))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("tenant_id", tenantId)
                    .build();
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),
                    claims);
            signed.sign(new RSASSASigner(SIGNING_KEY));
            return signed.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to mint test JWT", e);
        }
    }

    static String mint(UUID subject, UUID tenantId) {
        return mint(subject.toString(), tenantId.toString());
    }

    /** Convenience: mint a token whose tenant_id differs from the requested header tenant. */
    static String mintForTenant(UUID tenantId) {
        return mint(UUID.randomUUID(), tenantId);
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-fixture-1").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate test RSA keypair", e);
        }
    }
}
