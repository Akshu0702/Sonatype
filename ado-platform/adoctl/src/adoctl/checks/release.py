"""Release management checks (spec 9 and 15)."""

from __future__ import annotations

import re

from adoctl.checks import Ctx, guard

AREA = "Release"
RULE = "Spec 9.2"
GIT_NS = "2e9eb7ed-3c0a-47d4-87c1-0ffdd275fd87"
FORCE_PUSH = 8  # 'Force push (rewrite history, delete branches and tags)'
SEMVER_TAG = re.compile(r"^refs/tags/v(\d+\.\d+\.\d+)$")


def run(ctx: Ctx, out):
    code = ctx.m.code

    def definitions():
        defs = [d for d in ctx.release_defs() if d["name"].startswith("cd-")]
        if not defs:
            out.na("RL01", AREA, "Release pipelines have no trigger, a named approver and dryRun", RULE, "No cd-* release definition exists yet.")
            return
        for d in defs:
            sub = d["name"]
            triggers = [t for t in d.get("triggers", []) if t.get("triggerType") not in (None, "undefined")]
            if triggers:
                out.fail("RL01", AREA, "No trigger", "critical", RULE, f"Trigger(s): {', '.join(sorted({t.get('triggerType', '?') for t in triggers}))}. A release must start by a person.", "Remove the trigger unless the delivery lead approved auto-deploy in writing.", sub)
            else:
                out.ok("RL01", AREA, "No trigger", RULE, sub)
            envs = d.get("environments", [])
            unapproved = []
            for e in envs[:1]:  # the first environment needs a named approver
                ap = (e.get("preDeployApprovals") or {}).get("approvals", [])
                if not any((not a.get("isAutomated")) and (a.get("approver") or {}).get("id") for a in ap):
                    unapproved.append(e["name"])
            if not envs:
                out.fail("RL02", AREA, "A named approver before the first environment", "critical", RULE, "The definition has no environment.", "Add the first environment with an approver.", sub)
            elif unapproved:
                out.fail("RL02", AREA, "A named approver before the first environment", "critical", RULE, f"Environment {', '.join(unapproved)} has no named approver (automated or a group).", "Add a named person as the pre-deployment approver.", sub)
            else:
                out.ok("RL02", AREA, "A named approver before the first environment", RULE, sub)
            v = d.get("variables", {}).get("dryRun")
            if v is None:
                out.fail("RL03", AREA, "The variable dryRun is defined", "major", RULE, "No dryRun variable.", "Add dryRun=true.", sub)
            elif str(v.get("value", "")).lower() != "true":
                out.warn("RL03", AREA, "dryRun is true", RULE, f"dryRun is '{v.get('value')}': real deployments are enabled. Check that the go-ahead is recorded on the Board.", "Record the delivery lead's go-ahead, or set dryRun back to true.", sub, severity="major")
            else:
                out.ok("RL03", AREA, "dryRun is true", RULE, sub)

    guard(out, ctx, "RL01", AREA, "Release definitions", RULE, definitions)

    infra = ctx.repo(f"{code}-infra")
    repos = [r for _m, r in ctx.repos_of_manifest() if r]

    def promote():
        rel = [r["name"] for r in repos if any(b.startswith("release/") for b in ctx.branches(r["id"]))]
        if not rel:
            out.na("RL04", AREA, "A promote pipeline exists once a release branch exists", "Spec 9.1", "No release/* branch exists.")
            return
        names = {d["name"] for d in ctx.build_defs()}
        if f"promote-{code}-release" in names:
            out.ok("RL04", AREA, "A promote pipeline exists once a release branch exists", "Spec 9.1", evidence=f"release branches in {', '.join(rel)}")
        else:
            out.fail("RL04", AREA, "A promote pipeline exists once a release branch exists", "major", "Spec 9.1", f"release/* exists in {', '.join(rel)} but promote-{code}-release does not.", "Create the promote pipeline.")

    guard(out, ctx, "RL04", AREA, "Promote pipeline", "Spec 9.1", promote)

    def tags():
        tagged = []
        for r in repos:
            refs = ctx.client.get(ctx.client.proj(f"git/repositories/{r['id']}/refs?filter=tags/v"))["value"]
            if refs:
                tagged.append((r, [x["name"] for x in refs]))
        if not tagged:
            out.na("RL05", AREA, "v* tags are protected", "Spec 9.3", "No v* tag exists yet.")
            out.na("RL06", AREA, "releases/X.Y.Z.yml exists for each released version", "Spec 9.3", "No release tag exists yet.")
            return
        pid = ctx.project_id()
        for r, _names in tagged:
            tok = f"repoV2/{pid}/{r['id']}/refs/tags/"
            acl = ctx.client.get(ctx.client.org(f"accesscontrollists/{GIT_NS}?token={tok}&includeExtendedInfo=false&recurse=false")).get("value", [])
            denied = any(int(a.get("deny", 0)) & FORCE_PUSH for e in acl for a in (e.get("acesDictionary") or {}).values())
            if denied:
                out.ok("RL05", AREA, "v* tags are protected", "Spec 9.3", r["name"])
            else:
                out.fail("RL05", AREA, "v* tags are protected", "major", "Spec 9.3", "Nobody is denied 'Force push' on refs/tags, so a tag can be moved or deleted.", "Deny Force push on the tags folder for Contributors and the build identity.", r["name"])
        if infra:
            released = sorted({m.group(1) for _r, ns in tagged for n in ns if (m := SEMVER_TAG.match(n))})
            if not released:
                out.na("RL06", AREA, "releases/X.Y.Z.yml exists for each released version", "Spec 9.3", "Only release-candidate tags exist.")
            else:
                miss = [v for v in released if not ctx.file_exists(infra["id"], f"/releases/{v}.yml", "develop")]
                if miss:
                    out.fail("RL06", AREA, "releases/X.Y.Z.yml exists for each released version", "major", "Spec 9.3", f"Missing manifests: {', '.join(miss)}", "Add releases/X.Y.Z.yml to the infrastructure repository.")
                else:
                    out.ok("RL06", AREA, "releases/X.Y.Z.yml exists for each released version", "Spec 9.3", evidence=f"{len(released)} releases")

    guard(out, ctx, "RL05", AREA, "Tags", "Spec 9.3", tags)
