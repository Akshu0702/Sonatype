# Argo CD

Argo CD runs in **each** cluster and manages only that cluster. Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Helm-and-Argo-CD and ADR-0007

**Status: the three manifests validate against the Argo CD custom resource schemas with kubeconform (3 valid, 0 invalid). Not applied to a cluster.**

| Path | Purpose |
|---|---|
| `projects/lab-addons.yaml` | The project: one source repository, this cluster as the only destination |
| `apps/<env>/cluster-addons.yaml` | One ApplicationSet per cluster; it creates an application for each add-on |

## Rules

- **No automated sync.** The workflows request a sync after approval. This is the rule for lab clusters; stateful applications never prune.
- Repository credentials are never in Git; they are delivered by External Secrets.
- Argo CD itself is installed from `helm/charts/argo-cd` (bootstrap by hand once per cluster, then it manages itself through its own application).

## Validate

```
docker run --rm -v "$PWD/argocd:/argocd:ro" ghcr.io/yannh/kubeconform:v0.7.0 -strict -summary   -schema-location default   -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' /argocd
```

## Open points

- **A ClusterSecretStore for Secrets Manager is not included.** Add it once the External Secrets chart's custom resource version is confirmed on a real cluster.
- **Chart repositories need egress.** The wrapper charts pull their dependencies from public chart repositories when Argo CD renders them. The cluster's outbound allow-list must include those hosts, or the charts must be vendored or mirrored (for example in ECR). Decide before the first sync.
- The repository URL is the Azure DevOps address of `mls-infra`; change it if the repository moves.
