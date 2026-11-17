#!/usr/bin/env python3
"""Creates and authorises the pipelines of the ado-platform repository AFTER its pull requests are merged.

  create_audit_pipeline.py --manifest manifests/sonatype-mls.yml            # prints what it would do
  create_audit_pipeline.py --manifest manifests/sonatype-mls.yml --apply    # does it

Creates (if missing): ci-ado-platform (pipelines/ci-ado-platform.yml) and audit-<code>-fortnightly (pipelines/audit-fortnightly.yml).
Authorises them to use the agent pool and, if --service-connection is given, that Azure service connection.
Needs an identity that may create pipelines and manage pipeline permissions (the setup identity or the DevOps engineer), never the audit
identity. The credential comes from the environment (ADO_TOKEN or ADO_PAT) and is never printed. It never deletes anything and it does
not add build validation to branches (that is a separate, reviewed step: see the PR description).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "adoctl", "src"))
from adoctl.client import ApiError, Client, token_from_env  # noqa: E402
from adoctl.manifest import Manifest  # noqa: E402

REPO = "ado-platform"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--pool", help="agent pool name (default: the manifest's agents.pool)")
    ap.add_argument("--service-connection", help="Azure service connection of the read-only audit identity, e.g. mls-ado-audit-sc")
    ap.add_argument("--apply", action="store_true")
    a = ap.parse_args()
    m = Manifest.load(a.manifest)
    tok, scheme = token_from_env()
    c = Client(m.organisation, m.name, tok, scheme)
    do = a.apply
    say = lambda s: print(c.scrub(s))  # noqa: E731
    say(("APPLY" if do else "PLAN (nothing is changed; add --apply)") + f" for project {m.name}")

    repo = next((r for r in c.get(c.proj("git/repositories"))["value"] if r["name"] == REPO), None)
    if not repo:
        say(f"The repository {REPO} does not exist. Stop.")
        return 2
    if not c.get_or_none(c.proj(f"git/repositories/{repo['id']}/items?path=/pipelines/audit-fortnightly.yml&versionDescriptor.version=develop&$format=json")):
        say("pipelines/audit-fortnightly.yml is not on develop yet: merge the pull requests first. Stop.")
        return 2
    existing = {p["name"]: p for p in c.paged(c.proj("pipelines?$top=200"))}
    pool = a.pool or m.data.get("agents", {}).get("pool")
    queues = [q for q in c.get(c.proj("distributedtask/queues"))["value"] if q["name"] == pool]
    wanted = {"ci-ado-platform": "/pipelines/ci-ado-platform.yml", f"audit-{m.code}-fortnightly": "/pipelines/audit-fortnightly.yml"}
    created = {}
    for name, path in wanted.items():
        if name in existing:
            say(f"pipeline {name} exists (id {existing[name]['id']}); leaving it")
            created[name] = existing[name]["id"]
            continue
        say(f"create pipeline {name} from {path} on develop")
        if do:
            p = c.request("POST", c.proj("pipelines"), {"name": name, "folder": "\\", "configuration": {"type": "yaml", "path": path, "repository": {"id": repo["id"], "type": "azureReposGit"}}})
            created[name] = p["id"]
    if not queues:
        say(f"Agent pool '{pool}' was not found; authorise it by hand.")
    elif do:
        for name, pid in created.items():
            c.request("PATCH", c.proj(f"pipelines/pipelinepermissions/queue/{queues[0]['id']}"), {"pipelines": [{"id": pid, "authorized": True}]})
            say(f"authorised {name} to use pool {pool}")
    else:
        say(f"authorise both pipelines to use pool {pool}")
    if a.service_connection:
        ep = next((e for e in c.get(c.proj("serviceendpoint/endpoints?endpointNames=" + a.service_connection))["value"]), None)
        if not ep:
            say(f"Service connection '{a.service_connection}' was not found. Create it first (runbook Phase 0A, A5).")
        else:
            aud = created.get(f"audit-{m.code}-fortnightly")
            say(f"authorise ONLY audit-{m.code}-fortnightly to use service connection {a.service_connection}")
            if do and aud:
                c.request("PATCH", c.proj(f"pipelines/pipelinepermissions/endpoint/{ep['id']}"), {"pipelines": [{"id": aud, "authorized": True}]})
    say("Next, by hand: run the audit pipeline once manually with failOn=none; add build validation to develop and main for ado-platform; check the run summary.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ApiError as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(2)
