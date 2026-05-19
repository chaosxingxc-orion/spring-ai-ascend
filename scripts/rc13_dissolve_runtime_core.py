#!/usr/bin/env python3
"""rc13: Dissolve agent-runtime-core. Redistribute its 16 Java sources + 1 test
to semantic-home modules per ADR-0088.

- Run / RunStatus / RunStateMachine / RunRepository (+ package-info)        -> agent-service
- IdempotencyRecord                                                          -> agent-service
- RunMode + 6 orchestration.spi types (engine.orchestration.spi)             -> agent-execution-engine
- 3 s2c.spi types (bus.spi.s2c)                                              -> agent-bus
- RunRecordTenantLibraryTest                                                 -> agent-service

Cross-platform safe: reads bytes, normalizes CRLF -> LF, writes bytes.
"""

from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# (old_relative, new_relative, new_package, old_package)
MOVES = [
    # ---- agent-service (runs + idempotency) ----
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/Run.java",
     "agent-service/src/main/java/ascend/springai/service/runtime/runs/Run.java",
     "ascend.springai.service.runtime.runs",
     "ascend.springai.service.runtime.runs"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/RunStatus.java",
     "agent-service/src/main/java/ascend/springai/service/runtime/runs/RunStatus.java",
     "ascend.springai.service.runtime.runs",
     "ascend.springai.service.runtime.runs"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/RunStateMachine.java",
     "agent-service/src/main/java/ascend/springai/service/runtime/runs/RunStateMachine.java",
     "ascend.springai.service.runtime.runs",
     "ascend.springai.service.runtime.runs"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/spi/RunRepository.java",
     "agent-service/src/main/java/ascend/springai/service/runtime/runs/spi/RunRepository.java",
     "ascend.springai.service.runtime.runs.spi",
     "ascend.springai.service.runtime.runs.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/spi/package-info.java",
     "agent-service/src/main/java/ascend/springai/service/runtime/runs/spi/package-info.java",
     "ascend.springai.service.runtime.runs.spi",
     "ascend.springai.service.runtime.runs.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/idempotency/IdempotencyRecord.java",
     "agent-service/src/main/java/ascend/springai/service/runtime/idempotency/IdempotencyRecord.java",
     "ascend.springai.service.runtime.idempotency",
     "ascend.springai.service.runtime.idempotency"),
    # ---- agent-execution-engine (RunMode + orchestration.spi) ----
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/RunMode.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/RunMode.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.runs"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi/Checkpointer.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/Checkpointer.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi/Orchestrator.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/Orchestrator.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi/RunContext.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/RunContext.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi/SuspendSignal.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/SuspendSignal.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi/TraceContext.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/TraceContext.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/orchestration/spi/ExecutorDefinition.java",
     "agent-execution-engine/src/main/java/ascend/springai/engine/orchestration/spi/ExecutorDefinition.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    # ---- agent-bus (s2c.spi) ----
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/s2c/spi/S2cCallbackTransport.java",
     "agent-bus/src/main/java/ascend/springai/bus/spi/s2c/S2cCallbackTransport.java",
     "ascend.springai.bus.spi.s2c",
     "ascend.springai.service.runtime.s2c.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/s2c/spi/S2cCallbackEnvelope.java",
     "agent-bus/src/main/java/ascend/springai/bus/spi/s2c/S2cCallbackEnvelope.java",
     "ascend.springai.bus.spi.s2c",
     "ascend.springai.service.runtime.s2c.spi"),
    ("agent-runtime-core/src/main/java/ascend/springai/service/runtime/s2c/spi/S2cCallbackResponse.java",
     "agent-bus/src/main/java/ascend/springai/bus/spi/s2c/S2cCallbackResponse.java",
     "ascend.springai.bus.spi.s2c",
     "ascend.springai.service.runtime.s2c.spi"),
    # ---- agent-service (test) ----
    ("agent-runtime-core/src/test/java/ascend/springai/service/runtime/runs/RunRecordTenantLibraryTest.java",
     "agent-service/src/test/java/ascend/springai/service/runtime/runs/RunRecordTenantLibraryTest.java",
     "ascend.springai.service.runtime.runs",
     "ascend.springai.service.runtime.runs"),
    ("agent-runtime-core/src/test/java/ascend/springai/service/runtime/runs/RunStateMachineLibraryTest.java",
     "agent-service/src/test/java/ascend/springai/service/runtime/runs/RunStateMachineLibraryTest.java",
     "ascend.springai.service.runtime.runs",
     "ascend.springai.service.runtime.runs"),
    # ---- agent-execution-engine (test) ----
    ("agent-runtime-core/src/test/java/ascend/springai/service/runtime/orchestration/spi/SuspendSignalLibraryTest.java",
     "agent-execution-engine/src/test/java/ascend/springai/engine/orchestration/spi/SuspendSignalLibraryTest.java",
     "ascend.springai.engine.orchestration.spi",
     "ascend.springai.service.runtime.orchestration.spi"),
    # ---- agent-bus (test) ----
    ("agent-runtime-core/src/test/java/ascend/springai/service/runtime/s2c/spi/S2cCallbackEnvelopeLibraryTest.java",
     "agent-bus/src/test/java/ascend/springai/bus/spi/s2c/S2cCallbackEnvelopeLibraryTest.java",
     "ascend.springai.bus.spi.s2c",
     "ascend.springai.service.runtime.s2c.spi"),
]

# Global byte-level rewrites applied to every .java file in the affected modules
# AND to the moved files themselves. Order matters: more-specific first to avoid
# clobbering. RunMode is the ONLY type pulled out of runs/ — keep the runs.Run /
# runs.RunStatus / runs.RunStateMachine imports intact.
REMAPS = [
    (b"ascend.springai.service.runtime.orchestration.spi", b"ascend.springai.engine.orchestration.spi"),
    (b"ascend.springai.service.runtime.s2c.spi",          b"ascend.springai.bus.spi.s2c"),
    (b"ascend.springai.service.runtime.runs.RunMode",     b"ascend.springai.engine.orchestration.spi.RunMode"),
]

SWEEP_MODULES = [
    "agent-service",
    "agent-execution-engine",
    "agent-bus",
    "agent-middleware",
    "agent-evolve",
    "agent-client",
    "spring-ai-ascend-graphmemory-starter",
]


def normalize(b: bytes) -> bytes:
    return b.replace(b"\r\n", b"\n")


def apply_remaps(b: bytes) -> bytes:
    for src, dst in REMAPS:
        b = b.replace(src, dst)
    return b


def move_files() -> None:
    moved = 0
    for old_rel, new_rel, new_pkg, old_pkg in MOVES:
        old = REPO / old_rel
        new = REPO / new_rel
        if not old.exists():
            print(f"  SKIP missing: {old_rel}")
            continue
        body = normalize(old.read_bytes())
        if new_pkg != old_pkg:
            needle = f"package {old_pkg};".encode()
            replacement = f"package {new_pkg};".encode()
            if needle not in body:
                raise SystemExit(f"FAIL: package declaration {old_pkg!r} not found in {old_rel}")
            body = body.replace(needle, replacement, 1)
        body = apply_remaps(body)
        new.parent.mkdir(parents=True, exist_ok=True)
        new.write_bytes(body)
        old.unlink()
        moved += 1
        print(f"  MOVED {old_rel} -> {new_rel}")
    print(f"move_files: {moved} files moved")


def sweep_imports() -> None:
    rewrote = 0
    for module in SWEEP_MODULES:
        src_root = REPO / module / "src"
        if not src_root.exists():
            continue
        for path in src_root.rglob("*.java"):
            original = path.read_bytes()
            body = normalize(original)
            body = apply_remaps(body)
            if body != original:
                path.write_bytes(body)
                rewrote += 1
                print(f"  REWROTE {path.relative_to(REPO)}")
    print(f"sweep_imports: {rewrote} files rewritten")


def remove_runtime_core_dirs() -> None:
    """Best-effort cleanup of empty agent-runtime-core source tree (pom + metadata
    + ARCHITECTURE.md handled separately by the caller). Removes leaf dirs that
    become empty after MOVES."""
    target_root = REPO / "agent-runtime-core" / "src"
    if not target_root.exists():
        print("remove_runtime_core_dirs: src/ already gone")
        return
    leftover = list(target_root.rglob("*.java"))
    if leftover:
        print(f"  WARN: {len(leftover)} java files remain in agent-runtime-core/src; not removing dirs")
        for f in leftover:
            print(f"    leftover: {f.relative_to(REPO)}")
        return
    # Remove empty directories bottom-up
    import shutil
    shutil.rmtree(REPO / "agent-runtime-core" / "src")
    print("  removed agent-runtime-core/src")


if __name__ == "__main__":
    print("=== Stage 1: move files ===")
    move_files()
    print("=== Stage 2: sweep imports across affected modules ===")
    sweep_imports()
    print("=== Stage 3: clean up empty agent-runtime-core/src ===")
    remove_runtime_core_dirs()
    print("OK")
