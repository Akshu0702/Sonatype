"""Stack checks: pinned versions in the manifest match the build files (spec 2.2 and 15)."""

from __future__ import annotations

import json
import re

from adoctl.checks import Ctx, guard

AREA = "Stack"
RULE = "Spec 2.2"


def run(ctx: Ctx, out):
    pinned = (ctx.m.stack or {}).get("pinned") or {}
    if not pinned:
        out.na("ST01", AREA, "Pinned versions match the build files", RULE, "The manifest pins no versions (stack.pinned is empty).")
        return
    for mr, repo in ctx.repos_of_manifest():
        if not repo or mr.get("kind") not in ("api", "library", "ui"):
            continue
        sub = repo["name"]

        def per_repo(mr=mr, repo=repo, sub=sub):
            problems, checked = [], 0
            if mr["kind"] in ("api", "library") and ("java" in pinned or "spring_boot" in pinned):
                pom = ctx.file_text(repo["id"], "/pom.xml", "develop")
                if pom is None:
                    out.na("ST01", AREA, "Pinned versions match the build files", RULE, "No pom.xml on develop (the repository has no build yet).", sub)
                    return
                if "java" in pinned:
                    m = re.search(r"<(?:java\.version|maven\.compiler\.release|release)>\s*(\d+)\s*<", pom)
                    checked += 1
                    if not m or m.group(1) != str(pinned["java"]):
                        problems.append(f"java is {m.group(1) if m else 'not set'}, pinned {pinned['java']}")
                if "spring_boot" in pinned and mr["kind"] == "api":
                    m = re.search(r"spring-boot-starter-parent</artifactId>\s*<version>([^<]+)<", pom)
                    checked += 1
                    if not m or m.group(1).strip() != str(pinned["spring_boot"]):
                        problems.append(f"spring boot is {m.group(1).strip() if m else 'not set'}, pinned {pinned['spring_boot']}")
            if mr["kind"] == "ui" and "node" in pinned:
                pj = ctx.file_text(repo["id"], "/package.json", "develop")
                if pj is None:
                    out.na("ST01", AREA, "Pinned versions match the build files", RULE, "No package.json on develop.", sub)
                    return
                eng = (json.loads(pj).get("engines") or {}).get("node", "")
                checked += 1
                if str(pinned["node"]) not in eng:
                    problems.append(f"engines.node is '{eng or 'not set'}', pinned {pinned['node']}")
            if not checked:
                out.na("ST01", AREA, "Pinned versions match the build files", RULE, "No pinned version applies to this repository.", sub)
            elif problems:
                out.fail("ST01", AREA, "Pinned versions match the build files", "major", RULE, "; ".join(problems), "Align the build file with the manifest, or change the pin in a reviewed decision.", sub)
            else:
                out.ok("ST01", AREA, "Pinned versions match the build files", RULE, sub)

        guard(out, ctx, "ST01", AREA, f"Stack of {sub}", RULE, per_repo)
