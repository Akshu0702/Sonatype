# Branching and Release

## Branches

`main` holds released code only and is never built or deployed. `develop` integrates finished work and deploys to dev. Feature, bugfix and hotfix branches build and test but publish nothing. `release/X.Y.Z` stabilises one release. Direct pushes to `main` and `develop` are blocked.

## Pull requests

1. One task, one pull request, one repository, linked to its work item. It opens as a draft.
2. Tests and documentation for the change are in the same pull request, including the wiki page.
3. No one approves or merges their own pull request.
4. An independent review happens before the request is opened; the description states what was found and what was **not** covered.
5. Merge order follows dependency: library, services, automation, portal.

## Policies on `main` and `develop`

Minimum one reviewer other than the author, comment resolution, build validation, linked work item, required merge strategy (squash into `develop`, merge commit from `release/*` into `main`).

## Versions

- Snapshot `X.Y.Z-SNAPSHOT` from `develop`; release candidate `X.Y.Z-rc.N` from `release/X.Y.Z`; release `X.Y.Z` (the promoted candidate).
- A version is built once and never reused. A mistake is corrected by the next version.
- The Firewall plugin bundle uses the same scheme and is promoted by feed view (`@Local`, `@Prerelease`, `@Release`).

## Quality gates

Report mode first; blocking once findings are triaged. Gates: build and unit tests, API tests, secrets scan, code scan, dependency scan. Details are in [Secure Development Standards](/Security-and-Compliance/Secure-Development-Standards).
