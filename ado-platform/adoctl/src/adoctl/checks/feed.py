"""Artifact feed checks (spec 8 and 15)."""

from __future__ import annotations

from adoctl.checks import Ctx, guard

AREA = "Feed"
RULE = "Spec 8"
WRITE_ROLES = {"contributor", "collaborator", "owner", "administrator"}


def run(ctx: Ctx, out):
    fcfg = ctx.m.feed
    if not fcfg:
        out.na("F01", AREA, "The feed and its views exist", RULE, "The manifest has no feed section.")
        return

    def go():
        feeds = ctx.client.get(ctx.client.feeds("packaging/feeds"))["value"]
        feed = next((f for f in feeds if f["name"] == fcfg["name"]), None)
        if not feed:
            out.fail("F01", AREA, "The feed and its views exist", "major", RULE, f"No feed named {fcfg['name']}.", "Create the feed (spec 12 step 9).")
            return
        views = {v["name"] for v in ctx.client.get(ctx.client.feeds(f"packaging/feeds/{feed['id']}/views"))["value"]}
        want = set(fcfg.get("views", ["Local", "Prerelease", "Release"]))
        miss = sorted(want - views)
        if miss:
            out.fail("F01", AREA, "The feed and its views exist", "major", RULE, f"Missing views: {', '.join(miss)}", "Create the views.")
        else:
            out.ok("F01", AREA, "The feed and its views exist", RULE, evidence=f"{feed['name']}: {', '.join(sorted(views))}")

        def perms():
            ps = ctx.client.get(ctx.client.feeds(f"packaging/feeds/{feed['id']}/permissions"))["value"]
            pid = ctx.project_id()
            # the descriptor of a project build service looks like ...ServiceIdentity;<collection>:Build:<project id>; displayName is often empty
            ok = [p for p in ps if str(p.get("role", "")).lower() in WRITE_ROLES
                  and ("build service" in (p.get("displayName") or "").lower() or f":Build:{pid}" in (p.get("identityDescriptor") or ""))]
            if ok:
                out.ok("F02", AREA, "The build identity is Contributor on the feed", RULE, evidence=ok[0].get("displayName") or "project Build Service identity")
            else:
                out.fail("F02", AREA, "The build identity is Contributor on the feed", "major", RULE, "No Build Service identity holds a write role on the feed.", "Give the project's Build Service identity the Contributor role.")

        guard(out, ctx, "F02", AREA, "The build identity is Contributor on the feed", RULE, perms)

        pubs = [r["name"] for r in ctx.m.repositories if r.get("publishes")]
        if not pubs:
            out.na("F03", AREA, "The latest snapshot of each service is in @Local", RULE, "No repository in the manifest has publishes: true.")
            return
        pk = ctx.client.get(ctx.client.feeds(f"packaging/Feeds/{feed['id']}@Local/packages?includeAllVersions=false"))["value"]
        names = sorted(p["name"] for p in pk)
        if len(pk) < len(pubs):
            out.fail("F03", AREA, "The latest snapshot of each service is in @Local", "minor", RULE,
                     f"{len(pk)} package(s) in @Local ({', '.join(names) or 'none'}) for {len(pubs)} publishing repositories ({', '.join(pubs)}).",
                     "Run the ci pipeline on develop for the repositories that do not publish yet.")
        else:
            out.ok("F03", AREA, "The latest snapshot of each service is in @Local", RULE, evidence=f"{len(pk)} packages: {', '.join(names)}")

    guard(out, ctx, "F01", AREA, "The feed", RULE, go)
