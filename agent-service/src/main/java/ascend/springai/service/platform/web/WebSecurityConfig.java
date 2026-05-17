package ascend.springai.service.platform.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security filter chain.
 *
 * <p>L1 (ADR-0056): when a {@link JwtDecoder} bean is present (either JWKS-backed
 * or dev-local-mode), every request beyond the permit list requires a valid
 * Bearer token. When no decoder is registered (e.g. {@code dev} posture with no
 * auth config), the chain falls back to W0's {@code denyAll} default for
 * non-permitted paths.
 *
 * <p>Permitted (no auth):
 * <ul>
 *   <li>{@code GET /v1/health}</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}, {@code /actuator/prometheus}</li>
 *   <li>{@code /v3/api-docs} and {@code /v3/api-docs/**} (OpenAPI snapshot IT)</li>
 * </ul>
 *
 * <p>CSRF disabled (stateless API). Sessions disabled
 * ({@code SessionCreationPolicy.STATELESS}).
 *
 * <p>{@code /swagger-ui/**} stays denied at L1; posture-aware exposure is W2.
 */
@Configuration
public class WebSecurityConfig {

    private static final String[] PERMIT_ALL_PATHS = {
            "/v1/health",
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<JwtDecoder> jwtDecoder) throws Exception {
        JwtDecoder decoder = jwtDecoder.getIfAvailable();

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PERMIT_ALL_PATHS).permitAll();
                    if (decoder != null) {
                        auth.anyRequest().authenticated();
                    } else {
                        // PostureBootGuard (Phase F) aborts startup in research/prod when
                        // no decoder is registered. In dev with no auth config, denyAll
                        // matches the W0 behaviour for non-permitted paths.
                        auth.anyRequest().denyAll();
                    }
                });

        if (decoder != null) {
            http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)));
        }

        return http.build();
    }
}
