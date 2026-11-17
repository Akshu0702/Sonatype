# Argo CD for the UAT and Prod clusters (not applied anywhere)

**No UAT or Prod cluster exists, and none of these manifests is applied.** They are for the Argo CD that will run **inside** each of those clusters, which manages only its own cluster (ADR-0007). They are deliberately **not** under `argocd/apps`, which is what the lab Argo CD loads: the lab clusters must never see them. A check (`scripts/ci/helm-platform-checks.sh`) fails the build if a uat or prod manifest appears under `argocd/apps`.

| File | Purpose |
|---|---|
| `<env>/project.yaml` | The project `platform-<env>`: one source repository, this cluster as the only destination |
| `<env>/cluster-addons.yaml` | The add-ons (load balancer controller, autoscaler, metrics, logging, External Secrets) from the same wrapper charts the lab uses, with `helm/values/<env>` |
| `<env>/secret-store.yaml` | The External Secrets store: AWS Secrets Manager, through the add-on's IAM role |
| `<env>/harbor.yaml` | Harbor, from `helm/platform/charts/harbor`. It never prunes and never syncs by itself |
| `<env>/platform-services.yaml` | The four platform services, from `helm/platform/charts/mls-service` and `helm/platform/values/<env>` |

## Sync rules

| | UAT | Prod |
|---|---|---|
| Platform services | Automatic sync, self-heal, **never prunes** | **No automated sync.** A change reaches the cluster only when someone approves and requests the sync |
| Harbor and add-ons | No automated sync | No automated sync |

The change itself is a pull request that changes an image tag in `helm/platform/values/<env>/<service>.yaml` (opened by `scripts/ci/open-promote-pr.py`). Merging it is the decision; for Prod the sync is a second, separate approval.

## Bring-up order (when an environment is approved)

Install Argo CD once by hand (`helm/charts/argo-cd`), apply `project.yaml`, then `cluster-addons.yaml` and `secret-store.yaml`, then `harbor.yaml`, and only then `platform-services.yaml` (the services cannot start before Harbor holds their images).

## Validate

```
bash scripts/ci/helm-platform-checks.sh
```
