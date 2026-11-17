# Deploying a service from the feed

The Release pipelines `cd-mls-tenants-api` and `cd-mls-portal-api` (Azure DevOps, Pipelines, Releases) deploy a package **from the feed `mls-maven`**, not from a rebuild: CI builds and publishes once, the release pipeline takes that artifact.

| Script | Runs on | What it does |
|---|---|---|
| `deploy-from-feed.sh` | the build agent, inside the release | Downloads the newest snapshot (view `@Local`) from the feed and checks it. With `DRY_RUN=true` it stops there. Otherwise it puts the jar in an S3 bucket and sends `host-deploy.sh` to the application host through Session Manager, then waits for the result |
| `host-deploy.sh` | the application host | Checks the checksum, builds the image, replaces the running container, waits for the health URL, and starts the previous version again if the new one does not become healthy |

## Safe by default

- The releases have **no trigger**: someone creates a release.
- The `dev` stage needs **a named approver** before anything runs.
- The variable `dryRun` is **true**: a release downloads and checks the jar and says what it would do. It makes no AWS call.

## Before the first real deploy (`dryRun=false`)

In place (2026-11-16):

1. The agent's policy `mls-deploy-allow` allows `ssm:SendCommand` on the application host (`mls-ec2-dev-lab-001`) and the `AWS-RunShellScript` document, and `ssm:GetCommandInvocation`.
2. The bucket `mls-deploy-artifacts-096631344162` (ap-south-1, versioned, private, jars expire after 30 days) and the host role's inline policy `mls-deploy-read` (`s3:GetObject` on `deploy/*`).
3. The release variables `deployBucket` and `appHostId` are set on both releases.
4. The AWS CLI: the deploy installs the official v2 on the host the first time if it is missing (the boot script now does the same on a rebuilt host).

Not in place yet:

5. A settings file on the host for each service, `/opt/mls/<service>/env` (database, Keycloak and tenants-service addresses, secrets). PostgreSQL and Keycloak are not deployed on the host yet.
6. The application host is stopped while it is not in use; start it before a real deploy.
7. The first controlled deploy needs an explicit go-ahead from the delivery lead.

## Tested

- `deploy-from-feed.sh` in dry-run mode against the real feed: downloaded the real `tenants-service` snapshot and printed its checksum.
- `host-deploy.sh` with stand-in services in Docker: a healthy version goes live and is logged; a wrong checksum is refused with nothing changed; a version that never becomes healthy is rolled back and the previous one keeps answering.

Not tested: the S3 and Session Manager path against the real host (permissions are in place, it has not run), and the real services (they need their database and Keycloak).
