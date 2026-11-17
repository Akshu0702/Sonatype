# Packages and Feature Flags

What a tenant can use, and how it is switched on and off. Modelled on the Baton platform (its wiki section *Packages and Feature Flags* and ADR-0007), with **one more layer**: Baton has Packages and Features; MLS has **Packages, Products and Product Features**.

**Status: design. Nothing here is built yet.**

```
Package  ->  Products (one or more)  ->  Feature groups  ->  Features
```

| Page | What it says |
|---|---|
| [Entitlement Model](/Packages-and-Feature-Flags/Entitlement-Model) | The layers, the rules and what a service sees |
| [Packages](/Packages-and-Feature-Flags/Packages) | Packages, versions and the proposed bundles |
| [Products and Features](/Packages-and-Feature-Flags/Products-and-Features) | The catalogue: Products, feature groups, features |
| [Tenant Switches](/Packages-and-Feature-Flags/Tenant-Switches) | What a tenant administrator can turn off |
| [Adding a Feature Flag](/Packages-and-Feature-Flags/Adding-a-Feature-Flag) | Checklist for a developer |

The earlier [Feature Flags](/Feature-Flags) pages describe the first design, in which a feature was a permission with a flag. They remain for the permission list; the model on these pages replaces them.
