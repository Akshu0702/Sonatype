# Infrastructure Diagrams

Architecture and flow diagrams, written as Mermaid so they are reviewed as text.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## AWS deployment, primary region

::: mermaid
flowchart LR
  subgraph OFF["Offices"]
    SO["Sonatype users"]
    MO["Motivity engineers"]
  end
  SO -->|"Site-to-Site VPN"| TGW
  MO -->|"Site-to-Site VPN"| TGW
  subgraph REG["Mumbai ap-south-1, three AZs"]
    TGW["Transit Gateway"]
    subgraph SH["Shared services VPC 10.40.0.0/20"]
      ALB["Internal ALB"]
      PORTAL["Portal x2"]
      KC["Keycloak x2"]
      IQ["IQ Server x2 + EFS"]
      ELK["ELK"]
      CICD["GitLab, runners, ADO agents"]
      RDS1[("RDS PostgreSQL")]
      RDSIQ[("RDS for IQ")]
    end
    subgraph E1["Lab Env-1 VPC 10.41.0.0/20"]
      EKS1["EKS: Artifactory x3"]
      RDSA1[("RDS")]
    end
    subgraph E2["Lab Env-2 VPC 10.42.0.0/20"]
      EKS2["EKS: Artifactory x3 + Harbor"]
      RDSA2[("RDS")]
    end
    TGW --> ALB
    ALB --> PORTAL
    ALB --> KC
    PORTAL --> RDS1
    KC --> RDS1
    IQ --> RDSIQ
    EKS1 --> IQ
    EKS2 --> IQ
    EKS1 --> RDSA1
    EKS2 --> RDSA2
    TGW -.->|"automation only"| EKS1
    TGW -.->|"automation only"| EKS2
  end
:::

Offices reach the portal, Keycloak and Kibana only. Env-1 and Env-2 cannot reach each other.

## Traffic and egress

::: mermaid
flowchart TB
  U["User over VPN"] --> A["Internal ALB: portal, Keycloak, Kibana, HTTPS 443"]
  A --> P["Portal"]
  P --> SF["Step Functions"]
  SF --> L["Lambda, VPC-attached"]
  L -->|"private endpoint"| K["EKS API"]
  L --> S3["S3 via endpoint"]
  K --> AR["Artifactory"]
  AR --> IQ["IQ Server"]
  AR -->|"NAT + Network Firewall allow-list"| EXT["SaaS repositories and public registries"]
:::

## Delivery chain

::: mermaid
flowchart LR
  W["Boards and wiki"] --> BR["Branch per task"]
  BR --> PR["Pull request: review, policies"]
  PR --> CI["CI: build, tests, scans"]
  CI --> FD["Feed: snapshot, rc, release views"]
  FD --> TF["Terraform: plan, approve, apply"]
  FD --> AC["Argo CD: sync after approval"]
  TF --> ENV["Environments"]
  AC --> ENV
:::

## Plugin deployment

::: mermaid
sequenceDiagram
  participant R as Requester
  participant P as Portal
  participant SF as Step Functions
  participant G as Git (mls-infra)
  participant A as Argo CD
  participant K as Artifactory pods
  R->>P: Raise ticket, upload bundle
  P->>SF: Start ticket-lifecycle
  SF->>SF: Verify checksum, wait for approval
  SF->>G: Bump plugin.version
  SF->>A: Request sync
  A->>K: Rolling restart, init container pulls bundle
  K-->>SF: firewallVersion on every pod
  alt version matches
    SF->>P: Record evidence, continue to tests
  else mismatch
    SF->>G: Revert plugin.version
    SF->>A: Request sync
    SF->>P: Open blocker
  end
:::

## Ticket lifecycle

::: mermaid
stateDiagram-v2
  [*] --> Intake
  Intake --> Workspace
  Workspace --> AwaitingApproval
  AwaitingApproval --> Deploying: approved
  AwaitingApproval --> Closed: rejected
  Deploying --> Verifying
  Verifying --> Testing: version matches
  Verifying --> RolledBack: mismatch
  RolledBack --> Blocked
  Testing --> Reporting
  Testing --> Blocked: failure
  Blocked --> Testing: resolved
  Reporting --> Closed
  Closed --> [*]
:::

## Provisioning an environment

::: mermaid
sequenceDiagram
  participant U as Requester
  participant API as Provisioning API
  participant SF as Step Functions
  participant TF as Terraform module
  participant E as Environment
  participant R as TTL reaper
  U->>API: Request configuration, TTL, ticket
  API->>SF: Start provision-environment
  SF->>TF: Apply hardened Linux + PostgreSQL
  TF-->>E: Launched, tagged with TTL, owner, ticket
  SF->>API: Register environment
  API-->>U: Details and expiry
  R->>E: On expiry or nightly sweep, remove
  R->>API: Record teardown evidence
:::

## Disaster recovery

::: mermaid
flowchart LR
  subgraph P["Mumbai: active"]
    PRDS[("RDS")]
    PS3[("S3")]
    PEFS[("EFS")]
  end
  subgraph D["Hyderabad: pilot light"]
    DRDS[("RDS read replicas")]
    DS3[("S3 replicas")]
    DEFS[("EFS replica")]
    DEKS["EKS and EC2 from IaC, scaled to zero"]
  end
  PRDS -->|"async replication"| DRDS
  PS3 -->|"cross-region replication"| DS3
  PEFS -->|"replication"| DEFS
  DNS["Route 53 failover"] -.-> D
:::

On a region failure: promote the replicas, create the compute from code, and fail DNS over. Recovery objectives are estimates until the DR drill.

## Related

[Architecture](/Architecture), [Bill of Materials](/Architecture/Bill-of-Materials), [Automation](/Automation).
