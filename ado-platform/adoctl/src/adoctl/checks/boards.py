"""Boards checks (spec sections 3 and 15)."""

from __future__ import annotations

import re

from adoctl.checks import Ctx, guard

AREA = "Boards"
PARENT = "System.LinkTypes.Hierarchy-Reverse"
TESTED_BY = "Microsoft.VSTS.Common.TestedBy-Forward"
AS_A = re.compile(r"^\s*as (an?|the) .+\bI\b.+\bso that\b", re.I)


def _ids(ctx: Ctx, wtype: str) -> list[int]:
    q = ("SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] = '%s' "
         "AND [System.State] <> 'Removed' ORDER BY [System.Id]" % wtype)
    r = ctx.client.request("POST", ctx.client.proj("wit/wiql"), {"query": q})
    return [w["id"] for w in r.get("workItems", [])]


def _items(ctx: Ctx, wtype: str) -> list[dict]:
    def load():
        ids = _ids(ctx, wtype)
        out = []
        for i in range(0, len(ids), 200):
            r = ctx.client.request("POST", ctx.client.proj("wit/workitemsbatch"), {"ids": ids[i:i + 200], "$expand": "relations"})
            out += r.get("value", [])
        return out

    return ctx.cached(("wi", wtype), load)


def _list(ids, n=20) -> str:
    ids = list(ids)
    return ", ".join(f"#{i}" for i in ids[:n]) + (f" and {len(ids) - n} more" if len(ids) > n else "")


def _leaf(path: str) -> str:
    return path.replace("/", "\\").split("\\")[-1]


def run(ctx: Ctx, out):
    m = ctx.m

    def areas_and_sprints():
        rule = "Spec 3.2, 12 step 2"
        a = ctx.client.get(ctx.client.proj("wit/classificationnodes/areas?$depth=2"))
        have = {c["name"] for c in a.get("children", [])}
        missing = [x for x in m.areas if x not in have]
        if missing:
            out.fail("B01", AREA, "Area paths exist", "major", rule, f"Missing: {', '.join(missing)}", "Create the areas (adoctl apply --step 2).")
        else:
            out.ok("B01", AREA, "Area paths exist", rule, evidence=f"{len(m.areas)} areas")
        it = ctx.client.get(ctx.client.proj("wit/classificationnodes/iterations?$depth=2"))
        have_i = {c["name"] for c in it.get("children", [])}
        want = [n for n, _a, _b in m.sprint_dates()]
        miss = [n for n in want if n not in have_i]
        if not want:
            out.na("B02", AREA, "Sprints exist", rule, "The manifest has no sprints section.")
        elif miss:
            out.fail("B02", AREA, "Sprints exist", "major", rule, f"Missing: {', '.join(miss)}", "Create the sprints (adoctl apply --step 2).")
        else:
            out.ok("B02", AREA, "Sprints exist", rule, evidence=f"{len(want)} sprints")

    guard(out, ctx, "B01", AREA, "Area paths and sprints", "Spec 3.2", areas_and_sprints)

    def tasks():
        ts = _items(ctx, "Task")
        no_parent, bad_area = [], []
        for t in ts:
            rels = [r for r in t.get("relations") or [] if r.get("rel") == PARENT]
            if len(rels) != 1:
                no_parent.append(t["id"])
            leaf = _leaf(t["fields"].get("System.AreaPath", ""))
            if m.areas and leaf not in m.areas:
                bad_area.append(t["id"])
        rule = "Spec 3.1, 3.2"
        if no_parent:
            out.fail("B03", AREA, "Every Task has exactly one parent Story", "major", rule, f"{len(no_parent)} of {len(ts)} tasks: {_list(no_parent)}", "Link each task to its story.")
        else:
            out.ok("B03", AREA, "Every Task has exactly one parent Story", rule, evidence=f"{len(ts)} tasks")
        if bad_area:
            out.fail("B04", AREA, "Every Task is in a repository area", "minor", rule, f"{len(bad_area)} tasks outside the manifest areas: {_list(bad_area)}", "Move the task to its repository's area path.")
        else:
            out.ok("B04", AREA, "Every Task is in a repository area", rule, evidence=f"{len(ts)} tasks")
        active = [t for t in ts if t["fields"].get("System.State") == "Active"]
        names = set()
        for _mr, r in ctx.repos_of_manifest():
            if r:
                names.update(ctx.branches(r["id"]))
        no_branch = [t["id"] for t in active if not any(re.search(rf"-T{t['id']}($|\b)", b) for b in names)]
        if no_branch:
            out.fail("B07", AREA, "No Task is Active without a branch", "major", "Spec 3.2 (Active means the branch exists)",
                     f"{len(no_branch)} of {len(active)} active tasks have no branch feature/US<story>-T<task>: {_list(no_branch)}",
                     "Close or resolve the task if its pull request is merged; otherwise create the branch (adoctl work start).")
        else:
            out.ok("B07", AREA, "No Task is Active without a branch", "Spec 3.2", evidence=f"{len(active)} active tasks")

    guard(out, ctx, "B03", AREA, "Task checks", "Spec 3.1", tasks)

    def stories():
        ss = _items(ctx, "User Story")
        rule = "Spec 3.2"
        live = [s for s in ss if s["fields"].get("System.State") not in ("Closed", "Removed")]
        no_ac = [s["id"] for s in live if not (s["fields"].get("Microsoft.VSTS.Common.AcceptanceCriteria") or "").strip()]
        if no_ac:
            out.fail("B05", AREA, "Every open Story has acceptance criteria", "major", rule, f"{len(no_ac)} of {len(live)} open stories: {_list(no_ac)}", "Write the acceptance criteria; a story without them is not ready.")
        else:
            out.ok("B05", AREA, "Every open Story has acceptance criteria", rule, evidence=f"{len(live)} open stories")
        no_tc = [s["id"] for s in live if not any(r.get("rel") == TESTED_BY for r in s.get("relations") or [])]
        if no_tc:
            out.fail("B06", AREA, "Every open Story has a Test Case", "major", rule, f"{len(no_tc)} of {len(live)} open stories have no Tested By link: {_list(no_tc)}", "Create one Test Case per acceptance criterion and link it Tested By.")
        else:
            out.ok("B06", AREA, "Every open Story has a Test Case", rule, evidence=f"{len(live)} open stories")
        odd = [s["id"] for s in ss if not AS_A.search(s["fields"].get("System.Title", ""))]
        if odd:
            out.warn("B09", AREA, "Story titles read 'As a ... I ... so that ...'", rule, f"{len(odd)} of {len(ss)} stories: {_list(odd)}", "Rewrite the title as the stories are refined.")
        else:
            out.ok("B09", AREA, "Story titles read 'As a ... I ... so that ...'", rule, evidence=f"{len(ss)} stories")

    guard(out, ctx, "B05", AREA, "Story checks", "Spec 3.2", stories)

    out.na("B08", AREA, "No Story is Closed with wiki pages not updated", "Spec 3.2",
           "Not checkable through the REST API: whether a page was updated for a story is a judgement. Review at sprint review (spec section 13).")
