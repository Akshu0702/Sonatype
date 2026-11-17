# Adding a Feature Flag

A checklist for a developer introducing an optional capability. Anything optional **must** be a flag so the platform can package it and the tenant can opt out. Adapted from Baton's checklist; the difference is that a feature belongs to a **Product** and a **feature group** inside it.

**Status: design (nothing built yet).**

1. **Name it** `<product>.<group>.<name>` in lower case, for example `itsm.tickets.create_support`. The key must match `^[a-z][a-z0-9]*(\.[a-z][a-z0-9_]*){2,}$`.
2. **Catalogue it.** Add a Flyway migration to the service that owns the catalogue inserting a row into `features` (group, title, plain description, `needs`, `default_on`, `enforced`, sort). Add the group to `feature_groups` under its Product if it is new. List features after the ones they need.
3. **Put it in packages.** Add the group to the packages that should include it (`package_groups`) and, where a package differs from the default, a row in `package_features`. Published packages are versioned: a change is a **new version**; decide which tenants get it.
4. **Tie the permission to it.** The scope (IAM) that lets a role use the capability points at this feature, so a role cannot use a feature the tenant has not got.
5. **Enforce it where the capability acts**, with the shared `TenantFeatures` bean: an interceptor for a family of endpoints, a check inside the operation, or `@PreAuthorize("@features.on('x')")`. Off means **404** for endpoints and "skip silently" for background work.
6. **Make dependencies honest.** Say what it needs in the catalogue; do not re-check in code.
7. **UI.** Gate menu items and buttons with the feature; the UI is a convenience, step 5 is the control.
8. **Test.** Off: endpoints are 404 and nothing else changes. Granted: they work. The tenant switch turns it off and on. A dependency off means not live. A feature with no package mapping is unreachable.
9. **Document.** Update [Products and Features](/Packages-and-Feature-Flags/Products-and-Features) and the Product's service page.
10. **Deploy order.** The service that owns the catalogue first, then the enforcing service.

Startup fails in non-development environments if code references a feature key that has no row in the catalogue or is in no package (PLATFORM-SPEC-v3 15.2 item 6).
