# mls-portal-ui-react

Lab portal user interface (React).

Part of the Sonatype Managed Lab (SOW #8). Project code: `mls`.

## Branches

| Branch | Use |
|---|---|
| `main` | Released code only; never built or deployed |
| `develop` | Integration; builds snapshots and deploys to dev |
| `feature/US<story>-T<task>` | One task, one pull request |
| `bugfix/US<story>-T<task>`, `hotfix/US<story>-<issue>` | Fixes |
| `release/X.Y.Z` | Stabilises one release |

Direct pushes to `main` and `develop` are blocked. Open a draft pull request linked to your task.

## Documentation

Behaviour, architecture, runbooks and decisions live in the wiki: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki

A change is not done until its wiki page is updated.

## What it does today

The first screens of the lab console (React, Vite, TypeScript).

- Signs in against the tenant's own Keycloak realm (authorization code with PKCE, no library). `public/config.json` says which Keycloak and realm; deployments replace it.
- The menu is built from `GET /api/v1/me`, so a tenant sees only the Products its package gives it (`src/menu.ts`). The API answers 404 for the rest regardless.
- **AWS environments**: live VPCs and servers from the lab's AWS account (read-only).
- **Tickets** (the ITSM Product, its own service `mls-itsm-api`): your tickets, a form to raise one with the build ZIP, a ticket page with its history, and for approvers an approvals queue with approve and reject. The console calls the ITSM service directly at `itsmApiUrl` from `public/config.json` (default `/itsm/api/v1`, a path the web server must pass on to the service; or a full URL, in which case the service must list this site in `ITSM_CORS_ORIGINS`). What each person sees follows the features the tenant has.

```
npm install
npm test         # menu rules and the raise-form rules
npm run build
npm run dev      # port 5173, proxies /api to localhost:8090
```

## Status

**First slice. 4 unit tests (menu rules). The screens were exercised only through the API calls they make, signed in with the same flow; no browser test yet. Not deployed anywhere.**
