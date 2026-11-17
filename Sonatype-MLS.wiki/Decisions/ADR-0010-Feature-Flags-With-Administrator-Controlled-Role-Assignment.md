# ADR-0010: Feature flags with administrator-controlled role assignment

**Status:** Proposed

## Context
Sonatype and Motivity users have different needs, and the right split of functions may change during the engagement. The portal administrator must be able to decide what each role can do without a code change, and new capabilities must be releasable without being exposed at once.

## Decision
Every user-visible capability is a **scope** in the catalogue, grouped into feature groups, with a flag. The portal administrator assigns groups and scopes to roles and switches flags. Access is computed on each request from the user's roles (own and through groups), the scopes those roles hold and the flags, all from the portal database, never from identity-provider claims ([ADR-0011](/Decisions/ADR-0011-Scopes-From-The-Database-Users-Groups-Roles-Scopes)). Guard rails enforce separation of duties and protect sensitive scopes. Every change is audited.

## Consequences
- Roles and features can change without a deployment.
- New features can be released dark and turned on after verification.
- One catalogue and one axis: a feature is a scope, so there is no second table of switches to drift.
- Each scope must be tested with its flag on and off.
- Unlike Baton, the administrator may adjust the scopes of built-in roles; built-in roles still cannot be deleted or renamed.
