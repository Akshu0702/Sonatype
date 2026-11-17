# eks-cluster

An EKS cluster with a **private-only API endpoint**, encrypted secrets, all control plane logs, access by explicit entries, an OIDC provider for service-account roles, managed node groups and core add-ons.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Infrastructure-Diagrams

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## Secure by construction

| Choice | Setting |
|---|---|
| API endpoint | Private only. There is no option to make it public |
| Secrets | Encrypted with the customer-managed KMS key |
| Logs | api, audit, authenticator, controllerManager and scheduler to CloudWatch |
| Who can use it | EKS access entries only. The creator is **not** an administrator, and at least one entry must be a cluster administrator, so a cluster nobody can use is rejected at plan time |
| Nodes | IMDSv2 only with a hop limit of 1, instance tags not exposed in metadata, encrypted gp3 root volumes, detailed monitoring, no SSH (Session Manager through the managed SSM policy), boot-time hardening (below) |
| Pod identity | An OIDC provider, so add-ons use IAM roles for service accounts, not the node role |

## Node hardening

Every node group gets launch template user data (`node_hardening`, on by default) with two parts:

| Part | What it sets |
|---|---|
| Kubelet (nodeadm `NodeConfig`) | read-only port off; anonymous requests refused; webhook authentication and authorization; TLS 1.2 minimum; certificate rotation; streaming connections idle out after 5 minutes; default seccomp profile for pods (`seccompDefault`); a limit of 4096 processes per pod |
| Shell (runs at boot) | kernel settings in `/etc/sysctl.d` (the values the kubelet expects with `protectKernelDefaults`, plus reverse-path filtering); unused kernel modules (`cramfs`, `udf`, `usb-storage`, `dccp`, `sctp`, `rds`, `tipc` and others) disabled |

The node groups are limited to Amazon Linux 2023 types (`AL2023_*`); another operating system is rejected until it has its own hardening.
Two kubelet options are **off** by default because each needs something outside the module:

- `protect_kernel_defaults`: the kubelet refuses to start if the kernel settings differ from its expectations. The shell part sets them, but the order of that script against the kubelet start has not been proven on a real node. Turn it on in a non-production cluster first.
- `server_tls_bootstrap`: kubelet serving certificates are requested through certificate signing requests that nothing approves automatically. Turn it on only together with an approver, or metrics-server and `kubectl logs` break.

The alignment is with the CIS Amazon EKS Benchmark worker node recommendations; it is **not a certification** and the benchmark has not been run against a node. File permissions on the kubelet files (benchmark section 3) are not set by this module.

## Node groups

For workloads with zonal volumes, such as the Artifactory StatefulSet, define **one group per availability zone with a single subnet**, so a pod and its volume stay together. The tests use the Gate 1 layout: three Artifactory groups (`m6i.2xlarge`, 200 GB) and one system group (`m6i.large`, two nodes).

## Use

```hcl
module "eks" {
  source             = "../../modules/eks-cluster"
  name               = module.name_eks.name
  tags               = module.name_eks.tags
  kubernetes_version = "<decided version>"
  subnet_ids         = module.vpc_env1.subnet_ids["nodes"]
  kms_key_arn        = module.kms.key_arns["ebs"]

  access_entries = {
    platform_admin = { principal_arn = "<Identity Center administrator role ARN>" }
  }

  node_groups = {
    artifactory-a = { subnet_ids = [module.vpc_env1.subnet_ids["nodes"][0]], instance_types = ["m6i.2xlarge"], min_size = 1, desired_size = 1, max_size = 2, disk_size_gb = 200 }
    # artifactory-b and artifactory-c the same, in zones b and c
    system        = { subnet_ids = module.vpc_env1.subnet_ids["nodes"], instance_types = ["m6i.large"], min_size = 2, desired_size = 2, max_size = 3 }
  }
}
```

## Open points

- **`kubernetes_version` has no default on purpose.** The Kubernetes version is a decision (wiki Known Issues).
- The node role carries the managed CNI policy, which the AWS guidance prefers to give to the add-on through a service-account role instead. Moving it is a follow-up with the add-on roles.
- The EBS CSI driver add-on needs a service-account role and KMS permissions; add it through `cluster_addons` once the identity module exists.
- Load Balancer Controller, Cluster Autoscaler, Fluent Bit, metrics-server and External Secrets are Helm applications (the `eks-addons` change), not part of this module.
- Not in this module: Fargate, Karpenter, a second control plane in the DR region.

## Test

```
terraform init -backend=false
terraform test
```
