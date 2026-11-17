"""A fake Azure DevOps API for tests: a dictionary 'world' behind the same URLs adoctl calls. Records every write.

good_world() is a fully compliant project; each test breaks one thing and expects the matching check to fail.
"""

from __future__ import annotations

import copy
import io
import json
import re
import urllib.parse
import zipfile

ORG, PROJ = "Org", "Proj"
TOKEN = "T" * 12 + "-fake-token-" + "k" * 12  # not token-shaped for the scanners; unique for the never-logged test
MANIFEST = f"""
project: {{code: tst, name: {PROJ}, organisation: {ORG}, process: Agile, delivery_lead: Lead One, project_manager: Pm One, go_live: 2026-12-04}}
sprints: {{length_days: 7, first_start: 2026-10-26, count: 2}}
areas: [Infra, Api, Delivery]
repositories:
  - {{name: tst-infra, kind: infra, area: Infra}}
  - {{name: tst-svc-api, kind: api, area: Api, publishes: true}}
repositories_ignore: [{PROJ}]
policies: {{min_reviewers: 1, author_can_approve: false, reset_votes_on_push: true, merge_develop: squash, merge_main: merge-commit, max_file_size_mb: 10}}
feed: {{name: tst-maven, views: [Local, Prerelease, Release]}}
stack: {{pinned: {{java: "21", spring_boot: "4.1.2"}}}}
"""

TYPES = {"Minimum number of reviewers": "t-min", "Comment requirements": "t-com", "Work item linking": "t-wi", "Build": "t-build",
         "Require a merge strategy": "t-merge", "File size restriction": "t-size"}
POM = "<project><parent><artifactId>spring-boot-starter-parent</artifactId><version>4.1.2</version></parent><properties><java.version>21</java.version></properties></project>"
STANDARD = ["Work-Management", "Repositories-and-Naming", "Branching-and-Release", "CI-CD-Plan", "Artifact-Feed-and-Tagging", "Testing-Strategy",
            "Local-Development", "Documentation-Standards", "Pull-request-approval"]


def make_zip(files: dict[str, str | bytes]) -> bytes:
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w") as z:
        for p, c in files.items():
            z.writestr(p.lstrip("/"), c)
    return b.getvalue()


def policy(type_name, repo_id, branch, **settings):
    scope = [{"repositoryId": repo_id, "refName": f"refs/heads/{branch}", "matchKind": "Exact"}] if branch else [{"repositoryId": repo_id}]
    return {"id": 1, "isEnabled": True, "isBlocking": True, "isDeleted": False, "type": {"id": TYPES[type_name], "displayName": type_name}, "settings": {**settings, "scope": scope}}


def good_world() -> dict:
    w = {"project_id": "p-1", "areas": ["Infra", "Api", "Delivery"], "iterations": ["Sprint 1", "Sprint 2"], "writes": [], "headers": [], "next_id": 100}
    w["repos"] = [{"id": "r-infra", "name": "tst-infra", "defaultBranch": "refs/heads/develop"},
                  {"id": "r-api", "name": "tst-svc-api", "defaultBranch": "refs/heads/develop"}]  # the wiki repository is not in this list in the real API either
    base = {"/README.md": "# r", "/.gitignore": "x", "/.azuredevops/pull_request_template.md": "t"}
    w["refs"] = {"r-infra": {"refs/heads/main": "a1", "refs/heads/develop": "a2"},
                 "r-api": {"refs/heads/main": "b1", "refs/heads/develop": "b2", "refs/heads/feature/US1-T3": "b3"}, "r-wiki": {}}
    w["files"] = {"r-infra": dict(base), "r-api": {**base, "/pom.xml": POM, "/ci/pipeline.yml": "steps:\n- template: templates/ci.yml@infra\n"}, "r-wiki": {}}
    pages = {f"{p}.md": "Status: Built\n" for p in ("Home", "Architecture", "Services", "API-Reference", "Engineering", "Operations", "Decisions", "Roadmap")}
    pages.update({f"Engineering/{p}.md": ("Owner table\nStatus: Built\n" if p == "Documentation-Standards" else "Status: Built\n") for p in STANDARD})
    pages["Operations/Accepted-Risks.md"] = "Status: Built\n"
    w["files"]["r-wiki"] = pages
    pol = []
    for rid in ("r-infra", "r-api"):
        for br in ("main", "develop"):
            pol += [policy("Minimum number of reviewers", rid, br, minimumApproverCount=1, creatorVoteCounts=False, resetOnSourcePush=True, blockLastPusherVote=True),
                    policy("Comment requirements", rid, br), policy("Work item linking", rid, br),
                    policy("Require a merge strategy", rid, br, **({"allowSquash": True} if br == "develop" else {"allowNoFastForward": True}))]
        pol.append(policy("File size restriction", rid, None, maximumGitBlobSizeInBytes=10 * 1024 * 1024))
    for br in ("main", "develop"):
        pol.append(policy("Build", "r-api", br, buildDefinitionId=11))
        pol.append(policy("Build", "r-infra", br, buildDefinitionId=10))
    w["policies"] = pol
    w["builddefs"] = [{"id": 10, "name": "ci-tst-infra", "repository": {"id": "r-infra", "name": "tst-infra"}, "process": {"yamlFilename": "ci.yml"}, "variables": {}},
                      {"id": 11, "name": "ci-tst-svc-api", "repository": {"id": "r-api", "name": "tst-svc-api"}, "process": {"yamlFilename": "ci/pipeline.yml"}, "variables": {"mvnArgs": {"value": "-B"}}}]
    w["builds"] = {10: [{"id": 500, "buildNumber": "1", "result": "succeeded"}], 11: [{"id": 501, "buildNumber": "2", "result": "succeeded"}]}
    w["feed"] = {"feeds": [{"id": "f1", "name": "tst-maven"}], "views": ["Local", "Prerelease", "Release"],
                 "permissions": [{"displayName": None, "role": "contributor", "identityDescriptor": "Microsoft.TeamFoundation.ServiceIdentity;c1:Build:p-1"}], "packages": [{"name": "svc"}]}
    w["releasedefs"] = [{"id": 1, "name": "cd-tst-svc-api", "triggers": [], "variables": {"dryRun": {"value": "true"}},
                         "environments": [{"name": "dev", "preDeployApprovals": {"approvals": [{"isAutomated": False, "approver": {"id": "u1"}}]}}]}]
    w["tags"] = {}
    w["acl"] = {}
    w["prs"] = []
    w["pr_workitems"] = {}
    sid = "System."
    w["workitems"] = {
        1: {"id": 1, "fields": {sid + "WorkItemType": "User Story", sid + "State": "Active", sid + "Title": "As a dev I build things so that they ship",
                                "Microsoft.VSTS.Common.AcceptanceCriteria": "<li>x</li>", sid + "AreaPath": "Proj\\Api"},
            "relations": [{"rel": "Microsoft.VSTS.Common.TestedBy-Forward", "url": "x/2"}]},
        2: {"id": 2, "fields": {sid + "WorkItemType": "Test Case", sid + "State": "Design", sid + "Title": "tc", sid + "AreaPath": "Proj\\Api"}, "relations": []},
        3: {"id": 3, "fields": {sid + "WorkItemType": "Task", sid + "State": "Active", sid + "Title": "Add the thing", sid + "AreaPath": "Proj\\Api", sid + "IterationPath": "Proj\\Sprint 1",
                                sid + "ChangedDate": "2026-11-16T00:00:00Z"},
            "relations": [{"rel": "System.LinkTypes.Hierarchy-Reverse", "url": "https://dev.azure.com/Org/_apis/wit/workItems/1"}]},
        4: {"id": 4, "fields": {sid + "WorkItemType": "Task", sid + "State": "Closed", sid + "Title": "Done thing", sid + "AreaPath": "Proj\\Api", sid + "IterationPath": "Proj\\Sprint 1",
                                sid + "ChangedDate": "2026-11-10T00:00:00Z"},
            "relations": [{"rel": "System.LinkTypes.Hierarchy-Reverse", "url": "https://dev.azure.com/Org/_apis/wit/workItems/1"}]},
    }
    w["wikis"] = [{"id": "w1", "type": "projectWiki", "repositoryId": "r-wiki", "versions": [{"version": "wikiMaster"}]}]
    w["fail_paths"] = {}  # regex -> status, to simulate a permission error
    w["error_echo_token"] = False
    return w


class FakeAdo:
    def __init__(self, world: dict | None = None):
        self.w = world if world is not None else good_world()
        self.requests = []

    # a transport for adoctl.client.Client
    def __call__(self, method, url, headers, body):
        u = urllib.parse.urlparse(url)
        path, q = urllib.parse.unquote(u.path), urllib.parse.parse_qs(u.query)
        self.w["headers"].append(headers.get("Authorization", ""))
        self.requests.append((method, path + ("?" + u.query if u.query else "")))
        data = json.loads(body) if body and headers.get("Content-Type", "").startswith("application/json") else None
        for rx, status in self.w["fail_paths"].items():
            if re.search(rx, path):
                msg = f"denied {headers.get('Authorization', '')}" if self.w.get("error_echo_token") else "denied"
                return status, {}, json.dumps({"message": msg}).encode()
        if method != "GET":
            self.w["writes"].append((method, path, data))
        for m, rx, fn in ROUTES:
            if m == method and (mm := re.search(rx, path)):
                r = fn(self, mm, q, data, headers)
                if r is not None:
                    st, payload = r[0], r[1]
                    hdr = r[2] if len(r) > 2 else {}
                    return st, hdr, payload if isinstance(payload, bytes) else json.dumps(payload).encode()
        return 404, {}, json.dumps({"message": f"fake: no route {method} {path}"}).encode()

    def writes(self):
        return self.w["writes"]


def _lst(items):
    return 200, {"value": items, "count": len(items)}


def _children(names):
    return 200, {"name": PROJ, "children": [{"name": n} for n in names]}


def _repo(f, rid):
    return next(r for r in f.w["repos"] if r["id"] == rid)


def items(f, m, q, d, h):
    rid = m.group(1)
    files = f.w["files"].get(rid)
    if files is None:
        return 404, {"message": "no repo"}
    if q.get("$format", [""])[0] == "zip":
        return 200, make_zip({p: c for p, c in files.items()})
    p = q.get("path", ["/"])[0]
    if p in files:
        return 200, {"path": p, "content": files[p]}
    return 404, {"message": "not found"}


def wiql(f, m, q, d, h):
    text = d["query"]
    t = re.search(r"WorkItemType\] = '([^']+)'", text)
    ids = [i for i, w in f.w["workitems"].items() if (not t or w["fields"]["System.WorkItemType"] == t.group(1))]
    if "System.Title] CONTAINS" in text:
        tag = re.search(r"CONTAINS '(.+)'", text).group(1).replace("''", "'")
        ids = [i for i in ids if tag in f.w["workitems"][i]["fields"]["System.Title"] and f.w["workitems"][i]["fields"]["System.State"] != "Closed"]
    if "IterationPath] = " in text:
        ip = re.search(r"IterationPath\] = '([^']+)'", text).group(1)
        ids = [i for i in ids if f.w["workitems"][i]["fields"].get("System.IterationPath") == ip]
    return 200, {"workItems": [{"id": i} for i in ids]}


def batch(f, m, q, d, h):
    return _lst([f.w["workitems"][i] for i in d["ids"]])


def create_ref(f, m, q, d, h):
    for r in d:
        f.w["refs"][m.group(1)][r["name"]] = r["newObjectId"]
    return 200, {"value": d}


def push(f, m, q, d, h):
    rid = m.group(1)
    for ref in d["refUpdates"]:
        f.w["refs"].setdefault(rid, {})[ref["name"]] = "new-commit"
    f.w["files"].setdefault(rid, {}).update({c["item"]["path"]: c["newContent"]["content"] for cm in d["commits"] for c in cm["changes"]})
    return 201, {"refUpdates": [{"newObjectId": "new-commit"}]}


def new_repo(f, m, q, d, h):
    rid = "r-" + d["name"]
    f.w["repos"].append({"id": rid, "name": d["name"], "defaultBranch": None})
    f.w["refs"][rid] = {}
    f.w["files"][rid] = {}
    return 201, {"id": rid, "name": d["name"]}


def patch_repo(f, m, q, d, h):
    _repo(f, m.group(1)).update(d)
    return 200, {}


def add_policy(f, m, q, d, h):
    tname = next(k for k, v in TYPES.items() if v == d["type"]["id"])
    f.w["policies"].append({**d, "isDeleted": False, "type": {"id": d["type"]["id"], "displayName": tname}})
    return 200, {}


def prs(f, m, q, d, h):
    top = q.get("$top", [None])[0]
    skip = int(q.get("$skip", ["0"])[0])
    out = [{**p, "description": (p.get("description") or "")[:400]} for p in f.w["prs"]]  # the real list endpoint truncates
    return _lst(out[skip:skip + int(top)] if top else out)


def pr_detail(f, m, q, d, h):
    return 200, next(p for p in f.w["prs"] if p["pullRequestId"] == int(m.group(2)))


def builds(f, m, q, d, h):
    did = int(q.get("definitions", ["0"])[0])
    if "resultFilter" in q:
        return _lst([{"id": 901}, {"id": 900}] if f.w.get("last_good_build") else [])
    return _lst(f.w["builds"].get(did, []))


def artifacts(f, m, q, d, h):
    z = f.w.get("prev_artifact")
    bid = int(re.search(r"/builds/(\d+)/", m.string).group(1))
    return (200, z) if z and bid == 900 else (404, {"message": "none"})  # build 901 was a skipped run: no artifact


def feed_pk(f, m, q, d, h):
    return _lst(f.w["feed"]["packages"])


def acl(f, m, q, d, h):
    return _lst(f.w["acl"].get(q["token"][0], []))


def refs(f, m, q, d, h):
    rid = m.group(1)
    flt = q.get("filter", [""])[0]
    if flt.startswith("tags/"):
        return _lst([{"name": n} for n in f.w["tags"].get(rid, [])])
    return _lst([{"name": n, "objectId": o} for n, o in f.w["refs"].get(rid, {}).items() if n.startswith("refs/" + flt)])


def wiki_put(f, m, q, d, h):
    page = q["path"][0]
    existing = f.w.setdefault("wiki_pages", {})
    if page in existing and h.get("If-Match") != existing[page]:
        return 412, {"message": "needs If-Match"}
    existing[page] = "etag-2"
    return 200, {}


def wiki_get(f, m, q, d, h):
    page = q["path"][0]
    if page in f.w.get("wiki_pages", {}):
        return 200, {"path": page}, {"ETag": f.w["wiki_pages"][page]}
    return 404, {"message": "no page"}


def workitem_get(f, m, q, d, h):
    return 200, f.w["workitems"][int(m.group(1))]


def workitem_patch(f, m, q, d, h):
    return 200, {}


def new_bug(f, m, q, d, h):
    nid = f.w["next_id"]
    f.w["next_id"] += 1
    title = next(o["value"] for o in d if o["path"].endswith("Title"))
    f.w["workitems"][nid] = {"id": nid, "fields": {"System.WorkItemType": "Bug", "System.State": "New", "System.Title": title}, "relations": []}
    return 200, {"id": nid}


ROUTES = [
    ("GET", r"/_apis/projects/[^/]+$", lambda f, m, q, d, h: (200, {"id": f.w["project_id"]})),
    ("GET", r"/wit/classificationnodes/areas", lambda f, m, q, d, h: _children(f.w["areas"])),
    ("GET", r"/wit/classificationnodes/iterations", lambda f, m, q, d, h: _children(f.w["iterations"])),
    ("POST", r"/wit/classificationnodes/areas", lambda f, m, q, d, h: (f.w["areas"].append(d["name"]), (200, {}))[1]),
    ("POST", r"/wit/classificationnodes/iterations", lambda f, m, q, d, h: (f.w["iterations"].append(d["name"]), (200, {}))[1]),
    ("POST", r"/wit/wiql$", wiql),
    ("POST", r"/wit/workitemsbatch$", batch),
    ("POST", r"/wit/workitems/\$Bug$", new_bug),
    ("GET", r"/wit/workitems/(\d+)$", workitem_get),
    ("PATCH", r"/wit/workitems/(\d+)$", workitem_patch),
    ("GET", r"/git/repositories$", lambda f, m, q, d, h: _lst(f.w["repos"])),
    ("POST", r"/git/repositories$", new_repo),
    ("PATCH", r"/git/repositories/([^/]+)$", patch_repo),
    ("GET", r"/git/repositories/([^/]+)/refs$", refs),
    ("POST", r"/git/repositories/([^/]+)/refs$", create_ref),
    ("GET", r"/git/repositories/([^/]+)/items$", items),
    ("POST", r"/git/repositories/([^/]+)/pushes$", push),
    ("POST", r"/git/repositories/([^/]+)/pullrequests$", lambda f, m, q, d, h: (201, {"pullRequestId": 77})),
    ("GET", r"/git/repositories/([^/]+)/pullRequests/(\d+)/workitems$", lambda f, m, q, d, h: _lst(f.w["pr_workitems"].get(int(m.group(2)), []))),
    ("GET", r"/git/pullrequests$", prs),
    ("GET", r"/git/repositories/([^/]+)/pullRequests/(\d+)$", pr_detail),
    ("GET", r"/policy/configurations$", lambda f, m, q, d, h: _lst(f.w["policies"])),
    ("POST", r"/policy/configurations$", add_policy),
    ("GET", r"/policy/types$", lambda f, m, q, d, h: _lst([{"id": v, "displayName": k} for k, v in TYPES.items()])),
    ("GET", r"/build/definitions$", lambda f, m, q, d, h: _lst([{"id": x["id"], "name": x["name"]} for x in f.w["builddefs"]])),
    ("GET", r"/build/definitions/(\d+)$", lambda f, m, q, d, h: (200, next(x for x in f.w["builddefs"] if x["id"] == int(m.group(1))))),
    ("GET", r"/build/builds$", builds),
    ("GET", r"/build/builds/\d+/artifacts$", artifacts),
    ("GET", r"/packaging/feeds$", lambda f, m, q, d, h: _lst(f.w["feed"]["feeds"])),
    ("GET", r"/packaging/feeds/[^/]+/views$", lambda f, m, q, d, h: _lst([{"name": n} for n in f.w["feed"]["views"]])),
    ("GET", r"/packaging/feeds/[^/]+/permissions$", lambda f, m, q, d, h: _lst(f.w["feed"]["permissions"])),
    ("GET", r"/packaging/Feeds/[^/]+@Local/packages$", feed_pk),
    ("GET", r"/release/definitions$", lambda f, m, q, d, h: _lst([{"id": x["id"], "name": x["name"]} for x in f.w["releasedefs"]])),
    ("GET", r"/release/definitions/(\d+)$", lambda f, m, q, d, h: (200, next(x for x in f.w["releasedefs"] if x["id"] == int(m.group(1))))),
    ("GET", r"/release/artifacts/versions$", lambda f, m, q, d, h: (200, {"artifactVersions": [{"alias": "pkg", "defaultVersion": {"id": "1.0.0-SNAPSHOT", "name": "1.0.0-SNAPSHOT"}}]})),
    ("POST", r"/release/releases$", lambda f, m, q, d, h: (200, {"id": 5, "name": "Release-5"})),
    ("GET", r"/accesscontrollists/[^/]+$", acl),
    ("GET", r"/wiki/wikis$", lambda f, m, q, d, h: _lst(f.w["wikis"])),
    ("GET", r"/wiki/wikis/[^/]+/pages$", wiki_get),
    ("PUT", r"/wiki/wikis/[^/]+/pages$", wiki_put),
]


def fresh() -> FakeAdo:
    return FakeAdo(copy.deepcopy(good_world()))
