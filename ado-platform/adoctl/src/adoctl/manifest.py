"""The project manifest (ado-project.yml): the only input to setup and audit. Names and decisions, never secrets."""

from __future__ import annotations

import datetime as dt
import re
from pathlib import Path

import yaml

PLACEHOLDER = re.compile(r"<[A-Za-z][^<>\n]*>")
TOKENISH = re.compile(r"[A-Za-z0-9]{40,}")


class ManifestError(Exception):
    pass


class Manifest:
    def __init__(self, data: dict, path: str = ""):
        self.data = data
        self.path = path
        self.validate()

    # ---- loading ------------------------------------------------------------------------------------------------
    @classmethod
    def load(cls, path: str) -> "Manifest":
        p = Path(path)
        if not p.is_file():
            raise ManifestError(f"No manifest at {path}. Create one with `adoctl init` (a project that is not in a manifest is never touched).")
        data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        return cls(data, str(p))

    # ---- accessors ----------------------------------------------------------------------------------------------
    @property
    def project(self) -> dict:
        return self.data["project"]

    @property
    def name(self) -> str:
        return self.project["name"]

    @property
    def code(self) -> str:
        return self.project["code"]

    @property
    def organisation(self) -> str:
        return self.project["organisation"]

    @property
    def repositories(self) -> list[dict]:
        return self.data.get("repositories", [])

    @property
    def ignored_repositories(self) -> list[str]:
        return self.data.get("repositories_ignore", [])

    @property
    def areas(self) -> list[str]:
        return self.data.get("areas", [])

    @property
    def policies(self) -> dict:
        return self.data.get("policies", {})

    @property
    def sprints(self) -> dict:
        return self.data.get("sprints", {})

    @property
    def feed(self) -> dict:
        return self.data.get("feed", {})

    @property
    def stack(self) -> dict:
        return self.data.get("stack", {})

    def repo_names(self) -> list[str]:
        return [r["name"] for r in self.repositories]

    def area_for_repo(self, repo: str) -> str | None:
        for r in self.repositories:
            if r["name"] == repo:
                return r.get("area")
        return None

    def repo_for_area(self, area: str) -> str | None:
        for r in self.repositories:
            if r.get("area") == area:
                return r["name"]
        return None

    def sprint_dates(self) -> list[tuple[str, dt.date, dt.date]]:
        s = self.sprints
        if not s:
            return []
        length = int(s.get("length_days", 7))
        count = int(s.get("count", 0))
        if s.get("first_start"):
            start = _date(s["first_start"])
        else:  # counted back from go-live
            start = _date(self.project["go_live"]) - dt.timedelta(days=length * count)
        out = []
        for i in range(count):
            a = start + dt.timedelta(days=length * i)
            out.append((f"Sprint {i + 1}", a, a + dt.timedelta(days=length - 1)))
        return out

    # ---- safety -------------------------------------------------------------------------------------------------
    def require_project(self, name: str | None):
        """A project that is not in the manifest is never touched."""
        if name and name != self.name:
            raise ManifestError(f"Project '{name}' is not the project in the manifest ('{self.name}'). Refusing to run.")

    def validate(self):
        d = self.data
        for k in ("project",):
            if k not in d:
                raise ManifestError(f"The manifest has no '{k}' section.")
        for k in ("code", "name", "organisation"):
            if not d["project"].get(k):
                raise ManifestError(f"project.{k} is required.")
        left = sorted(set(PLACEHOLDER.findall(Path(self.path).read_text(encoding="utf-8")))) if self.path and Path(self.path).is_file() else []
        left = [x for x in left if not x.startswith("<!--")]
        if left:
            raise ManifestError("Unfilled placeholders in the manifest: " + ", ".join(left[:8]) + (" ..." if len(left) > 8 else ""))
        if not re.fullmatch(r"[a-z]{2,5}", str(d["project"]["code"])):
            raise ManifestError("project.code must be 2 to 5 lower-case letters.")
        for text in _walk_strings(d):
            if TOKENISH.fullmatch(text):
                raise ManifestError("A value in the manifest looks like a token. The manifest never holds secrets.")


def _walk_strings(x):
    if isinstance(x, str):
        yield x
    elif isinstance(x, dict):
        for v in x.values():
            yield from _walk_strings(v)
    elif isinstance(x, list):
        for v in x:
            yield from _walk_strings(v)


def _date(v) -> dt.date:
    return v if isinstance(v, dt.date) else dt.date.fromisoformat(str(v))


def template_text(code: str, name: str, organisation: str, delivery_lead: str, project_manager: str, go_live: str, first_start: str, count: int) -> str:
    return f"""# ado-project.yml: the only input to adoctl setup and audit. Names and decisions, never secrets.
project:
  code: {code}
  name: {name}
  organisation: {organisation}
  process: Agile
  delivery_lead: {delivery_lead}
  project_manager: {project_manager}
  go_live: {go_live}

sprints:
  length_days: 7
  first_start: {first_start}
  count: {count}

areas: [Delivery]            # add one area per repository

repositories:                # name = {code}-<svc>-<kind>
  - {{name: {code}-infra, kind: infra, area: Delivery}}

repositories_ignore: []      # repositories that exist but are not part of this project

policies:
  min_reviewers: 1
  author_can_approve: false
  reset_votes_on_push: true
  merge_develop: squash
  merge_main: merge-commit
  max_file_size_mb: 10

feed:
  name: {code}-maven
  views: [Local, Prerelease, Release]

environments:
  - {{name: dev, approver: {delivery_lead}, dry_run_first: true}}

agents:
  pool: {code}-agents

stack: {{}}                  # pinned versions, for example: {{pinned: {{java: "21"}}}}
"""
