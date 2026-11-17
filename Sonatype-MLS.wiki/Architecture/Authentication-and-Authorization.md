# Authentication and Authorization

How people sign in and how the portal decides what they may do. The access model follows the Baton project: users, groups, roles and scopes.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Partly.** Single sign-on and MFA exist in the prototype; the model below is the target design.

## Authentication

### Realms and clients

| Realm | Purpose | Clients |
|---|---|---|
| `master` | Platform automation and operators. Never a sign-in realm for people who use the portal | An automation service account |
| One realm per organisation (Sonatype, Motivity) | The organisation's sign-in realm, federated to its identity provider | The portal's public client (authorisation code with PKCE; no implicit or direct grant) |

The administration console of Keycloak and the `master` realm are reachable only from listed networks, over the VPN.

### Sign-in flow

The browser uses the authorisation code flow with PKCE directly against the organisation's realm, which brokers to the organisation's SAML or OIDC identity provider. The portal never sees a password. MFA is enforced at sign-in.

### Token validation

Each service validates the token's signature, issuer and expiry against the issuing realm's published keys. Keys are cached and a failure to fetch them fails closed. Access tokens are short-lived (the lifetime is set at build time; Baton uses five minutes).

### First sign-in

The portal keeps its own `users` rows, linked to the sign-in subject. The first request of a person creates or links their row. The first administrator named for the lab becomes `PORTAL_ADMIN`. **Anyone else with no invitation starts with no roles and no scopes** until an administrator assigns them. An invitation by an administrator creates the user row with the chosen roles and groups, and the person signs in through single sign-on.

## Authorization

### Scopes come from the database

- The **scope catalogue** is fixed in code and mirrored in the `scopes` table. Each scope is also a feature with a flag ([Feature Flags](/Feature-Flags)).
- **Roles** are sets of scopes, stored in the database. Built-in roles are seeded and cannot be deleted or renamed; administrators can add custom roles.
- **Groups** carry roles and give reach. A person's scopes are the union of their own roles and their groups' roles.
- Nothing from the token's claims grants a scope. Keycloak authenticates; the portal authorises.

### The chain

::: mermaid
flowchart LR
  U["User"] -->|"member of"| G["Groups"]
  U -->|"holds directly"| R["Roles"]
  G -->|"carry"| R
  R -->|"hold"| S["Scopes"]
  S -->|"only if flag is On"| E["Allowed endpoint"]
:::

### Where checks happen

| Service | How it checks |
|---|---|
| Portal API (including the IAM module) | A route declares its scope; the check reads the portal database |
| Provisioning API and other services | A cached call to the portal's internal scope lookup (15-second cache; the cache key includes the persona). The check answers false if the portal is unreachable (fail closed) |

Rules of thumb:

- A check runs after request validation, so a malformed request is a 400 before a 403.
- Object-level checks (is this ticket yours? is this environment within your group's reach?) live in the service, not in the route declaration.
- Hiding a menu item is never the only control.

### Personas (working as one role)

A header names one role. The portal narrows the person's scopes to that role, only if they hold it directly or through a group. A role the person does not hold is ignored, so the header cannot widen access. The UI stores the chosen role in the browser and drops one the person no longer holds.

### Service-to-service

Internal endpoints carry no user token. The caller passes the subject it has already verified, and the receiving service acts for that subject. Reachability is the control: paths under `/internal` are reachable only on the private network and answered with 404 by the edge for anyone outside.

### Features as an access dimension

A scope whose flag is Off is refused for everyone, whatever roles they hold ([Feature Flags](/Feature-Flags)).

### Two-person rules

| Rule | Enforced by |
|---|---|
| Nobody decides their own ticket | Service check and a database constraint on the approval |
| Whoever administers cannot also approve | Checked on a person's effective scopes when roles or groups change ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)) |
| A sensitive scope change needs a second administrator (proposed) | Service check |
| A signed report cannot change | Database trigger |

## Not built yet

- Session and token revocation when a user is disabled: a disabled user is refused at their next scope check, but a live short-lived token stays valid until it expires.
- Group reach beyond environments.
- Operator-level groups and scopes for platform operators.

## Related

[IAM](/Services/IAM), [IAM API](/API-Reference/IAM-API), [Data Model](/Architecture/Data-Model), [Roles](/Roles), [ADR-0011](/Decisions/ADR-0011-Scopes-From-The-Database-Users-Groups-Roles-Scopes).
