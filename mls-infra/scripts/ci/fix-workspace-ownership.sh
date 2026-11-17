#!/usr/bin/env bash
# The checks run tools in containers, and containers run as root: they leave root-owned files in the checkout
# (for example .terraform and .helm) and in the artifact folder. On a self-hosted agent the workspace is kept between
# jobs, so the next job's checkout could not clean it. This hands the files back to the agent's user.
# Usage: bash scripts/ci/fix-workspace-ownership.sh <folder> [<folder>...]     Never fails the job.
set -uo pipefail
for dir in "$@"; do
  [ -d "$dir" ] || continue
  docker run --rm -v "$dir:/w" alpine:3.20 chown -R "$(id -u):$(id -g)" /w || echo "could not change the owner of $dir"
done
exit 0
