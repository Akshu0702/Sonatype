"""Every audit check: the good project passes it, and a project broken in exactly that way fails it."""

import pytest
from conftest import by

from adoctl.model import overall

AKIA = "AKIA" + "ABCDEFGHIJKLMNOP"  # built at run time so this file is not itself token-shaped


def pol(w, type_name, rid, branch=None):
    return [p for p in w["policies"] if p["type"]["displayName"] == type_name and p["settings"]["scope"][0]["repositoryId"] == rid
            and (branch is None or p["settings"]["scope"][0].get("refName") == f"refs/heads/{branch}")]


def drop(w, type_name, rid, branch=None):
    for p in pol(w, type_name, rid, branch):
        w["policies"].remove(p)


def add_pr(w, **kw):
    pr = {"pullRequestId": 7, "repository": {"id": "r-api", "name": "tst-svc-api"}, "sourceRefName": "refs/heads/feature/US1-T3", "targetRefName": "refs/heads/develop",
          "description": "What\n## Verified\nx\n## Not covered\ny", "creationDate": "2026-11-16T00:00:00Z", "isDraft": False, "reviewers": [{"vote": 10}]}
    pr.update(kw)
    w["prs"].append(pr)
    w["pr_workitems"].setdefault(7, [{"id": "3"}])
    return pr


def tag_with_release(w, acl_deny=True, manifest_file=False):
    w["tags"]["r-infra"] = ["refs/tags/v1.0.0"]
    if acl_deny:
        w["acl"]["repoV2/p-1/r-infra/refs/tags/"] = [{"acesDictionary": {"x": {"deny": 8, "allow": 0}}}]
    if manifest_file:
        w["files"]["r-infra"]["/releases/1.0.0.yml"] = "version: 1.0.0"


def set_var(w, name, value, secret=False):
    w["builddefs"][1]["variables"][name] = {"value": value, "isSecret": secret}


# (check id, subject or None, status expected, severity expected, mutation of the world)
BREAKS = [
    ("B01", None, "fail", "major", lambda w: w["areas"].remove("Api")),
    ("B02", None, "fail", "major", lambda w: w["iterations"].remove("Sprint 2")),
    ("B03", None, "fail", "major", lambda w: w["workitems"][3].update(relations=[])),
    ("B04", None, "fail", "minor", lambda w: w["workitems"][3]["fields"].update({"System.AreaPath": "Proj\\Other"})),
    ("B05", None, "fail", "major", lambda w: w["workitems"][1]["fields"].update({"Microsoft.VSTS.Common.AcceptanceCriteria": ""})),
    ("B06", None, "fail", "major", lambda w: w["workitems"][1].update(relations=[])),
    ("B07", None, "fail", "major", lambda w: w["refs"]["r-api"].pop("refs/heads/feature/US1-T3")),
    ("B09", None, "warn", "minor", lambda w: w["workitems"][1]["fields"].update({"System.Title": "Build things"})),
    ("R01", None, "fail", "major", lambda w: w["repos"].remove(next(r for r in w["repos"] if r["name"] == "tst-svc-api"))),
    ("R02", "tst-infra", "fail", "major", lambda w: w["refs"]["r-infra"].pop("refs/heads/develop")),
    ("R03", "tst-infra", "fail", "minor", lambda w: w["repos"][0].update(defaultBranch="refs/heads/main")),
    ("R04", "tst-infra", "fail", "minor", lambda w: w["files"]["r-infra"].pop("/README.md")),
    ("R05", None, "warn", "minor", lambda w: w["repos"].append({"id": "r-x", "name": "stray-repo", "defaultBranch": None})),
    ("P01", "tst-svc-api:develop", "fail", "major", lambda w: pol(w, "Minimum number of reviewers", "r-api", "develop")[0]["settings"].update(minimumApproverCount=0)),
    ("P01", "tst-svc-api:main", "fail", "major", lambda w: pol(w, "Minimum number of reviewers", "r-api", "main")[0]["settings"].update(creatorVoteCounts=True)),
    ("P01", "tst-infra:develop", "fail", "major", lambda w: drop(w, "Minimum number of reviewers", "r-infra", "develop")),
    ("P02", "tst-svc-api:develop", "fail", "major", lambda w: drop(w, "Comment requirements", "r-api", "develop")),
    ("P03", "tst-svc-api:main", "fail", "major", lambda w: pol(w, "Work item linking", "r-api", "main")[0].update(isBlocking=False)),
    ("P04", "tst-svc-api:develop", "fail", "major", lambda w: drop(w, "Build", "r-api", "develop")),
    ("P05", "tst-svc-api:develop", "fail", "major", lambda w: pol(w, "Require a merge strategy", "r-api", "develop")[0]["settings"].update(allowRebase=True)),
    ("P05", "tst-infra:main", "fail", "major", lambda w: pol(w, "Require a merge strategy", "r-infra", "main")[0]["settings"].update(allowSquash=True)),
    ("P06", "tst-infra", "fail", "minor", lambda w: drop(w, "File size restriction", "r-infra")),
    ("PR01", None, "fail", "major", lambda w: (add_pr(w), w["pr_workitems"].update({7: []}))),
    ("PR02", None, "fail", "major", lambda w: add_pr(w, targetRefName="refs/heads/release/1.0.0")),
    ("PR03", None, "fail", "major", lambda w: add_pr(w, targetRefName="refs/heads/feature/other")),
    ("PR04", None, "warn", "minor", lambda w: add_pr(w, description="no sections")),
    ("PR05", None, "fail", "major", lambda w: add_pr(w, reviewers=[], creationDate="2020-01-01T00:00:00Z")),
    ("C01", "tst-svc-api", "fail", "major", lambda w: w["builddefs"].pop(1)),
    ("C02", "tst-svc-api", "warn", "minor", lambda w: w["files"]["r-api"].update({"/ci/pipeline.yml": "steps: []"})),
    ("C03", "tst-svc-api", "fail", "major", lambda w: w["builds"][11].__setitem__(0, {"id": 1, "buildNumber": "9", "result": "failed"})),
    ("C03", "tst-infra", "warn", "minor", lambda w: w["builds"].update({10: []})),
    ("F01", None, "fail", "major", lambda w: w["feed"].update(views=["Local"])),
    ("F01", None, "fail", "major", lambda w: w["feed"].update(feeds=[])),
    ("F02", None, "fail", "major", lambda w: w["feed"]["permissions"][0].update(role="reader")),
    ("F03", None, "fail", "minor", lambda w: w["feed"].update(packages=[])),
    ("RL01", "cd-tst-svc-api", "fail", "critical", lambda w: w["releasedefs"][0].update(triggers=[{"triggerType": "artifactSource"}])),
    ("RL02", "cd-tst-svc-api", "fail", "critical", lambda w: w["releasedefs"][0]["environments"][0]["preDeployApprovals"]["approvals"][0].update(isAutomated=True)),
    ("RL02", "cd-tst-svc-api", "fail", "critical", lambda w: w["releasedefs"][0]["environments"][0]["preDeployApprovals"]["approvals"][0].pop("approver")),
    ("RL03", "cd-tst-svc-api", "fail", "major", lambda w: w["releasedefs"][0]["variables"].pop("dryRun")),
    ("RL03", "cd-tst-svc-api", "warn", "major", lambda w: w["releasedefs"][0]["variables"]["dryRun"].update(value="false")),
    ("RL04", None, "fail", "major", lambda w: w["refs"]["r-api"].update({"refs/heads/release/1.0.0": "z"})),
    ("RL05", "tst-infra", "fail", "major", lambda w: tag_with_release(w, acl_deny=False)),
    ("RL06", None, "fail", "major", lambda w: tag_with_release(w, acl_deny=True, manifest_file=False)),
    ("W01", None, "fail", "major", lambda w: w["files"]["r-wiki"].pop("Roadmap.md")),
    ("W02", None, "fail", "major", lambda w: w["files"]["r-wiki"].pop("Engineering/Testing-Strategy.md")),
    ("W03", None, "warn", "minor", lambda w: w["files"]["r-wiki"].update({"Services/Tenants.md": "just words"})),
    ("W04", None, "warn", "minor", lambda w: w["files"]["r-wiki"].update({"Engineering/Documentation-Standards.md": "Status: Built"})),
    ("W05", "project", "warn", "minor", lambda w: w["files"]["r-wiki"].pop("Operations/Accepted-Risks.md")),
    ("S01", None, "fail", "critical", lambda w: w["files"]["r-api"].update({"/src/Config.java": f'String k = "{AKIA}";'})),
    ("S01", None, "fail", "critical", lambda w: w["files"]["r-wiki"].update({"Home.md": f"Status: Built\n{AKIA}"})),
    ("S02", None, "warn", "minor", lambda w: w["files"]["r-infra"].update({"/deploy.sh": 'password = "s3cr3t-value-123456"'})),
    ("S03", None, "fail", "major", lambda w: set_var(w, "dbPassword", "hunter2hunter2")),
    ("ST01", "tst-svc-api", "fail", "major", lambda w: w["files"]["r-api"].update({"/pom.xml": "<project><properties><java.version>17</java.version></properties></project>"})),
]


def ids(b):
    return [f"{x[0]}-{x[1] or 'p'}-{i}" for i, x in enumerate(b)]


def test_good_project_is_green(audit):
    results = audit()
    bad = [(r.id, r.subject, r.status, r.evidence) for r in results if r.status in ("fail", "warn")]
    assert bad == []
    assert overall(results) == "Green"
    # every check id of the spec's section 15 ran against the good project
    seen = {r.id for r in results}
    for must in ["B01", "B02", "B03", "B04", "B05", "B06", "B07", "B08", "B09", "R01", "R02", "R03", "R04", "R05", "R06", "P01", "P02", "P03", "P04", "P05", "P06",
                 "PR01", "PR02", "PR03", "PR04", "PR05", "C01", "C02", "C03", "C04", "F01", "F02", "F03", "RL01", "RL02", "RL03", "RL04", "RL05", "RL06",
                 "W01", "W02", "W03", "W04", "W05", "S01", "S02", "S03", "ST01"]:
        assert must in seen, f"{must} did not run"


@pytest.mark.parametrize("cid,subject,status,severity,mutate", BREAKS, ids=ids(BREAKS))
def test_each_check_fails_when_its_rule_is_broken(fake, audit, cid, subject, status, severity, mutate):
    mutate(fake.w)
    found = [r for r in by(audit(), cid, subject) if r.status == status]
    assert found, f"{cid} did not report {status} for {subject}"
    assert found[0].severity == severity
    assert found[0].fix and found[0].rule and found[0].evidence is not None


def test_tag_protection_passes_when_tags_are_denied_force_push_and_manifest_exists(fake, audit):
    tag_with_release(fake.w, acl_deny=True, manifest_file=True)
    r = audit()
    assert by(r, "RL05")[0].status == "pass" and by(r, "RL06")[0].status == "pass"


def test_promote_pipeline_passes_when_it_exists(fake, audit):
    fake.w["refs"]["r-api"]["refs/heads/release/1.0.0"] = "z"
    fake.w["builddefs"].append({"id": 12, "name": "promote-tst-release", "repository": {"id": "r-infra", "name": "tst-infra"}, "variables": {}})
    assert by(audit(), "RL04")[0].status == "pass"


def test_secret_value_is_never_reported(fake, audit):
    fake.w["files"]["r-api"]["/src/Config.java"] = f'String k = "{AKIA}"; // password = "s3cr3t-value-123456"'
    results = audit()
    blob = repr([r.to_dict() for r in results])
    assert AKIA not in blob and "s3cr3t-value-123456" not in blob
    f = by(results, "S01")[0]
    assert "tst-svc-api:src/Config.java:1" in f.evidence and "values not shown" in f.evidence


def test_ordinary_text_and_placeholders_are_not_secrets(fake, audit):
    fake.w["files"]["r-api"]["/README.md"] = 'token = "${TOKEN}"\npassword: <your password here>\nsecret: changeme-please-now\n'
    r = audit()
    assert by(r, "S01")[0].status == "pass" and by(r, "S02")[0].status == "pass"


def test_a_code_reference_is_not_a_secret_assignment(fake, audit):
    fake.w["files"]["r-api"]["/src/auth.ts"] = "sessionStorage.setItem(K, JSON.stringify({ token: body.access_token, expires: 1 }));\nconst secret = config.client.secret_value;"
    assert by(audit(), "S02")[0].status == "pass"


def test_secret_variable_that_is_marked_secret_or_a_reference_is_fine(fake, audit):
    set_var(fake.w, "dbPassword", "", secret=True)
    set_var(fake.w, "apiToken", "$(System.AccessToken)")
    assert by(audit(), "S03")[0].status == "pass"


def test_permission_error_is_a_blind_spot_not_a_pass(fake, audit):
    fake.w["fail_paths"] = {r"policy/configurations": 403}
    results = audit()
    blind = [r for r in results if r.blind]
    assert blind and all(r.status == "warn" and r.evidence.startswith("NOT COVERED") for r in blind)
    assert not [r for r in by(results, "P01") if r.status == "pass"]
    assert overall(results) == "Amber"


def test_a_failing_check_does_not_hide_the_others(fake, audit):
    fake.w["fail_paths"] = {r"wit/wiql": 500}
    results = audit()
    assert any(r.blind for r in results) and by(results, "R01") and by(results, "F01")


def test_application_of_default_branch_policy_scope(fake, audit):
    p = pol(fake.w, "Comment requirements", "r-api", "develop")[0]
    p["settings"]["scope"] = [{"repositoryId": "r-api", "refName": None, "matchKind": "DefaultBranch"}]
    assert by(audit(), "P02", "tst-svc-api:develop")[0].status == "pass"


def _audit_with_manifest(fake, tmp_path, text):
    import fake_ado
    from adoctl.checks import run_all
    from adoctl.client import Client
    from adoctl.manifest import Manifest

    p = tmp_path / "m.yml"
    p.write_text(text, encoding="utf-8")
    c = Client(fake_ado.ORG, fake_ado.PROJ, fake_ado.TOKEN, "Basic", transport=fake, sleep=lambda s: None)
    return run_all(c, Manifest.load(str(p)))


def test_repository_name_without_the_project_prefix_fails_unless_exempt(fake, tmp_path):
    import fake_ado

    fake.w["repos"][0]["name"] = "shared-tooling"
    text = fake_ado.MANIFEST.replace("tst-infra", "shared-tooling")
    assert by(_audit_with_manifest(fake, tmp_path, text), "R06")[0].status == "fail"
    exempt = text.replace("{name: shared-tooling, kind: infra, area: Infra}", "{name: shared-tooling, kind: infra, area: Infra, naming_exempt: true}")
    assert by(_audit_with_manifest(fake, tmp_path, exempt), "R06")[0].status == "pass"
