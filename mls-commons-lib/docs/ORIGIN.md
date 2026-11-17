# Origin of this code

This library is a **copy of Baton's shared security library**, ported to Spring Boot 4.1. It is kept compatible so the two can be merged back into one library later (PLATFORM-SPEC-v3 section 15.2, item 14).

| | |
|---|---|
| Source repository | `Baton/baton-commons-lib` (Azure DevOps project `Baton`) |
| Source branch and commit | `main` at `d89ee4e` (30 October 2026) |
| Copied on | 14 November 2026 |
| Coordinates | Unchanged: `com.platform:platform-security-commons`, packages `com.platform.security.*` |

## What was changed from Baton's version

| Change | Why |
|---|---|
| Parent `spring-boot-starter-parent` 3.5.16 changed to **4.1.2** | Decision of 14 November 2026 |
| Starter `spring-boot-starter-web` renamed `spring-boot-starter-webmvc`; `spring-boot-starter-oauth2-resource-server` renamed `spring-boot-starter-security-oauth2-resource-server` | Spring Boot 4 renamed them |
| In `RlsTenancyAutoConfiguration`, three imports moved to their new packages: `DataSourceAutoConfiguration` to `org.springframework.boot.jdbc.autoconfigure`, `HibernateJpaAutoConfiguration` and `HibernatePropertiesCustomizer` to `org.springframework.boot.hibernate.autoconfigure` | Spring Boot 4 split auto-configuration into modules |
| Added `scripts/test.sh` and this file | MLS conventions |

Nothing else was changed: the source files and the 30 tests are Baton's.

## Keeping it in step

Fixes made here that apply to Baton should go back to Baton, and the other way round. Record each sync in this table.

| Date | Direction | Baton commit | Note |
|---|---|---|---|
| 2026-11-14 | Baton to MLS | `d89ee4e` | First copy, ported to Spring Boot 4.1 |

## Changed after the copy

| Date | Change |
|---|---|
| 2026-11-15 | `TenantFeatures` knows Products: `State` carries `product`, new `productOn` and `products`; with no earlier answer from tenants-service, the `platform` Product stays on (it is mandatory and in no package). First tests for the class (7). A 4xx answer (unknown tenant) is an answer, not an outage. |
