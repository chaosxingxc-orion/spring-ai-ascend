#!/usr/bin/env python3
"""Keep ADR-0121, ModelGateway.java, and the contract catalog in sync."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


FQN = "com.huawei.ascend.middleware.model.spi.ModelGateway"
PACKAGE = "package com.huawei.ascend.middleware.model.spi;"
SIGNATURE = "ModelResponse invoke(ModelInvocation invocation);"
REACTIVE_SIGNATURE = "Mono<ModelResponse>"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="Repository root")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    adr = root / "docs" / "adr" / "0121-model-gateway-spi.yaml"
    java = root / "agent-middleware" / "src" / "main" / "java" / "com" / "huawei" / "ascend" / "middleware" / "model" / "spi" / "ModelGateway.java"
    catalog = root / "docs" / "contracts" / "contract-catalog.md"

    failures: list[str] = []
    adr_text = read(adr)
    java_text = read(java)
    catalog_text = read(catalog)

    if FQN not in adr_text:
        failures.append(f"{adr.relative_to(root)} must name {FQN}")
    if REACTIVE_SIGNATURE in adr_text:
        failures.append(f"{adr.relative_to(root)} must not retain reactive {REACTIVE_SIGNATURE} signature text")
    if SIGNATURE not in adr_text:
        failures.append(f"{adr.relative_to(root)} must name synchronous signature `{SIGNATURE}`")
    if PACKAGE not in java_text:
        failures.append(f"{java.relative_to(root)} package must be middleware.model.spi")
    if SIGNATURE not in java_text:
        failures.append(f"{java.relative_to(root)} must expose `{SIGNATURE}`")
    if FQN.rsplit(".", 1)[0] not in catalog_text:
        failures.append(f"{catalog.relative_to(root)} must list package {FQN.rsplit('.', 1)[0]}")

    if failures:
        print("; ".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
