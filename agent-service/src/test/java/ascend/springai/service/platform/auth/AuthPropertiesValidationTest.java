package ascend.springai.service.platform.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthProperties} (ADR-0056). Verifies defaults, the
 * {@link AuthProperties#hasJwksConfig()} helper, and the cross-field
 * dev-local-mode/jwks-uri consistency check.
 *
 * <p>Layer 1 (unit). Enforcer-row coverage: feeds into E9 / E11.
 */
class AuthPropertiesValidationTest {

    @Test
    void defaults_apply_when_durations_omitted() {
        AuthProperties auth = new AuthProperties(null, null, null, null, null, false);
        assertThat(auth.clockSkew()).isEqualTo(Duration.ofSeconds(60));
        assertThat(auth.jwksCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void has_jwks_config_requires_issuer_uri_and_audience_all_present() {
        AuthProperties none = new AuthProperties(null, null, null, null, null, false);
        AuthProperties partial = new AuthProperties("https://issuer", null, "aud", null, null, false);
        AuthProperties complete = new AuthProperties("https://issuer", "https://jwks", "aud", null, null, false);

        assertThat(none.hasJwksConfig()).isFalse();
        assertThat(partial.hasJwksConfig()).isFalse();
        assertThat(complete.hasJwksConfig()).isTrue();
    }

    @Test
    void dev_local_mode_alone_is_consistent() {
        AuthProperties auth = new AuthProperties(null, null, null, null, null, true);
        assertThat(auth.isDevLocalModeConsistent()).isTrue();
    }

    @Test
    void dev_local_mode_with_jwks_uri_is_rejected() {
        AuthProperties auth = new AuthProperties(null, "https://jwks", null, null, null, true);
        assertThat(auth.isDevLocalModeConsistent()).isFalse();
    }

    @Test
    void no_dev_local_mode_means_consistency_trivially_holds() {
        AuthProperties auth = new AuthProperties("https://issuer", "https://jwks", "aud", null, null, false);
        assertThat(auth.isDevLocalModeConsistent()).isTrue();
    }
}
