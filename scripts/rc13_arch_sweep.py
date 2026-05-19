#!/usr/bin/env python3
"""rc13 sweep: rewrite agent-service / agent-execution-engine L1 ARCHITECTURE.md
narratives to reflect ADR-0088 dissolution."""

from pathlib import Path

REPLACEMENTS_AGENT_SERVICE = [
    # Java path refs.
    (b"agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs",
     b"agent-service/src/main/java/ascend/springai/service/runtime/runs"),
    (b"agent-runtime-core/src/main/java/ascend/springai/service/runtime/idempotency",
     b"agent-service/src/main/java/ascend/springai/service/runtime/idempotency"),
    (b"agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi",
     b"agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi"),
    (b"agent-runtime-core/src/main/java/ascend/springai/service/runtime/s2c/spi",
     b"agent-bus/src/main/java/ascend/springai/bus/spi/s2c"),
    # Package path refs (already swept by main script, but defensive).
    (b"ascend.springai.service.runtime.orchestration.spi",
     b"ascend.springai.engine.orchestration.spi"),
    (b"ascend.springai.service.runtime.s2c.spi",
     b"ascend.springai.bus.spi.s2c"),
    # Prose phrases that pinned ADR-0079 wording.
    (b"`agent-runtime-core` + `agent-execution-engine` post-ADR-0079",
     b"`agent-execution-engine` (rc13 - orchestration SPI relocated from dissolved agent-runtime-core per ADR-0088)"),
    (b"owned by `agent-runtime-core`",
     b"owned by `agent-service` (relocated from dissolved agent-runtime-core per ADR-0088)"),
    (b"SPI in `agent-runtime-core` post-ADR-0079",
     b"SPI in `agent-bus.spi.s2c` (rc13 - relocated from dissolved agent-runtime-core per ADR-0088)"),
    (b"post-ADR-0079: extracted to `agent-runtime-core`",
     b"post-rc13: relocated to `agent-service` per ADR-0088 dissolution"),
    (b"agent-runtime-core/src/main/java/ascend/springai/service/runtime/idempotency",
     b"agent-service/src/main/java/ascend/springai/service/runtime/idempotency"),
    # Bare token replacement (last resort to catch stragglers).
    (b"the `agent-runtime-core` ", b"the `agent-execution-engine` (rc13 dissolution per ADR-0088) "),
    (b"module `agent-runtime-core`", b"module `agent-service` (rc13 dissolution per ADR-0088)"),
    (b"agent-runtime-core/ARCHITECTURE.md", b"agent-execution-engine/ARCHITECTURE.md (rc13: agent-runtime-core dissolved per ADR-0088)"),
    (b"`agent-runtime-core` ->", b"`agent-execution-engine` (rc13 dissolution per ADR-0088) ->"),
]

REPLACEMENTS_AGENT_ENGINE = [
    (b"shared `agent-runtime-core` module that hosts",
     b"transient `agent-runtime-core` module that hosted (later dissolved per ADR-0088 - kernel types relocated to this module under engine.orchestration.spi)"),
    (b"both `agent-service` and `agent-execution-engine` depend on `agent-runtime-core`",
     b"after ADR-0088 dissolution, agent-execution-engine self-contains its orchestration SPI; agent-service depends on agent-execution-engine for that SPI"),
    (b"`agent-runtime-core`", b"`agent-execution-engine` (rc13 dissolution per ADR-0088)"),
]


def sweep(path: Path, rules):
    body = path.read_bytes().replace(b"\r\n", b"\n")
    for src, dst in rules:
        body = body.replace(src, dst)
    path.write_bytes(body)


if __name__ == "__main__":
    repo = Path(__file__).resolve().parent.parent
    sweep(repo / "agent-service" / "ARCHITECTURE.md", REPLACEMENTS_AGENT_SERVICE)
    sweep(repo / "agent-execution-engine" / "ARCHITECTURE.md", REPLACEMENTS_AGENT_ENGINE)
    print("OK")
