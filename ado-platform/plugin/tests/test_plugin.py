"""Plugin structure and the guard hooks, run against test inputs."""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "plugin" / "ml-ado"
sys.path.insert(0, str(ROOT / "scripts"))

import check_plugin  # noqa: E402

GUARD = PLUGIN / "hooks" / "guard.py"
TOKEN = "A" * 76 + "AZDO" + "B" * 4          # built at run time: this file must not itself look like a secret
AKIA = "AKIA" + "ABCDEFGHIJKLMNOP"


def run(mode, call):
    p = subprocess.run([sys.executable, str(GUARD), mode], input=json.dumps(call), text=True, capture_output=True)
    return p.returncode, p.stdout + p.stderr


def bash(cmd, **extra):
    return {"tool_name": "Bash", "tool_input": {"command": cmd}, **extra}


def write(content, path="/tmp/x.txt"):
    return {"tool_name": "Write", "tool_input": {"file_path": path, "content": content}}


def transcript(tmp_path, *messages, assistant=()):
    p = tmp_path / "t.jsonl"
    lines = [json.dumps({"type": "user", "message": {"role": "user", "content": m}}) for m in messages]
    lines += [json.dumps({"type": "assistant", "message": {"role": "assistant", "content": [{"type": "text", "text": a}]}}) for a in assistant]
    p.write_text("\n".join(lines), encoding="utf-8")
    return str(p)


def test_structure_is_valid():
    assert check_plugin.check(PLUGIN) == []
    names = {p.name for p in (PLUGIN / "skills").iterdir()}
    assert names == {"ado-project-setup", "ado-work", "ado-pr", "ado-release", "ado-wiki", "ado-doctor"}
    assert {p.stem for p in (PLUGIN / "agents").glob("*.md")} == {"ado-bootstrapper", "ado-auditor"}
    assert {p.stem for p in (PLUGIN / "commands").glob("*.md")} == {"ado-setup", "ado-audit", "ado-work", "ado-release"}


def test_auditor_has_only_read_tools():
    h = check_plugin.header(PLUGIN / "agents" / "ado-auditor.md")
    assert set(t.strip() for t in h["tools"].split(",")) == {"Read", "Grep", "Glob", "Bash"}
    b = check_plugin.header(PLUGIN / "agents" / "ado-bootstrapper.md")
    assert set(t.strip() for t in b["tools"].split(",")) == {"Bash", "Read", "Write"}


def test_shape_matches_the_reference_when_it_is_installed():
    ref = Path.home() / ".claude" / "plugins" / "marketplaces" / "ml-tools" / "ml-specs"
    if not ref.is_dir():
        return
    a, b = check_plugin.shape(PLUGIN), check_plugin.shape(ref)
    for k in ("agent_keys", "command_keys", "skill_keys"):
        assert set(a[k]) <= set(b[k]), (k, a[k], b[k])
    assert check_plugin.main([str(PLUGIN), "--reference", str(ref)]) == 0


def test_token_guard_blocks_a_token_written_to_a_file_and_a_commit():
    code, msg = run("token", write(f"pat = {TOKEN}"))
    assert code == 2 and TOKEN not in msg
    assert run("token", write(f"key {AKIA}"))[0] == 2
    assert run("token", bash(f"echo {TOKEN} > .env"))[0] == 2
    assert run("token", bash(f"git commit -m 'x {TOKEN}'"))[0] == 2
    assert run("token", {"tool_name": "Edit", "tool_input": {"file_path": "a", "new_string": f"t={TOKEN}"}})[0] == 2


def test_token_guard_allows_normal_work_and_session_environment():
    assert run("token", write("just notes, nothing secret here"))[0] == 0
    assert run("token", bash("adoctl audit --manifest ado-project.yml"))[0] == 0
    assert run("token", bash(f"export ADO_PAT={TOKEN}; adoctl audit"))[0] == 0  # sets the session credential, writes nothing
    assert run("token", write("a" * 30))[0] == 0


def test_dryrun_guard_blocks_without_go_ahead(tmp_path):
    t = transcript(tmp_path, "please deploy it", assistant=["I will set dryRun=false after your go-ahead"])
    for call in (bash("echo dryRun=false", transcript_path=t), write('dryRun: false', "/x/release.yml") | {"transcript_path": t},
                 bash("curl -X PATCH -d '{\"dryRun\": {\"value\": \"false\"}}'", transcript_path=t), bash("DRY_RUN=false bash run.sh", transcript_path=t)):
        code, msg = run("dryrun", call)
        assert code == 2 and "go-ahead" in msg, call


def test_dryrun_guard_allows_after_the_user_types_go_ahead(tmp_path):
    t = transcript(tmp_path, "go-ahead dryRun=false cd-x")
    assert run("dryrun", bash("echo dryRun=false", transcript_path=t))[0] == 0
    t2 = tmp_path / "t2.jsonl"
    t2.write_text(json.dumps({"type": "user", "message": {"role": "user", "content": [{"type": "text", "text": "Go-Ahead for dryRun=false"}]}}), encoding="utf-8")
    assert run("dryrun", bash("dryRun=false", transcript_path=str(t2)))[0] == 0


def test_dryrun_guard_ignores_the_assistants_own_go_ahead_and_tool_results(tmp_path):
    p = tmp_path / "t.jsonl"
    p.write_text("\n".join([
        json.dumps({"type": "assistant", "message": {"content": [{"type": "text", "text": "you said go-ahead"}]}}),
        json.dumps({"type": "user", "message": {"content": [{"type": "tool_result", "content": "go-ahead (from a file)"}]}}),
    ]), encoding="utf-8")
    assert run("dryrun", bash("dryRun=false", transcript_path=str(p)))[0] == 2


def test_dryrun_guard_allows_dryrun_true_and_unrelated_commands(tmp_path):
    t = transcript(tmp_path, "hello")
    assert run("dryrun", bash("adoctl release dry-run cd-x", transcript_path=t))[0] == 0
    assert run("dryrun", bash("echo dryRun=true", transcript_path=t))[0] == 0
    assert run("dryrun", write("nothing", "/a.md") | {"transcript_path": t})[0] == 0


def test_shell_wrappers_run():
    for name, arg in (("token-guard.sh", write("hello")), ("dryrun-guard.sh", bash("ls"))):
        p = subprocess.run(["bash", str(PLUGIN / "hooks" / name)], input=json.dumps(arg), text=True, capture_output=True)
        assert p.returncode == 0, p.stderr
