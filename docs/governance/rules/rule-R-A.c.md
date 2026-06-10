---
rule_id: R-A.c
title: "Quickstart CI Smoke Run"
level: L1
view: scenarios
principle_ref: P-A
authority_refs: [ADR-0064]
enforcer_refs: [E107]
status: active
scope_phase: design
kernel_cap: 12
kernel: |
  **A CI job named `quickstart-smoke` in `.github/workflows/ci.yml` MUST boot `agent-service` via `mvn -pl agent-service spring-boot:run` on a clean container and assert `GET /v1/health` returns 200 within 60 seconds. Job failure is a ship-blocking finding under Rule D-5 (HTTP / API contract category). The quickstart instructions live at `docs/quickstart.md` (Rule R-A / P-A authority). Activated 2026-05-18 (Wave 4 Track E).**
---

# Rule R-A.c — Quickstart CI Smoke Run

## Motivation

Rule R-A (Business/Platform Decoupling) requires a runnable quickstart so a
new developer reaches first-agent execution without platform-team
intervention. Rule R-A.c codifies the CI assertion that the quickstart still
works: every PR boots agent-service on a clean container and polls `/v1/health`
until 200 with a 60-second deadline.

## Active surface (W1)

`.github/workflows/ci.yml` defines a `quickstart-smoke` job that:

1. Runs on `ubuntu-latest` (per Rule G-7 — Linux-first dev environment).
2. Sets up Java 21 via `actions/setup-java@v4`.
3. Runs `./mvnw -pl agent-service -am package -DskipTests` to produce the jar.
4. Boots `java -jar agent-service/target/agent-service-0.1.0-SNAPSHOT-boot.jar &` (the boot-classifier jar from ADR-0078).
5. Polls `curl -fsS http://localhost:8080/v1/health` every 1s for up to 60s.
6. Fails the job if no 200 by deadline.

## Enforcer

Gate rule `quickstart_smoke_job_present` (E107) asserts `.github/workflows/ci.yml`
contains a job named `quickstart-smoke` AND a step that polls `/v1/health`.

## Trigger

Activated 2026-05-18 by Wave 4 Track E (historical session plan, local-only).
