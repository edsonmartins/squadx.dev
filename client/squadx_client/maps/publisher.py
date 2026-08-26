"""Generate maps and publish their IR/HTML to the SquadX control plane."""

from __future__ import annotations

import json
import uuid
from pathlib import Path
from typing import Any

import aiohttp
import structlog

from squadx_client.config import settings

logger = structlog.get_logger()


class MapsArtifactPublisher:
    def __init__(self, api_url: str, token: str, maps_url: str | None = None):
        self.api_url = api_url.rstrip("/")
        self.token = token
        self.maps_url = (maps_url or settings.maps_url).rstrip("/")

    async def generate_and_publish(
        self, *, execution_id: int | str, workspace_path: str, git_revision: str | None,
        pullwise_review_id: int | str | None = None,
        canonical_context: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        workspace = Path(workspace_path).resolve()
        request = {
            # Both services see the same workspace mount. Maps resolves this against its
            # configured root and rejects paths outside that boundary.
            "repositoryPath": str(workspace),
            "title": f"Architecture at {git_revision or 'working-tree'}",
            "render": True,
            "includeContent": True,
            "outputPath": f"execution-{execution_id}/architecture.html",
        }
        if isinstance(canonical_context, dict) and canonical_context:
            # Maps remains the renderer/validator; canonical provider metadata is carried as
            # evidence and never treated as an unverified graph replacement.
            request["canonicalEvidence"] = canonical_context
        timeout = aiohttp.ClientTimeout(total=settings.maps_timeout_seconds)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            baseline = None
            async with session.get(
                f"{self.api_url}/api/v1/executions/{execution_id}/artifacts/architecture-baseline",
                headers={"Authorization": f"Bearer {self.token}"},
            ) as response:
                response.raise_for_status()
                baseline = (await response.json()).get("data")

            async with session.post(
                f"{self.maps_url}/v1/maps/generate",
                json=request,
                headers={"X-SquadX-Repository-Path": str(workspace)},
            ) as response:
                response.raise_for_status()
                generated = await response.json()

            evidence = generated.get("artifact", {}).get("evidence") or {}
            if isinstance(canonical_context, dict) and canonical_context:
                evidence = {**evidence, "code_intelligence": canonical_context}
            group = f"architecture:{execution_id}:{uuid.uuid4().hex[:12]}"
            base_revision = baseline.get("git_revision") if baseline else None
            artifacts = [
                {
                    "artifact_key": "architecture.after.ir",
                    "type": "ARCHITECTURE_MAP",
                    "format": "JSON",
                    "name": "Architecture — After (IR)",
                    "git_revision": git_revision,
                    "base_revision": base_revision,
                    "artifact_group": group,
                    "view_role": "HEAD",
                    "evidence_json": json.dumps(evidence, separators=(",", ":")),
                    "content": json.dumps(generated["map"], indent=2),
                },
                {
                    "artifact_key": "architecture.after.html",
                    "type": "ARCHITECTURE_MAP",
                    "format": "HTML",
                    "name": "Architecture — After",
                    "git_revision": git_revision,
                    "base_revision": base_revision,
                    "artifact_group": group,
                    "view_role": "HEAD",
                    "evidence_json": json.dumps(evidence, separators=(",", ":")),
                    "content": generated["html"],
                },
            ]
            if baseline and baseline.get("content"):
                base_map = json.loads(baseline["content"])
                async with session.post(
                    f"{self.maps_url}/v1/maps/compare",
                    json={
                        "base": base_map,
                        "head": generated["map"],
                        "repositoryPath": str(workspace),
                        "outputPath": f"execution-{execution_id}/architecture-delta.html",
                        "includeContent": True,
                    },
                ) as response:
                    response.raise_for_status()
                    comparison = await response.json()
                artifacts[0:0] = [{
                    "artifact_key": "architecture.before.ir",
                    "type": "ARCHITECTURE_MAP", "format": "JSON",
                    "name": "Architecture — Before (IR)",
                    "git_revision": base_revision, "base_revision": base_revision,
                    "artifact_group": group, "view_role": "BASE",
                    "evidence_json": baseline.get("evidence_json"),
                    "content": baseline["content"],
                }]
                artifacts.append({
                    "artifact_key": "architecture.delta.html",
                    "type": "ARCHITECTURE_DELTA", "format": "HTML",
                    "name": "Architecture Delta — Before / Delta / After",
                    "git_revision": git_revision, "base_revision": base_revision,
                    "artifact_group": group, "view_role": "DELTA",
                    "evidence_json": json.dumps(comparison.get("receipt", {}), separators=(",", ":")),
                    "content": comparison["html"],
                })
            published = []
            for artifact in artifacts:
                async with session.put(
                    f"{self.api_url}/api/v1/executions/{execution_id}/artifacts",
                    json=artifact,
                    headers={"Authorization": f"Bearer {self.token}"},
                ) as response:
                    response.raise_for_status()
                    published.append((await response.json()).get("data", {}))
            await self._notify_pullwise(
                published, pullwise_review_id=pullwise_review_id,
                artifact_group=group, base_revision=base_revision,
                head_revision=git_revision,
            )
            return published

    async def _notify_pullwise(
        self, published: list[dict[str, Any]], *, pullwise_review_id: int | str | None,
        artifact_group: str, base_revision: str | None, head_revision: str | None,
    ) -> None:
        if not pullwise_review_id:
            return
        if not settings.pullwise_url or not settings.pullwise_service_secret:
            logger.warning("pullwise_delta_notification_skipped", reason="integration_not_configured")
            return
        delta = next((item for item in published if item.get("view_role") == "DELTA"), None)
        if not delta:
            return  # The first architecture snapshot has no baseline/delta yet.
        if not settings.artifact_url_template or "{artifact_id}" not in settings.artifact_url_template:
            logger.warning("pullwise_delta_notification_skipped", reason="artifact_url_template_missing")
            return
        delta_url = settings.artifact_url_template.format(artifact_id=delta["id"])
        timeout = aiohttp.ClientTimeout(total=30)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.put(
                f"{settings.pullwise_url.rstrip('/')}/api/internal/reviews/"
                f"{pullwise_review_id}/architecture-delta",
                headers={"X-SquadX-Service-Secret": settings.pullwise_service_secret},
                json={
                    "artifactGroup": artifact_group,
                    "deltaUrl": delta_url,
                    "baseRevision": base_revision,
                    "headRevision": head_revision,
                },
            ) as response:
                response.raise_for_status()
