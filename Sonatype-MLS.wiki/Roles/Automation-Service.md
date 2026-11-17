# Automation Service

The role used by the platform itself. It is not a person and has no portal sign-in.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## What it is

Workflows and functions act for the lab: they prepare workspaces, store evidence, check versions, request syncs, provision environments and remove expired ones. Each uses its own cloud role with only the permissions its task needs, and credentials come from Secrets Manager.

## Rules

- **Not assignable.** The portal administrator cannot assign this role to a person, and cannot give a person its permissions.
- **Scoped.** One automated function acts on one bucket or one cluster, never across environments.
- **Recorded.** Every automated action is an audit event that names the workflow execution and the ticket.
- **No approvals.** Automation can wait for an approval but can never give one.
- **Internal only.** Automation calls the portal's internal endpoints, reachable only from the private network.

## What it does, by stage

| Stage | Automated action |
|---|---|
| S1 to S3 | Store the build file, record its checksum, prepare the workspace |
| S4 to S5 | Bump the plugin version, request the sync, restart in order |
| S6 | Check the deployed versions on every pod |
| S7 to S9 | Run tests, store evidence, assemble the report |
| Provisioning | Create and tag an environment; remove it at expiry |
| Monitoring | Turn alarms into incidents |

## Related

[Roles](/Roles), [Services](/Services), [Step Functions](/Services/Step-Functions).
