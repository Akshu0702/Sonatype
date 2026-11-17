# Ansible

Configures what runs **on** a host after Terraform creates it. Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Ansible-Roles-and-Playbooks

**Status: syntax-checked, linted and run in Ubuntu and Amazon Linux containers (see the test notes). Not run on a real EC2 host.**

| Path | Purpose |
|---|---|
| `playbooks/harden.yml` | Applies the baseline to a host |
| `roles/baseline_hardening` | SSH, kernel network settings, unused modules and packages, time, updates, file permissions, journald |
| `roles/auditd` | Audit daemon and rules |
| `roles/ssm_agent` | Confirms Systems Manager access, so no host needs inbound SSH |
| `inventories/example` | An example inventory; real inventories hold no secrets |
| `tests/` | Converge playbook and the container test script |

## Rules

- Hosts are reached through Systems Manager, not SSH (see the wiki).
- Every playbook supports `--check --diff` and is idempotent: a second run changes nothing.
- No secret, password or key is stored here.
- Roles are named with underscores and every role variable carries the role name as a prefix.

## Run

```
ansible-playbook -i inventories/<env>/hosts.yml playbooks/harden.yml --check --diff
ansible-playbook -i inventories/<env>/hosts.yml playbooks/harden.yml
```

## Tests

```
bash tests/run-container-test.sh ubuntu:24.04
bash tests/run-container-test.sh amazonlinux:2023
bash tests/lint.sh
```

Each test installs Ansible in the container, runs the baseline twice and fails if the second run changes anything. Containers have no init system and a read-only kernel, so services, sysctl and audit rules are skipped there (`baseline_hardening_manage_services: false`); those parts are **not exercised** by the container tests.

## Not a certification

The roles apply a hardening baseline **aligned with the CIS Level 1 server topics** (SSH, network parameters, unused services, time, updates, permissions, auditing). They are **not** a certified or complete implementation of any CIS benchmark; the benchmark and its version for the chosen Linux distribution are still to be pinned (wiki: ADR-0005, Known Issues).
