# Pipelines

CI for `mls-itsm-api`: `ci-mls-itsm-api.yml`, run on the self-hosted pool `mls-agents`, the same layout as `mls-portal-api` and `mls-tenants-api`.

## What runs

- **OpenAPI contract:** lints `openapi/*.yaml` with Redocly; fails on errors.
- **Security scans:** gitleaks and Trivy, in report mode (`securityEnforce` is `false` until the findings are triaged).
- **Build and test:** checks out `mls-commons-lib` (develop), builds it into the Maven cache, then runs `scripts/test.sh` (Maven, JDK 21, PostgreSQL 16 in containers). Until the library is in the package feed, a change to it is picked up here only after it is merged to its `develop`.
- **Publish:** the shared templates in `mls-infra` (`maven-version.yml`, `maven-publish.yml`) publish `com.motivitylabs.mls:mls-itsm-api` to the Azure Artifacts feed `mls-maven`: a snapshot from `develop`, `X.Y.Z-rc.N` from `release/X.Y.Z`. The release pipeline `cd-mls-itsm-api` deploys from the feed.

## Rules

- A check with nothing to check prints a warning and says it was skipped; it never passes silently.
- Tool images are pinned in the scripts; a change is a pull request.
