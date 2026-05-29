# ADR-0057 — Durable Idempotency Claim/Replay

- Status: Accepted
- Date: 2026-05-14
- Authority: L1 plan `l1-modular-russell` §8; architect guidance §10.

> **Post-2026-05-27 audit clarification.** The 2026-05-27 agent-service L1 architecture audit (AUD-IDEM-1, AUD-IDEM-2, AUD-IDEM-3, AUD-IDEM-9) confirms two things this ADR's W2-deferral claim makes implicit and which deserve explicit statement here:
> (a) the `Status.COMPLETED` and `Status.FAILED` enum members declared in `IdempotencyStore.Status` are **unreached** in L1 production code — only `CLAIMED` is ever written by either impl. The schema CHECK constraint accepts them but no Java write-site transitions a row out of CLAIMED. Family: `F-half-built-state-machine`.
> (b) `request_hash` body-drift detection is **scoped to a single TTL window only**: when `JdbcIdempotencyStore` performs the TTL re-claim (`ON CONFLICT … DO UPDATE SET request_hash = EXCLUDED.request_hash WHERE expires_at <= EXCLUDED.created_at`) it replaces the prior hash wholesale, so a third request with the original pre-TTL body would no longer be flagged as drift. This is the intended semantics (TTL re-claim IS a fresh claim) but the L1 contract should be explicit. The L1 process.md "200 cached response" alt-branch (AUD-IDEM-3) is unreachable at L1 — that branch is W2-design per §2 deferral.
>

## Context

L0 ships `IdempotencyHeaderFilter` which validates the `Idempotency-Key` header as a UUID and increments missing/invalid counters. The W0 `IdempotencyStore` is a `@PostConstruct`-only stub: it logs a warning in `dev` posture and throws `IllegalStateException` in `research`/`prod`. No deduplication actually happens.

Architect guidance §10 requires the W1 idempotency contract:

- Key scope: `(tenant_id, idempotency_key)`.
- First request claims the key; concurrent duplicate returns 409.
- Stored record includes `request_hash` to detect key reuse with different body.
- Failure behaviour is explicit.
- Posture-aware: durable in `research`/`prod`, in-memory only behind an explicit flag in `dev`.

`architecture-status.yaml` rows at lines 276 and 685 list this as W1-deferred. L1 promotes them.

## Decision

### 1. Schema — Flyway `V2__idempotency_dedup.sql`

```sql
CREATE TABLE idempotency_dedup (
  tenant_id          UUID         NOT NULL,
  idempotency_key    UUID         NOT NULL,
  request_hash       VARCHAR(64)  NOT NULL,
  status             VARCHAR(32)  NOT NULL,
  response_status    INTEGER,
  response_body_ref  VARCHAR(512),
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  completed_at       TIMESTAMPTZ,
  expires_at         TIMESTAMPTZ  NOT NULL,
  CONSTRAINT pk_idempotency_dedup PRIMARY KEY (tenant_id, idempotency_key),
  CONSTRAINT ck_idempotency_status CHECK (status IN ('CLAIMED', 'COMPLETED', 'FAILED'))
);
CREATE INDEX idx_idempotency_dedup_expires ON idempotency_dedup (expires_at);
```

`tenant_id` and `idempotency_key` are both `UUID` because `TenantContext` and
`IdempotencyKey` already enforce UUID validation upstream. `request_hash` is the
base64-no-padding SHA-256 of canonical request bytes (44 chars; `VARCHAR(64)` is
ample slack). `status` is constrained at the database layer to the three legal
values; the `CHECK` constraint is itself a Rule-R-C.a enforcer (kind: schema).

### 2. `IdempotencyStore` SPI (replaces W0 stub class outright)

```java
public interface IdempotencyStore {
    Optional<IdempotencyRecord> claimOrFind(UUID tenantId, UUID key, String requestHash);
}
```

Returns:
- `Optional.empty()` — the row was newly inserted with `status=CLAIMED`. Caller proceeds.
- `Optional.of(existing)` — a row already exists for `(tenant_id, key)`. Caller compares
  `requestHash` to `existing.requestHash()` to decide between `idempotency_conflict`
  (same hash → duplicate) and `idempotency_body_drift` (different hash → key reuse
  with a different body).

`IdempotencyRecord` is an immutable Java record carrying every column needed for
the caller's 409 decision plus future replay (W2). Status transitions
(`CLAIMED → COMPLETED|FAILED`) land in W2 with response replay; L1 stops at the
`CLAIMED` state and falls back to TTL expiry for retried failures.

### 3. Two implementations, posture-gated

- **`JdbcIdempotencyStore`** (default) — Spring `JdbcClient`, `INSERT … ON CONFLICT
  (tenant_id, idempotency_key) DO NOTHING` semantics; `SELECT` on collision.
  `expires_at = now() + app.idempotency.ttl`. Registered when a `DataSource` bean
  exists; that is always true in `research`/`prod`. `PostureBootGuard` (Phase F)
  aborts startup if absent there.
- **`InMemoryIdempotencyStore`** — `ConcurrentHashMap`. Registered **only** when
  `app.posture=dev` AND `app.idempotency.allow-in-memory=true`. Both required;
  default off. `PostureBootGuard` rejects the bean's presence outside `dev`.

Both implement the same interface; the filter consumes whichever is wired.

### 4. `IdempotencyHeaderFilter` integration

The L0 filter only validated the header. L1 promotes it to actively dedup:

1. Read tenant from `TenantContextHolder` (populated by `TenantContextFilter` at
   order 20; this filter at order 30 runs after).
2. Parse `Idempotency-Key` as UUID (existing behaviour).
3. Wrap the request in `ContentCachingRequestWrapper`; compute
   `requestHash = base64Url(SHA-256(method | ":" | path | ":" | body))`.
4. Call `store.claimOrFind(tenantId, key, requestHash)`.
5. On collision return 409 with `ErrorEnvelope`:
   - same hash → `idempotency_conflict`.
   - different hash → `idempotency_body_drift`.
6. Otherwise pass the wrapped request down the chain.

Body size is bounded by the existing Tomcat `max-http-form-post-size` (1MB).
For W1 only `/v1/runs` is mutating; bodies are small JSON.

### 5. Configuration — `IdempotencyProperties`

```
app.idempotency.ttl                 (Duration, default PT24H)
app.idempotency.allow-in-memory     (boolean, default false)
```

Bound by `@ConfigurationProperties("app.idempotency")` with constructor binding.

### 6. Posture matrix

| Posture | DataSource present | allow-in-memory | Resulting bean |
|---|---|---|---|
| dev | yes | (any) | `JdbcIdempotencyStore` |
| dev | no | true | `InMemoryIdempotencyStore` |
| dev | no | false | no store; `IdempotencyHeaderFilter` falls back to no-op claim (header-only behaviour) |
| research / prod | yes | (any) | `JdbcIdempotencyStore` |
| research / prod | no | (any) | startup aborts (PostureBootGuard) |
| research / prod | (any) | true | startup aborts (PostureBootGuard) |

### 7. Enforcers (per Rule R-C.a)

- **E12** — `IdempotencyDurabilityIT`: claim persists across `Orchestrator.submit`
  failure; key cannot be re-claimed before `expires_at`.
- **E13** — schema `PRIMARY KEY (tenant_id, idempotency_key)` rejects duplicate
  inserts at the storage layer; exercised by `IdempotencyStorePostgresIT
  .duplicateInsertRejected()`.
- **E14** — `IdempotencyStorePostgresIT.bodyDriftReturns409()`: same key with
  different request hash returns 409 `idempotency_body_drift`.
- **E15** — gate sub-check 28a (`tenant_column_present`): verifies the
  migration declares `tenant_id`.
- **E22** — `InMemoryIdempotencyAllowFlagIT`: in-memory bean only present when
  both posture and flag agree.

## Consequences

### Positive

- Concurrent duplicates collide at the storage layer (`PRIMARY KEY`), not in
  application logic — race-free.
- Body-drift detection catches client bugs where the same `Idempotency-Key` is
  paired with two different bodies.
- One construction path per store flavour (Rule D-8).
- Posture-aware fail-closed behaviour matches Rule D-6.

### Negative

- Body hashing requires `ContentCachingRequestWrapper`, which buffers the
  request body in memory. Bounded by Tomcat's 1MB limit at L1.
- Status transitions to `COMPLETED`/`FAILED` are W2; L1 relies on TTL expiry to
  recover keys after a failed run. Documented as the W1 trade-off; replay
  semantics are W2.

## Alternatives Considered

### A. Hash only headers + URL, skip body

Rejected: body-drift detection requires hashing the body. Without it, a
malicious or buggy client can re-use a key with a different payload.

### B. Use database-side advisory locks instead of `INSERT … ON CONFLICT`

Rejected: advisory locks don't survive across connections cleanly, and we need
the persisted record anyway for the 409 decision and future replay.

### C. Skip `IdempotencyProperties.allowInMemory` and rely on DataSource presence

Rejected: a `dev` posture without Postgres should still be productive. The
explicit flag makes the in-memory fallback opt-in rather than implicit, which
prevents a misconfigured prod environment from silently falling back.

## §16 Review Checklist

- [x] The module owner is clear (`agent-platform.idempotency`).
- [x] The out-of-scope list is explicit (no W2 replay).
- [x] No future-wave capability is described as shipped.
- [x] Spring bean construction has one owner (`IdempotencyStoreAutoConfiguration`).
- [x] Configuration properties are validated and consumed (`IdempotencyProperties`).
- [x] Tenant identity flow is explicit (`(tenant_id, key)` composite).
- [x] Idempotency behavior is tenant-scoped (schema + filter).
- [x] Persistence survives restart when claimed (Postgres table).
- [x] Error status codes are stable (409 + envelope codes).
- [x] Metrics and logs exist for failure paths (`springai_ascend_idempotency_*`).
- [x] Tests cover unit, integration, and public contract layers (E12, E13, E14, E22).
- [x] `architecture-status.yaml` truth matches implementation (rows promoted in Phase J).
- [x] The design does not weaken existing Rule R-C.d, Rule R-C.e, or Rule G-2 sub-clause .a constraints.

## References

- L1 plan (historical session plan, local-only) §8, §11 (E12, E13, E14, E15, E22).
- Architect guidance §10.
- ADR-0027 (W0 stub IdempotencyStore; this ADR supersedes its W1 deferral note).
- ADR-0058 (PostureBootGuard).
- ADR-0059 (Rule R-C.a).
