"""adoctl command line. Read-only unless --apply is given. The token comes only from ADO_PAT or SYSTEM_ACCESSTOKEN."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from adoctl import __version__, actions, plan, releasecmd, report, work
from adoctl.checks import run_all
from adoctl.client import ApiError, Client, NoToken, token_from_env
from adoctl.manifest import Manifest, ManifestError, template_text
from adoctl.model import Result


def _client(m: Manifest, transport=None) -> Client:
    tok, scheme = token_from_env()
    return Client(m.organisation, m.name, tok, scheme, transport=transport)


def _load(args) -> Manifest:
    m = Manifest.load(args.manifest)
    m.require_project(getattr(args, "project", None))
    return m


def say(client: Client | None, text: str, stream=None):
    stream = stream or sys.stdout
    stream.write((client.scrub(text) if client else text) + "\n")


# ---- commands ------------------------------------------------------------------------------------------------------
def cmd_init(args, _t=None) -> int:
    out = Path(args.out)
    if out.exists() and not args.force:
        raise ManifestError(f"{out} already exists. Use --force to overwrite (nothing else is ever overwritten).")
    vals = {k: getattr(args, k) for k in ("code", "name", "organisation", "delivery_lead", "project_manager", "go_live", "first_start")}
    for k, v in list(vals.items()):
        if not v:
            if not sys.stdin.isatty():
                raise ManifestError(f"--{k.replace('_', '-')} is required (no terminal to ask on).")
            vals[k] = input(f"{k.replace('_', ' ')}: ").strip()
    out.write_text(template_text(vals["code"], vals["name"], vals["organisation"], vals["delivery_lead"], vals["project_manager"], vals["go_live"], vals["first_start"], args.sprints), encoding="utf-8")
    say(None, f"Wrote {out}. Edit areas and repositories, then run `adoctl plan`.")
    return 0


def cmd_plan_apply(args, transport=None, do=False) -> int:
    m = _load(args)
    c = _client(m, transport)
    try:
        results = plan.run_steps(c, m, args.step, do)
    except plan.NotBuilt as e:
        say(c, str(e), sys.stderr)
        return 3
    mode = "APPLIED" if do else "PLAN (nothing was changed; add --apply to do it)"
    say(c, f"{mode} for project {m.name}")
    for step, msgs in results:
        say(c, f"Step {step}: {plan.STEP_NAMES[step]}")
        for line in msgs or ["nothing to do"]:
            say(c, f"  - {line}")
    return 0


def cmd_audit(args, transport=None) -> int:
    m = _load(args)
    c = _client(m, transport)
    results: list[Result] = run_all(c, m)
    prev = actions.load_json(args.previous) if args.previous and Path(args.previous).is_file() else None
    rep = report.build(results, m.name, prev)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "report.json").write_text(json.dumps(rep, indent=2), encoding="utf-8")
    md = report.markdown(rep)
    (out / "report.md").write_text(md, encoding="utf-8")
    say(c, md)
    say(c, f"\nWrote {out / 'report.json'} and {out / 'report.md'}", sys.stderr)
    if args.fail_on:
        c_ = rep["counts"]
        if c_["critical_fail"] or (args.fail_on == "major" and c_["major_fail"]):
            return 1
    return 0


def cmd_work(args, transport=None) -> int:
    m = _load(args)
    c = _client(m, transport)
    try:
        if args.work_cmd == "start":
            msgs = work.start(c, m, args.task, args.apply)
        elif args.work_cmd == "pr":
            msgs = work.pr(c, m, args.task, args.what, args.verified, args.not_covered, args.draft, args.apply)
        else:
            msgs = work.status(c, m, args.iteration)
    except work.WorkError as e:
        say(c, str(e), sys.stderr)
        return 2
    if args.work_cmd != "status":
        say(c, "APPLIED" if args.apply else "PLAN (nothing was changed; add --apply)")
    for line in msgs:
        say(c, ("  - " if args.work_cmd != "status" else "") + line)
    return 0


def cmd_release(args, transport=None) -> int:
    m = _load(args)
    c = _client(m, transport)
    try:
        msgs = releasecmd.dry_run(c, args.definition, args.apply)
    except releasecmd.ReleaseRefused as e:
        say(c, str(e), sys.stderr)
        return 2
    say(c, "APPLIED" if args.apply else "PLAN (nothing was changed; add --apply)")
    for line in msgs:
        say(c, f"  - {line}")
    return 0


def cmd_fetch_previous(args, transport=None) -> int:
    m = _load(args)
    c = _client(m, transport)
    data = actions.fetch_previous(c, args.definition_id, args.artifact)
    if data is None:
        say(c, "No previous report found (first run, or the artifact has expired).")
        return 0
    Path(args.out).write_bytes(data)
    say(c, f"Previous report saved to {args.out}")
    return 0


def cmd_publish_wiki(args, transport=None) -> int:
    m = _load(args)
    c = _client(m, transport)
    md = Path(args.report).read_text(encoding="utf-8")
    for line in actions.publish_wiki(c, md, args.page, args.apply):
        say(c, f"  - {line}")
    return 0


def cmd_open_bugs(args, transport=None) -> int:
    m = _load(args)
    c = _client(m, transport)
    for line in actions.open_bugs(c, m, actions.load_json(args.report), args.apply):
        say(c, f"  - {line}")
    return 0


# ---- parser --------------------------------------------------------------------------------------------------------
def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="adoctl", description="Set up, work with and audit an Azure DevOps project (AzureDevOps-Platform-v1). Read-only unless --apply.")
    p.add_argument("--version", action="version", version=f"adoctl {__version__}")
    sub = p.add_subparsers(dest="cmd", required=True)

    def common(sp, apply=False):
        sp.add_argument("--manifest", default="ado-project.yml")
        sp.add_argument("--project", help="must equal the manifest's project; a project not in the manifest is never touched")
        if apply:
            sp.add_argument("--apply", action="store_true", help="make the change (the default is to describe it)")

    s = sub.add_parser("init", help="write ado-project.yml")
    s.add_argument("--out", default="ado-project.yml")
    s.add_argument("--force", action="store_true")
    for k in ("code", "name", "organisation", "delivery-lead", "project-manager", "go-live", "first-start"):
        s.add_argument(f"--{k}")
    s.add_argument("--sprints", type=int, default=6)
    s.set_defaults(fn=cmd_init)

    s = sub.add_parser("plan", help="describe what apply would do for steps 2 to 4")
    common(s)
    s.add_argument("--step", type=int)
    s.set_defaults(fn=lambda a, t=None: cmd_plan_apply(a, t, do=False))

    s = sub.add_parser("apply", help="do steps 2 to 4 (with --apply); steps 5 to 13 are not built")
    common(s, apply=True)
    s.add_argument("--step", type=int)
    s.set_defaults(fn=lambda a, t=None: cmd_plan_apply(a, t, do=a.apply))

    s = sub.add_parser("audit", help="read-only check of the project against the spec")
    common(s)
    s.add_argument("--out", default="audit-out")
    s.add_argument("--previous", help="a previous report.json, for the trend")
    s.add_argument("--fail-on", choices=["critical", "major"])
    s.set_defaults(fn=cmd_audit)

    w = sub.add_parser("work", help="start a task, open its pull request, show status")
    ws = w.add_subparsers(dest="work_cmd", required=True)
    s = ws.add_parser("start")
    common(s, apply=True)
    s.add_argument("task", type=int)
    s = ws.add_parser("pr")
    common(s, apply=True)
    s.add_argument("task", type=int)
    s.add_argument("--what", required=True)
    s.add_argument("--verified", required=True)
    s.add_argument("--not-covered", required=True, dest="not_covered")
    s.add_argument("--draft", action="store_true")
    s = ws.add_parser("status")
    common(s)
    s.add_argument("--iteration")
    w.set_defaults(fn=cmd_work)
    for x in ws.choices.values():
        x.set_defaults(fn=cmd_work)

    r = sub.add_parser("release", help="dry-run releases only")
    rs = r.add_subparsers(dest="release_cmd", required=True)
    s = rs.add_parser("dry-run")
    common(s, apply=True)
    s.add_argument("definition")
    s.set_defaults(fn=cmd_release)

    s = sub.add_parser("fetch-previous", help="download report.json of the last successful run of a pipeline")
    common(s)
    s.add_argument("--definition-id", type=int, required=True)
    s.add_argument("--artifact", default="audit-report")
    s.add_argument("--out", default="previous.json")
    s.set_defaults(fn=cmd_fetch_previous)

    s = sub.add_parser("publish-wiki", help="publish report.md to a wiki page")
    common(s, apply=True)
    s.add_argument("--report", required=True)
    s.add_argument("--page", default="Operations/Process-Audit")
    s.set_defaults(fn=cmd_publish_wiki)

    s = sub.add_parser("open-bugs", help="open one Bug per new critical drift (deduplicated by check id and subject)")
    common(s, apply=True)
    s.add_argument("--report", required=True)
    s.set_defaults(fn=cmd_open_bugs)
    return p


def main(argv=None, transport=None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.cmd == "init":
            return args.fn(args)
        return args.fn(args, transport)
    except (ManifestError, NoToken) as e:
        sys.stderr.write(f"error: {e}\n")
        return 2
    except ApiError as e:
        sys.stderr.write(f"error: {e} ({e.url})\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
