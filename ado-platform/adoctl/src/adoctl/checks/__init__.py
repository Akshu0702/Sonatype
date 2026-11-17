"""The audit: every check in AzureDevOps-Platform-v1 section 15 that the Azure DevOps REST API allows. Read-only.

Rule references (R-numbers are spec sections): see each check's `rule` field.
A check that cannot run (a permission or API error) is reported as a blind spot, never as a pass.
"""

from __future__ import annotations

from adoctl.client import ApiError, Client
from adoctl.manifest import Manifest
from adoctl.model import Collector, Result


class Ctx:
    """Shared, cached reads for all checks."""

    def __init__(self, client: Client, manifest: Manifest):
        self.client = client
        self.m = manifest
        self._cache: dict = {}

    def cached(self, key, fn):
        if key not in self._cache:
            self._cache[key] = fn()
        return self._cache[key]

    # ---- project and repositories ---------------------------------------------------------------------------------
    def project_id(self) -> str:
        return self.cached("pid", lambda: self.client.get(self.client.org(f"projects/{self.m.name}"))["id"])

    def all_repos(self) -> list[dict]:
        return self.cached("repos", lambda: [r for r in self.client.get(self.client.proj("git/repositories"))["value"] if not r.get("isDisabled")])

    def repo(self, name: str) -> dict | None:
        return next((r for r in self.all_repos() if r["name"] == name), None)

    def branches(self, repo_id: str) -> list[str]:
        def load():
            refs = self.client.get(self.client.proj(f"git/repositories/{repo_id}/refs?filter=heads/"))["value"]
            return [r["name"][len("refs/heads/"):] for r in refs]

        return self.cached(("branches", repo_id), load)

    def file_exists(self, repo_id: str, path: str, branch: str | None = None) -> bool:
        v = f"&versionDescriptor.version={branch}" if branch else ""
        return self.client.get_or_none(self.client.proj(f"git/repositories/{repo_id}/items?path={path}{v}&$format=json")) is not None

    def file_text(self, repo_id: str, path: str, branch: str | None = None) -> str | None:
        v = f"&versionDescriptor.version={branch}" if branch else ""
        r = self.client.get_or_none(self.client.proj(f"git/repositories/{repo_id}/items?path={path}{v}&includeContent=true&$format=json"))
        return None if r is None else r.get("content", "")

    # ---- policies and pipelines -----------------------------------------------------------------------------------
    def policies(self) -> list[dict]:
        def load():
            return [p for p in self.client.paged(self.client.proj("policy/configurations")) if p.get("isEnabled") and not p.get("isDeleted")]

        return self.cached("policies", load)

    def build_defs(self) -> list[dict]:
        """Full build definitions (with repository and variables)."""
        def load():
            out = []
            for d in self.client.paged(self.client.proj("build/definitions?$top=200")):
                out.append(self.client.get(self.client.proj(f"build/definitions/{d['id']}")))
            return out

        return self.cached("builddefs", load)

    def release_defs(self) -> list[dict]:
        def load():
            out = []
            for d in self.client.paged(self.client.vsrm("release/definitions?$top=200")):
                out.append(self.client.get(self.client.vsrm(f"release/definitions/{d['id']}")))
            return out

        return self.cached("releasedefs", load)

    def ci_pipeline_for(self, repo: dict) -> dict | None:
        for d in self.build_defs():
            r = d.get("repository") or {}
            if (r.get("id") == repo["id"] or r.get("name") == repo["name"]) and d["name"].startswith("ci-"):
                return d
        for d in self.build_defs():
            if d["name"] in (f"ci-{repo['name']}", f"ci-{repo['name'].split('-', 1)[-1]}"):
                return d
        return None

    def repos_of_manifest(self) -> list[tuple[dict, dict | None]]:
        return [(mr, self.repo(mr["name"])) for mr in self.m.repositories]


def guard(out: Collector, ctx: Ctx, id: str, area: str, title: str, rule: str, fn):
    """Runs one check. An API error becomes a blind spot, never a silent pass."""
    try:
        fn()
    except ApiError as e:
        out.blind(id, area, title, rule, f"{ctx.client.scrub(e)} ({ctx.client.scrub(e.url)})", denied=e.status in (401, 403))
    except Exception as e:  # a bug in a check must not hide the other checks
        out.blind(id, area, title, rule, f"check failed with {type(e).__name__}: {ctx.client.scrub(e)}")


def run_all(client: Client, manifest: Manifest) -> list[Result]:
    from adoctl.checks import boards, ci, feed, policies, prs, release, repos, security, stack, wiki

    ctx = Ctx(client, manifest)
    out = Collector()
    for module in (boards, repos, policies, prs, ci, feed, release, wiki, security, stack):
        module.run(ctx, out)
    return out.results
