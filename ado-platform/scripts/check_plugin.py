#!/usr/bin/env python3
"""Checks that a plugin folder has the shape of a Claude Code plugin like ml-specs.

Usage: check_plugin.py <plugin dir> [--reference <another plugin dir>]
Without --reference it checks against the rules below; with it, it also compares the folder layout and the header keys of
skills, agents and commands with the reference and prints any difference. Exit code 1 on a problem.
"""
import json
import re
import sys
from pathlib import Path

HEADER = re.compile(r"\A---\n(.*?)\n---\n", re.S)


def header(path: Path) -> dict:
    m = HEADER.match(path.read_text(encoding="utf-8"))
    if not m:
        return {}
    out = {}
    for line in m.group(1).splitlines():
        if ":" in line and not line.startswith(" "):
            k, v = line.split(":", 1)
            out[k.strip()] = v.strip()
    return out


def shape(root: Path) -> dict:
    return {
        "top": sorted(p.name for p in root.iterdir() if p.is_dir()),
        "skills": sorted(p.name for p in (root / "skills").glob("*") if p.is_dir()),
        "agent_keys": sorted({k for f in (root / "agents").glob("*.md") for k in header(f)}),
        "command_keys": sorted({k for f in (root / "commands").glob("*.md") for k in header(f)}),
        "skill_keys": sorted({k for f in (root / "skills").glob("*/SKILL.md") for k in header(f)}),
    }


def check(root: Path) -> list[str]:
    problems = []
    pj = root / ".claude-plugin" / "plugin.json"
    if not pj.is_file():
        return [f"missing {pj}"]
    meta = json.loads(pj.read_text(encoding="utf-8"))
    for k in ("name", "description", "version"):
        if not meta.get(k):
            problems.append(f"plugin.json has no '{k}'")
    for d in root.glob("skills/*"):
        if not (d / "SKILL.md").is_file():
            problems.append(f"{d.name}: no SKILL.md")
            continue
        h = header(d / "SKILL.md")
        if h.get("name") != d.name:
            problems.append(f"skill {d.name}: header name is '{h.get('name')}'")
        if len(h.get("description", "")) < 40:
            problems.append(f"skill {d.name}: description missing or too short")
    for f in root.glob("agents/*.md"):
        h = header(f)
        for k in ("name", "description", "tools", "model"):
            if not h.get(k):
                problems.append(f"agent {f.stem}: header has no '{k}'")
        if h.get("name") != f.stem:
            problems.append(f"agent {f.stem}: header name is '{h.get('name')}'")
    for f in root.glob("commands/*.md"):
        h = header(f)
        if not h.get("description"):
            problems.append(f"command {f.stem}: no description")
    hj = root / "hooks" / "hooks.json"
    if hj.is_file():
        data = json.loads(hj.read_text(encoding="utf-8"))
        for event, groups in data.get("hooks", {}).items():
            for g in groups:
                for h in g.get("hooks", []):
                    cmd = re.sub(r"^bash\s+", "", h.get("command", "")).replace("${CLAUDE_PLUGIN_ROOT}/", "")
                    if not (root / cmd).is_file():
                        problems.append(f"hooks.json {event}: {cmd} does not exist")
    else:
        problems.append("no hooks/hooks.json")
    for need in ("README.md", "CHANGELOG.md"):
        if not (root / need).is_file():
            problems.append(f"missing {need}")
    return problems


def main(argv) -> int:
    if not argv:
        print(__doc__)
        return 2
    root = Path(argv[0])
    problems = check(root)
    if "--reference" in argv:
        ref = Path(argv[argv.index("--reference") + 1])
        a, b = shape(root), shape(ref)
        print("plugin:   ", {k: v for k, v in a.items() if k != "skills"})
        print("reference:", {k: v for k, v in b.items() if k != "skills"})
        for k in ("agent_keys", "command_keys", "skill_keys"):
            if set(a[k]) - set(b[k]):
                problems.append(f"{k} has keys the reference does not: {sorted(set(a[k]) - set(b[k]))}")
        for d in ("agents", "commands", "skills", "hooks"):
            if d in b["top"] and d not in a["top"]:
                problems.append(f"the reference has '{d}/' and this plugin does not")
    for p in problems:
        print("PROBLEM:", p)
    print("plugin structure OK" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
