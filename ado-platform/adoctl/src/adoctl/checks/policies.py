"""Branch policy checks on main and develop of every repository (spec 5.2)."""

from __future__ import annotations

from adoctl.checks import Ctx, guard

AREA = "Policies"
RULE = "Spec 5.2"

T_MIN = "Minimum number of reviewers"
T_COMMENT = "Comment requirements"
T_WORKITEM = "Work item linking"
T_BUILD = "Build"
T_MERGE = "Require a merge strategy"
T_SIZE = "File size restriction"


def applies(cfg: dict, repo_id: str, branch: str, default_branch: str) -> bool:
    for s in cfg.get("settings", {}).get("scope", []):
        rid = s.get("repositoryId")
        if rid not in (None, repo_id):
            continue
        ref, kind = s.get("refName"), s.get("matchKind", "Exact")
        if kind == "DefaultBranch":
            if branch == default_branch:
                return True
        elif ref is None:  # repository-wide (for example the file size limit): not a branch policy
            continue
        elif kind == "Prefix":
            if f"refs/heads/{branch}".startswith(ref or ""):
                return True
        elif ref == f"refs/heads/{branch}":
            return True
    return False


def find(ctx: Ctx, type_name: str, repo: dict, branch: str):
    default = (repo.get("defaultBranch") or "").replace("refs/heads/", "")
    return [p for p in ctx.policies() if p.get("type", {}).get("displayName") == type_name and applies(p, repo["id"], branch, default)]


def run(ctx: Ctx, out):
    pol = ctx.m.policies
    for mr, repo in ctx.repos_of_manifest():
        if not repo:
            continue
        sub_repo = repo["name"]

        def per_repo(repo=repo, sub_repo=sub_repo, mr=mr):
            ci = ctx.ci_pipeline_for(repo)
            for branch in ("main", "develop"):
                sub = f"{sub_repo}:{branch}"
                # minimum reviewers
                found = find(ctx, T_MIN, repo, branch)
                want_n = int(pol.get("min_reviewers", 1))
                if not found:
                    out.fail("P01", AREA, "Minimum reviewers policy", "major", RULE, "No minimum-reviewers policy applies.", f"Add a blocking policy with at least {want_n} reviewer.", sub)
                else:
                    s = found[0]["settings"]
                    problems = []
                    if int(s.get("minimumApproverCount", 0)) < want_n:
                        problems.append(f"requires {s.get('minimumApproverCount')} reviewer(s), manifest says {want_n}")
                    if not found[0].get("isBlocking"):
                        problems.append("not blocking")
                    if bool(s.get("creatorVoteCounts")) != bool(pol.get("author_can_approve", False)):
                        problems.append(f"creatorVoteCounts is {bool(s.get('creatorVoteCounts'))}, manifest says author_can_approve {bool(pol.get('author_can_approve', False))}")
                    if pol.get("reset_votes_on_push", True) and not s.get("resetOnSourcePush"):
                        problems.append("votes are not reset on a new push")
                    if problems:
                        out.fail("P01", AREA, "Minimum reviewers policy", "major", RULE, "; ".join(problems), "Align the policy with the manifest, or record the relaxation in the manifest and the wiki.", sub)
                    else:
                        out.ok("P01", AREA, "Minimum reviewers policy", RULE, sub)
                # comment resolution, work item linking
                for pid, tname, title in (("P02", T_COMMENT, "Comment resolution is required"), ("P03", T_WORKITEM, "A linked work item is required")):
                    f = find(ctx, tname, repo, branch)
                    if not f:
                        out.fail(pid, AREA, title, "major", RULE, f"No '{tname}' policy applies.", "Add the policy as blocking.", sub)
                    elif not f[0].get("isBlocking"):
                        out.fail(pid, AREA, title, "major", RULE, "The policy exists but is not blocking.", "Make it blocking.", sub)
                    else:
                        out.ok(pid, AREA, title, RULE, sub)
                # build validation, wherever a ci pipeline exists
                if not ci:
                    out.na("P04", AREA, "Build validation uses the repository's ci pipeline", RULE, "No ci-* pipeline exists for this repository yet.", sub)
                else:
                    f = [p for p in find(ctx, T_BUILD, repo, branch) if p["settings"].get("buildDefinitionId") == ci["id"]]
                    if not f:
                        out.fail("P04", AREA, "Build validation uses the repository's ci pipeline", "major", RULE, f"Pipeline '{ci['name']}' exists but is not a build validation on this branch.", "Add build validation.", sub)
                    elif not f[0].get("isBlocking"):
                        out.fail("P04", AREA, "Build validation uses the repository's ci pipeline", "major", RULE, "Build validation is not blocking.", "Make it required.", sub)
                    else:
                        out.ok("P04", AREA, "Build validation uses the repository's ci pipeline", RULE, sub)
                # merge strategy
                f = find(ctx, T_MERGE, repo, branch)
                if not f:
                    out.fail("P05", AREA, "Merge strategy is restricted", "major", RULE, "No merge-strategy policy applies.", "Add the policy.", sub)
                else:
                    s = f[0]["settings"]
                    allowed = {k: bool(s.get(k)) for k in ("allowSquash", "allowNoFastForward", "allowRebase", "allowRebaseMerge")}
                    want = {"allowSquash": True, "allowNoFastForward": False, "allowRebase": False, "allowRebaseMerge": False} if branch == "develop" \
                        else {"allowSquash": False, "allowNoFastForward": True, "allowRebase": False, "allowRebaseMerge": False}
                    if allowed != want:
                        out.fail("P05", AREA, "Merge strategy is restricted", "major", RULE, f"Allowed: {[k for k, v in allowed.items() if v]}, expected: {[k for k, v in want.items() if v]}", "Set squash only on develop and merge commit only on main.", sub)
                    else:
                        out.ok("P05", AREA, "Merge strategy is restricted", RULE, sub)
            # file size, repository level
            maxb = int(pol.get("max_file_size_mb", 10)) * 1024 * 1024
            sizes = [p for p in ctx.policies() if p.get("type", {}).get("displayName") == T_SIZE
                     and any(s.get("repositoryId") in (None, repo["id"]) for s in p["settings"].get("scope", []))]
            if not sizes:
                out.fail("P06", AREA, "File size limit", "minor", RULE, "No file-size policy applies to the repository.", f"Add a {pol.get('max_file_size_mb', 10)} MB limit.", sub_repo)
            elif int(sizes[0]["settings"].get("maximumGitBlobSizeInBytes", 0)) > maxb:
                out.fail("P06", AREA, "File size limit", "minor", RULE, "The limit is larger than the manifest allows.", "Lower it.", sub_repo)
            else:
                out.ok("P06", AREA, "File size limit", RULE, sub_repo)

        guard(out, ctx, "P01", AREA, f"Policies of {sub_repo}", RULE, per_repo)
