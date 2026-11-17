#!/usr/bin/env bash
# Secrets (gitleaks) and vulnerable dependencies, secrets and misconfiguration in files (Trivy filesystem scan).
# Report mode by default: findings are written to SARIF files and shown, but do not fail the build
# until SECURITY_ENFORCE=true. Works for any language: it reads manifests and lock files where they exist.
set -uo pipefail

GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-zricethezav/gitleaks:v8.18.4}"
TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.70.0}"
ENFORCE="${SECURITY_ENFORCE:-false}"
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
out="${SCAN_OUT:-$root/scan-results}"
mkdir -p "$out"

fail=0
exit_code=0
if [ "$ENFORCE" = "true" ]; then exit_code=1; fi

echo "== gitleaks (secrets)"
docker run --rm -v "$root:/repo" -v "$out:/out" "$GITLEAKS_IMAGE" detect --source /repo \
  --report-format sarif --report-path /out/gitleaks.sarif --exit-code "$exit_code" --no-banner || fail=1

echo "== trivy (dependencies, secrets, misconfiguration)"
docker run --rm -v "$root:/repo" -v "$out:/out" "$TRIVY_IMAGE" fs /repo \
  --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed \
  --format sarif --output /out/trivy-fs.sarif --exit-code "$exit_code" || fail=1

if [ "$fail" -ne 0 ]; then
  # In report mode the scanners exit 0 even with findings, so a non-zero exit here means a scanner did not
  # complete (for example it could not download its database). That must not pass quietly.
  echo "##vso[task.logissue type=error]A scanner failed to complete, or found issues with SECURITY_ENFORCE=true. See the log above."
  exit 1
fi
echo "Security scan finished (enforce=$ENFORCE). Results in $out"

