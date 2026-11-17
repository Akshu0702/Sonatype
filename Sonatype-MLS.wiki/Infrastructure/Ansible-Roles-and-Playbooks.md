# Ansible Roles and Playbooks

How host-based services are configured. Terraform creates the instance; Ansible configures what runs on it.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Where the code lives

`mls-infra`, folder `ansible/`.

| Folder | Holds |
|---|---|
| `ansible/inventories/<env>` | Hosts and the variables that differ between environments |
| `ansible/group_vars/all` | Shared defaults, no secrets |
| `ansible/roles/<role>` | One role per concern, with tasks, defaults, templates and tests |
| `ansible/playbooks` | One playbook per outcome |

## Roles

| Role | Does | Used on | Status |
|---|---|---|---|
| `baseline_hardening` | SSH, kernel network settings, unused modules and packages, time sync, automatic security updates, file permissions, banner, journal. Aligned with CIS Level 1 topics; not a certification | Every host | Not built (draft pull request; lint clean, idempotent in Ubuntu and Amazon Linux containers) |
| `auditd` | Audit daemon, retention and the lab's audit rules | Every host | Not built (draft pull request; same tests) |
| `ssm_agent` | Confirms the Systems Manager agent is present, and keeps it running (it does not install it) | Every host | Not built (draft pull request) |
| `log-shipping` | Filebeat configuration, secret masking rules | EC2 hosts | Not built |
| `portal-host` | Node.js runtime, service unit, reverse proxy | Portal | Not built |
| `keycloak` | Keycloak install and cluster configuration, realm import | Identity | Not built |
| `iq-server` | IQ Server install (Java runtime, service), cluster and shared-storage settings | Firewall | Not built |
| `elk-elasticsearch` | Three-node Elasticsearch cluster, snapshots to S3 | Logging | Not built |
| `elk-logstash` | Pipelines that parse, enrich and mask secrets | Logging | Not built |
| `elk-kibana` | Kibana with SSO | Logging | Not built |
| `gitlab` | GitLab Community Edition install and configuration | CI/CD | Not built |
| `ci-runner` | GitLab runner and Azure DevOps agent | CI/CD | Not built |
| `postgresql-local` | PostgreSQL install at a pinned version, least-privilege roles, TLS | Provisioned environments that run the database on the instance | Not built |
| `provisioned-os` | Post-launch settings for a requested configuration | Provisioned environments | Not built |
| `bastion` | Bastion host access rules | Bastion | Not built |

## Playbooks

| Playbook | Outcome |
|---|---|
| `site-shared.yml` | Configure the shared services hosts |
| `deploy-portal.yml` | Release the portal |
| `deploy-keycloak.yml` | Configure Keycloak and its realms |
| `deploy-iq.yml` | Configure IQ Server nodes |
| `deploy-elk.yml` | Configure the log platform |
| `deploy-cicd.yml` | Configure GitLab, runners and agents |
| `provision-env.yml` | Configure a requested environment after launch |
| `harden.yml` | Apply or re-apply the baseline |
| `patch.yml` | Apply the approved patch baseline |

## How they run

- Pipelines run playbooks through Systems Manager, so no host needs inbound SSH.
- Every playbook supports a check run (`--check --diff`) that changes nothing. The pipeline runs it first and attaches the output to the approval.
- Secrets are read from Secrets Manager at run time. No vault password and no secret file is stored in the repository.
- Roles are idempotent: a second run changes nothing.
- Role names use underscores, the Ansible convention, and every role variable carries the role name as a prefix.
- Each change is linted with `ansible-lint` and run twice in Ubuntu and Amazon Linux containers. Containers cannot run services, sysctl or audit rules, so those parts need a first run on a real host.
- A provisioned environment is configured by `provision-env.yml` from the configuration the requester chose.

## Related

[Terraform Modules](/Infrastructure/Terraform-Modules), [Hardening Baselines](/Security-and-Compliance/Hardening-Baselines).
