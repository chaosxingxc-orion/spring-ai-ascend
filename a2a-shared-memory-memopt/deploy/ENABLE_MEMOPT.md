# Enabling MemOpt under A2A shared memory

The A2A shared-memory kit ([`a2a-shared-memory`](../../a2a-shared-memory)) has two
layers — a run-scoped *blackboard* (in-memory by design) and a cross-run
*experience* layer. By default the experience layer is in-memory and lost on
restart. **MemOpt** gives it persistent, semantic memory. This bundle lets you turn
that on **on demand**, without building or seeing the MemOpt engine source.

```
A2A agents ──▶ a2a-shared-memory (experience SPI)
                      │
                      ▼
        a2a-shared-memory-memopt  (this module: a thin HTTP client)
                      │  HTTP /v1/memory/{save,search}
                      ▼
        MemOpt engine  ◀── closed container image (this bundle runs it) ──▶ NATS
```

## What you get / don't need

- **You don't build or get the MemOpt source.** MemOpt ships as a **closed image**;
  its Python source + Dockerfile live in the MemOpt repo. You only **pull and run**
  the image here.
- This module (`a2a-shared-memory-memopt`) is the **Java HTTP client** to it — plain
  JDK `HttpClient`, no MemOpt types.

## Prerequisites

- Docker (Compose v2). On macOS, Colima with the `vz` VM type works well.
- Access to the MemOpt image in your registry (ask the platform team for the tag),
  **or** a locally-built `memopt:<tag>`.
- LLM gateway credentials the engine calls (`GATEWAY_BASE_URL`, `GATEWAY_API_KEY`,
  `DEFAULT_MODEL`).

## Get the image (private registry)

The MemOpt image lives in a **private** registry — pull access is the IP boundary.
Log in first, then Compose will pull it.

**GHCR (GitHub Container Registry):** use a GitHub Personal Access Token with the
`read:packages` scope:

```bash
echo "$GH_PAT" | docker login ghcr.io -u <github-username> --password-stdin
# default MEMOPT_IMAGE=ghcr.io/kevin-708090/memopt:0.0.1  (TEMP personal namespace;
# moving to ghcr.io/chaosxingxc-orion/memopt once the org grants package-create)
```

**Harbor / other private registry:**

```bash
docker login harbor.example.com
# set MEMOPT_IMAGE=harbor.example.com/memopt/memopt:0.0.1 in .env
```

> The image bakes the Python source, so anyone who can pull it can read it — keep the
> registry **private** and grant pull only to authorized colleagues. Genuine
> source-hiding (compilation) or central hosting is out of scope for this bundle.

## Enable (3 steps)

```bash
cd a2a-shared-memory-memopt/deploy
cp .env.example .env          # set MEMOPT_IMAGE (your registry tag) + GATEWAY_*
./enable-memopt.sh            # brings up MemOpt + NATS, waits for /healthz, prints wiring
```

`./enable-memopt.sh` runs `docker compose up -d` (engine image + NATS + persistent
volumes) and verifies `GET /healthz`. Tear down with `docker compose down` (add `-v`
to also drop the memory volumes).

## Wire the A2A shared-memory experience layer to it

Point the kit's `ExperienceStore` at the running engine:

```java
ExperienceStore experience = new MemOptExperienceStore(
        "http://<memopt-host>:8077",
        new MemOptExperienceStore.Options(
                Duration.ofSeconds(2), /*failOpen*/ true, 5, 30_000L,
                System.getenv("MEMOPT_FACADE_AUTH_TOKEN")));  // null/blank => no auth header
```

Then hand that store to the A2A shared-memory experience kit (see
[`../README.md`](../README.md)). The client **fails open**: if MemOpt is slow or
down, recall returns empty and the agent path is never blocked.

## Security & persistence

- **Auth.** Set `MEMOPT_FACADE_AUTH_TOKEN` (in `.env`) whenever MemOpt is reachable
  beyond localhost; the kit must send the same token (the `authToken` above).
  `/healthz` + `/metrics` stay open for liveness/scrape.
- **TLS.** Terminate TLS at an ingress and use an `https://` base URL.
- **Persistence.** The compose mounts named volumes (`memopt_chroma/data/state/memory`)
  so memory survives restarts.
- **No secrets in files.** Gateway creds and the token come from `.env` (uncommitted),
  not from the compose file.

## Publishing the image (maintainers only)

The image is built from the MemOpt repo (closed); see its `docs/DEPLOY_CONTAINER.md`
for the build (incl. proxy build-args for slow-mirror regions). Then tag + push to the
**private** registry:

**GHCR** — token needs `write:packages`:

```bash
# TEMP personal namespace (org package-create not yet granted). Once it is, retag to
# ghcr.io/chaosxingxc-orion/memopt:0.0.1 and push there instead.
docker tag memopt:0.0.1 ghcr.io/kevin-708090/memopt:0.0.1
echo "$GH_PAT" | docker login ghcr.io -u <github-username> --password-stdin
docker push ghcr.io/kevin-708090/memopt:0.0.1
# Then in GitHub → the package's settings, confirm visibility = Private and grant
# pull access to the team. (GHCR packages are private by default.)
```

**Harbor:**

```bash
docker tag memopt:0.0.1 harbor.example.com/memopt/memopt:0.0.1
docker login harbor.example.com
docker push harbor.example.com/memopt/memopt:0.0.1
```

> The MemOpt **source + Dockerfile stay in the MemOpt repo**; only the built image is
> published, and only to a private registry. This GitHub PR ships the Java client +
> this deploy bundle — never the engine source.
