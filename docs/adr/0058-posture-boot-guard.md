# ADR-0058 — Posture Boot Guard

- Status: Accepted
- Date: 2026-05-14
- Authority: L1 plan `l1-modular-russell` §9; architect guidance §6.3, §9.2, §10.

## Context

Rule D-6 (Posture-Aware Defaults) requires `research`/`prod` to fail closed when
required configuration is absent. L0 ships a soft variant: individual beans log
a warning in `dev` and throw `IllegalStateException` on first use in
`research`/`prod`. That is not "fail closed" — startup completes, the
application reports healthy, and the failure surfaces only when a real request
hits the broken path.

L1 introduces the W1 JWT validator (ADR-0056), the durable idempotency store
(ADR-0057), and a metric tag filter (Phase H). Each needs different required
config in `research`/`prod`. Without a centralised boot-time gate, every
component would re-implement its own posture check; that fragments the
contract and risks divergence.

## Decision

Add a single Spring `ApplicationListener<ApplicationReadyEvent>` —
`PostureBootGuard` — that asserts the required-config matrix at startup and
throws `IllegalStateException` to abort the application context when any check
fails in `research` / `prod`. The guard is silent in `dev`.

### 1. Required-config matrix

| Posture | Required |
|---|---|
| `dev` | none |
| `research` | `AuthProperties.hasJwksConfig()` true, `DataSource` bean present, `JdbcIdempotencyStore` bean present, no `InMemoryIdempotencyStore` bean, `app.auth.dev-local-mode=false`, `MeterRegistry` bean present |
| `prod` | same as `research` |

The matrix lives in code (the `PostureBootGuard` class), not in YAML. Every
check has a unit-testable predicate. Adding a new requirement = adding a new
check method.

### 2. `@RequiredConfig` annotation

A marker annotation applied to `@ConfigurationProperties` fields that the
guard must validate. L1 uses Bean Validation (`@NotBlank`, `@NotNull`) for
within-record validation; `@RequiredConfig` is reserved for **cross-record**
requirements that depend on posture (e.g. "in research, this field must be
set; in dev it's optional"). At L1 it is documentation-only on the relevant
`AuthProperties` fields, since the guard inspects the record directly. The
annotation lands now so subsequent waves can extend the discipline.

### 3. Failure surface

On violation:

1. Log `ERROR` with the failing check name and posture.
2. Emit `springai_ascend_posture_boot_failure_total{reason}` counter (single
   increment per failed check).
3. Throw `IllegalStateException` with a multi-line message listing every
   failed check.

`ApplicationReadyEvent` is published after Spring finishes context refresh; an
exception thrown from the listener propagates up and causes
`SpringApplication.run(...)` to return a non-zero exit code (or throws to the
caller in `@SpringBootTest`).

### 4. Enforcers (per Rule R-C.a)

- **E11** (`JwtDevLocalModeGuardIT`) — `app.auth.dev-local-mode=true` outside
  `dev` posture aborts startup.
- **E21** (`PostureBootGuardIT`) — every check fires when its precondition
  fails; `dev` boots clean even with missing config.
- **E22** (`InMemoryIdempotencyAllowFlagIT`) — the in-memory store cannot
  register in `research`/`prod` (bean condition); the boot guard catches the
  case where someone bypasses the bean condition (e.g. manual registration).

## Consequences

### Positive

- Single, mechanical place to read the posture contract.
- Boot-time failure surfaces misconfiguration before traffic arrives — matches
  "Posture-Aware Defaults" Rule D-6 literally.
- Adding a new requirement is one method on the guard plus one test case.

### Negative

- The guard knows about types from `auth`, `idempotency`, and Micrometer.
  That coupling is acceptable: the guard's job is exactly to know what each
  required component looks like.

## Alternatives Considered

### A. Per-bean `@PostConstruct` checks

Rejected: that's the L0 pattern. It distributes the contract; the matrix is no
longer readable in one place.

### B. Spring's `ApplicationContextInitializer`

Rejected: runs too early (before `DataSource` autoconfig). `ApplicationReadyEvent`
runs after every bean is constructed, which is what we need.

### C. Use Bean Validation cross-class group validators

Rejected: Bean Validation runs during `@ConfigurationProperties` binding, which
predates `DataSource` autoconfig. The matrix needs to see bean presence, not
just property values.

## §16 Review Checklist

- [x] The module owner is clear (`agent-platform.posture`).
- [x] The out-of-scope list is explicit (per-bean `@PostConstruct` is out).
- [x] No future-wave capability is described as shipped.
- [x] Spring bean construction has one owner (`PostureBootGuard` listens, does
      not construct).
- [x] Configuration properties are validated and consumed (`@ConfigurationProperties`
      + boot-time guard).
- [x] Tenant identity flow is explicit (n/a for this ADR).
- [x] Idempotency behavior is tenant-scoped (n/a for this ADR).
- [x] Persistence survives restart when claimed (guard requires `DataSource` in
      research/prod).
- [x] Error status codes are stable (n/a — startup failure, not HTTP).
- [x] Metrics and logs exist for failure paths (`springai_ascend_posture_boot_failure_total`).
- [x] Tests cover unit, integration, and public contract layers (E11, E21, E22).
- [x] `architecture-status.yaml` truth matches implementation (row line 268
      promoted in Phase J).
- [x] The design does not weaken existing Rule R-C.d, Rule R-C.e, or Rule G-2 sub-clause .a constraints.

## References

- L1 plan (historical session plan, local-only) §9, §11 (E11, E21, E22).
- Architect guidance §6.3, §9.2, §10.
- ADR-0056 (AuthProperties consumed here).
- ADR-0057 (IdempotencyStore beans consumed here).
- ADR-0059 (Rule R-C.a).
