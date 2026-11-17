#!/usr/bin/env bash
# Runs the state machines in a local emulator with every Task answered by a mock (statemachines/tests/cases.json).
#
# Step 1 renders the ${...} placeholders of each definition with test values (render.mjs), the way the Terraform module step-functions
# does it before deploying. Step 2 runs every case with the real JSONata library (emulator.mjs).
#
# This is NOT the AWS service. It proves the expressions and the flow (what is rejected, which tags are sent, that a launch is retried
# without being duplicated, that an extended expiry is honoured). It cannot prove that the service accepts the definition (that is
# statemachine-validate-aws.sh) or that EC2 accepts the arguments (the first real execution does).
set -uo pipefail

root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
NODE_IMAGE="${NODE_IMAGE:-node:22-slim}"

shopt -s nullglob
machines=("$root"/statemachines/*.asl.json)
if [ "${#machines[@]}" -eq 0 ]; then
  echo "##vso[task.logissue type=warning]No *.asl.json state machines yet, so the emulator tests did NOT run."
  echo "SKIPPED: no state machines"
  exit 0
fi

docker run --rm -v "$root:/repo" -w /repo/statemachines/tests "$NODE_IMAGE" bash -c '
  set -euo pipefail
  mkdir -p /tmp/rendered
  for f in /repo/statemachines/*.asl.json; do
    name="$(basename "$f" .asl.json)"
    node render.mjs "$f" > "/tmp/rendered/$name.json"
  done
  npm ci --silent --no-audit --no-fund
  node emulator.mjs /tmp/rendered cases.json
'
status=$?

# Hand the workspace back to the agent user (the container ran as root).
docker run --rm -v "$root:/w" alpine:3.20 chown -R "$(id -u):$(id -g)" /w >/dev/null 2>&1 || true
exit $status
