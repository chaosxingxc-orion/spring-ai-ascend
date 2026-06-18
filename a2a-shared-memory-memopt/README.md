# a2a-shared-memory-memopt

The **HTTP client** that backs the [`a2a-shared-memory`](../a2a-shared-memory)
experience layer with the **MemOpt** cognitive memory engine, so cross-run A2A
experience gains *persistent, semantic* memory instead of living only in process.

**MemOpt is delivered as a closed container image** — customers run the image, not
the Python source. This module is the thin Java client that speaks MemOpt's
versioned `/v1` HTTP contract; it contains no MemOpt code.

## What it is

`a2a-shared-memory` defines two memory layers:

| Layer | Lifetime | Backend |
|---|---|---|
| **Blackboard** (run-scoped) | one collaboration run | in-memory (ephemeral by design) |
| **Experience** (cross-run) | spans runs | **MemOpt image** via this client |

This module replaces *only* the **experience** backend with MemOpt. It is a single
class — `MemOptExperienceStore` — implementing the kit's
[`ExperienceStore`](../a2a-shared-memory/src/main/java/com/huawei/ascend/a2a/memory/experience/ExperienceStore.java)
SPI over the MemOpt image's HTTP facade:

```
record(...)  ──▶  POST /v1/memory/save     (lesson + provenance metadata)
recall(...)  ──▶  POST /v1/memory/search   (signature query → distilled lessons)
reinforce(.) ──▶  POST /v1/memory/save     (re-confirm a recurring lesson)
```

## Enable it (deploy bundle)

To turn MemOpt on under A2A shared memory without building or seeing its source, use
the bundle in [`deploy/`](deploy/) — it **pulls** the closed image and wires it up:

```bash
cd a2a-shared-memory-memopt/deploy
cp .env.example .env      # set MEMOPT_IMAGE (your registry tag) + GATEWAY_*
./enable-memopt.sh        # MemOpt engine + NATS up; prints the Java wiring snippet
```

Full guide: [`deploy/ENABLE_MEMOPT.md`](deploy/ENABLE_MEMOPT.md).

## Delivery model: image, not source

MemOpt's cognitive engine is the IP, so it ships as a **closed Docker image**; the
customer never receives the Python source. The image is built from the MemOpt repo
(`docs/DEPLOY_CONTAINER.md` there: `Dockerfile` + `docker-compose.yml`) and pushed to
a registry; this repo only **pulls and runs** it (see `deploy/` above).

This Java module is the **client** to that image:

- pure JDK `HttpClient` + Jackson, no MemOpt types;
- speaks only the versioned `/v1` contract (engine internals can evolve freely);
- talks to the image over the network — point `baseUrl` at the container
  (`http://memopt-host:8077`), use `https://` when TLS is terminated upstream, and
  set an optional **bearer token** when the image enables `MEMOPT_FACADE_AUTH_TOKEN`.

```java
ExperienceStore experience = new MemOptExperienceStore("http://memopt-host:8077");

// networked image with auth + tuned resilience:
ExperienceStore secured = new MemOptExperienceStore(
        "https://memopt.internal:8077",
        new MemOptExperienceStore.Options(
                Duration.ofSeconds(2), /*failOpen*/ true, 5, 30_000L,
                System.getenv("MEMOPT_FACADE_AUTH_TOKEN")));  // null/blank => no header
```

## Resilience (memory is a side service)

Memory must never drag down an agent's main path, so the client is defensive:

| Concern | Behavior |
|---|---|
| Slow / down image | **fails open** — `recall` returns empty, `record` is skipped; no exception on the agent path |
| Repeated failures | a fail-open **circuit** trips after N consecutive errors and short-circuits for `circuitOpenMs`, then probes again |
| Timeouts | per-request connect + read timeout (default 2s) |
| Strict callers | set `Options.failOpen=false` to surface errors instead |

## Partitioning & provenance

Each `(tenant, capability-set + task-type)` keeps its own lessons. The client
derives a stable MemOpt partition (sent as `user_id`):

```
a2a-exp::<tenantId>::<sorted-capabilities>|<taskType>
```

Capabilities are sorted, so set order never changes the key. Per-lesson provenance
(`sourceAgentId`, `reinforcement`, `kind=a2a-experience`) rides in the record
`metadata`, which MemOpt forwards as the ingest event's `scope`.

> **Honest note on recall provenance.** MemOpt is a *cognitive* engine: it distills
> ingested turns into facts. On recall, hit `metadata` is retriever-shaped
> (`paths`/`tags`/`source`), so per-lesson `sourceAgentId`/`reinforcement` are
> **best-effort** — they may not echo back. The lesson **text** is the durable
> payload; the client degrades gracefully (null source, 0 reinforcement) when
> provenance is absent.

## Build & test

Orphan module (not in the root reactor). The SPI dependency must be installed first:

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw -o -f a2a-shared-memory/pom.xml -DskipTests install   # the ExperienceStore SPI
./mvnw -o -f a2a-shared-memory-memopt/pom.xml test           # this module (offline)
```

Tests stub MemOpt's facade with a JDK `HttpServer`, so they run fully offline — no
image, no network — and verify the save/search mapping, lesson parsing, fail-open,
strict mode, the bearer-token header, and stable partitioning.
