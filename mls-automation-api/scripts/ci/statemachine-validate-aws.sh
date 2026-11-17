#!/usr/bin/env bash
# Asks the Step Functions service itself whether each definition is valid (ValidateStateMachineDefinition: it reads, it creates nothing).
# The local emulator (statemachine-test.sh) cannot say this: only the service knows its own rules for JSONata, SDK integrations and Wait states.
#
# Needs credentials that may call states:ValidateStateMachineDefinition (see mls-infra, terraform/live/lab-test/provisioning/iam-by-hand.md).
# Without them the call is refused, and this script says so and fails: it never passes silently.
#
# Usage: scripts/ci/statemachine-validate-aws.sh   (the agent's role supplies the credentials)
set -uo pipefail

root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
NODE_IMAGE="${NODE_IMAGE:-node:22-slim}"
AWS_IMAGE="${AWS_IMAGE:-amazon/aws-cli:2.17.62}"
region="${AWS_REGION:-ap-south-1}"

shopt -s nullglob
machines=("$root"/statemachines/*.asl.json)
if [ "${#machines[@]}" -eq 0 ]; then
  echo "##vso[task.logissue type=warning]No *.asl.json state machines yet, so the service validation did NOT run."
  echo "SKIPPED: no state machines"
  exit 0
fi

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

# Fill the placeholders with test values first (the real values are filled in by Terraform).
docker run --rm -v "$root:/repo" -v "$out:/out" -w /repo/statemachines/tests "$NODE_IMAGE" bash -c '
  set -euo pipefail
  for f in /repo/statemachines/*.asl.json; do
    node render.mjs "$f" > "/out/$(basename "$f" .asl.json).json"
  done
' || exit 1

fail=0
for f in "$out"/*.json; do
  name="$(basename "$f" .json)"
  echo "== $name"
  result="$(docker run --rm -v "$out:/out:ro" -e AWS_REGION="$region" -e AWS_DEFAULT_REGION="$region" "$AWS_IMAGE" \
    stepfunctions validate-state-machine-definition --type STANDARD --definition "file:///out/$name.json" \
    --query '{result: result, diagnostics: diagnostics}' --output json 2>&1)" || { echo "$result"; echo "NOT VALIDATED: the call was refused or failed"; fail=1; continue; }
  echo "$result"
  echo "$result" | grep -q '"result": "OK"' || fail=1
done

exit "$fail"
