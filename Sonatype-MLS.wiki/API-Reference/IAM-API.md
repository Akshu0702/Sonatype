# IAM API

Users, groups, roles, scopes and federation. Base path `/api/v1/iam`. Contract: `mls-portal-api/openapi/iam.v1.yaml`. Conventions are in [API Reference](/API-Reference).

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** A header `X-Persona: <role name>` narrows a person to one role they hold; a role they do not hold is ignored.

| Method and path | Needs scope | Purpose |
|---|---|---|
| `GET /me` | any signed-in user | Identity, roles, groups, effective scopes, roles the person can work as, current role, and available features |
| `GET /scopes` | `roles.read` | The scope catalogue by feature group, with each flag's state |
| `PUT /scopes/{key}/flag` | `flags.manage` | Turn a feature on or off: `{enabled}` |
| `GET /users`, `GET /users/{id}` | `users.read` | People, with roles and groups |
| `POST /users` | `users.write` | Invite: email, display name, roles, groups. The person signs in through single sign-on |
| `PUT /users/{id}/status` | `users.write` | Enable or disable |
| `PUT /users/{id}/roles` | `roles.write` | Set a person's roles (checked against the guard rails) |
| `PUT /users/{id}/groups` | `groups.write` | Set a person's groups |
| `GET /groups`, `GET /groups/{id}` | `groups.read` | Groups with members, roles and reach |
| `POST /groups` | `groups.write` | Create |
| `DELETE /groups/{id}` | `groups.write` | Delete; removes the access it gave |
| `PUT /groups/{id}/members` | `groups.write` | Set members |
| `PUT /groups/{id}/roles` | `roles.write` | Set the group's roles |
| `PUT /groups/{id}/reach` | `groups.write` | Set the environments the group reaches |
| `GET /roles` | `roles.read` | Roles with their scopes |
| `POST /roles` | `roles.write` | Create a custom role |
| `PUT /roles/{id}` | `roles.write` | Update a custom role's description |
| `PUT /roles/{id}/scopes` | `roles.write` | Replace the role's scopes, validated against the guard rails. Built-in roles allowed within the guard rails |
| `DELETE /roles/{id}` | `roles.write` | Delete a custom role (built-in roles cannot be deleted) |
| `GET /scope-changes` | `roles.read` | Pending and past changes to sensitive scopes |
| `POST /scope-changes/{id}/confirm` | `roles.write` | A second administrator confirms (proposed) |
| `GET /sso/presets` | `sso.manage` | Identity-provider presets with fields |
| `GET /sso`, `POST /sso`, `PUT /sso/{id}`, `DELETE /sso/{id}` | `sso.manage` | The federation settings |

## Rules

- A save that breaks a guard rail returns a problem response that names the rule.
- A role name is up to 80 characters, starts with a letter or digit, and is unique ignoring case and not equal to a built-in name. At least one scope is required.
- Every change is an audit event with the actor.

## Internal

Reachable only on the private network.

| Path | For |
|---|---|
| `GET /internal/scopes?sub[&persona]` | Every service's scope check (15-second cache, fail closed) |
| `GET /internal/caller?sub` | User id, email, group ids (404 if unknown or disabled) |
| `POST /internal/users/exist`, `POST /internal/groups/exist` | Validation for other services |

## Related

[Portal API](/API-Reference/Portal-API), [IAM](/Services/IAM), [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment).
