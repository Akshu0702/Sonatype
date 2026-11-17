# Portal and ITSM

The lab portal is a custom service desk built on the existing Motivity prototype. There is no synchronisation with an external ITSM tool in this build.

[[_TOC_]]

| Feature | Status |
|---|---|
| SSO and MFA (Keycloak), six roles | Partly |
| Ticket intake and lifecycle | Partly |
| Approval gates and rollback | Not built |
| Change, incident and problem records | Not built |
| SLA clocks and KPI dashboard | Not built |
| Weekly and monthly reports | Not built |
| Notifications (email and Teams) | Not built |
| CMDB-lite (environments, repositories, versions, owners) | Not built |
| Audit trail | Partly |

## Ticket types

Provisioning, support, plugin and environment change, incident, problem, access (joiner, mover, leaver).

## Rules

- Access is computed from the portal database (users, groups, roles, scopes), never from identity-provider claims.
- A portal administrator cannot approve their own change.
- SLA clocks run on business hours (11 AM to 7 PM IST) and raise breach alerts.
- Access onboarding takes one business day; revocation, four hours; access is recertified quarterly.
- Alarms from CloudWatch open incidents through SNS.

## Roles and feature control

Every capability above is a feature in a catalogue with a flag. The portal administrator decides which roles hold which feature groups and features. See [Roles](/Roles) for what each person can do and [Feature Flags](/Feature-Flags) for how features are switched on and assigned.
