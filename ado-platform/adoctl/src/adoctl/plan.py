"""plan / apply for spec section 12 steps 2 to 4: areas and sprints, repositories, branch policies.

Everything is idempotent (a second run changes nothing) and nothing is ever deleted. `do=False` only describes.
Steps 5 to 13 are not built and are refused.
"""

from __future__ import annotations

from adoctl.checks import Ctx
from adoctl.checks.policies import T_COMMENT, T_MERGE, T_MIN, T_SIZE, T_WORKITEM, find
from adoctl.client import ApiError, Client
from adoctl.manifest import Manifest

ZERO = "0" * 40
BUILT_STEPS = (1, 2, 3, 4)
STEP_NAMES = {
    1: "Read the manifest",
    2: "Create areas and sprints",
    3: "Create repositories with main and develop",
    4: "Apply branch policies to main and develop",
    5: "Create the wiki skeleton",
    6: "Create the backlog skeleton",
    7: "Put the shared templates in the infrastructure repository",
    8: "Create the ci pipelines and add build validation",
    9: "Create the feed and publish the first snapshot",
    10: "Create the cd release pipelines as dry runs",
    11: "Prepare the deployment target",
    12: "First controlled deployment (needs the delivery lead's go-ahead)",
    13: "Record what was verified and what was not",
}


class NotBuilt(Exception):
    pass


def refuse_unbuilt(step: int):
    raise NotBuilt(f"Step {step} ({STEP_NAMES.get(step, '?')}) is not built in adoctl v0.1. Do it by hand following spec section 12, then run `adoctl audit`.")


def readme(m: Manifest, repo: str) -> str:
    return f"# {repo}\n\nPart of {m.name}. Follows AzureDevOps-Platform-v1 (branching, pull requests, wiki, release rules).\n\nStatus: Not built.\n"


GITIGNORE = "target/\nnode_modules/\ndist/\n.env\n*.log\n.idea/\n.vscode/\n.DS_Store\n"
PR_TEMPLATE = """## What
<one paragraph; the task and story ids>

## Verified
<commands run, counts, what the result proved>

## Not covered
<what was not run, what is a stand-in, what is left>

## Checklist
- [ ] Linked to a work item
- [ ] Targets develop
- [ ] Tests and wiki page in this pull request
- [ ] No secrets
"""


# ---- step 2 --------------------------------------------------------------------------------------------------------
def step2(ctx: Ctx, do: bool) -> list[str]:
    c, m = ctx.client, ctx.m
    msgs = []
    have = {n["name"] for n in c.get(c.proj("wit/classificationnodes/areas?$depth=2")).get("children", [])}
    for a in m.areas:
        if a in have:
            continue
        msgs.append(f"create area '{a}'")
        if do:
            c.request("POST", c.proj("wit/classificationnodes/areas"), {"name": a})
    have_i = {n["name"] for n in c.get(c.proj("wit/classificationnodes/iterations?$depth=2")).get("children", [])}
    for name, start, finish in m.sprint_dates():
        if name in have_i:
            continue
        msgs.append(f"create sprint '{name}' {start} to {finish}")
        if do:
            c.request("POST", c.proj("wit/classificationnodes/iterations"), {"name": name, "attributes": {"startDate": f"{start}T00:00:00Z", "finishDate": f"{finish}T00:00:00Z"}})
    return msgs


# ---- step 3 --------------------------------------------------------------------------------------------------------
def step3(ctx: Ctx, do: bool) -> list[str]:
    c, m = ctx.client, ctx.m
    msgs = []
    for mr in m.repositories:
        name = mr["name"]
        repo = ctx.repo(name)
        if not repo:
            msgs.append(f"create repository '{name}'")
            if do:
                repo = c.request("POST", c.proj("git/repositories"), {"name": name, "project": {"id": ctx.project_id()}})
                ctx._cache.pop("repos", None)
        rid = repo["id"] if repo else None
        refs = {}
        if rid:
            refs = {r["name"]: r["objectId"] for r in c.get(c.proj(f"git/repositories/{rid}/refs?filter=heads/"))["value"]}
        if "refs/heads/main" not in refs:
            msgs.append(f"first commit on main of '{name}' (README, .gitignore, pull request template)")
            if do:
                files = {"/README.md": readme(m, name), "/.gitignore": GITIGNORE, "/.azuredevops/pull_request_template.md": PR_TEMPLATE}
                push = c.request("POST", c.proj(f"git/repositories/{rid}/pushes"), {
                    "refUpdates": [{"name": "refs/heads/main", "oldObjectId": ZERO}],
                    "commits": [{"comment": "Add README, .gitignore and pull request template", "changes": [
                        {"changeType": "add", "item": {"path": p}, "newContent": {"content": t, "contentType": "rawtext"}} for p, t in files.items()]}]})
                refs["refs/heads/main"] = push["refUpdates"][0]["newObjectId"]
        if "refs/heads/develop" not in refs:
            msgs.append(f"create branch develop in '{name}'")
            if do:
                c.request("POST", c.proj(f"git/repositories/{rid}/refs"), [{"name": "refs/heads/develop", "oldObjectId": ZERO, "newObjectId": refs["refs/heads/main"]}])
        if not repo or (repo.get("defaultBranch") or "") != "refs/heads/develop":
            msgs.append(f"set develop as the default branch of '{name}'")
            if do:
                c.request("PATCH", c.proj(f"git/repositories/{rid}"), {"defaultBranch": "refs/heads/develop"})
                ctx._cache.pop("repos", None)
    return msgs


# ---- step 4 --------------------------------------------------------------------------------------------------------
def policy_bodies(m: Manifest, repo_id: str, branch: str, types: dict[str, str]) -> list[tuple[str, dict]]:
    p = m.policies
    scope = [{"repositoryId": repo_id, "refName": f"refs/heads/{branch}", "matchKind": "Exact"}]
    author = bool(p.get("author_can_approve", False))
    squash = branch == "develop"
    out = [
        (T_MIN, {"minimumApproverCount": int(p.get("min_reviewers", 1)), "creatorVoteCounts": author, "allowDownvotes": False,
                 "resetOnSourcePush": bool(p.get("reset_votes_on_push", True)), "requireVoteOnLastIteration": False,
                 "resetRejectionsOnSourcePush": False, "blockLastPusherVote": not author, "scope": scope}),
        (T_COMMENT, {"scope": scope}),
        (T_WORKITEM, {"scope": scope}),
        (T_MERGE, {"allowSquash": squash, "allowNoFastForward": not squash, "allowRebase": False, "allowRebaseMerge": False, "scope": scope}),
    ]
    return [(t, {"isEnabled": True, "isBlocking": True, "type": {"id": types[t]}, "settings": s}) for t, s in out if t in types]


def step4(ctx: Ctx, do: bool) -> list[str]:
    c, m = ctx.client, ctx.m
    msgs = []
    types = {t["displayName"]: t["id"] for t in c.get(c.proj("policy/types"))["value"]}
    for mr in m.repositories:
        repo = ctx.repo(mr["name"])
        if not repo:
            msgs.append(f"policies for '{mr['name']}' (after the repository exists)")
            continue
        for branch in ("main", "develop"):
            for tname, body in policy_bodies(m, repo["id"], branch, types):
                if find(ctx, tname, repo, branch):
                    continue
                msgs.append(f"add policy '{tname}' on {repo['name']}:{branch}")
                if do:
                    c.request("POST", c.proj("policy/configurations"), body)
        have_size = [p for p in ctx.policies() if p.get("type", {}).get("displayName") == T_SIZE
                     and any(s.get("repositoryId") in (None, repo["id"]) for s in p["settings"].get("scope", []))]
        if T_SIZE in types and not have_size:
            msgs.append(f"add policy '{T_SIZE}' ({m.policies.get('max_file_size_mb', 10)} MB) on {repo['name']}")
            if do:
                c.request("POST", c.proj("policy/configurations"), {"isEnabled": True, "isBlocking": True, "type": {"id": types[T_SIZE]}, "settings": {
                    "maximumGitBlobSizeInBytes": int(m.policies.get("max_file_size_mb", 10)) * 1024 * 1024, "useUncompressedSize": False,
                    "scope": [{"repositoryId": repo["id"]}]}})
    msgs.append("build validation is added when each repository's ci-* pipeline exists (step 8, not built)")
    return msgs


STEPS = {2: step2, 3: step3, 4: step4}


def run_steps(client: Client, m: Manifest, only: int | None, do: bool) -> list[tuple[int, list[str]]]:
    steps = [only] if only else [1, 2, 3, 4]
    for s in steps:
        if s not in BUILT_STEPS:
            refuse_unbuilt(s)
    ctx = Ctx(client, m)
    out = []
    for s in steps:
        if s == 1:
            out.append((1, [f"manifest valid: project {m.name} ({m.code}), {len(m.repositories)} repositories, {len(m.areas)} areas"]))
            continue
        try:
            out.append((s, STEPS[s](ctx, do)))
        except ApiError as e:
            raise ApiError(e.status, f"step {s}: {e.message}", e.url) from None
        ctx._cache.pop("policies", None)
    return out
