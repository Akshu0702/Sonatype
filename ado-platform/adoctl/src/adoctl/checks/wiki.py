"""Wiki checks (spec 6 and 15). The wiki is a git repository; one zip download reads all pages."""

from __future__ import annotations

import io
import re
import zipfile

from adoctl.checks import Ctx, guard

AREA = "Wiki"
RULE = "Spec 6"
TOP = ["Home", "Architecture", "Services", "API-Reference", "Engineering", "Operations", "Decisions", "Roadmap"]
ENGINEERING = ["Work-Management", "Repositories-and-Naming", "Branching-and-Release", "CI-CD-Plan", "Artifact-Feed-and-Tagging",
               "Testing-Strategy", "Local-Development", "Documentation-Standards", "Pull-request-approval"]
STATUS = re.compile(r"\b(Built|Partly|Not built)\b|\bStatus\b", re.I)


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower().replace(".md", ""))


def repo_zip(ctx: Ctx, repo_id: str, branch: str | None = None) -> dict[str, bytes]:
    v = f"&versionDescriptor.version={branch}" if branch else ""

    def load():
        raw = ctx.client.get_bytes(ctx.client.proj(f"git/repositories/{repo_id}/items?scopePath=/&$format=zip&recursionLevel=full&download=true{v}"))
        z = zipfile.ZipFile(io.BytesIO(raw))
        return {n.lstrip("/"): z.read(n) for n in z.namelist() if not n.endswith("/")}

    return ctx.cached(("zip", repo_id, branch), load)


def wiki_pages(ctx: Ctx) -> dict[str, str] | None:
    def load():
        ws = ctx.client.get(ctx.client.proj("wiki/wikis"))["value"]
        w = next((x for x in ws if x.get("type") == "projectWiki"), ws[0] if ws else None)
        if not w:
            return None
        files = repo_zip(ctx, w["repositoryId"], (w.get("versions") or [{}])[0].get("version"))
        return {p: b.decode("utf-8", "replace") for p, b in files.items() if p.endswith(".md")}

    return ctx.cached("wiki", load)


def run(ctx: Ctx, out):
    def go():
        pages = wiki_pages(ctx)
        if pages is None:
            out.fail("W01", AREA, "The wiki skeleton exists", "major", RULE, "The project has no wiki.", "Create the project wiki.")
            return
        top = {norm(p) for p in pages if "/" not in p}
        miss = [t for t in TOP if norm(t) not in top]
        if miss:
            out.fail("W01", AREA, "The wiki skeleton exists", "major", RULE, f"Missing top-level pages: {', '.join(miss)}", "Create them from the skeleton in spec section 6.")
        else:
            out.ok("W01", AREA, "The wiki skeleton exists", RULE, evidence=f"{len(pages)} pages")
        eng = {norm(p.split("/", 1)[1]) for p in pages if p.lower().startswith("engineering/")}
        miss = [e for e in ENGINEERING if norm(e) not in eng]
        if miss:
            out.fail("W02", AREA, "The engineering pages exist", "major", RULE, f"Missing under Engineering: {', '.join(miss)}", "Write them from this spec.")
        else:
            out.ok("W02", AREA, "The engineering pages exist", RULE)
        content = {p: t for p, t in pages.items() if not p.lower().startswith("decisions/") and p.lower() not in ("home.md",)}
        nolabel = sorted(p for p, t in content.items() if not STATUS.search(t))
        if nolabel:
            out.warn("W03", AREA, "Pages carry a status label (Built, Partly, Not built)", RULE, f"{len(nolabel)} of {len(content)} pages without one: {', '.join(nolabel[:12])}{' ...' if len(nolabel) > 12 else ''}", "Add a status line to capability pages.")
        else:
            out.ok("W03", AREA, "Pages carry a status label (Built, Partly, Not built)", RULE, evidence=f"{len(content)} pages")
        ds = next((t for p, t in pages.items() if norm(p).endswith("documentationstandards")), "")
        if re.search(r"\bowner", ds, re.I):
            out.ok("W04", AREA, "Section owners are named", RULE)
        else:
            out.warn("W04", AREA, "Section owners are named", RULE, "No ownership table found in Documentation-Standards.", "Add the owners table (spec section 6).")
        risk = [p for p in pages if "risk" in p.lower()]
        if risk:
            out.ok("W05", "Security", "An accepted-risk page exists", "Spec 11", evidence=risk[0])
        else:
            out.warn("W05", "Security", "An accepted-risk page exists", "Spec 11", "No wiki page about accepted risks was found.", "Create the accepted-risk register page with owners and dates.")

    guard(out, ctx, "W01", AREA, "Wiki", RULE, go)
