#!/usr/bin/env bash
# Lints every OpenAPI file under openapi/ (the contract is the source of truth: spec first).
# Fails on errors. Says so loudly if there is nothing to lint yet.
set -uo pipefail

REDOCLY_IMAGE="${REDOCLY_IMAGE:-redocly/cli:1.25.0}"
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

shopt -s nullglob
specs=("$root"/openapi/*.yaml "$root"/openapi/*.yml "$root"/openapi/*.json)
if [ "${#specs[@]}" -eq 0 ]; then
  echo "##vso[task.logissue type=warning]No OpenAPI files under openapi/ yet, so the contract check did NOT run."
  echo "SKIPPED: no OpenAPI files"
  exit 0
fi

fail=0
for f in "${specs[@]}"; do
  rel="openapi/$(basename "$f")"
  echo "== lint $rel"
  docker run --rm -v "$root:/spec" -w /spec "$REDOCLY_IMAGE" lint "$rel" --format=stylish || fail=1
done
exit "$fail"

