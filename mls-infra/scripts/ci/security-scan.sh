#!/usr/bin/env bash
# Scans for secrets (gitleaks) and Terraform misconfiguration (Trivy). Report mode by default:
# findings are written to SARIF files and shown, but do not fail the build until SECURITY_ENFORCE=true.
set -uo pipefail

GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-zricethezav/gitleaks:v8.18.4}"
TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.70.0}"
ENFORCE="${SECURITY_ENFORCE:-false}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out="${SCAN_OUT:-$root/scan-results}"
mkdir -p "$out"
cd "$root"

fail=0
exit_code=0
if [ "$ENFORCE" = "true" ]; then exit_code=1; fi

echo "== gitleaks (secrets)"
docker run --rm -v "$root:/repo" -v "$out:/out" "$GITLEAKS_IMAGE" detect --source /repo \
  --report-format sarif --report-path /out/gitleaks.sarif --exit-code "$exit_code" --no-banner || fail=1

echo "== trivy (Terraform misconfiguration)"
if [ -d terraform ]; then
  docker run --rm -v "$root:/repo" -v "$out:/out" "$TRIVY_IMAGE" config /repo/terraform \
    --format sarif --output /out/trivy-config.sarif --exit-code "$exit_code" || fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "Security scan found issues and SECURITY_ENFORCE=true"
  exit 1
fi
echo "Security scan finished (enforce=$ENFORCE). Results in $out"
