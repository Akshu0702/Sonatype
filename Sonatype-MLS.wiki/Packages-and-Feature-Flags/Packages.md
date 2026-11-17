# Packages

A **package** is a named, versioned bundle of **Products**, feature groups, feature settings and limits. A published version is read-only so the tenants on it do not change underneath them. Same lifecycle as Baton's packages.

**Status: design. The bundles below are proposals; nothing is priced or built.**

## Proposed bundles

| Package | Products | Notes |
|---|---|---|
| `lab-full` | ITSM and AWS Automation | What the Managed Lab for Sonatype uses |
| `itsm` | ITSM | Ticketing and service management only |
| `automation` | AWS Automation | Environments, deployments and status only |

Names, prices and limits are for the project owner to set. Limits are recorded and shown; enforcement and metering are separate work.

## Versions

| Status | Meaning |
|---|---|
| `draft` | Editable. At most one draft per package |
| `active` | The live version; at most one per package (unique partial index) |
| `retired` | Replaced; tenants may stay on it until moved |

- **New version**: a draft copy of the newest version; asking again returns the same draft.
- **Edit a draft**: Products, groups, feature settings, limits. Validation: unknown Products, groups, features and limits are refused; platform and mandatory groups must stay; a group's Product must be in the package; **no feature may be on while one it needs is off**; limits cannot be negative.
- **Publish**: the draft becomes the live version and the previous one is retired. The audit entry says how many tenants stay on the previous version until moved.
- A published version cannot be edited or published again.

## Moving tenants

Publishing moves nobody. An operator changes each tenant's package, with a **preview** of what is gained, what is lost and which overrides stay in force. A bulk move is future work.

## Audit

`package.version_created`, `package.updated`, `package.published`, `tenant.package.changed` (platform log and the tenant's log).
