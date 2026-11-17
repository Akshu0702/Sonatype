#!/usr/bin/env bash
# Formats, validates and tests every Terraform module. Runs Terraform from a pinned container image.
# Usage: scripts/ci/terraform-checks.sh   (from anywhere in the repository)
set -uo pipefail

TF_IMAGE="${TF_IMAGE:-hashicorp/terraform:1.9.8}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

tf() {
  # $1 = directory relative to the repository root, the rest = terraform arguments
  local dir="$1"; shift
  docker run --rm --user "$(id -u):$(id -g)" -e TF_IN_AUTOMATION=1 \
    -v "$root:/work" -w "/work/$dir" "$TF_IMAGE" "$@"
}

fail=0

echo "== terraform fmt (check)"
if [ -d terraform ]; then
  tf terraform fmt -check -recursive -diff || fail=1
fi

modules="$(find terraform/modules -mindepth 2 -maxdepth 2 -name '*.tf' -printf '%h\n' 2>/dev/null | sort -u)"
if [ -z "$modules" ]; then
  echo "no modules found"
fi

for dir in $modules; do
  echo "== init and validate: $dir"
  tf "$dir" init -backend=false -input=false >/dev/null || { echo "init failed: $dir"; fail=1; continue; }
  tf "$dir" validate || fail=1
  if ls "$dir"/tests/*.tftest.hcl >/dev/null 2>&1; then
    echo "== test: $dir"
    tf "$dir" test || fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo "Terraform checks FAILED"
  exit 1
fi
echo "Terraform checks passed"
