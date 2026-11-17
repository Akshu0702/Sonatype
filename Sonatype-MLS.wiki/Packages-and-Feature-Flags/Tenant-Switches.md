# Tenant Switches

Even when a feature is entitled, the **tenant's own administrator** can turn off features the platform enforces (opt-out), as in Baton. A switch can only turn a feature **off**; it never grants one.

**Status: design.**

- Stored in `tenant_features` (only the opt-outs).
- A feature is **live** when it is usable and not switched off by the tenant.
- A Product can be hidden by the tenant administrator the same way: all its features go off for that tenant's users, and the Product's menu and endpoints disappear.
- Each change is written to the tenant's audit log.
- The platform operator's override always wins over a tenant switch being turned **on** again if the operator has turned the feature off.
