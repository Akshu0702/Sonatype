# Total Cost of Ownership

How the cost of the Managed Lab is built up, what drives it, how it is tracked and how it is reviewed. This page describes the **structure** of the cost model. **No amounts are published on the wiki**: prices, rates and totals are held in the commercial model and in the commercial schedule.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** Cost tracking, budgets and the monthly cost review start when the first environment exists.

## What the total covers

Total cost of ownership is the full cost to Sonatype of the lab over its first year: the one-time build, the recurring managed service, the cloud hosting, the software subscriptions, and any variable consumption billed on actuals. The first year runs from signature through the build and the first twelve months of the managed service.

## Cost lines

| Cost line | What it covers | Charge type | Quantities come from |
|---|---|---|---|
| Discovery, architecture, implementation and integration | The six-week build (SOW #8): foundation, Firewall HA, repositories, CI/CD, automation, validation, knowledge transfer | One-time, paid on accepted milestones | Delivery plan |
| ITSM setup and integration | Portal service-desk features and integrations | One-time | Delivery plan |
| Managed-lab services | Operating the lab after go-live acceptance: monitoring, requests, provisioning, patching, upgrades, DR tests, SLA reporting | Recurring, monthly | Service scope and staffing model |
| Hosting: fixed platform | The always-on environment: EKS, EC2, databases, storage, network, DR pilot light | Recurring | [Bill of Materials](/Architecture/Bill-of-Materials) |
| Hosting: variable consumption | Temporary test environments, data transfer | Variable, billed on actuals at published unit rates | Provisioning usage |
| Platform security tooling | Threat detection, vulnerability scanning, secrets and key management | Recurring | [Security and Compliance](/Security-and-Compliance) |
| Repository software and subscriptions | Artifactory, Enterprise X or Artifactory SaaS, Cloudsmith, Harbor support | Recurring | Software and licences in the BOM |
| CI/CD platform | Baseline platform licence | Recurring | Software and licences in the BOM |
| Service-management subscription | Agent licences, if a separate tool is used | Recurring | Decision pending |
| Third-party handling fee | Any fee on third-party hosting, licences and subscriptions, **disclosed as a separate line** | Recurring | Commercial terms |
| Optional services | Test execution by Motivity engineers, additional CI/CD platforms, additional configuration automation | Optional, priced per unit | Change request |
| Travel, transition and other | Travel at approved actuals; transition support priced separately | As approved | Agreement |

## From the BOM to a cost line

Every BOM group maps to exactly one cost line, so nothing is priced twice or missed.

| BOM group | Cost line |
|---|---|
| EKS clusters and nodes, EC2 hosts, RDS, S3, EFS, ECR | Hosting: fixed platform |
| Transit Gateway, VPN, NAT gateways, Network Firewall, load balancers, Route 53, VPC endpoints | Hosting: fixed platform |
| Lambda, Step Functions, API Gateway, EventBridge Scheduler | Hosting: variable consumption (small), or fixed if usage is steady |
| Ephemeral environments (hardened Linux with PostgreSQL) | Hosting: variable consumption |
| KMS, Secrets Manager, GuardDuty, Security Hub, Config, Inspector, CloudTrail | Platform security tooling |
| CloudWatch, SNS, AWS Backup, SES, Budgets | Hosting: fixed platform |
| DR region (pilot light) | Hosting: fixed platform |
| Artifactory, Enterprise X or SaaS, Cloudsmith, Harbor | Repository software and subscriptions |
| GitLab, Azure DevOps, CodePipeline and CodeBuild | CI/CD platform |
| Terraform, Ansible, Helm, Argo CD, Keycloak, ELK | No licence cost if open source or basic tier; hosting only |
| Sonatype IQ Server and Firewall | Licence provided by Sonatype |

## How the total is calculated

The formulas are fixed; only the inputs change.

```
Year-1 TCO =
    one-time build and ITSM setup
  + hosting during the build        (fixed monthly x ramp share x build months)
  + managed service                 (monthly fee x managed-service months in the year)
  + hosting after go-live           (fixed monthly x months)
  + variable consumption            (actuals, budgeted as environment-hours x unit rate)
  + software and subscriptions      (annual)
  + platform security tooling       (annual)
  + third-party handling fee        (disclosed rate x third-party lines)
  + optional services and other     (as approved)

Variable consumption =
    concurrent environments x run-hours per day x working days x blended unit rate
  + data transfer above the included budget
```

## Inputs

| Input | Source |
|---|---|
| Build duration and plan | Delivery plan and SOW #8 |
| Managed-service term and fee | Commercial agreement |
| Concurrent temporary environments and run-hours per day | SOW scope; observed usage once running |
| Unit rates per environment type (Linux and PostgreSQL, Windows, licence-included databases) | Published to Sonatype; reviewed against actuals |
| Cloud prices | AWS Pricing Calculator for the primary region and the DR region |
| Software prices | Vendor quotes |
| Handling fee rate | Commercial terms |
| Escalation at renewal | Commercial agreement |

Amounts for each input are held in the commercial model, not on the wiki.

## Cost drivers

What moves the total most, and who controls it.

| Driver | Effect | Control |
|---|---|---|
| Number of Artifactory nodes | Software licences are typically per node, and each node needs compute | Design (three nodes per environment); confirm licence terms with the vendor |
| Environment-hours of temporary environments | The main variable cost | TTL, idle shutdown, quotas, budget alerts |
| Licence-included Windows and database editions | The most expensive environment types | Show the unit rate when a configuration is requested |
| NAT gateway per availability zone | Reliability against cost | Decision recorded in [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps) |
| DR pilot light | Standing replicas and storage in the second region | Keep compute at zero until needed; review replica sizes |
| Log volume and retention | Storage and compute for the log platform | Retention rules, masking and sampling |
| Storage growth | Artifact and evidence storage | Lifecycle rules, snapshot and image retention |
| Data transfer | Pulls by external users, cross-region copies | Egress budget and alarms |
| Number of agent licences | ITSM and CI/CD subscription cost | Licence only the people who need an agent seat |
| Always-on versus on-demand | Idle capacity costs the same as busy capacity | Right-size after the first weeks; commitment discounts for steady usage |

## Tracking actual cost

| Mechanism | Detail |
|---|---|
| Accounts | One AWS account per environment (`mls-shared`, `mls-lab`, `mls-dev`) gives a first cut of cost by environment |
| Cost allocation tags | `project`, `environment`, `function`, `owner`, `ticket` and `ttl` on every resource, set by automation, never by hand. A cost-centre tag is added when finance names the code. See [Repositories and Naming](/Engineering/Repositories-and-Naming) |
| Budgets and alarms | A budget per account, with alerts at 80 percent and 100 percent (alert ENV-06 in the [Alert Catalogue](/Operations/Alert-Catalogue)) |
| Per-request cost | Each provisioned environment carries its ticket, so its cost can be reported per request |
| Reports | A monthly cost report by cost line, environment and tag, compared with the budgeted figure |

## Cost review

A monthly review with Sonatype covers: actual against budget by cost line; variable consumption and its drivers; infrastructure cost pass-through, with any handling fee shown separately; right-sizing and commitment opportunities; and changes expected next month. This is the cost review proposed in the [Well-Architected Review](/Architecture/Well-Architected-Review) (cost optimisation pillar).

## Reducing cost

| Lever | Effect |
|---|---|
| Time-to-live and automatic teardown | No forgotten test environments |
| Idle shutdown of temporary environments | Pay only for hours in use |
| Right-size from metrics, not guesses | Smaller instances where load allows |
| Graviton-based database instances | Lower price for similar capacity, where compatible |
| Commitment discounts for always-on compute | Lower unit price for steady usage |
| Storage lifecycle and retention rules | Cap storage growth |
| Consolidate or share low-use resources | For example shared NAT, if the reliability trade-off is accepted |

## Reconciliation before the figures are final

The hosting estimate in the commercial model is checked against the [Bill of Materials](/Architecture/Bill-of-Materials) before submission: region (Mumbai primary, Hyderabad DR), availability zones, NAT and firewall counts, the dedicated IQ Server database, the Harbor node group, the additions from the automation addendum, and log retention. Any difference is explained or corrected, so the BOM and the cost model describe the same lab.

## Related

[Bill of Materials](/Architecture/Bill-of-Materials), [Well-Architected Review](/Architecture/Well-Architected-Review), [Alert Catalogue](/Operations/Alert-Catalogue), [Architecture](/Architecture).
