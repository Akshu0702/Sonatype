# Alert Catalogue

Every alert, its condition, severity and route. Conditions are starting points. All alerts are **Not built** until the infrastructure or workflow that emits them exists. Routing is described in [Monitoring and Alerting](/Operations/Monitoring-and-Alerting).

[[_TOC_]]

## Infrastructure

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| INF-01 | VPN tunnels | Both tunnels of a connection down | P1 | CloudWatch |
| INF-02 | VPN tunnel | One tunnel down | P2 | CloudWatch |
| INF-03 | EKS node | Node not ready for 5 minutes | P2 | Container Insights |
| INF-04 | Pod restarts | Crash loop or restarts above threshold | P2 | Container Insights |
| INF-05 | Artifactory pods | Fewer than 3 ready for 5 minutes (P2); fewer than 2 (P1) | P1 or P2 | Container Insights |
| INF-06 | RDS CPU | Above 80 percent for 15 minutes | P3 | CloudWatch |
| INF-07 | RDS storage | Free space below 20 percent | P2 | CloudWatch |
| INF-08 | RDS failover | A failover event | P2 | RDS events |
| INF-09 | Host disk | Above 80 percent (P3); above 90 percent (P2) | P3 or P2 | CloudWatch agent |
| INF-10 | Volume claims | Persistent volume above 80 percent | P3 | Container Insights |
| INF-11 | Memory | Sustained pressure on a node or host | P3 | CloudWatch |
| INF-12 | IQ Server | One node down (P2); both down (P1) | P1 or P2 | Health check |
| INF-13 | Load balancer | Unhealthy targets, or 5xx rate above threshold | P2 | CloudWatch |
| INF-14 | Certificates | Expiry within 30 days (P3); within 7 days (P2) | P3 or P2 | Certificate manager |
| INF-15 | Elasticsearch | Cluster status yellow (P3) or red (P2); disk watermark reached | P3 or P2 | Cluster health |
| INF-16 | Log pipeline | Logstash queue growing or ingestion stopped | P2 | Logstash metrics |
| INF-17 | Service quotas | Usage above 80 percent | P3 | Service Quotas |
| INF-18 | Egress | Spike in denied outbound traffic | P3 | Network Firewall |

## Backup and recovery

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| BKP-01 | Backup job | A job failed | P2 | AWS Backup |
| BKP-02 | Backup recency | No successful backup in 26 hours | P2 | AWS Backup |
| BKP-03 | DB replica lag | Cross-region replica behind by more than 5 minutes | P3 | CloudWatch |
| BKP-04 | Replication | S3 or EFS replication failing | P3 | CloudWatch |
| BKP-05 | Restore test | A scheduled restore test failed or is overdue | P3 | Portal task |

## Deployments and workflows

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| DEP-01 | Workflow failed | A ticket-lifecycle or rollout execution failed | P2 | Step Functions |
| DEP-02 | Workflow timed out | An execution exceeded its timeout | P2 | Step Functions |
| DEP-03 | Approval waiting | An approval pending longer than 4 business hours | P4 | Portal |
| DEP-04 | Argo CD application | Out of sync for more than 30 minutes, degraded, or sync failed | P2 | Argo CD |
| DEP-05 | Stuck release | A Helm release in a pending state for more than 30 minutes | P2 | Cluster |
| DEP-06 | Version mismatch | `firewallVersion` differs between pods after a rollout | P2 | Version check |
| DEP-07 | Rollback | An automatic rollback was performed | P3 | Workflow |
| DEP-08 | Listener error | Startup errors such as a missing Tomcat listener class | P2 | Log rule |
| DEP-09 | Bundle checksum | The init container rejected a bundle | P2 | Log rule |
| DEP-10 | Pipeline failure | A build or release pipeline failed on `develop` or a release branch | P3 | Pipeline |
| DEP-11 | Terraform apply | An apply failed | P2 | Pipeline |
| DEP-12 | Terraform drift | Drift detected between code and AWS | P3 | Scheduled plan |

## Environments and provisioning

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| ENV-01 | Launch failed | A requested environment failed to launch | P3 | Provisioning workflow |
| ENV-02 | Expiring soon | An environment expires within 2 hours | P4 | Registry, to the owner |
| ENV-03 | Past expiry | An environment is past its expiry and still present | P3 | Reaper |
| ENV-04 | Reaper failure | The reaper failed or did not run | P2 | Scheduler |
| ENV-05 | Missing tags | A resource lacks TTL, owner or ticket tags | P3 | Config rule |
| ENV-06 | Budget | Spend above 80 percent (P3) or 100 percent (P2) of budget | P3 or P2 | AWS Budgets |

## Services and Firewall effectiveness

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| SVC-01 | Artifactory health | Health endpoint failing on a node | P2 | Health check |
| SVC-02 | Portal health | Portal or API unhealthy | P1 | Health check |
| SVC-03 | Keycloak | Not serving sign-ins | P1 | Health check |
| SVC-04 | API errors | Error rate or latency above threshold | P2 | Metrics |
| SVC-05 | Firewall canary | A known-bad component is not quarantined, or a known-good one is blocked | P1 | Synthetic check |
| SVC-06 | Repository availability | A known-good component cannot be downloaded | P2 | Synthetic check |
| SVC-07 | Harbor | Registry unhealthy | P2 | Health check |

## Security and audit

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| SEC-01 | GuardDuty | A high-severity finding | P1 | GuardDuty |
| SEC-02 | Security Hub | A critical finding | P2 | Security Hub |
| SEC-03 | Audit logging | CloudTrail disabled or log validation failing | P1 | Config |
| SEC-04 | Audit chain | The audit chain fails verification | P1 | Portal |
| SEC-05 | Break-glass | The break-glass role was used | P2 | CloudTrail |
| SEC-06 | Sign-ins | A burst of failed sign-ins | P3 | Audit events |
| SEC-07 | Role changes | A role or permission changed outside a change window | P3 | Audit events |

## Service management

| ID | Signal | Condition (starting point) | Sev | Source |
|---|---|---|---|---|
| SLA-01 | SLA | A ticket will breach within 1 hour | P3 | Portal |
| SLA-02 | SLA | A ticket breached | P2 | Portal |
| SLA-03 | Blocker | A blocker unresolved past its class threshold | P3 | Portal |

## Related

[Monitoring and Alerting](/Operations/Monitoring-and-Alerting), [Dashboards and Status](/Operations/Dashboards-and-Status).
