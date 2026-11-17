#!/usr/bin/env bash
# Rebuilds each wrapper chart's dependency from its lock file, lints it and renders it for every environment.
# Usage: bash scripts/ci/helm-checks.sh   (set REPO_DIR to the absolute path of the repository if your shell rewrites paths)
set -uo pipefail

HELM_IMAGE="${HELM_IMAGE:-alpine/helm:3.19.0}"
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
fail=0

# Repository settings live inside the checkout (ignored by Git), so they persist between the separate container runs.
helm() {
  docker run --rm -e HOME=/tmp -e HELM_REPOSITORY_CONFIG=/repo/.helm/repositories.yaml     -e HELM_REPOSITORY_CACHE=/repo/.helm/cache -e HELM_CACHE_HOME=/repo/.helm     -v "$root:/repo" -w /repo "$HELM_IMAGE" "$@"
}

mkdir -p "$root/.helm"
echo "== registering chart repositories"
i=0
for url in $(grep -h '^ *repository:' "$root"/helm/charts/*/Chart.yaml | awk '{print $2}' | sort -u); do
  i=$((i + 1))
  helm repo add "r$i" "$url" >/dev/null || { echo "could not add $url"; fail=1; }
done

for chartdir in "$root"/helm/charts/*/; do
  app="$(basename "$chartdir")"
  echo "== $app: dependency build"
  helm dependency build "helm/charts/$app" >/dev/null || { echo "dependency build failed: $app"; fail=1; continue; }

  for envdir in "$root"/helm/values/*/; do
    env="$(basename "$envdir")"
    # The base values are shared and incomplete on their own (the cluster name comes from the environment values).
    [ "$env" = "base" ] && continue
    args=()
    [ -f "$root/helm/values/base/$app.yaml" ] && args+=(-f "helm/values/base/$app.yaml")
    [ -f "$root/helm/values/$env/$app.yaml" ] && args+=(-f "helm/values/$env/$app.yaml")
    echo "== $app ($env): lint and template"
    helm lint "helm/charts/$app" "${args[@]}" || fail=1
    helm template "$app" "helm/charts/$app" --namespace check "${args[@]}" >/dev/null || fail=1
  done
done

if [ "$fail" -ne 0 ]; then echo "Helm checks FAILED"; exit 1; fi
echo "Helm checks passed"
