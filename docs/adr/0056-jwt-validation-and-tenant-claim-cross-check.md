# ADR-0056 — JWT Validation and Tenant Claim Cross-Check

- Status: Accepted
- Date: 2026-05-14
- Authority: L1 plan `l1-modular-russell` §7; architect guidance §9.

## Context

L0 ships `WebSecurityConfig` with `permitAll` on `/v1/health`, `/actuator/**`, `/v3/api-docs(/**)`, and `denyAll` on everything else. There is no JWT validation, no tenant claim cross-check, and the `agent-platform/.../tenant/TenantContextFilter` accepts any `X-Tenant-Id` header without authenticating the caller.

L1 must:

1. Validate every mutating request with an RS256-signed JWT (issuer, audience, exp, nbf, clock-skew).
2. Cross-check the JWT `tenant_id` claim against the `X-Tenant-Id` header; mismatch → 403 `tenant_mismatch`.
3. Preserve `dev` posture's existing zero-config experience while making `research`/`prod` fail-closed.

Architect guidance §9.2 dictates the validation surface (RS256, JWKS, iss, aud, exp, nbf, clock-skew, JWKS cache TTL). Architect guidance §9.1 dictates the header-vs-claim cross-check semantics.

## Decision

### 1. `AuthProperties` — bound config

```
app.auth.issuer            (required in research/prod)
app.auth.jwks-uri          (required in research/prod)
app.auth.audience          (required in research/prod)
app.auth.clock-skew        (default PT60S)
app.auth.jwks-cache-ttl    (default PT5M)
app.auth.dev-local-mode    (default false; only honoured when app.posture=dev)
```

`@ConfigurationProperties("app.auth")`, constructor-bound, Bean Validation annotations (`@NotBlank`, `@DurationMin`, etc.). `PostureBootGuard` (Phase F, ADR-0058) enforces the research/prod requirements at startup.

### 2. `JwtDecoderConfig` — one construction path

Single `@Bean JwtDecoder jwtDecoder(AuthProperties auth)`, conditional on `app.auth.issuer` being set. Wraps `NimbusJwtDecoder.withJwkSetUri(...).cache(Duration.ofMinutes(...))` with:

- Mandatory `OAuth2TokenValidator` chain: signature (RS256), `iss`, `aud`, `exp`, `nbf` (with `clock-skew`).
- Rejects non-RS256 algorithms.
- Emits `springai_ascend_auth_failure_total{reason}` on validation failure.

`dev-local-mode=true` (only valid when `app.posture=dev`): registers a `JwtDecoder` backed by a static RSA public key from classpath `auth/dev-jwk.json`. Used for local development and `JwtValidationIT`. `PostureBootGuard` aborts startup if `dev-local-mode=true` and posture is not `dev`.

### 3. `WebSecurityConfig` — chain composition

Replaces the L0 permissive config:

- `permitAll`: `/v1/health`, `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs(/**)`.
- `authenticated`: everything else, when a `JwtDecoder` bean is present.
- `denyAll`: everything else, when no `JwtDecoder` bean exists (preserves W0 dev posture absent JWT config).
- Configures `oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwtDecoder)))`.
- CSRF disabled (stateless API; matches W0).

### 4. Failure surface

| Case | HTTP | `ErrorEnvelope.code` | Metric |
|---|---:|---|---|
| Missing `Authorization` header | 401 | `unauthenticated` | `springai_ascend_auth_failure_total{reason="missing"}` |
| Malformed JWT | 401 | `unauthenticated` | `…{reason="malformed"}` |
| Bad signature | 401 | `unauthenticated` | `…{reason="signature"}` |
| Wrong issuer | 401 | `unauthenticated` | `…{reason="issuer"}` |
| Wrong audience | 401 | `unauthenticated` | `…{reason="audience"}` |
| Expired (`exp`) | 401 | `unauthenticated` | `…{reason="expired"}` |
| Future `nbf` | 401 | `unauthenticated` | `…{reason="not_yet_valid"}` |
| `tenant_id` ≠ `X-Tenant-Id` | 403 | `tenant_mismatch` | `springai_ascend_tenant_mismatch_total` |

Tenant cross-check itself is Phase D / ADR-0056 §3 of this ADR's sibling.

### 5. Enforcers (per Rule R-C.a)

- **E9** (`JwtValidationIT`) — exercises every failure row in the table above.
- **E10** (`JwtTenantClaimCrossCheckTest`) — header/claim mismatch.
- **E11** (`JwtDevLocalModeGuardIT`) — `dev-local-mode` rejected outside `dev`.
- **E21** (`PostureBootGuardIT`, Phase F) — research/prod fail-closed on missing config.

## Consequences

### Positive

- One construction path for `JwtDecoder` (Rule D-8 satisfied).
- `dev-local-mode` makes local development tractable without leaking into prod.
- Auth failures emit stable codes and metrics; reviewers can audit the failure surface from `enforcers.yaml#E9`.

### Negative

- Tests touching mutating endpoints now need a JWT fixture or `@WithMockUser`. Existing L0 tests are unaffected because they only target `/v1/health` (permitted).
- Transitive dep on `spring-boot-starter-oauth2-resource-server` (already declared at L0; no pom change).

## Alternatives Considered

### A. Custom filter that decodes JWT manually

Rejected. Spring Security's oauth2-resource-server is the maintained path; rolling our own decoder adds maintenance burden and risks crypto bugs.

### B. Use opaque tokens + introspection endpoint

Rejected. Adds an extra HTTP round-trip per request, requires running an introspection endpoint in tests, and architect guidance §9.2 specifies RS256 + JWKS.

### C. Defer JWT validation to W2

Rejected. Architect guidance §15.3 lists JWT validation as W1 scope. Without it, `tenant_id` header is trust-only and `JwtTenantClaimCrossCheck` (Phase D) has no claim to cross-check.

## §16 Review Checklist

- [x] The module owner is clear (`agent-platform.auth`).
- [x] The out-of-scope list is explicit (no opaque tokens, no custom decoder).
- [x] No future-wave capability is described as shipped.
- [x] Spring bean construction has one owner (`JwtDecoderConfig`).
- [x] Configuration properties are validated and consumed (`AuthProperties` with Bean Validation).
- [x] Tenant identity flow is explicit (Phase D filter consumes JWT claim).
- [x] Idempotency behavior is tenant-scoped (n/a — Phase E).
- [x] Persistence survives restart when claimed (n/a — JWKS cache is process-scoped + TTL'd).
- [x] Error status codes are stable (table above).
- [x] Metrics and logs exist for failure paths (`springai_ascend_auth_failure_total{reason}`).
- [x] Tests cover unit, integration, and public contract layers (E9–E11, E21).
- [x] `architecture-status.yaml` truth matches implementation (row at line 892 promoted in Phase J).
- [x] The design does not weaken existing Rule R-C.d, Rule R-C.e, or Rule G-2 sub-clause .a constraints.

## References

- L1 plan (historical session plan, local-only) §7, §11 (E9, E10, E11, E21).
- Architect guidance §9.
- ADR-0055 (module direction).
- ADR-0058 (PostureBootGuard — required-config enforcement).
- ADR-0059 (Rule R-C.a).
