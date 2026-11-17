# Helm and Argo CD

How workloads on the two EKS clusters are packaged, configured and deployed.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Where the code lives

`mls-infra`, folders `helm/` and `argocd/`.

| Folder | Holds |
|---|---|
| `helm/charts/<app>` | A thin wrapper chart per application: the upstream chart as a pinned dependency, plus our templates |
| `helm/values/<env>/<app>.yaml` | The values for one application in one environment |
| `argocd/projects` | Argo CD projects and their permissions |
| `argocd/applicationsets` | The generators that create one application per cluster |
| `argocd/apps/<env>` | Application definitions |

## Applications

| Application | Chart | Cluster | Status |
|---|---|---|---|
| Artifactory (3 nodes) | `jfrog/artifactory` | Env-1, Env-2 | Not built |
| Harbor | `harbor/harbor`, bundled cache (no external Redis) | Env-2 | Not built |
| AWS Load Balancer Controller | `aws-load-balancer-controller` 3.6.2 (eks-charts) | Both | Not built (draft pull request) |
| EBS CSI driver | EKS managed add-on, installed by the `eks-addons` Terraform module (not Helm) | Both | Not built (draft pull request) |
| Cluster Autoscaler | `cluster-autoscaler` 9.59.0 (kubernetes/autoscaler) | Both | Not built (draft pull request) |
| Fluent Bit | `fluent-bit` 0.58.3 (fluent) | Both | Not built (draft pull request) |
| metrics-server | `metrics-server` 3.14.0 (kubernetes-sigs) | Both | Not built (draft pull request) |
| External Secrets | `external-secrets` 2.14.0 (external-secrets) | Both | Not built (draft pull request) |
| Argo CD | `argo-cd` 10.11.6 (argoproj), bundled cache, no external Redis | Both | Not built (draft pull request) |

Chart versions are pinned in each wrapper chart's `Chart.yaml`, resolved in `Chart.lock`, and moved by pull request. The charts and the Argo CD manifests are validated before a pull request opens: `helm lint` and `helm template` for every chart in both environments, every setting checked against the upstream chart's defaults, and the Argo CD manifests validated against the Argo CD schemas (see `helm/README.md` and `argocd/README.md` in `mls-infra`).

**Open decisions:** the chart repositories must be reachable from the clusters (egress allow-list, vendored charts or a mirror); placeholders for account ids, the VPC id and the Logstash host must come from Terraform outputs; the autoscaler chart must match the Kubernetes version.

## Artifactory values that matter

| Value | Purpose |
|---|---|
| `plugin.version` | The Firewall plugin bundle to load. Changing it drives the rollout ([ADR-0002](/Decisions/ADR-0002-Immutable-Plugin-Bundle)) |
| Init container (`copy-tomcat-listener`) image tag | Derived from the same version variable as the application |
| `frontend.image.tag` | Pinned and checked against the registry before an upgrade |
| Database, filestore and workload identity | A secret for the database, S3 with a workload identity role; no static keys |
| Replicas and pod placement | Three replicas, spread across availability zones |

## GitOps model

Argo CD runs in each cluster, so Env-1 and Env-2 stay isolated. A change reaches a cluster only through Git.

| Rule | Reason |
|---|---|
| The desired state is the `develop` branch of `mls-infra`; a release uses a tag | One source of truth, and a version that can be named |
| Applications are created from an ApplicationSet (app of apps) | The same pattern on both clusters |
| Lab clusters do not sync automatically. The Step Functions workflow requests a sync after approval | An approval gate and the pre-flight checks come first |
| Stateful applications (Artifactory, Harbor) never prune automatically | A removed manifest must not delete data |
| A rollback is a revert of the values change, then a sync | The previous bundle version comes back |
| Secrets come from External Secrets, never from Git | No secret in a repository |
| Argo CD access uses single sign-on and the same roles as the portal | One identity model |

## Related

[Automation](/Automation), [ADR-0007](/Decisions/ADR-0007-GitOps-With-Argo-CD), [Terraform Modules](/Infrastructure/Terraform-Modules).
