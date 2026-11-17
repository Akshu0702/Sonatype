#!/usr/bin/env bash
# Checks the plugin bundle against its manifest checksums when a manifest exists. The package and checksum
# scripts are part of the bundle work; until they exist this says so loudly instead of passing quietly.
set -uo pipefail
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
if [ ! -f "$root/scripts/verify-bundle.sh" ]; then
  echo "##vso[task.logissue type=warning]scripts/verify-bundle.sh is not in this branch, so the bundle integrity check did NOT run."
  echo "SKIPPED: bundle integrity (no verify-bundle.sh yet)"
  exit 0
fi
cd "$root" && bash scripts/verify-bundle.sh
