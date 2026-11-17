#!/usr/bin/env bash
# Runs a check script if it exists, and says so loudly if it does not, so a missing check is never silent.
# Usage: bash scripts/ci/run-if-present.sh <label> <script> [args...]
set -uo pipefail
label="$1"; script="$2"; shift 2
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [ ! -f "$root/$script" ]; then
  echo "##vso[task.logissue type=warning]$label: $script is not in this branch, so the check did NOT run."
  echo "SKIPPED: $label ($script not present)"
  exit 0
fi
cd "$root"
bash "$script" "$@"
