# Well-Architected Review

How the lab design is reviewed against the AWS Well-Architected Framework, what each pillar means for this lab, and how findings are tracked.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

The pillar tables below record the **design intent** from the Gate 1 architecture. Every row is **Not built** until the matching story is closed; the review itself has not yet been run.

## How the review works

| Step | What happens |
|---|---|
| Define the workload | One workload per environment in the AWS Well-Architected Tool: the shared services, the lab (Env-1 and Env-2) and DR |
| Answer the questions | Each pillar has a named owner who answers the framework questions and records the reasoning, not only the choice |
| Rate the risks | High and medium risks become work items tagged `war-risk`, each with an owner and a target sprint |
| Decide trade-offs | A risk that is accepted rather than fixed is recorded as an exception ([Evidence and Exceptions](/Security-and-Compliance/Evidence-and-Exceptions)) |
| Re-review | At Gate 2 against the as-built system, and quarterly during managed services |

Proposed review points: **Gate 2** (as-built, with the validation evidence pack) and **quarterly** in managed services. The Gate 1 design is also read against the pillars below.

## Pillar 1: Operational excellence

| Design intent | Evidence | Status |
|---|---|---|
| Everything is infrastructure as code; changes go through reviewed pull requests and an approval | Repositories, branch policies, pipeline runs | Partly |
| Runbooks mirror the automated workflows and are updated with them | Wiki Operations section | Not built |
| Operational events open portal incidents; changes need approval, a risk note and a rollback plan | Portal records | Not built |
| Dashboards and alarms cover pods, nodes, databases, VPN tunnels, backups and certificates (see [Alert Catalogue](/Operations/Alert-Catalogue)) | CloudWatch and ELK | Not built |
| Learning from incidents: a short, blameless review for each P1, recorded in the wiki | Wiki | Not built |

## Pillar 2: Security

Covered in [Security and Compliance](/Security-and-Compliance). In short: VPN-only access with no public endpoints, MFA, one IAM role per function, customer-managed KMS keys, Secrets Manager, GuardDuty, Security Hub, Config and CloudTrail, with CIS baselines and OWASP checks in the pipeline.

| Design intent | Evidence | Status |
|---|---|---|
| Identity, network, data, workload and detection controls as in the Gate 1 security slide | Security Hub, Config, scan reports | Not built |

## Pillar 3: Reliability

| Design intent | Evidence | Status |
|---|---|---|
| Three availability zones; Artifactory runs three pods, IQ Server two nodes, the portal and Keycloak two each | Failover test report | Not built |
| Multi-AZ PostgreSQL; S3 for filestores and artifacts | Config and test report | Not built |
| Harbor runs two or more replicas with external PostgreSQL and S3; its bundled cache is a single instance, a known limit ([ADR-0012](/Decisions/ADR-0012-No-Kafka-Or-Redis-For-Now)) | Failover test report | Not built |
| Backups on a schedule, copied to ap-south-2; point-in-time recovery | Backup and restore test | Not built |
| Region failure handled by DR in Hyderabad (pilot light) | DR drill report | Not built |
| Workflows retry and roll back by design; a failed rollout opens a blocker | Step Functions history | Not built |
| Service quotas and limits checked ahead of provisioning peaks | Quota alarms | Not built |

Recovery objectives are **estimates** until the DR drill sets the actual values.

## Pillar 4: Performance efficiency

| Design intent | Evidence | Status |
|---|---|---|
| Artifactory nodes sized on 8 vCPU and 32 GiB; gp3 storage; S3 for binaries | BOM, load observations | Not built |
| CI runners and agents scale with demand | Runner metrics | Not built |
| Sizing reviewed after the first weeks of real use, from metrics rather than guesses | CloudWatch and Compute Optimizer review | Not built |
| Firewall policy evaluation latency observed under test-matrix load | Test harness results | Not built |

## Pillar 5: Cost optimisation

| Design intent | Evidence | Status |
|---|---|---|
| Every provisioned environment has a TTL; a reaper removes expired ones | Reaper logs, cost report | Not built |
| Cost allocation tags on every resource; a cost-centre tag when finance names the code | Tag compliance report | Not built |
| Budgets and alarms per account | AWS Budgets | Not built |
| Retention rules for snapshots, images, logs and evidence | Lifecycle policies | Not built |
| Monthly cost review shared with Sonatype, covering infrastructure cost pass-through (see [Total Cost of Ownership](/Architecture/Total-Cost-of-Ownership)) | Cost report | Not built |

Trade-off to record: a NAT gateway in each availability zone costs more than one shared gateway but avoids a cross-zone dependency. See [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps).

## Pillar 6: Sustainability

| Design intent | Evidence | Status |
|---|---|---|
| India regions (Mumbai and Hyderabad) chosen for data residency | Architecture | Not built |
| DR is a pilot light, scaled to zero until needed | DR configuration | Not built |
| Temporary environments are removed automatically; no idle test hosts | Reaper logs | Not built |
| Right-sizing and Graviton-based database instances where they meet the need | BOM | Not built |

## Related

[Architecture](/Architecture), [Security and Compliance](/Security-and-Compliance), [ADR-0006](/Decisions/ADR-0006-Well-Architected-Framework-For-Design-Review).
