# ADR-0011: Scopes from the database (users, groups, roles, scopes)

**Status:** Proposed. Adopted from the Baton project's ADR on permissions from the database.

## Context
Putting roles in the sign-in token couples authorisation to the identity provider, makes changes wait for the token to expire, and breaks when an organisation brings its own provider. The lab federates to the Sonatype and Motivity identity providers.

## Decision
The token proves identity only. A fixed catalogue of **scopes** is in code and mirrored in the database. **Roles** are sets of scopes. **Groups** carry roles and give reach. A person's scopes are the union of their own roles and the roles of all their groups. The portal and every other service ask the portal's IAM module what a caller may do, with the answer cached for 15 seconds, and fail closed. A person with several roles can work as one role at a time.

## Consequences
- Role changes apply within seconds without signing in again.
- Any identity provider works: federation brings identity, not authority.
- The IAM module is on the path of every protected call, hence the short cache and the fail-closed rule.
- Adding a scope is a code change plus a migration.
- The provisioning API must call the internal scope lookup, so it depends on the portal being reachable.
