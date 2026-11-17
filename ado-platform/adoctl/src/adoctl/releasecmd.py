"""adoctl release dry-run: create a release of a cd-* definition, only while its variable dryRun is 'true'.

This tool never sets dryRun to false and never creates a release of a definition whose dryRun is not 'true'.
The release still waits for the named approver; nothing deploys until a person approves.
"""

from __future__ import annotations

from adoctl.client import Client


class ReleaseRefused(Exception):
    pass


def dry_run(c: Client, definition: str, do: bool) -> list[str]:
    defs = [d for d in c.paged(c.vsrm("release/definitions?$top=200")) if d["name"] == definition]
    if not defs:
        raise ReleaseRefused(f"No release definition named '{definition}'.")
    d = c.get(c.vsrm(f"release/definitions/{defs[0]['id']}"))
    v = (d.get("variables") or {}).get("dryRun")
    if v is None or str(v.get("value", "")).lower() != "true":
        raise ReleaseRefused(f"Refusing: dryRun on '{definition}' is '{(v or {}).get('value', 'not defined')}', not 'true'. adoctl only creates dry-run releases.")
    if d.get("triggers"):
        raise ReleaseRefused(f"Refusing: '{definition}' has a trigger; fix the definition first (a release must start by a person).")
    msgs = [f"create a dry-run release of '{definition}' (dryRun=true); it waits for the named approver before the first environment"]
    if not do:
        return msgs
    versions = c.get(c.vsrm(f"release/artifacts/versions?releaseDefinitionId={d['id']}")).get("artifactVersions", [])
    artifacts = []
    for a in versions:
        dv = a.get("defaultVersion") or (a.get("versions") or [{}])[0]
        if dv.get("id"):
            artifacts.append({"alias": a["alias"], "instanceReference": {"id": dv["id"], "name": dv.get("name", dv["id"])}})
    r = c.request("POST", c.vsrm("release/releases"), {"definitionId": d["id"], "description": "adoctl dry run (dryRun=true)", "isDraft": False, "reason": "manual", "artifacts": artifacts})
    msgs.append(f"created release {r.get('name')} (id {r.get('id')}); approve it in Azure DevOps to run the dry run")
    return msgs
