# Security and Compliance

The standards the lab is built and operated against, how each is enforced, and where the evidence comes from.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Principles

1. **Published standards, not house rules.** Where a recognised benchmark exists, we use it and record deviations.
2. **Enforced in the pipeline, not by memory.** Images, infrastructure and code are checked automatically; the check is the control.
3. **Evidence is a by-product.** Scan and test outputs are stored and become part of the Gate 2 pack.
4. **Report mode first, then blocking.** A gate that is always ignored is worse than none, so findings are triaged before a gate blocks.
5. **Every deviation is recorded** with a reason, an owner and an expiry (see [Evidence and Exceptions](/Security-and-Compliance/Evidence-and-Exceptions)).

## Standards and how they are enforced

| Standard | Applies to | Enforced by | Evidence | Status |
|---|---|---|---|---|
| CIS Benchmark for the chosen Linux distribution (Level 1 server profile) | Provisioned environments; portal, Keycloak, IQ Server, ELK and CI hosts | Hardened AMI from EC2 Image Builder; Inspector scan of the image and running instances | Scan report per AMI version | Not built |
| CIS Amazon EKS Benchmark | EKS clusters lab-env1 and lab-env2 | AWS-managed control plane; node and workload checks with kube-bench; Pod Security Standards | kube-bench report per cluster | Not built |
| CIS AWS Foundations Benchmark | Every AWS account | Security Hub standard and AWS Config rules | Security Hub score and findings | Not built |
| AWS Foundational Security Best Practices | Every AWS account | Security Hub standard | Security Hub findings | Not built |
| CIS PostgreSQL Benchmark | RDS and on-instance PostgreSQL | Parameter groups, enforced TLS, least-privilege roles, scripted checks | Check report per database | Not built |
| OWASP ASVS (Level 1 for every service; Level 2 for the portal API, provisioning API and Keycloak configuration) | Portal and automation | Design review, tests and scans | ASVS checklist per release | Not built |
| OWASP API Security Top 10 | Portal API, provisioning API | Authorisation test suite in CI | Test run per release candidate | Not built |
| Secrets, code and dependency scanning | Every repository, every image | gitleaks, Semgrep, Trivy in CI; ECR and Inspector for images | SARIF per build | Not built |
| Vendor hardening guides | Artifactory, IQ Server, Harbor, Keycloak, GitLab | Applied per product and recorded | As-built document | Not built |
| Cryptography and data residency | All data stores | TLS 1.2 or later; KMS customer keys; encryption at rest; data kept in India regions; AWS Config rules | Config compliance report | Not built |
| Identity and access | All humans and services | MFA, IAM Identity Center, one IAM role per function, break-glass role, quarterly recertification | Access review record | Not built |
| AWS Well-Architected Framework (all six pillars) | The lab architecture | Structured review in the Well-Architected Tool; risks tracked as work items | Review report and improvement plan | Not built |

**Benchmark versions are pinned.** Each benchmark's version is recorded in the control register the day it is adopted and changed only by a reviewed pull request. A newer published version is a planned change, not a silent update.

## Related

- [Hardening Baselines](/Security-and-Compliance/Hardening-Baselines)
- [Secure Development Standards](/Security-and-Compliance/Secure-Development-Standards)
- [Evidence and Exceptions](/Security-and-Compliance/Evidence-and-Exceptions)
- [Well-Architected Review](/Architecture/Well-Architected-Review)
- [ADR-0005: CIS benchmarks as the hardening baseline](/Decisions/ADR-0005-CIS-Benchmarks-As-The-Hardening-Baseline)
