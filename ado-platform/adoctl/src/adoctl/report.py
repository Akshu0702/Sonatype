"""Turns audit results into the JSON report and the Markdown report (overall status, per-area table, drift list, trend)."""

from __future__ import annotations

import datetime as dt

from adoctl import __version__
from adoctl.model import OVERALL_RULE, SEVERITY_ORDER, Result, counts, overall

AREA_ORDER = ["Boards", "Repositories", "Policies", "Pull requests", "CI", "Feed", "Release", "Wiki", "Security", "Stack"]


def build(results: list[Result], project: str, previous: dict | None = None, now: dt.datetime | None = None) -> dict:
    now = now or dt.datetime.now(dt.timezone.utc)
    rep = {
        "tool": f"adoctl {__version__}",
        "generated": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "project": project,
        "overall": overall(results),
        "rule": OVERALL_RULE,
        "counts": counts(results),
        "results": [r.to_dict() for r in results],
    }
    if previous:
        rep["trend"] = trend(rep, previous)
    return rep


def _failing(rep: dict) -> dict:
    return {f"{r['id']}|{r['subject']}": r for r in rep.get("results", []) if r["status"] == "fail"}


def trend(rep: dict, prev: dict) -> dict:
    now, before = _failing(rep), _failing(prev)
    pc, nc = prev.get("counts", {}), rep["counts"]
    return {
        "previous_generated": prev.get("generated"),
        "previous_overall": prev.get("overall"),
        "overall_changed": prev.get("overall") != rep["overall"],
        "failed_now": len(now),
        "failed_before": len(before),
        "delta_failed": len(now) - len(before),
        "delta_critical": nc["critical_fail"] - pc.get("critical_fail", 0),
        "delta_major": nc["major_fail"] - pc.get("major_fail", 0),
        "new": sorted(set(now) - set(before)),
        "resolved": sorted(set(before) - set(now)),
    }


def drift(rep: dict) -> list[dict]:
    """Failures and warnings, most severe first."""
    items = [r for r in rep["results"] if r["status"] in ("fail", "warn")]
    return sorted(items, key=lambda r: (r["status"] != "fail", SEVERITY_ORDER[r["severity"]], r["id"], r["subject"]))


def per_area(rep: dict) -> list[dict]:
    areas: dict[str, dict] = {}
    for r in rep["results"]:
        a = areas.setdefault(r["area"], {"area": r["area"], "pass": 0, "warn": 0, "fail": 0, "not-applicable": 0})
        a[r["status"]] += 1
    order = {n: i for i, n in enumerate(AREA_ORDER)}
    return sorted(areas.values(), key=lambda a: order.get(a["area"], 99))


def _cell(s: str, n: int = 260) -> str:
    s = " ".join(str(s).split()).replace("|", "\\|")
    return s if len(s) <= n else s[: n - 1] + "…"


def markdown(rep: dict) -> str:
    c = rep["counts"]
    out = [f"# Process audit: {rep['project']}", "", f"Generated {rep['generated']} by {rep['tool']}.", ""]
    badge = {"Green": "GREEN", "Amber": "AMBER", "Red": "RED"}[rep["overall"]]
    out += [f"## Overall status: {badge}", "", f"*Rule.* {rep['rule']}", ""]
    out += [
        f"Checks run: {sum(c[s] for s in ('pass', 'warn', 'fail', 'not-applicable'))}. Passed {c['pass']}, warnings {c['warn']}, failed {c['fail']} "
        f"(critical {c['critical_fail']}, major {c['major_fail']}, minor {c['minor_fail']}), not applicable {c['not-applicable']}, "
        f"not covered or could not run {c['blind']}.",
        "",
    ]
    if "trend" in rep:
        t = rep["trend"]
        arrow = "unchanged" if not t["overall_changed"] else f"was {t['previous_overall']}"
        out += [
            "## Trend",
            "",
            f"Compared with the report of {t['previous_generated']}: overall status {arrow}; failed checks {t['failed_before']} to {t['failed_now']} "
            f"({t['delta_failed']:+d}); critical {t['delta_critical']:+d}; major {t['delta_major']:+d}. "
            f"New drift: {len(t['new'])}. Resolved: {len(t['resolved'])}.",
            "",
        ]
        if t["new"]:
            out += ["New: " + ", ".join(f"`{k}`" for k in t["new"][:25]) + (" …" if len(t["new"]) > 25 else ""), ""]
        if t["resolved"]:
            out += ["Resolved: " + ", ".join(f"`{k}`" for k in t["resolved"][:25]) + (" …" if len(t["resolved"]) > 25 else ""), ""]
    out += ["## By area", "", "| Area | Pass | Warn | Fail | Not applicable |", "|---|---|---|---|---|"]
    for a in per_area(rep):
        out.append(f"| {a['area']} | {a['pass']} | {a['warn']} | {a['fail']} | {a['not-applicable']} |")
    out += ["", "## Drift (most severe first)", ""]
    d = drift(rep)
    if not d:
        out += ["No drift found.", ""]
    else:
        out += ["| # | Severity | Check | Where | What was found | How to fix | Rule |", "|---|---|---|---|---|---|---|"]
        for i, r in enumerate(d, 1):
            sev = r["severity"] if r["status"] == "fail" else f"{r['severity']} (warning)"
            out.append(f"| {i} | {sev} | {r['id']} {_cell(r['title'], 80)} | {_cell(r['subject'], 60)} | {_cell(r['evidence'])} | {_cell(r['fix'], 200)} | {r['rule']} |")
        out.append("")
    blind = [r for r in rep["results"] if r.get("blind")]
    if blind:
        out += ["## Not covered", "",
                "These checks did not run, so the report says nothing about them (they are neither passes nor failures). Fix the identity's read access, then run again.", ""]
        out += [f"- {r['id']} {r['title']}: {_cell(r['evidence'], 240)}" for r in blind]
        out.append("")
    na = [r for r in rep["results"] if r["status"] == "not-applicable"]
    if na:
        out += ["## Not applicable", ""] + [f"- {r['id']} {r['title']} ({r['subject']}): {_cell(r['evidence'], 160)}" for r in na] + [""]
    return "\n".join(out)
