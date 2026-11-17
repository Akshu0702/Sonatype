"""Continuous integration checks (spec 7 and 15)."""

from __future__ import annotations

import urllib.parse

from adoctl.checks import Ctx, guard

AREA = "CI"
RULE = "Spec 7"


def run(ctx: Ctx, out):
    for mr, repo in ctx.repos_of_manifest():
        if not repo:
            continue
        sub = repo["name"]

        def per_repo(mr=mr, repo=repo, sub=sub):
            ci = ctx.ci_pipeline_for(repo)
            if not ci:
                out.fail("C01", AREA, "The repository has a ci-* pipeline", "major", RULE, "No pipeline named ci-* is attached to this repository.", "Create ci-<code>-<name> calling the shared templates.", sub)
                out.na("C02", AREA, "The pipeline calls the shared templates", RULE, "No ci pipeline.", sub)
                out.na("C03", AREA, "The last run on develop is green", RULE, "No ci pipeline.", sub)
                return
            out.ok("C01", AREA, "The repository has a ci-* pipeline", RULE, sub, evidence=ci["name"])
            yaml_path = (ci.get("process") or {}).get("yamlFilename")
            if mr.get("kind") == "infra":
                out.na("C02", AREA, "The pipeline calls the shared templates", RULE, "The infrastructure repository holds the templates.", sub)
            elif not yaml_path:
                out.warn("C02", AREA, "The pipeline calls the shared templates", RULE, "Not a YAML pipeline, so its steps live outside Git.", "Move it to YAML that calls the shared templates.", sub)
            else:
                text = ctx.file_text(repo["id"], "/" + yaml_path.lstrip("/"), "develop")
                if text is None:
                    out.warn("C02", AREA, "The pipeline calls the shared templates", RULE, f"{yaml_path} not found on develop.", "Fix the pipeline's YAML path.", sub)
                elif "template:" not in text:
                    out.warn("C02", AREA, "The pipeline calls the shared templates", RULE, f"{yaml_path} has no 'template:' reference.", "Call the shared templates in <code>-infra/pipelines/templates.", sub)
                else:
                    out.ok("C02", AREA, "The pipeline calls the shared templates", RULE, sub)
            q = urllib.parse.urlencode({"definitions": ci["id"], "branchName": "refs/heads/develop", "statusFilter": "completed", "queryOrder": "finishTimeDescending", "$top": 1})
            runs = ctx.client.get(ctx.client.proj(f"build/builds?{q}")).get("value", [])
            if not runs:
                out.warn("C03", AREA, "The last run on develop is green", RULE, f"{ci['name']} has never completed a run on develop.", "Merge something to develop or run the pipeline.", sub)
            elif runs[0].get("result") == "succeeded":
                out.ok("C03", AREA, "The last run on develop is green", RULE, sub, evidence=f"build {runs[0].get('buildNumber')}")
            else:
                out.fail("C03", AREA, "The last run on develop is green", "major", RULE, f"Last run {runs[0].get('buildNumber')} ended '{runs[0].get('result')}'.", "Fix the build before anything else is merged.", sub)

        guard(out, ctx, "C01", AREA, f"CI of {sub}", RULE, per_repo)
    out.na("C04", AREA, "Pipelines are authorised for the feed", RULE,
           "Feed access is a permission of the build identity (checked as F02), not a per-pipeline setting; a pipeline that cannot publish fails its last run (C03).")
