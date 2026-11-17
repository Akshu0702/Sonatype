# Entitlements: Products, packages and overrides

A tenant is the wrapper. What it may use is a set of **Products**, each with **feature groups** and **features**. The service answers one question, "is this feature live for this tenant?", for the other services (`GET /internal/tenants/{id}/features`, with `product` on each feature).

## Resolution, in order, the later winning

| # | Layer | Where |
|---|---|---|
| 1 | **Product**: on when it is mandatory, or a tenant override says so, or the package includes it. **A Product that is off is off for everything in it**, whatever a group or feature override says | `package_products`, `tenant_overrides` kind `product` |
| 2 | the feature's own default | `features.default_on` |
| 3 | the package: the groups it includes and the features it switches | `package_groups`, `package_features` |
| 4 | a tenant override of a whole group | `tenant_overrides` kind `group` |
| 5 | a tenant override of one feature | `tenant_overrides` kind `feature` |
| 6 | the tenant administrator's own switch, for features the platform enforces | `tenant_features` |

- The **platform** Product is mandatory: always on, in no package, not overridable.
- A Product the tenant has **only through an override** brings all its groups.
- A feature that **needs** another is blocked while the other is not usable. The tenant must be `active` or `provisioning`.
- An override **needs a reason** and may **expire**; an expired one is ignored. Overrides never change the package.
- A package version is read-only once published; a change is a new version, and tenants stay on theirs until moved.
- A new feature is off until a package switches it on.

## Catalogue

Keys are `<product>.<group>.<name>`. 65 features in 10 groups: `platform` (Administration, Audit and access review), `itsm` (Tickets, Approvals, Service management, Reports, Notifications), `automation` (Deployments and environments, Testing and evidence, Status). Packages: `lab-full` (ITSM and AWS Automation), `itsm`, `automation`. New tenants start on `lab-full`.

## Keys in service code

A service marks an API with `@RequiresFeature("itsm.tickets.tickets_create_incident")` (404 when the tenant does not have it live) or asks `@features.on('...')` / `@features.productOn('...')` in `@PreAuthorize`. At startup the shared library reads `GET /internal/catalogue` (all Product and feature keys, container network only) and compares. In every environment except `dev`, `local` and `test`, a key that is misspelt or not in the catalogue stops the service from starting.

## Limits

`api.requests_per_minute`, `users.max_active`, `tickets.per_month`, `environments.max_active`, `deployments.per_month`, `storage.max_gb`. None is seeded, so every limit reads as unlimited. Nothing enforces limits yet.
