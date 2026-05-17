package ascend.springai.service.platform.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the JWT validator chain wired by {@link JwtDecoderConfig}.
 * Exercises every failure row of {@code ADR-0056 §4} against a real Nimbus
 * decoder and a real RSA keypair generated at boot.
 *
 * <p>Layer 2: real {@link NimbusJwtDecoder} + real validators
 * (issuer / audience / timestamp). No Spring context, no HTTP layer — that
 * layer is covered by Phase G's {@code RunHttpContractIT}.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E9.
 */
class JwtValidationIT {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "spring-ai-ascend";

    private static RSAKey signingKey;
    private static AuthProperties auth;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setup() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-1").generate();
        auth = new AuthProperties(ISSUER, null, AUDIENCE, Duration.ofSeconds(60), Duration.ofMinutes(5), false);
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> validators = JwtDecoderConfig.buildValidator(auth, new SimpleMeterRegistry(), "test");
        nimbus.setJwtValidator(validators);
        decoder = nimbus;
    }

    @Test
    void valid_token_decodes_successfully() throws Exception {
        String token = mintToken(claims -> {});
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).contains(AUDIENCE);
    }

    @Test
    void wrong_issuer_is_rejected() throws Exception {
        String token = mintToken(claims -> claims.issuer("https://other-issuer.test"));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void wrong_audience_is_rejected() throws Exception {
        String token = mintToken(claims -> claims.audience(List.of("different-audience")));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void expired_token_is_rejected() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String token = mintToken(claims -> claims
                .issueTime(Date.from(past.minusSeconds(600)))
                .expirationTime(Date.from(past)));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void not_yet_valid_token_is_rejected() throws Exception {
        Instant future = Instant.now().plusSeconds(3600);
        String token = mintToken(claims -> claims.notBeforeTime(Date.from(future)));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void malformed_token_is_rejected() {
        assertThatThrownBy(() -> decoder.decode("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void token_signed_by_wrong_key_is_rejected() throws Exception {
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID("other").generate();
        String token = sign(foreignKey, baseClaims().build());
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private static String mintToken(ClaimsCustomizer customizer) throws JOSEException {
        JWTClaimsSet.Builder builder = baseClaims();
        customizer.apply(builder);
        return sign(signingKey, builder.build());
    }

    private static JWTClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }

    @FunctionalInterface
    private interface ClaimsCustomizer {
        void apply(JWTClaimsSet.Builder builder);
    }
}
