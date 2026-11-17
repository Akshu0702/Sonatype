# Known Issues and Gaps

An honest list. Severity: **H** needs attention before the next milestone, **M** planned, **L** nuisance. Updated as items close.

| # | Area | Issue | Sev | Plan | Needed by |
|---|---|---|---|---|---|
| 1 | Licences | Artifactory, Enterprise X and Cloudsmith licences are on the critical path | H | Orders placed; track weekly | Demo 2 |
| 2 | Firewall | IQ HA licence scope not confirmed | H | Confirm with Sonatype | Demo 1 |
| 3 | Scope | Whether JFrog Enterprise X and Artifactory SaaS are one platform or two | M | Confirm with Sonatype | Demo 2 |
| 4 | Scope | Nexus Repository Manager is not in the architecture | M | Confirm out of scope | Gate 1 |
| 5 | Plan | Demo 1 date differs between documents (06-Nov vs 09-Nov) | M | Hold Fri 06-Nov | Demo 1 |
| 6 | Design | Linux distribution and PostgreSQL versions for provisioned environments | M | Confirm with Sonatype | Demo 3 |
| 7 | DR | Availability of Network Firewall, GuardDuty-EKS, Backup and Transit Gateway in ap-south-2 not verified | M | Verify | Gate 2 |
| 8 | DR | Recovery objectives differ between two Gate 1 slides | L | Align; final values from the DR drill | Gate 2 |
| 9 | Operations | Support hours, paging tool and notification channel not chosen | M | Decide | Gate 2 |
| 10 | Automation | Plugin deployment is a manual runbook today | H | Immutable bundle flow | Demo 2 |
| 11 | Security | CIS benchmark profile (Level 1 or 2) and benchmark versions not yet chosen | M | Decide in ADR-0005; pin in the control register | Gate 1 |
| 12 | Security | CIS hardening available in Image Builder depends on the Linux distribution, which is unconfirmed | M | Confirm the distribution, then confirm the hardening component | Demo 3 |
| 13 | Security | Scan thresholds for moving gates from report to blocking not set | L | Set after first triage | Gate 2 |
| 14 | Architecture | Well-Architected review not yet run; pillar owners not named; risks not baselined | M | Name owners; run at Gate 2 | Gate 2 |
| 15 | Architecture | Trade-off to record: NAT gateway per availability zone (reliability) against one shared gateway (cost) | L | Decide and record | Gate 2 |
| 16 | Delivery | Argo CD against a pipeline-driven Helm release is not yet decided | M | Decide in ADR-0007 | Demo 2 |
| 17 | Terraform | Terraform is pinned to 1.9.8 in CI (container image), but the AWS provider is a range (`~> 5.0`), no lock-file policy is set, and the state locking mechanism (DynamoDB or S3 lock file) is undecided | M | Pin the provider and decide locking and lock files before the first environment is applied | Demo 1 |
| 18 | API | OpenAPI specifications not yet written; prototype API not compared with the design | M | Write the portal spec first | Demo 2 |
| 19 | Data | Prototype database schema not reconciled with the data model; organisation-based row-level security not confirmed | M | Reconcile before the first migration | Demo 2 |
| 20 | Audit | Audit retention periods and tamper evidence (hash chain) not agreed with Sonatype | M | Agree retention and decide ADR | Gate 2 |
| 21 | Workflows | Step Functions testing approach to be confirmed when the first state machine is built | L | Choose and record | Demo 2 |
| 22 | Alerting | Paging tool, on-call rota and out-of-hours arrangement for P1 and P2 not decided | M | Decide with Sonatype; delivery hours are 11 AM to 7 PM IST | Gate 2 |
| 23 | Alerting | Alert thresholds are starting points and the SLA response targets are not agreed | M | Agree SLAs; tune after the first weeks | Gate 2 |
| 24 | Monitoring | Firewall canary design (known-bad and known-good components per repository and format) not defined | M | Define with the test matrix | Demo 3 |
| 25 | Monitoring | Whether Sonatype users also get a read-only public status page is not decided | L | Decide with Sonatype | Gate 2 |
| 26 | Cost | Format, cadence and audience of the monthly cost report not agreed; cost-centre tag value not named by finance | M | Agree with Sonatype and finance | Gate 2 |
| 27 | Roles | The Gate 1 deck says roles are issued as sign-in claims; the design assigns roles in the portal database | M | Claims identify only; correct the deck wording | Demo 1 |
| 28 | Roles | First-line operations engineers are not a named role | M | Use Infra engineer at first or add a role | Demo 2 |
| 29 | Roles | A read-only role for Sonatype management users is not defined | M | Build from existing features, or add a built-in role | Demo 2 |
| 30 | Roles | Custom roles are proposed, as in Baton; to be confirmed with Sonatype | L | Confirm | Demo 2 |
| 31 | Feature flags | Two-person confirmation for sensitive feature changes is proposed, not agreed | M | Decide with Sonatype | Demo 2 |
| 32 | Feature flags | The default role and feature assignment needs Sonatype review | M | Review the matrix | Demo 2 |
| 33 | Roles | Baton's built-in roles cannot be edited; here the administrator may adjust the scopes of built-in roles (they still cannot be deleted or renamed) | M | Confirm this deliberate difference with Sonatype | Demo 2 |
| 34 | Roles | Group reach is proposed for environments only; reach for other things is undecided | L | Confirm | Demo 2 |
| 35 | Roles | The "working as" persona rule is adopted from Baton; to be confirmed for the lab | L | Confirm | Demo 2 |
| 36 | IAM | IAM is a module of the Portal API; whether to split it into its own service is undecided | L | Decide at design review | Demo 2 |
| 37 | Harbor | Harbor's bundled cache is a single instance, so Harbor is highly available at the application tier only (SOW asks for Harbor HA) | M | State the limit to Sonatype; restart runbook; revisit per ADR-0012 | Demo 2 |
| 38 | Audit | Audit delivery from automation relies on retries; there is no queue, so events could be lost if the portal is down longer than the retries | M | Test the failure case; adopt a queue only if needed | Gate 2 |
| 39 | Portal | Portal rate limits are per node, so with two nodes the effective limit can double | L | Accept, or revisit per ADR-0012 | Gate 2 |
| 40 | Terraform | The first Trivy scan of the modules reported four findings: VPC flow logs off by default (network-vpc, medium), no IAM database authentication (rds-postgres, medium), and no access logging on the S3 and state buckets (low). Each is an option in the module, not yet a default | M | Decide per module whether to make it the default; scans are in report mode | Demo 2 |
| 41 | CI | Trivy scans module code without variable values, so `for_each` resources are only partly checked | L | Scan the `live/` compositions once they exist | Demo 3 |
| 42 | CI | **Mostly resolved on 14 Nov 2026.** Microsoft-hosted minutes ran out on 3 Nov. A self-hosted build agent (`mls-agent-001`, pool `mls-agents`, t3.xlarge in Mumbai) now runs `ci-mls-infra`: run 2576 passed all four jobs. Still open: the fix (PR 976) is not merged, so `develop` still defaults to Microsoft-hosted agents; each pipeline must be authorised for the pool once; build validation is not attached to `main` or `develop` | M | Merge PR 976, make `mls-agents` the default pool, authorise the other pipelines, then attach build validation | Demo 2 |
| 43 | EKS | The Kubernetes version is not chosen (the eks-cluster module has no default) | M | Decide with the Artifactory and Harbor chart requirements | Demo 2 |
| 44 | EKS | The add-ons are in draft pull requests (roles and the EBS CSI add-on in `eks-addons`; charts and Argo CD in `helm/` and `argocd/`) but nothing is applied. The node role still carries the managed CNI policy | M | Review, then apply through the live composition | Demo 2 |
| 45 | Ansible | Container tests do not exercise services, sysctl, audit rules, `sshd -t` or the ssm_agent role; the roles have not run on a real host | M | First run on a dev host through Systems Manager | Demo 2 |
| 46 | Helm | The clusters must reach the public chart repositories when Argo CD renders the wrapper charts; the egress allow-list does not include them, and vendoring or a mirror is undecided | M | Decide: allow-list, vendor the charts, or mirror them in ECR | Demo 2 |
| 47 | Helm | Values still hold placeholders (account id, VPC id, Logstash host) with no automated check for leftovers | M | Fill from Terraform outputs; add a CI check | Demo 2 |
| 48 | Helm | The cluster-autoscaler chart version must match the Kubernetes version, which is undecided (Known Issue 43) | L | Choose together | Demo 2 |
| 49 | Helm | No ClusterSecretStore for Secrets Manager, no Argo CD SSO and no repository credentials yet | M | Add after the first real cluster | Demo 3 |
| 50 | Logging | Fluent Bit's output to Logstash (protocol, port, TLS and host) is a placeholder; the Logstash input is not designed | M | Agree with the log platform design | Demo 2 |
| 51 | Nodes | Node hardening is applied at boot through user data; whether EKS merges the partial `NodeConfig` on managed node groups and the boot order of the kernel settings against the kubelet are unproven on a real node | M | Boot one node in a non-production cluster and check the kubelet configuration; then decide on `protectKernelDefaults` | Demo 2 |
| 52 | Nodes | No hardened or baked node image: choose between boot-time hardening (now), a baked Amazon Linux 2023 image (Packer with the Ansible roles) and Bottlerocket | M | Decide with the Linux distribution decision | Demo 2 |
| 53 | Nodes | Kubelet file permissions and the CIS EKS Benchmark worker node checks are not verified; kube-bench has not been run | M | Run kube-bench on the first real cluster and record the result | Demo 3 |
| 54 | CI | The application repositories have pipelines for security scans, OpenAPI and state machine checks only (pull requests 827 to 831); build and unit-test jobs wait for the language and framework decisions. No pipeline is registered or required by a policy yet, because no agent can run it (Known Issue 42) | H | Decide the stacks; provide a self-hosted agent or hosted minutes; then register and require the checks | Demo 2 |
| 55 | CI | The `mls-infra` security script (pull request 739) prints "SECURITY_ENFORCE=true" even when a scanner failed to run in report mode; the application repository scripts word this correctly | L | Align the infra script | Demo 2 |
| 56 | CI | A self-hosted agent keeps its workspace between jobs, and the checks run tools in containers as root, so files left in the checkout (`.terraform`, `.helm`) block the next checkout. Fixed for the CI pipeline in PR 976 (`fix-workspace-ownership.sh` at the end of every job); the **deploy and destroy pipelines (merged) have the same problem** and are not fixed yet | M | Add the same step to the deploy and destroy pipelines | Demo 2 |
| 57 | CI | The build agent runs jobs whose containers can use its instance role (metadata hop limit 2) and the role carries the deploy policies, so any pipeline authorised for the pool can deploy to Mumbai. The deploy pipelines still expect access keys rather than the role | M | Authorise only the pipelines that should deploy; change the deploy pipelines to use the role (task 7985) | Demo 2 |
