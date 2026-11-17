"""adoctl work start | pr | status (spec 3.4)."""

from __future__ import annotations

import datetime as dt

from adoctl.checks import Ctx
from adoctl.client import Client
from adoctl.manifest import Manifest

ZERO = "0" * 40
PARENT = "System.LinkTypes.Hierarchy-Reverse"


class WorkError(Exception):
    pass


def _task(c: Client, task_id: int) -> dict:
    t = c.get(c.proj(f"wit/workitems/{task_id}?$expand=relations"))
    if t["fields"].get("System.WorkItemType") != "Task":
        raise WorkError(f"#{task_id} is a {t['fields'].get('System.WorkItemType')}, not a Task.")
    return t


def context(c: Client, m: Manifest, task_id: int) -> dict:
    t = _task(c, task_id)
    parents = [r for r in t.get("relations") or [] if r.get("rel") == PARENT]
    if len(parents) != 1:
        raise WorkError(f"Task #{task_id} must have exactly one parent Story (it has {len(parents)}).")
    story = int(parents[0]["url"].rstrip("/").split("/")[-1])
    area = t["fields"]["System.AreaPath"].replace("/", "\\").split("\\")[-1]
    repo = m.repo_for_area(area)
    if not repo:
        raise WorkError(f"Area '{area}' of task #{task_id} matches no repository in the manifest.")
    return {"task": task_id, "story": story, "repo": repo, "title": t["fields"]["System.Title"], "branch": f"feature/US{story}-T{task_id}", "state": t["fields"]["System.State"]}


def start(c: Client, m: Manifest, task_id: int, do: bool) -> list[str]:
    x = context(c, m, task_id)
    ctx = Ctx(c, m)
    repo = ctx.repo(x["repo"])
    if not repo:
        raise WorkError(f"Repository {x['repo']} does not exist.")
    msgs = []
    refs = {r["name"]: r["objectId"] for r in c.get(c.proj(f"git/repositories/{repo['id']}/refs?filter=heads/"))["value"]}
    if f"refs/heads/{x['branch']}" in refs:
        msgs.append(f"branch {x['branch']} already exists in {x['repo']}")
    else:
        msgs.append(f"create branch {x['branch']} in {x['repo']} from develop")
        if do:
            c.request("POST", c.proj(f"git/repositories/{repo['id']}/refs"), [{"name": f"refs/heads/{x['branch']}", "oldObjectId": ZERO, "newObjectId": refs["refs/heads/develop"]}])
    if x["state"] != "Active":
        msgs.append(f"set task #{task_id} Active")
        if do:
            c.request("PATCH", c.proj(f"wit/workitems/{task_id}"), [{"op": "add", "path": "/fields/System.State", "value": "Active"}], content_type="application/json-patch+json")
    return msgs


def pr_description(what: str, verified: str, not_covered: str, task_id: int, story: int) -> str:
    d = (f"{what.strip()}\n\nTask #{task_id}, story #{story}.\n\n## Verified\n{verified.strip()}\n\n## Not covered\n{not_covered.strip()}\n\n"
         "## Checklist\n- [x] Linked to a work item\n- [x] Targets develop\n- [ ] Tests and wiki page in this pull request\n- [x] No secrets\n")
    if len(d) >= 4000:
        raise WorkError(f"The description is {len(d)} characters; the limit is 4000. Link the story instead of pasting it.")
    return d


def pr(c: Client, m: Manifest, task_id: int, what: str, verified: str, not_covered: str, draft: bool, do: bool) -> list[str]:
    x = context(c, m, task_id)
    repo = Ctx(c, m).repo(x["repo"])
    desc = pr_description(what, verified, not_covered, task_id, x["story"])
    title = f"{x['title']} (#{task_id})"
    msgs = [f"open pull request '{title}' from {x['branch']} to develop in {x['repo']} ({'draft' if draft else 'ready for review'}), linked to #{task_id} and #{x['story']}; description {len(desc)} characters"]
    if do:
        r = c.request("POST", c.proj(f"git/repositories/{repo['id']}/pullrequests"), {
            "sourceRefName": f"refs/heads/{x['branch']}", "targetRefName": "refs/heads/develop", "title": title, "description": desc,
            "isDraft": draft, "workItemRefs": [{"id": str(task_id)}, {"id": str(x["story"])}]})
        msgs.append(f"opened pull request {r.get('pullRequestId')}")
    return msgs


def status(c: Client, m: Manifest, iteration: str | None = None, now: dt.datetime | None = None) -> list[str]:
    now = now or dt.datetime.now(dt.timezone.utc)
    today = now.date()
    if not iteration:
        cur = [n for n, a, b in m.sprint_dates() if a <= today <= b]
        iteration = cur[0] if cur else None
    lines = []
    ids_q = "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] = 'Task' AND [System.State] <> 'Removed'"
    if iteration:
        ids_q += f" AND [System.IterationPath] = '{m.name}\\{iteration}'"
    ids = [w["id"] for w in c.request("POST", c.proj("wit/wiql"), {"query": ids_q}).get("workItems", [])]
    items = []
    for i in range(0, len(ids), 200):
        items += c.request("POST", c.proj("wit/workitemsbatch"), {"ids": ids[i:i + 200], "fields": ["System.Id", "System.Title", "System.State", "System.AreaPath", "System.ChangedDate"]}).get("value", [])
    by = {}
    for t in items:
        by.setdefault(t["fields"]["System.State"], []).append(t)
    lines.append(f"Tasks in {iteration or 'the project'}: {len(items)}. " + ", ".join(f"{k} {len(v)}" for k, v in sorted(by.items())))
    stale = []
    for t in by.get("Active", []):
        ch = t["fields"].get("System.ChangedDate")
        if ch and (now - dt.datetime.fromisoformat(ch.replace("Z", "+00:00"))).days >= 2:
            stale.append(f"#{t['id']} {t['fields']['System.Title']}")
    if stale:
        lines.append("Active more than two days: " + "; ".join(stale[:10]))
    prs = [p for p in c.paged(c.proj("git/pullrequests?searchCriteria.status=active&$top=100")) if p["repository"]["name"] in m.repo_names()]
    waiting = [f"{p['repository']['name']}!{p['pullRequestId']}" for p in prs
               if not any(r.get("vote", 0) for r in p.get("reviewers", [])) and (now - dt.datetime.fromisoformat(p["creationDate"].replace("Z", "+00:00"))).days >= 1]
    lines.append(f"Active pull requests: {len(prs)}; waiting for review more than a day: {len(waiting)}" + (f" ({', '.join(waiting[:10])})" if waiting else ""))
    ctx = Ctx(c, m)
    red = []
    for _mr, repo in ctx.repos_of_manifest():
        ci = ctx.ci_pipeline_for(repo) if repo else None
        if not ci:
            continue
        runs = c.get(c.proj(f"build/builds?definitions={ci['id']}&branchName=refs/heads/develop&statusFilter=completed&queryOrder=finishTimeDescending&$top=1")).get("value", [])
        if runs and runs[0].get("result") != "succeeded":
            red.append(ci["name"])
    lines.append("Red builds on develop: " + (", ".join(red) if red else "none"))
    return lines
