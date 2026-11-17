# Hardening Baselines

How each layer is hardened. Every item is **Not built** until the infrastructure story that creates it is closed.

[[_TOC_]]

## Linux images

Provisioned environments and the lab's own hosts use images built by EC2 Image Builder.

1. Start from the vendor image of the chosen distribution.
2. Apply the CIS hardening for that distribution (Level 1 server profile). A Level 2 control is added only where a reason is recorded.
3. Scan the result with Inspector. A critical finding stops promotion.
4. Version the image, record the benchmark version and scan result in its manifest, and copy it to ap-south-2.
5. Patch running instances through Systems Manager Patch Manager baselines.
6. Retire old image versions by a stated retention rule.

Open dependency: the distribution is not confirmed, and the CIS hardening available in Image Builder depends on it. See [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps).

## Amazon EKS

| Control | Setting |
|---|---|
| API endpoint | Private only |
| Workload identity | IAM roles for service accounts; no node-wide credentials |
| Pod security | Pod Security Standards; `restricted` where the workload allows, `baseline` otherwise, with the exception recorded |
| Images | From the private registry only, scanned on push |
| Network | Network policies; Env-1 and Env-2 cannot reach each other |
| Secrets | External Secrets from Secrets Manager; none in manifests |
| Node and workload checks | kube-bench against the CIS EKS Benchmark, run on a schedule |
| Audit | Control plane logs shipped to the log platform; GuardDuty EKS protection on |

### Worker nodes

Applied to every managed node group by the `eks-cluster` module at boot (draft pull request 775, stacked on 750; Amazon Linux 2023 only). Status: **written and unit-tested against a mocked provider; no node has been booted**.

| Area | Setting |
|---|---|
| Access | No SSH and no key pair; sessions through Session Manager only (tested) |
| Instance metadata | IMDSv2 required, hop limit 1, instance tags not exposed |
| Storage | Root volume encrypted with the customer-managed key |
| Kubelet | Read-only port off; anonymous requests refused; webhook authentication and authorization; TLS 1.2 minimum; certificate rotation; streaming idle timeout 5 minutes; default seccomp profile for pods; 4096 processes per pod |
| Kernel | Sysctl values the kubelet expects; reverse-path filtering; unused modules disabled (`cramfs`, `freevxfs`, `hfs`, `hfsplus`, `jffs2`, `udf`, `usb-storage`, `dccp`, `sctp`, `rds`, `tipc`) |
| Off until chosen | `protectKernelDefaults` (needs the boot order proven on a real node); kubelet serving-certificate bootstrap (needs a certificate approver) |
| Not covered | Kubelet file permissions; a hardened or baked image; running kube-bench to prove the result; this is alignment with the CIS EKS Benchmark, not certification |

## AWS accounts

One account per environment. Service control policies set the guardrails. CloudTrail runs with log-file validation, AWS Config records and evaluates, GuardDuty and Security Hub are on in every account, S3 Block Public Access is on, and encryption uses customer-managed KMS keys.

## PostgreSQL

TLS required; no public access; one least-privilege role per application; parameter groups under version control; automated backups with point-in-time recovery; connection and statement logging forwarded to the log platform; credentials from Secrets Manager.

## Products

Artifactory, IQ Server, Harbor, Keycloak and GitLab follow the vendor hardening guidance. At minimum: anonymous access off, administrators through SSO with MFA, service tokens scoped and rotated, default credentials changed, management endpoints reachable only from the shared services network. What was applied is recorded in the as-built document for each product.

## Related

[Security and Compliance](/Security-and-Compliance), [Infrastructure](/Infrastructure).
