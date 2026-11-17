"""Active pull request checks (spec 5.3 and 15)."""

from __future__ import annotations

import datetime as dt

from adoctl.checks import Ctx, guard

AREA = "Pull requests"
RULE = "Spec 5.3"


def _age_days(iso: str, now: dt.datetime) -> float:
    t = dt.datetime.fromisoformat(iso.replace("Z", "+00:00"))
    return (now - t).total_seconds() / 86400


def run(ctx: Ctx, out, now: dt.datetime | None = None):
    now = now or dt.datetime.now(dt.timezone.utc)
    managed = {r["id"]: r["name"] for _mr, r in ctx.repos_of_manifest() if r}

    def go():
        prs = [p for p in ctx.client.paged(ctx.client.proj("git/pullrequests?searchCriteria.status=active&$top=100"))
               if p["repository"]["id"] in managed]
        if not prs:
            for pid, title in (("PR01", "Every active pull request links a work item"), ("PR02", "Every active pull request targets develop"),
                               ("PR03", "No pull request is stacked"), ("PR04", "Descriptions say what was verified and not covered"),
                               ("PR05", "No pull request waits more than three days for a review")):
                out.ok(pid, AREA, title, RULE, evidence="No active pull requests.")
            return
        no_wi, wrong, stacked, thin, stale = [], [], [], [], []
        for p in prs:
            tag = f"{managed[p['repository']['id']]}!{p['pullRequestId']}"
            wi = ctx.client.get(ctx.client.proj(f"git/repositories/{p['repository']['id']}/pullRequests/{p['pullRequestId']}/workitems")).get("value", [])
            if not wi:
                no_wi.append(tag)
            tgt, src = p["targetRefName"].replace("refs/heads/", ""), p["sourceRefName"].replace("refs/heads/", "")
            if tgt.startswith(("feature/", "bugfix/", "hotfix/")):
                stacked.append(f"{tag} ({src} to {tgt})")
            elif tgt != "develop" and not (tgt == "main" and src.startswith(("release/", "hotfix/"))):
                wrong.append(f"{tag} ({src} to {tgt})")
            # the list endpoint cuts descriptions at 400 characters, so read the pull request itself
            full = ctx.client.get(ctx.client.proj(f"git/repositories/{p['repository']['id']}/pullRequests/{p['pullRequestId']}"))
            d = (full.get("description") or "").lower()
            if "verified" not in d or "not covered" not in d:
                thin.append(tag)
            reviewed = any(r.get("vote", 0) != 0 for r in p.get("reviewers", []))
            if not reviewed and not p.get("isDraft") and _age_days(p["creationDate"], now) > 3:
                stale.append(f"{tag} ({_age_days(p['creationDate'], now):.0f} days)")
        n = len(prs)

        def res(pid, title, bad, sev, fix, status="fail"):
            if bad:
                (out.fail(pid, AREA, title, sev, RULE, f"{len(bad)} of {n}: {', '.join(bad[:15])}", fix) if status == "fail"
                 else out.warn(pid, AREA, title, RULE, f"{len(bad)} of {n}: {', '.join(bad[:15])}", fix))
            else:
                out.ok(pid, AREA, title, RULE, evidence=f"{n} active pull requests")

        res("PR01", "Every active pull request links a work item", no_wi, "major", "Link the task.")
        res("PR02", "Every active pull request targets develop", wrong, "major", "Retarget to develop (main only from release/* or hotfix/*).")
        res("PR03", "No pull request is stacked", stacked, "major", "Retarget to develop; a squash merge strands stacked work.")
        res("PR04", "Descriptions say what was verified and not covered", thin, "minor", "Add the Verified and Not covered sections.", status="warn")
        res("PR05", "No pull request waits more than three days for a review", stale, "major", "Review it, or ask the delivery lead to.")

    guard(out, ctx, "PR01", AREA, "Active pull requests", RULE, go)
