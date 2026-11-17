# Helm

Wrapper charts and values for the cluster add-ons. Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Helm-and-Argo-CD

**Status: dependencies built from `Chart.lock`, `helm lint` and `helm template` pass for every chart in both environments, and every value set here was checked to exist in the upstream chart. Not installed on a cluster.**

## Layout

| Path | Purpose |
|---|---|
| `charts/<app>/Chart.yaml` | A wrapper chart with **one pinned dependency** (the upstream chart) |
| `charts/<app>/Chart.lock` | The resolved dependency, with its digest. Committed |
| `values/base/<app>.yaml` | Settings that are the same in every cluster |
| `values/<env>/<app>.yaml` | Settings for one cluster: its name, VPC and role ARNs |

Values are nested under the upstream chart's name, for example `aws-load-balancer-controller:`.

## Add-ons and pinned versions

| App | Upstream chart | App version | Namespace |
|---|---|---|---|
| `aws-load-balancer-controller` | 3.6.2 | v3.6.2 | kube-system |
| `cluster-autoscaler` | 9.59.0 | 1.35.0 | kube-system |
| `metrics-server` | 3.14.0 | 0.9.0 | kube-system |
| `fluent-bit` | 0.58.3 | 5.1.3 | logging |
| `external-secrets` | 2.14.0 | v2.14.0 | external-secrets |
| `argo-cd` | 10.11.6 | v3.5.6 | argocd |

## Rules

- A version change is a pull request that updates `Chart.yaml` and `Chart.lock` together.
- The `cluster-autoscaler` image should match the cluster's Kubernetes minor version; choose that chart version when the Kubernetes version is decided.
- Values with `REPLACE_WITH_...` or `<account-id>` are placeholders. They come from the Terraform outputs of the environment (`eks-addons` role ARNs, the VPC id) and must be replaced before a sync. A CI check for leftover placeholders is a follow-up.
- No secret appears in any values file.

## Check

```
bash scripts/ci/helm-checks.sh
```

It rebuilds each chart's dependency from the lock file, lints it and renders it for every environment, using a pinned Helm image.
