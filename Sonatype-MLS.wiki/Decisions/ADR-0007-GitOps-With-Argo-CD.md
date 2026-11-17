# ADR-0007: GitOps with Argo CD

**Status:** Proposed

## Context
Artifactory and Harbor run on two isolated EKS clusters. Changes such as a plugin version bump must be reviewed, repeatable and reversible, and the clusters must not be changed by hand.

## Decision
Run Argo CD in each cluster. The desired state is in `mls-infra` (`helm/` and `argocd/`). Lab clusters do not sync automatically; the Step Functions workflow requests a sync after approval. Stateful applications never prune automatically.

## Consequences
- Every cluster change is a pull request and is visible in Git.
- Rollback is a revert and a sync.
- One more component to run and secure per cluster.
- A pipeline-driven Helm release remains the fallback if Argo CD is not adopted.
