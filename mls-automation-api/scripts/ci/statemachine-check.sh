#!/usr/bin/env bash
# Checks that every state machine definition under statemachines/ is valid JSON and has the keys
# every Amazon States Language document needs. It does not replace testing against the service.
set -uo pipefail

root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

shopt -s nullglob
files=("$root"/statemachines/*.json "$root"/statemachines/*.asl.json)
if [ "${#files[@]}" -eq 0 ]; then
  echo "##vso[task.logissue type=warning]No state machine definitions under statemachines/ yet, so the check did NOT run."
  echo "SKIPPED: no state machines"
  exit 0
fi

docker run --rm -i -v "$root:/repo" -w /repo python:3.12-slim python - <<'PY'
import glob, json, sys
bad = 0
for f in sorted(set(glob.glob("statemachines/*.json"))):
    try:
        d = json.load(open(f, encoding="utf-8"))
    except Exception as e:
        print("INVALID JSON", f, e); bad += 1; continue
    missing = [k for k in ("StartAt", "States") if k not in d]
    if not missing and d["StartAt"] not in d["States"]:
        missing = ["StartAt names a state that does not exist"]
    if missing:
        print("PROBLEM", f, missing); bad += 1
    else:
        print("ok", f, len(d["States"]), "states")
sys.exit(1 if bad else 0)
PY
