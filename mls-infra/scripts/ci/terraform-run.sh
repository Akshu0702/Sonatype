#!/usr/bin/env bash
# Runs Terraform from the pinned container image inside a working directory of the repository.
# Usage: bash scripts/ci/terraform-run.sh <working-directory> <terraform arguments...>
# AWS credentials are passed to the container by NAME only (never on the command line), so they are not echoed.
set -euo pipefail

TF_IMAGE="${TF_IMAGE:-hashicorp/terraform:1.9.8}"
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
wd="$1"; shift

docker run --rm \
  -v "$root:/repo" -w "/repo/$wd" \
  -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN -e AWS_DEFAULT_REGION -e AWS_REGION \
  -e TF_IN_AUTOMATION=1 -e TF_INPUT=0 -e CHECKPOINT_DISABLE=1 \
  "$TF_IMAGE" "$@"
