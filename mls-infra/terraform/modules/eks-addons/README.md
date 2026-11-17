# eks-addons

The AWS side of the cluster add-ons: an **IAM role for each add-on's Kubernetes service account** (IRSA), plus the EBS CSI driver as an EKS managed add-on.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Helm-and-Argo-CD and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS. Needs Terraform 1.9 or later.**

The Helm side (the charts and their values) is in `helm/` and is a separate change. The Helm values must use the service account names below.

## Roles

| Role (suffix) | Add-on | Service account (default) | Permissions |
|---|---|---|---|
| `ebs-csi` | EBS CSI driver (managed add-on, installed by this module) | `kube-system/ebs-csi-controller-sa` | AWS managed `AmazonEBSCSIDriverPolicy`, plus use of the volume KMS keys and grants limited to AWS resources |
| `load-balancer-controller` | AWS Load Balancer Controller | `kube-system/aws-load-balancer-controller` | The controller's **official IAM policy, unchanged** |
| `cluster-autoscaler` | Cluster Autoscaler | `kube-system/cluster-autoscaler` | Describe calls, and scale or terminate **only** Auto Scaling groups tagged for this cluster |
| `external-secrets` | External Secrets | `external-secrets/external-secrets` | Read the listed Secrets Manager secrets (no write) and decrypt them |

Each role can be assumed **only by its own service account, in its own namespace, on this cluster**. Fluent Bit ships logs to the log platform, so it needs no AWS role. metrics-server needs none.

## Official policy source

`policies/aws-load-balancer-controller.json` is the unmodified `docs/install/iam_policy.json` from the `v3.6.2` tag of `kubernetes-sigs/aws-load-balancer-controller` (SHA-256 starts `16f232c9d9f79366`). When the controller chart is upgraded, replace the file from the matching tag in the same pull request.

## Use

```hcl
module "eks_addons" {
  source            = "../../modules/eks-addons"
  name_prefix       = "mls-role-prod-lab"
  cluster_name      = module.eks.cluster_name
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_issuer       = module.eks.oidc_issuer
  tags              = module.name_roles.tags

  ebs_kms_key_arns              = [module.kms.key_arns["ebs"]]
  external_secrets_secret_arns  = ["arn:aws:secretsmanager:ap-south-1:<account>:secret:mls/prod/*"]
  external_secrets_kms_key_arns = [module.kms.key_arns["secrets"]]
}
```

Put `module.eks_addons.role_arns[...]` into the Helm values as the service account annotation `eks.amazonaws.com/role-arn`.

## Open points

- Role names use `<name_prefix>-<component>` and so extend the naming standard's three-digit id; to be recorded in the standard.
- The cluster autoscaler relies on the Auto Scaling groups of the managed node groups carrying the `k8s.io/cluster-autoscaler/enabled` and `k8s.io/cluster-autoscaler/<cluster>` tags. Confirm on the first real cluster that EKS adds them; if not, add them as node group tags in the `eks-cluster` module.
- The node role in `eks-cluster` still carries the managed CNI policy; moving it to a role for the VPC CNI add-on is a follow-up.

## Test

```
terraform init -backend=false
terraform test
```
