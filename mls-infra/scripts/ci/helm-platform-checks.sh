#!/usr/bin/env bash
# Checks the platform charts (helm/platform) for every environment, and the Argo CD manifests for environments (argocd/environments):
#   - helm lint and helm template for every values file, against the chart that renders it (harbor.yaml -> harbor, the rest -> mls-service)
#   - the rendered Kubernetes objects and the Argo CD manifests against their schemas (kubeconform, strict)
#   - the rules the platform relies on: a pod that runs non-root, with a read-only root file system, no privilege escalation,
#     limits, probes; a prod application set that does NOT sync by itself; no uat or prod manifest inside argocd/apps (the lab Argo CD).
# Usage: bash scripts/ci/helm-platform-checks.sh   (set REPO_DIR to the absolute path of the repository if your shell rewrites paths)
set -uo pipefail

HELM_IMAGE="${HELM_IMAGE:-alpine/helm:3.19.0}"
KUBECONFORM_IMAGE="${KUBECONFORM_IMAGE:-ghcr.io/yannh/kubeconform:v0.7.0}"
root="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
fail=0
schemas=(-schema-location default -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json')

helm() {
  MSYS_NO_PATHCONV=1 docker run --rm -e HOME=/tmp -e HELM_REPOSITORY_CONFIG=/repo/.helm/repositories.yaml \
    -e HELM_REPOSITORY_CACHE=/repo/.helm/cache -e HELM_CACHE_HOME=/repo/.helm -v "$root:/repo" -w /repo "$HELM_IMAGE" "$@"
}
kubeconform() {
  MSYS_NO_PATHCONV=1 docker run --rm -i -v "$root:/repo:ro" -w /repo "$KUBECONFORM_IMAGE" "$@"
}

mkdir -p "$root/.helm"
helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || { echo "could not add the Harbor chart repository"; fail=1; }
echo "== harbor: dependency build"
helm dependency build helm/platform/charts/harbor >/dev/null || { echo "dependency build failed: harbor"; fail=1; }

for envdir in "$root"/helm/platform/values/*/; do
  env="$(basename "$envdir")"
  for values in "$envdir"*.yaml; do
    app="$(basename "$values" .yaml)"
    chart="helm/platform/charts/mls-service"
    [ "$app" = "harbor" ] && chart="helm/platform/charts/harbor"
    rel="helm/platform/values/$env/$app.yaml"
    echo "== $app ($env): lint, template, schema"
    helm lint "$chart" -f "$rel" >/dev/null || { echo "lint failed: $app $env"; fail=1; continue; }
    out="$(helm template "$app" "$chart" --namespace check -f "$rel")" || { echo "template failed: $app $env"; fail=1; continue; }
    printf '%s\n' "$out" | kubeconform -strict -summary "${schemas[@]}" - >/dev/null || { echo "schema check failed: $app $env"; printf '%s\n' "$out" | kubeconform -strict -summary "${schemas[@]}" -; fail=1; }

    if [ "$chart" = "helm/platform/charts/mls-service" ]; then
      printf '%s\n' "$out" | python3 "$root/scripts/ci/check-platform-render.py" "$app" "$env" || fail=1
    fi
  done
done

echo "== argocd/environments: schema and rules"
kubeconform -strict -summary "${schemas[@]}" argocd/environments >/dev/null || { echo "schema check failed: argocd/environments"; kubeconform -strict -summary "${schemas[@]}" argocd/environments; fail=1; }
if grep -rnE 'automated:' "$root/argocd/environments/prod" | grep -v 'Prod' >/dev/null 2>&1; then
  echo "prod must not sync automatically: found an automated sync policy in argocd/environments/prod"; fail=1
fi
if find "$root/argocd/apps" -mindepth 1 -maxdepth 1 \( -iname '*uat*' -o -iname '*prod*' \) | grep -q .; then
  echo "an uat or prod manifest is inside argocd/apps, which the lab Argo CD loads"; fail=1
fi

if [ "$fail" -ne 0 ]; then echo "Platform helm checks FAILED"; exit 1; fi
echo "Platform helm checks passed"
