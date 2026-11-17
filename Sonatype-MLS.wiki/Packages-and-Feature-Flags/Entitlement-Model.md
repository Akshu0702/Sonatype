# Entitlement Model

Adapted from Baton's Entitlement Model (four layers). MLS adds the **Product** between the package and the features.

**Status: design.**

## Vocabulary

| Term | Meaning |
|---|---|
| **Package** | A named, versioned bundle: which Products a tenant gets, which feature groups and features inside them, and limits. A published version is read-only |
| **Product** | A capability sold and deployed as a unit, with its own APIs, roles and scopes: **ITSM** and **AWS Automation** today |
| **Feature group** | A set of related features inside one Product (for example *Tickets*) |
| **Feature** | One switchable capability, with a stable key `<product>.<group>.<name>` |
| **Tenant** | The wrapper: one organisation. It has **one package**, and so gets that package's Products |

## Layers, the later winning

| # | Layer | Source | Example |
|---|---|---|---|
| 1 | **Product in package?** | `package_products` | A package without AWS Automation: every AWS Automation feature is off ("Not in package") |
| 2 | Feature default | `features.default_on` | A rarely-used feature defaults off |
| 3 | Package groups and features | `package_groups` (is the group in the package?) and `package_features` (explicit on or off) | The package includes *Tickets* but switches one feature off |
| 4 | **Tenant product override** | `tenant_overrides`, kind `product` | Switch a whole Product on or off for one tenant |
| 5 | Tenant group override | `tenant_overrides`, kind `group` | Add or remove a group for one tenant |
| 6 | Tenant feature override | `tenant_overrides`, kind `feature` | Switch one feature on or off for one tenant |

Layers 2 and 3 apply only when the Product is in the package (layer 1) or a tenant product override says so.

## Rules

- **Nothing is on by default.** A feature nobody has granted is unreachable.
- **Platform features** (Administration, Audit and access review) are mandatory: always on, not in any package, cannot be overridden.
- A **mandatory group** inside a Product is always on whenever the Product is.
- A group not in the package is off ("Not in package"), unless an override says otherwise.
- A group override adds or removes the whole group; each feature in it then follows the package's setting for that feature, or its default.
- A feature override beats everything above it.
- Every override **needs a reason** (written to both audit logs) and may **expire** (a date; an expired override is simply ignored; it can never be dated in the past).
- A feature **needs** at most one other feature **of the same Product**. A feature that is on but blocked says so (`needs itsm.tickets.create_support`).
- Changing a Product's package setting is a **new package version**; tenants stay on theirs until moved.

## From "on" to "live"

| Word | Meaning |
|---|---|
| **entitled** | The six layers say yes |
| **usable** | Entitled, and the feature it needs is usable, and the tenant is active (or provisioning) |
| **enabled** | The tenant's administrator has not turned it off (default yes) |
| **live** | Usable and (for features the platform enforces) enabled |

## What decides a request

A person can use a feature when **both**:
1. one of their roles (in that Product) grants the **scope** the endpoint needs (IAM, from the database, never from token claims), **and**
2. the **feature** that scope belongs to is **live** for the person's tenant.

Both are read from the platform's own data on every request. If the entitlement answer cannot be obtained, anything optional is **off**.

## What services see

`GET /internal/tenants/{id}/features` returns every feature with `{key, product, group, needs, enforced, entitled, enabled, live}`. Services use only `live`, through the shared `TenantFeatures` helper in `mls-commons-lib` (10-second cache, the last answer kept if the owning service cannot be reached, no answer at all means everything optional is off). A feature that is off answers **404**.

## Tables (proposed; extending Baton's)

| Table | Purpose |
|---|---|
| `products` | `key`, `name`, `status`, `mandatory` (true only for Platform) |
| `feature_groups` | `key`, `product_key`, `name`, `mandatory`, `sort` |
| `features` | `key`, `group_key`, `title`, `description`, `needs`, `default_on`, `enforced`, `sort` |
| `packages` | `key`, `version`, `name`, `type`, `status` (draft, active, retired), `is_public`, `description`. One active version per key |
| `package_products` | `pkg_key`, `pkg_version`, `product_key` (**new**) |
| `package_groups` | `pkg_key`, `pkg_version`, `group_key`; the group's Product must be in the package |
| `package_features` | `pkg_key`, `pkg_version`, `feature`, `on_`; only where a package differs from the default |
| `package_limits` | `pkg_key`, `pkg_version`, `limit_key`, `value` (null = unlimited) |
| `tenant_overrides` | `tenant_id`, `kind` (`product`, `group`, `feature`), `key`, `on_`, `reason`, `until`, `updated_by` |
| `tenant_limit_overrides` | `tenant_id`, `limit_key`, `value`, `reason` |
| `tenant_features` | The tenant administrator's own opt-outs |
| `tenants.package_key`, `package_version` | The tenant's package |

Tables read before a tenant context exists (the catalogue, packages) are reached by exact key through the owning service. Tenant-owned tables carry `tenant_id` with forced row-level security.

## Where it lives

Inside the **Tenants service** (`mls-tenants-api`), as in Baton (decided 14 November 2026). Other services read the answer through `TenantFeatures` in `mls-commons-lib`.

## Differences from Baton, on purpose

| Baton | MLS |
|---|---|
| Package > Feature groups > Features | Package > **Products** > Feature groups > Features |
| Four layers | Six layers (adds *product in package* and *tenant product override*) |
| One set of roles | Roles belong to a Product (or the Platform) |
| Features live in the tenants service | Same: in the tenants service |
