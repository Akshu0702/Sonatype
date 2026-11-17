# IAM

The module of the Portal API that owns users, groups, roles and the scope catalogue, and tells every other service what a caller may do.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built** as designed. Repository: `mls-portal-api`, folder for the IAM module. It may be split into its own service later; the internal contract below stays the same.

## Purpose

Who people are and what they may do. IAM owns users, groups, roles and the scope catalogue, the federation settings, and the feature flags on scopes. Every other service asks IAM what a caller may do.

## Responsibilities

- **Current user** (`GET /api/v1/iam/me`): identity, roles, groups, effective scopes, the roles the person can work as, the role in force, and the features available (drives menus).
- **Users**: list, get, invite, enable or disable, assign roles and groups.
- **Groups**: create, delete, set members, set roles, set reach.
- **Roles**: list, create, update, delete (custom only), and decide each role's scopes.
- **Scopes**: the catalogue, and each scope's flag.
- **Federation**: the Sonatype and Motivity identity-provider settings.
- **Internal lookups** for other services: scopes, caller, users and groups exist.

## Model

| Table | Holds |
|---|---|
| `users` | id, email, display name, identity subject, organisation, status (active or disabled), last sign-in |
| `groups` | name, description |
| `roles` | name, description, `system` flag |
| `scopes` | key, feature group, name, description, sensitive |
| `scope_flag` | per scope: on or off, who changed it and when |
| `role_scopes` | the scopes of each role |
| `user_roles`, `group_members`, `group_roles` | the links |
| `group_environments` | the environments a group reaches |
| `auth_provider_config` | the identity-provider federation settings (secrets held as references to the secret store, never as values) |

Full column lists are in [Data Model](/Architecture/Data-Model).

A person's scopes = scopes of their own roles ∪ scopes of the roles of all their groups, minus scopes whose flag is Off. With a persona: only the scopes of that one role, and only if the person holds it.

## Reach

Groups collect people for two purposes: **roles** (every member gets the group's roles) and **reach** (environments are granted to groups). So "can this person do X" is two questions: do they hold the scope, and do their groups reach the thing. Reach for tickets follows the ticket (requester, assignee or assignee group). Reach beyond environments is proposed and not agreed.

## Scope lookup for other services

`GET /internal/scopes?sub[&persona]` returns the scope set (empty for an unknown or disabled user). Callers cache it for 15 seconds in their own process memory (there is no shared cache), keyed with the persona, and fail closed.

It also runs the **role sync** once per process: a built-in role receives a scope in its defaults only in the release that introduces it, and is never given back a scope an administrator removed. Custom roles are never touched.

`GET /internal/caller?sub` returns the user id, email and group ids (404 for unknown, unlinked or disabled).

## First sign-in and invitation

See [Authentication and Authorization](/Architecture/Authentication-and-Authorization). An invite creates the user row with roles and groups; the person signs in through single sign-on. There is no temporary password.

## API summary

See [IAM API](/API-Reference/IAM-API).

## Security notes

- The internal paths IAM serves are listed explicitly; a new internal endpoint must be added to the list or it answers 401.
- A disabled user gets no scopes from the next lookup onwards.
- No secret appears in a column, log or response.

## Tests

Tests cover first sign-in provisioning, roles, groups, persona rules, the guard rails, flag on and off, the scope counts per built-in role, and fail-closed behaviour when IAM is unreachable.

## Related

[Roles](/Roles), [Feature Flags](/Feature-Flags), [Audit and Logging](/Architecture/Audit-and-Logging).
