"""Repository checks (spec sections 4 and 15)."""

from __future__ import annotations

from adoctl.checks import Ctx, guard

AREA = "Repositories"
FILES = ["/README.md", "/.gitignore", "/.azuredevops/pull_request_template.md"]


def run(ctx: Ctx, out):
    m = ctx.m

    def names():
        rule = "Spec 4"
        want = m.repo_names()
        have = {r["name"] for r in ctx.all_repos()}
        missing = [w for w in want if w not in have]
        extra = [h for h in have if h not in want and h not in m.ignored_repositories]
        if missing:
            out.fail("R01", AREA, "Every repository in the manifest exists", "major", rule, f"Missing: {', '.join(missing)}", "Create it (adoctl apply --step 3).")
        else:
            out.ok("R01", AREA, "Every repository in the manifest exists", rule, evidence=f"{len(want)} repositories")
        if extra:
            out.warn("R05", AREA, "No repository outside the manifest", rule, f"Not in the manifest: {', '.join(sorted(extra))}",
                     "Add it to the manifest, or list it under repositories_ignore if it is not part of this project.")
        else:
            out.ok("R05", AREA, "No repository outside the manifest", rule)
        exempt = {r["name"] for r in m.repositories if r.get("naming_exempt")}  # a tooling repository shared by several projects keeps its own name
        bad = [r for r in want if not r.startswith(m.code + "-") and r not in exempt]
        if bad:
            out.fail("R06", AREA, "Repository names follow <code>-<svc>-<kind>", "minor", rule, f"Names without the prefix '{m.code}-': {', '.join(bad)}", "Rename or recreate.")
        else:
            out.ok("R06", AREA, "Repository names follow <code>-<svc>-<kind>", rule)

    guard(out, ctx, "R01", AREA, "Repository names", "Spec 4", names)

    for _mr, repo in ctx.repos_of_manifest():
        if not repo:
            continue
        sub = repo["name"]

        def branch_checks(repo=repo, sub=sub):
            br = ctx.branches(repo["id"])
            miss = [b for b in ("main", "develop") if b not in br]
            if miss:
                out.fail("R02", AREA, "main and develop exist", "major", "Spec 4, 5.1", f"Missing branch: {', '.join(miss)}", "Create it from main (adoctl apply --step 3).", sub)
            else:
                out.ok("R02", AREA, "main and develop exist", "Spec 4, 5.1", sub)
            default = (repo.get("defaultBranch") or "").replace("refs/heads/", "")
            if default != "develop":
                out.fail("R03", AREA, "The default branch is develop", "minor", "Spec 4", f"Default branch is '{default or 'none'}'.", "Set develop as the default branch.", sub)
            else:
                out.ok("R03", AREA, "The default branch is develop", "Spec 4", sub)
            missing = [f for f in FILES if not ctx.file_exists(repo["id"], f, "develop" if "develop" in br else None)]
            if missing:
                out.fail("R04", AREA, "README, .gitignore and pull request template exist", "minor", "Spec 4", f"Missing: {', '.join(missing)}", "Add the files in a pull request.", sub)
            else:
                out.ok("R04", AREA, "README, .gitignore and pull request template exist", "Spec 4", sub)

        guard(out, ctx, "R02", AREA, f"Repository {sub}", "Spec 4", branch_checks)
