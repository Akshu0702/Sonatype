# Adding a Feature Flag

The steps an engineer follows so that every new capability ships behind a flag and can be given to roles by the portal administrator. A feature is one scope.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Rule

**A user-visible capability is not done until it has a scope in the catalogue, a flag, a default assignment and its wiki pages.** This is part of the Definition of Done ([Work Management](/Engineering/Work-Management)).

## Steps

1. **Name the scope.** Use `<group>.<action>` in lower case, for example `tickets.create_support`. Pick the feature group it belongs to, or propose a new group in the story.
2. **Mark it sensitive** if it approves, deploys, tears down, signs off, administers or reads audit data.
3. **Add it in a migration** to the `scopes` catalogue with its group, name and description, and a `scope_flag` row set to **Off**. Migrations only move forward. The catalogue in code and the table must match; a startup check fails if they differ.
4. **Declare it on the route.** Every API route declares the scope it requires. A route with no declared scope fails the build.
5. **Gate the screen.** The UI reads the user's scopes from `GET /api/v1/iam/me` and shows the menu entry and screen only if the scope is present. This is a convenience; the server is the control.
6. **Test the states.** Tests cover the scope held with the flag On, the scope not held, and the flag Off. A direct call with the flag Off, or without the scope, must be refused.
7. **Set the default assignment** in the seed data for the built-in roles that should have it, within the guard rails. Existing installations receive it once, in the release that introduces the scope.
8. **Update the wiki.** Add the scope to the [Feature Catalogue](/Feature-Flags/Feature-Catalogue), the default matrix in [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment), the API page and the role page.
9. **Release dark.** Deploy with the flag Off. After verification, the portal administrator turns it On.

## Removing a feature

A withdrawn feature is set Off, then removed from the catalogue in a later release by migration. A feature that is permanently on keeps its scope; only the switch is retired.

## Related

[Feature Flags](/Feature-Flags), [IAM](/Services/IAM), [Secure Development Standards](/Security-and-Compliance/Secure-Development-Standards).
