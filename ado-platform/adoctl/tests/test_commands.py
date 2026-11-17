"""Commands: dry-run by default, refuse-without-manifest, steps not built, work, release, actions, report, exit codes, token never shown."""

import io
import json
import zipfile
from pathlib import Path

import fake_ado
import pytest

from adoctl import actions, report
from adoctl.cli import main
from adoctl.model import Collector, overall


def run_cli(fake, mpath, *args, env=None, capsys=None):
    import os

    old = {k: os.environ.get(k) for k in ("ADO_PAT", "ADO_TOKEN", "SYSTEM_ACCESSTOKEN")}
    for k in old:
        os.environ.pop(k, None)
    os.environ.update(env if env is not None else {"ADO_TOKEN": fake_ado.TOKEN})
    try:
        code = main(list(args), transport=fake)
    finally:
        for k, v in old.items():
            os.environ.pop(k, None)
            if v is not None:
                os.environ[k] = v
    out = capsys.readouterr()
    return code, out.out, out.err


# ---- refusals -------------------------------------------------------------------------------------------------------------
def test_refuses_without_a_manifest(fake, tmp_path, capsys):
    code, out, err = run_cli(fake, None, "audit", "--manifest", str(tmp_path / "none.yml"), capsys=capsys)
    assert code == 2 and "No manifest" in err and not fake.requests


def test_refuses_a_project_that_is_not_in_the_manifest(fake, mpath, capsys):
    code, out, err = run_cli(fake, mpath, "apply", "--manifest", mpath, "--project", "Other", "--apply", capsys=capsys)
    assert code == 2 and "not the project in the manifest" in err and not fake.requests


def test_refuses_without_a_token(fake, mpath, capsys):
    code, out, err = run_cli(fake, mpath, "audit", "--manifest", mpath, env={}, capsys=capsys)
    assert code == 2 and "ADO_TOKEN" in err and not fake.requests


# ---- plan and apply -----------------------------------------------------------------------------------------------------------
def broken(fake):
    fake.w["areas"].remove("Api")
    fake.w["iterations"].remove("Sprint 2")
    fake.w["repos"] = [r for r in fake.w["repos"] if r["name"] != "tst-svc-api"]
    fake.w["policies"] = [p for p in fake.w["policies"] if p["settings"]["scope"][0]["repositoryId"] != "r-api"]


def test_plan_and_apply_without_flag_change_nothing(fake, mpath, capsys):
    broken(fake)
    for cmd in ("plan", "apply"):
        code, out, _ = run_cli(fake, mpath, cmd, "--manifest", mpath, capsys=capsys)
        assert code == 0 and "nothing was changed" in out and "create area 'Api'" in out and "create repository 'tst-svc-api'" in out
    assert fake.writes() == []


def test_apply_creates_what_is_missing_and_second_run_changes_nothing(fake, mpath, capsys):
    broken(fake)
    code, out, _ = run_cli(fake, mpath, "apply", "--manifest", mpath, "--apply", capsys=capsys)
    assert code == 0 and "APPLIED" in out
    assert "Api" in fake.w["areas"] and "Sprint 2" in fake.w["iterations"]
    repo = next(r for r in fake.w["repos"] if r["name"] == "tst-svc-api")
    assert repo["defaultBranch"] == "refs/heads/develop"
    refs = fake.w["refs"][repo["id"]]
    assert "refs/heads/main" in refs and "refs/heads/develop" in refs
    assert {"/README.md", "/.gitignore", "/.azuredevops/pull_request_template.md"} <= set(fake.w["files"][repo["id"]])
    for br in ("main", "develop"):
        names = {p["type"]["displayName"] for p in fake.w["policies"] if p["settings"]["scope"][0]["repositoryId"] == repo["id"] and p["settings"]["scope"][0].get("refName") == f"refs/heads/{br}"}
        assert {"Minimum number of reviewers", "Comment requirements", "Work item linking", "Require a merge strategy"} <= names
    dev_merge = next(p for p in fake.w["policies"] if p["type"]["displayName"] == "Require a merge strategy" and p["settings"]["scope"][0]["repositoryId"] == repo["id"]
                     and p["settings"]["scope"][0]["refName"] == "refs/heads/develop")
    assert dev_merge["settings"]["allowSquash"] is True and dev_merge["settings"]["allowNoFastForward"] is False
    assert all(w[0] != "DELETE" for w in fake.writes())
    before = len(fake.writes())
    code, out, _ = run_cli(fake, mpath, "apply", "--manifest", mpath, "--apply", capsys=capsys)
    assert code == 0 and len(fake.writes()) == before, "a second apply must change nothing"


@pytest.mark.parametrize("step", [5, 6, 8, 9, 10, 11, 12, 13])
def test_unbuilt_steps_are_refused(fake, mpath, capsys, step):
    code, out, err = run_cli(fake, mpath, "apply", "--manifest", mpath, "--step", str(step), "--apply", capsys=capsys)
    assert code == 3 and "not built" in err and fake.writes() == []


def test_init_writes_a_manifest_and_will_not_overwrite(tmp_path, capsys):
    out = tmp_path / "ado-project.yml"
    args = ["init", "--out", str(out), "--code", "abc", "--name", "Proj", "--organisation", "Org", "--delivery-lead", "A B", "--project-manager", "C D", "--go-live", "2027-01-31", "--first-start", "2026-12-07"]
    assert main(args) == 0 and "code: abc" in out.read_text(encoding="utf-8")
    assert main(args) == 2  # exists: refused
    assert main(args + ["--force"]) == 0


# ---- audit command ---------------------------------------------------------------------------------------------------------------
def test_audit_writes_json_and_markdown_and_exit_codes(fake, mpath, tmp_path, capsys):
    out_dir = tmp_path / "o"
    code, out, _ = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(out_dir), "--fail-on", "major", capsys=capsys)
    assert code == 0 and "Overall status: GREEN" in out
    rep = json.loads((out_dir / "report.json").read_text(encoding="utf-8"))
    assert rep["overall"] == "Green" and (out_dir / "report.md").is_file() and {"id", "area", "status", "severity", "evidence", "fix", "rule"} <= set(rep["results"][0])
    fake.w["releasedefs"][0]["triggers"] = [{"triggerType": "artifactSource"}]  # a critical failure
    code, out, _ = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(out_dir), "--fail-on", "critical", capsys=capsys)
    assert code == 1 and "Overall status: RED" in out
    code, *_ = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(out_dir), capsys=capsys)
    assert code == 0, "without --fail-on the exit code is 0"
    fake.w["releasedefs"][0]["triggers"] = []
    fake.w["policies"] = [p for p in fake.w["policies"] if not (p["type"]["displayName"] == "Comment requirements" and p["settings"]["scope"][0]["repositoryId"] == "r-api")]
    code, *_ = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(out_dir), "--fail-on", "critical", capsys=capsys)
    assert code == 0
    code, *_ = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(out_dir), "--fail-on", "major", capsys=capsys)
    assert code == 1


def test_audit_makes_no_writes(fake, mpath, tmp_path, capsys):
    run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(tmp_path / "o"), capsys=capsys)
    assert fake.writes() == [] or all(w[1].endswith("/wit/wiql") or w[1].endswith("/wit/workitemsbatch") for w in fake.writes())


def test_token_is_never_in_output_or_files(fake, mpath, tmp_path, capsys):
    fake.w["fail_paths"] = {r"policy/configurations": 403}
    fake.w["error_echo_token"] = True  # the server echoes the Authorization header in its error body
    out_dir = tmp_path / "o"
    code, out, err = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(out_dir), capsys=capsys)
    blob = out + err + "".join(p.read_text(encoding="utf-8") for p in out_dir.iterdir())
    assert fake_ado.TOKEN not in blob
    import base64

    assert base64.b64encode((":" + fake_ado.TOKEN).encode()).decode() not in blob
    assert "NOT COVERED" in blob  # the denied read is listed as not covered, not as a failure
    assert not [r for r in json.loads((out_dir / "report.json").read_text(encoding="utf-8"))["results"] if r["id"] == "P01" and r["status"] == "fail"]
    assert all(fake_ado.TOKEN not in str(w) for w in fake.writes())


def test_bearer_header_used_for_ado_token(fake, mpath, tmp_path, capsys):
    run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(tmp_path / "o"), capsys=capsys)
    assert set(fake.w["headers"]) == {"Bearer " + fake_ado.TOKEN}
    fake.w["headers"].clear()
    run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(tmp_path / "o"), env={"ADO_PAT": "pat-xyz"}, capsys=capsys)
    assert all(h.startswith("Basic ") for h in fake.w["headers"])


# ---- report ---------------------------------------------------------------------------------------------------------------------------
def test_overall_status_rule():
    c = Collector()
    assert overall(c.results) == "Green"
    c.ok("X1", "Boards", "t", "r")
    c.warn("X2", "Boards", "t", "r", "e", "f")
    assert overall(c.results) == "Amber"
    c.fail("X3", "Boards", "t", "major", "r", "e", "f")
    assert overall(c.results) == "Amber"
    c.fail("X4", "Boards", "t", "critical", "r", "e", "f")
    assert overall(c.results) == "Red"
    d = Collector()
    for i in range(10):
        d.fail(f"M{i}", "Boards", "t", "major", "r", "e", "f")
    assert overall(d.results) == "Red"
    n = Collector()
    n.blind("B1", "Boards", "t", "r", "denied", denied=True)
    assert overall(n.results) == "Amber", "something not covered can never be Green"


def test_trend_and_markdown(fake, audit):
    r1 = report.build(audit(), "Proj")
    fake.w["areas"].remove("Api")
    fake.w["releasedefs"][0]["triggers"] = [{"triggerType": "artifactSource"}]
    r2 = report.build(audit(), "Proj", previous=r1)
    t = r2["trend"]
    assert t["previous_overall"] == "Green" and t["overall_changed"] and t["delta_critical"] == 1 and t["delta_failed"] == 2
    assert {"B01|project", "RL01|cd-tst-svc-api"} <= set(t["new"])
    md = report.markdown(r2)
    for must in ("## Overall status: RED", "*Rule.*", "## Trend", "## By area", "## Drift (most severe first)", "| critical |"):
        assert must in md
    assert md.index("| critical |") < md.index("| major |")
    r3 = report.build(audit(), "Proj", previous=r2)
    fake.w["areas"].append("Api")
    fake.w["releasedefs"][0]["triggers"] = []
    r4 = report.build(audit(), "Proj", previous=r3)
    assert {"B01|project", "RL01|cd-tst-svc-api"} <= set(r4["trend"]["resolved"])


def test_not_covered_section_lists_denied_reads(fake, audit):
    fake.w["fail_paths"] = {r"/packaging/feeds$": 403}
    md = report.markdown(report.build(audit(), "Proj"))
    assert "## Not covered" in md and "NOT COVERED (the identity cannot read this)" in md


# ---- work -------------------------------------------------------------------------------------------------------------------------------
def test_work_start_pr_and_status(fake, mpath, capsys):
    code, out, _ = run_cli(fake, mpath, "work", "start", "--manifest", mpath, "3", capsys=capsys)
    assert code == 0 and "branch feature/US1-T3 already exists" in out and fake.writes() == []
    del fake.w["refs"]["r-api"]["refs/heads/feature/US1-T3"]
    code, out, _ = run_cli(fake, mpath, "work", "start", "--manifest", mpath, "3", capsys=capsys)
    assert "create branch feature/US1-T3 in tst-svc-api from develop" in out and fake.writes() == []
    fake.w["workitems"][3]["fields"]["System.State"] = "New"
    run_cli(fake, mpath, "work", "start", "--manifest", mpath, "3", "--apply", capsys=capsys)
    assert "refs/heads/feature/US1-T3" in fake.w["refs"]["r-api"]
    assert any(w[0] == "PATCH" and w[1].endswith("/wit/workitems/3") for w in fake.writes())
    n = len(fake.writes())
    args = ("work", "pr", "--manifest", mpath, "3", "--what", "Does a thing", "--verified", "ran x", "--not-covered", "y")
    code, out, _ = run_cli(fake, mpath, *args, capsys=capsys)
    assert "ready for review" in out and len(fake.writes()) == n
    run_cli(fake, mpath, *args, "--apply", capsys=capsys)
    body = fake.writes()[-1][2]
    assert body["isDraft"] is False and body["targetRefName"] == "refs/heads/develop" and {"id": "3"} in body["workItemRefs"] and {"id": "1"} in body["workItemRefs"]
    assert len(body["description"]) < 4000 and "## Not covered" in body["description"]
    code, out, err = run_cli(fake, mpath, "work", "pr", "--manifest", mpath, "3", "--what", "x" * 4100, "--verified", "v", "--not-covered", "n", capsys=capsys)
    assert code == 2 and "4000" in err
    code, out, _ = run_cli(fake, mpath, "work", "status", "--manifest", mpath, "--iteration", "Sprint 1", capsys=capsys)
    assert code == 0 and "Tasks in Sprint 1: 2" in out and "Red builds on develop: none" in out


def test_work_on_a_story_is_refused(fake, mpath, capsys):
    code, out, err = run_cli(fake, mpath, "work", "start", "--manifest", mpath, "1", capsys=capsys)
    assert code == 2 and "not a Task" in err


# ---- release ---------------------------------------------------------------------------------------------------------------------------
def test_release_dry_run_is_planned_by_default_and_created_with_apply(fake, mpath, capsys):
    code, out, _ = run_cli(fake, mpath, "release", "dry-run", "--manifest", mpath, "cd-tst-svc-api", capsys=capsys)
    assert code == 0 and "nothing was changed" in out and fake.writes() == []
    code, out, _ = run_cli(fake, mpath, "release", "dry-run", "--manifest", mpath, "cd-tst-svc-api", "--apply", capsys=capsys)
    assert code == 0 and fake.writes()[-1][1].endswith("/release/releases") and "approve it" in out
    assert fake.writes()[-1][2]["description"].startswith("adoctl dry run")


def test_release_refuses_when_dry_run_is_not_true_or_a_trigger_exists(fake, mpath, capsys):
    fake.w["releasedefs"][0]["variables"]["dryRun"]["value"] = "false"
    code, out, err = run_cli(fake, mpath, "release", "dry-run", "--manifest", mpath, "cd-tst-svc-api", "--apply", capsys=capsys)
    assert code == 2 and "not 'true'" in err and fake.writes() == []
    fake.w["releasedefs"][0]["variables"]["dryRun"]["value"] = "true"
    fake.w["releasedefs"][0]["triggers"] = [{"triggerType": "artifactSource"}]
    code, out, err = run_cli(fake, mpath, "release", "dry-run", "--manifest", mpath, "cd-tst-svc-api", "--apply", capsys=capsys)
    assert code == 2 and "trigger" in err and fake.writes() == []
    code, out, err = run_cli(fake, mpath, "release", "dry-run", "--manifest", mpath, "cd-nope", capsys=capsys)
    assert code == 2 and "No release definition" in err


# ---- actions -------------------------------------------------------------------------------------------------------------------------------
def test_fetch_previous_reads_report_json_from_the_last_successful_run(fake, client):
    assert actions.fetch_previous(client, 99, "audit-report") is None
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("audit-report/report.json", '{"overall": "Green"}')
    fake.w["last_good_build"] = True
    fake.w["prev_artifact"] = buf.getvalue()
    assert json.loads(actions.fetch_previous(client, 99, "audit-report")) == {"overall": "Green"}


def test_publish_wiki_creates_then_updates_with_etag(fake, client, manifest):
    assert actions.publish_wiki(client, "# r", "Operations/Process-Audit", False) and fake.writes() == []
    actions.publish_wiki(client, "# r1", "Operations/Process-Audit", True)
    actions.publish_wiki(client, "# r2", "Operations/Process-Audit", True)  # needs If-Match, or the fake answers 412
    assert [w[0] for w in fake.writes()] == ["PUT", "PUT"]


def critical_report():
    return {"generated": "2026-11-17T00:00:00Z", "results": [
        {"id": "RL01", "subject": "cd-x", "title": "No trigger", "status": "fail", "severity": "critical", "evidence": "trigger", "fix": "remove", "rule": "Spec 9.2"},
        {"id": "B06", "subject": "project", "title": "t", "status": "fail", "severity": "major", "evidence": "e", "fix": "f", "rule": "r"}]}


def test_open_bugs_one_per_critical_drift_and_deduplicated(fake, client, manifest):
    rep = critical_report()
    assert "no critical drift" in actions.open_bugs(client, manifest, {"generated": "x", "results": []}, True)[0]
    plan = actions.open_bugs(client, manifest, rep, False)
    assert plan == ["open a Bug for [audit:RL01|cd-x] No trigger"] and fake.writes() == [] or all(w[1].endswith("wiql") for w in fake.writes())
    actions.open_bugs(client, manifest, rep, True)
    bugs = [w for w in fake.writes() if w[1].endswith("$Bug")]
    assert len(bugs) == 1 and "[audit:RL01|cd-x]" in bugs[0][2][0]["value"]
    again = actions.open_bugs(client, manifest, rep, True)
    assert "already has open Bug" in again[0]
    assert len([w for w in fake.writes() if w[1].endswith("$Bug")]) == 1, "no second Bug for the same check and subject"


def test_markdown_file_is_written_utf8(fake, mpath, tmp_path, capsys):
    code, *_ = run_cli(fake, mpath, "audit", "--manifest", mpath, "--out", str(tmp_path / "o"), capsys=capsys)
    assert Path(tmp_path / "o" / "report.md").read_text(encoding="utf-8").startswith("# Process audit: Proj")
