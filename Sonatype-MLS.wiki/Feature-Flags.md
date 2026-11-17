# Feature Flags

The portal is built so that every user-visible capability is a **feature** that can be switched on or off and given to roles. In the access model, **a feature is one scope**. The portal administrator decides which role gets which feature groups and features.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** This is the design for the first release; the prototype is not known to have it.

## Vocabulary

| Term | Meaning |
|---|---|
| User | A person, linked to their single sign-on identity. Never holds a credential in the portal |
| Group | A named set of users. Gives its members its roles, and gives teams reach to environments |
| Role | A named set of scopes |
| Scope | One thing a person may do, for example `tickets.create_support`. The scope catalogue is fixed in code and mirrored in the database. Baton's wiki calls these permissions |
| Feature | A scope as the administrator sees it, with a name and a description. Each feature is exactly one scope |
| Feature group | A set of related features, for example Tickets or Approvals |
| Flag | An on or off switch on a scope, platform-wide |

Users, groups, roles and scopes follow the model in the Baton project ([Authentication and Authorization](/Architecture/Authentication-and-Authorization)).

## Three layers

::: mermaid
flowchart LR
  U["Request from a signed-in user"] --> R["The user's roles: own roles plus roles of all their groups"]
  R --> S["Scopes those roles hold"]
  S --> F["Keep only scopes whose flag is On"]
  F --> D{"Endpoint's scope held?"}
  D -->|"yes"| OK["Allowed"]
  D -->|"no"| NO["Refused: generic error"]
:::

1. **The flag** decides whether a feature exists at all. Off means unavailable to everyone, including administrators.
2. **The assignment** decides which roles hold the scope. People get roles directly or through groups.
3. **The scope check** happens on every request, on the server.

A person's scopes are the union of their own roles and their groups' roles. Nothing in the sign-in token grants a scope.

## Rules

- **Deny by default.** A scope no role holds is unavailable. A new feature starts with its flag **Off**.
- **Server-side.** The screen hides what you cannot use, but the server enforces it. A direct call to a hidden feature is refused.
- **One axis.** There is one scope catalogue. No second list of switches elsewhere.
- **Takes effect quickly.** A change applies within seconds. The lookup is cached for 15 seconds and fails closed if the portal cannot be reached.
- **Recorded.** Every flag change, role assignment and group change is an audit event with who made it and who confirmed it.
- **Guard rails.** Some combinations are refused, and some scopes are sensitive ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)).
- **Built in.** A feature is not done until it has a catalogue entry, a flag and a default assignment ([Adding a Feature Flag](/Feature-Flags/Adding-a-Feature-Flag)).

## Flag states

| State | Meaning |
|---|---|
| Off | Unavailable to everyone; the API refuses it; menus hide it. The default for a new feature |
| On | Available to every role that holds the scope |

To roll a feature out gradually, give it to one role or one group first, then to others.

## What the administrator can do

| Decision | Where |
|---|---|
| Give a role a whole group of features, or single features | Administration, Roles |
| Create a custom role | Administration, Roles |
| Give a group roles, members and reach | Administration, Groups |
| Give a person roles and groups | Administration, Users |
| Switch a feature on or off | Administration, Features |
| See what a role, group or person can do | Administration, Roles, Groups, Users |

## Pages

| Page | Covers |
|---|---|
| [Feature Catalogue](/Feature-Flags/Feature-Catalogue) | Every group and feature (scope) |
| [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment) | The default matrix, guard rails and how assignment works |
| [Adding a Feature Flag](/Feature-Flags/Adding-a-Feature-Flag) | The engineering steps |

## Related

[Roles](/Roles), [IAM](/Services/IAM), [IAM API](/API-Reference/IAM-API), [Data Model](/Architecture/Data-Model), [ADR-0010](/Decisions/ADR-0010-Feature-Flags-With-Administrator-Controlled-Role-Assignment), [ADR-0011](/Decisions/ADR-0011-Scopes-From-The-Database-Users-Groups-Roles-Scopes).
