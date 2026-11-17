"""Security checks (spec 11 and 15): token-shaped strings in repositories and the wiki, and secrets held in pipeline variables.

The scan reports file, line and the kind of pattern. It NEVER reports, logs or stores the matched value.
"""

from __future__ import annotations

import re

from adoctl.checks import Ctx, guard
from adoctl.checks.wiki import repo_zip

AREA = "Security"
RULE = "Spec 11"
MAX_FILE = 400_000
SKIP_EXT = (".png", ".jpg", ".jpeg", ".gif", ".ico", ".zip", ".jar", ".gz", ".pdf", ".docx", ".pptx", ".xlsx", ".woff", ".woff2", ".ttf", ".class", ".lock")
SKIP_NAMES = ("package-lock.json", "yarn.lock", "pnpm-lock.yaml")

# (name, regex, severity). Strong patterns are failures; the generic key=value pattern is a warning.
STRONG = [
    ("Azure DevOps personal access token (new format)", re.compile(r"(?<![A-Za-z0-9])[A-Za-z0-9]{76}AZDO[A-Za-z0-9]{4}(?![A-Za-z0-9])"), "critical"),
    ("Azure DevOps personal access token (old format)", re.compile(r"(?<![A-Za-z0-9])[a-z2-7]{52}(?![A-Za-z0-9])"), "critical"),
    ("AWS access key id", re.compile(r"(?<![A-Z0-9])AKIA[0-9A-Z]{16}(?![A-Z0-9])"), "critical"),
    ("Private key block", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "critical"),
    ("GitHub token", re.compile(r"gh[pousr]_[0-9A-Za-z]{30,}"), "critical"),
    ("JSON web token", re.compile(r"eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}"), "critical"),
]
GENERIC = re.compile(r"""(?i)\b(password|passwd|secret|api[_-]?key|access[_-]?key|token)\b["']?\s*[:=]\s*["']?([^\s"'<>$(){},;]{12,})""")
PLACEHOLDERISH = re.compile(r"(?i)^(changeme|example|placeholder|xxxx|your[-_]|<|\*{3}|none|null|true|false|\$\{|%\()|^[a-z]+(_[a-z]+)+$|^[a-z.]+$|^[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)+$")  # the last one: a code reference such as body.access_token
SECRET_WORDS = {"pass", "password", "passwd", "pwd", "secret", "token", "apikey", "accesskey", "key", "credential", "credentials", "pat"}


def secret_name(name: str) -> bool:
    """True if a variable name contains a secret-like word (splits snake_case, kebab-case and camelCase)."""
    words = [w.lower() for w in re.sub(r"([a-z0-9])([A-Z])", lambda m: m.group(1) + " " + m.group(2), name).replace("_", " ").replace("-", " ").replace(".", " ").split()]
    return any(w in SECRET_WORDS for w in words) or "apikey" in "".join(words)


def scan_files(files: dict[str, bytes], label: str):
    """Yields (severity, kind, 'label:path:line') and never the value."""
    for path, data in files.items():
        low = path.lower()
        if low.endswith(SKIP_EXT) or low.rsplit("/", 1)[-1] in SKIP_NAMES or len(data) > MAX_FILE or b"\x00" in data[:2000]:
            continue
        test_like = any(x in low for x in ("/test", "tests/", "fixture", "testdata", "/mock", "example"))
        for n, line in enumerate(data.decode("utf-8", "replace").splitlines(), 1):
            if len(line) > 2000:
                continue
            for kind, rx, sev in STRONG:
                if rx.search(line):
                    yield sev, kind, f"{label}:{path}:{n}"
            g = GENERIC.search(line)
            if g and not test_like and not PLACEHOLDERISH.search(g.group(2)):
                yield "minor", "Possible secret assignment (key = value)", f"{label}:{path}:{n}"


def run(ctx: Ctx, out):
    targets = [(r["name"], r["id"], "develop" if "develop" in ctx.branches(r["id"]) else None) for _m, r in ctx.repos_of_manifest() if r]

    def scan():
        hits_crit, hits_warn = [], []
        for name, rid, br in targets:
            try:
                files = repo_zip(ctx, rid, br)
            except Exception as e:  # an empty repository has no zip
                if "404" in str(e) or "400" in str(e):
                    continue
                raise
            for sev, kind, where in scan_files(files, name):
                (hits_crit if sev == "critical" else hits_warn).append(f"{where} ({kind})")
        ws = ctx.client.get(ctx.client.proj("wiki/wikis"))["value"]
        w = next((x for x in ws if x.get("type") == "projectWiki"), None)
        if w:
            files = repo_zip(ctx, w["repositoryId"], (w.get("versions") or [{}])[0].get("version"))
            for sev, kind, where in scan_files(files, "wiki"):
                (hits_crit if sev == "critical" else hits_warn).append(f"{where} ({kind})")
        if hits_crit:
            out.fail("S01", AREA, "No token-shaped string in any repository or the wiki", "critical", RULE,
                     f"{len(hits_crit)} finding(s), values not shown: " + "; ".join(hits_crit[:12]) + (" ..." if len(hits_crit) > 12 else ""),
                     "Remove it from the file AND from history if it was real, then rotate the credential.")
        else:
            out.ok("S01", AREA, "No token-shaped string in any repository or the wiki", RULE, evidence=f"{len(targets)} repositories and the wiki scanned")
        if hits_warn:
            out.warn("S02", AREA, "No likely secret assignment in code", RULE, f"{len(hits_warn)} possible: " + "; ".join(hits_warn[:12]) + (" ..." if len(hits_warn) > 12 else "") + " (values not shown; review each)",
                     "Move the value to a secret store or mark it a placeholder.")
        else:
            out.ok("S02", AREA, "No likely secret assignment in code", RULE)

    guard(out, ctx, "S01", AREA, "Secrets scan", RULE, scan)

    def variables():
        bad = []
        for d in ctx.build_defs():
            for name, v in (d.get("variables") or {}).items():
                if secret_name(name) and not v.get("isSecret") and str(v.get("value", "")).strip() and not str(v.get("value", "")).startswith("$("):
                    bad.append(f"build {d['name']}: {name}")
        try:
            for d in ctx.release_defs():
                for name, v in (d.get("variables") or {}).items():
                    if secret_name(name) and not v.get("isSecret") and str(v.get("value", "")).strip() and not str(v.get("value", "")).startswith("$("):
                        bad.append(f"release {d['name']}: {name}")
        except Exception:
            pass
        if bad:
            out.fail("S03", AREA, "No secret held in a plain pipeline variable", "major", RULE, f"{len(bad)}: " + "; ".join(bad[:12]), "Mark it secret or move it to a secret store (names only shown, never values).")
        else:
            out.ok("S03", AREA, "No secret held in a plain pipeline variable", RULE)

    guard(out, ctx, "S03", AREA, "Pipeline variables", RULE, variables)
