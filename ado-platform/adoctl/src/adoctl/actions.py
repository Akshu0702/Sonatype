"""Actions the scheduled audit pipeline uses around `audit`: fetch the previous report, publish the report to the wiki, open Bugs for new critical drift."""

from __future__ import annotations

import io
import json
import urllib.parse
import zipfile

from adoctl.client import ApiError, Client
from adoctl.manifest import Manifest


def fetch_previous(c: Client, definition_id: int, artifact: str) -> bytes | None:
    """The report.json of the last successful run of this pipeline, or None."""
    # A run that skipped itself (the off week of a fortnightly schedule) also succeeds but has no artifact, so look back through recent runs.
    builds = c.get(c.proj(f"build/builds?definitions={definition_id}&resultFilter=succeeded&statusFilter=completed&queryOrder=finishTimeDescending&$top=10")).get("value", [])
    for b in builds:
        try:
            raw = c.get_bytes(c.proj(f"build/builds/{b['id']}/artifacts?artifactName={urllib.parse.quote(artifact)}&$format=zip"))
        except ApiError as e:
            if e.status == 404:
                continue
            raise
        z = zipfile.ZipFile(io.BytesIO(raw))
        name = next((n for n in z.namelist() if n.endswith("report.json")), None)
        if name:
            return z.read(name)
    return None


def publish_wiki(c: Client, markdown: str, page: str, do: bool) -> list[str]:
    path = "/" + page.strip("/")
    msgs = [f"publish the report to wiki page {path} ({len(markdown)} characters)"]
    if not do:
        return msgs
    ws = c.get(c.proj("wiki/wikis"))["value"]
    w = next((x for x in ws if x.get("type") == "projectWiki"), ws[0])
    base = c.proj(f"wiki/wikis/{w['id']}/pages?path={urllib.parse.quote(path)}")
    status, headers, _ = c.raw("GET", base)
    headers = {k.lower(): v for k, v in headers.items()}
    extra = {"If-Match": headers["etag"]} if status == 200 and headers.get("etag") else {}
    c.request("PUT", base, {"content": markdown}, ok=(200, 201), extra_headers=extra)
    msgs.append("published")
    return msgs


def _tag(r: dict) -> str:
    return f"[audit:{r['id']}|{r['subject']}]"


def open_bugs(c: Client, m: Manifest, report: dict, do: bool) -> list[str]:
    """One Bug per critical failed check, deduplicated by check id and subject (an open Bug carrying the tag is reused)."""
    msgs = []
    crit = [r for r in report["results"] if r["status"] == "fail" and r["severity"] == "critical"]
    for r in crit:
        tag = _tag(r).replace("'", "''")
        q = f"SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] = 'Bug' AND [System.State] <> 'Closed' AND [System.State] <> 'Removed' AND [System.Title] CONTAINS '{tag}'"
        existing = c.request("POST", c.proj("wit/wiql"), {"query": q}).get("workItems", [])
        if existing:
            msgs.append(f"{_tag(r)} already has open Bug #{existing[0]['id']}")
            continue
        msgs.append(f"open a Bug for {_tag(r)} {r['title']}")
        if do:
            ops = [
                {"op": "add", "path": "/fields/System.Title", "value": f"{_tag(r)} {r['title']} ({r['subject']})"},
                {"op": "add", "path": "/fields/System.AreaPath", "value": f"{m.name}\\Delivery"},
                {"op": "add", "path": "/fields/System.Tags", "value": "process-audit"},
                {"op": "add", "path": "/fields/Microsoft.VSTS.TCM.ReproSteps", "value": f"<p>{r['evidence']}</p><p><b>Fix:</b> {r['fix']}</p><p>Rule: {r['rule']}. Found by adoctl audit on {report['generated']}.</p>"},
            ]
            c.request("POST", c.proj("wit/workitems/$Bug"), ops, content_type="application/json-patch+json")
    if not crit:
        msgs.append("no critical drift")
    return msgs


def load_json(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)
