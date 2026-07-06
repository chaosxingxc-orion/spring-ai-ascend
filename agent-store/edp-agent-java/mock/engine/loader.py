"""Load and hot-reload workflow JSON definitions."""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

logger = logging.getLogger("mock_versatile.loader")

_BASE_DIR = Path(__file__).resolve().parent.parent


def base_dir() -> Path:
    return _BASE_DIR


def load_server_config(config_path: Path | None = None) -> dict[str, Any]:
    path = config_path or (_BASE_DIR / "config" / "server.json")
    with path.open(encoding="utf-8") as f:
        cfg = json.load(f)
    cfg.setdefault("features", {})
    return cfg


class WorkflowStore:
    def __init__(self, server_config: dict[str, Any] | None = None) -> None:
        self.server_config = server_config or load_server_config()
        self.workflows: dict[str, dict[str, Any]] = {}
        self.load_errors: list[str] = []
        self.reload()

    def workflows_dir(self) -> Path:
        rel = self.server_config.get("workflows_dir", "workflows")
        return _BASE_DIR / rel

    def reload(self) -> None:
        self.workflows.clear()
        self.load_errors.clear()
        wf_dir = self.workflows_dir()
        if not wf_dir.is_dir():
            self.load_errors.append(f"workflows dir not found: {wf_dir}")
            return

        for path in sorted(wf_dir.glob("*.json")):
            if path.name.startswith("_"):
                continue
            try:
                with path.open(encoding="utf-8") as f:
                    wf = json.load(f)
                wf_id = wf.get("id") or path.stem
                wf["id"] = wf_id
                self._validate_workflow(wf, path.name)
                self.workflows[wf_id] = wf
            except Exception as exc:
                msg = f"failed to load {path.name}: {exc}"
                logger.error(msg)
                self.load_errors.append(msg)

        default_id = self.server_config.get("default_workflow", "default")
        if default_id not in self.workflows and self.workflows:
            logger.warning("default workflow %r not found", default_id)

        logger.info("loaded %d workflows from %s", len(self.workflows), wf_dir)

    def _validate_workflow(self, wf: dict[str, Any], filename: str) -> None:
        required = ("id", "match", "output")
        for key in required:
            if key not in wf:
                raise ValueError(f"{filename}: missing required field {key!r}")
        match = wf["match"]
        if match.get("type") not in ("query", "context"):
            raise ValueError(f"{filename}: match.type must be 'query' or 'context'")
        output = wf["output"]
        if "frames" not in output and "error" not in output:
            raise ValueError(f"{filename}: output must contain 'frames' or 'error'")

    def list_workflows(self) -> list[dict[str, Any]]:
        return sorted(
            self.workflows.values(),
            key=lambda w: w.get("priority", 0),
            reverse=True,
        )

    def get(self, workflow_id: str) -> dict[str, Any] | None:
        return self.workflows.get(workflow_id)

    def get_default(self) -> dict[str, Any]:
        default_id = self.server_config.get("default_workflow", "default")
        wf = self.workflows.get(default_id)
        if wf:
            return wf
        if self.workflows:
            return next(iter(self.workflows.values()))
        raise RuntimeError("no workflows loaded")
