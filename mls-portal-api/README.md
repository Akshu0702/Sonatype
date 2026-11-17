# mls-portal-api

Lab portal API (Java 21, Spring Boot 4.1).

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

The first slice of the lab portal's API. Every call needs a token from a tenant's own sign-in realm; the shared library (`mls-commons-lib`) works out which tenant it is and asks `tenants-service` what that tenant may use.

| Call | Needs | Answers |
|---|---|---|
| `GET /api/v1/me` | any signed-in user | the tenant, the Products and the features that are live (the console builds its menu from this) |
| `GET /api/v1/environments` | feature `automation.deployments_and_environments.environments_view` (AWS Automation Product) | the lab's real AWS environments: VPCs and servers tagged `project=mls`, grouped by their `environment` tag. Read-only, kept for 60 seconds |
| `GET /api/v1/tickets` | feature `itsm.tickets.tickets_view_own` (ITSM Product) | **placeholder**: three fixed sample tickets with `placeholder: true`, until the ticketing service exists |

A tenant without the Product gets **404** from the last two. At startup the service checks these keys against the tenants-service catalogue and refuses to start on a misspelt one (outside the `dev`, `local` and `test` profiles).

## Configuration

| Setting | Meaning |
|---|---|
| `TENANTS_URL` | where tenants-service is (container network) |
| `MLS_AWS_REGION` (default `ap-south-1`) | the one region it reads |
| `MLS_AWS_PROJECT_TAG` (default `mls`) | only resources with this `project` tag are shown |

AWS credentials come from the standard AWS chain: an instance role in AWS, a read-only profile on a laptop (`AWS_PROFILE`). The service makes only `DescribeVpcs` and `DescribeInstances` calls. Give it a read-only role; it never needs more.

## Build and test

Needs Docker. The shared library is not in a package feed yet, so it is built first:

```
COMMONS_DIR=../mls-commons-lib bash scripts/test.sh
```

## Status

**First slice. 4 tests pass, including a start of the whole application with its startup key check. Run against a real tenants-service, Keycloak and the lab's AWS account on a laptop only; not deployed anywhere.**

Not covered: EKS, RDS, load balancers and other AWS services (EC2 and VPCs only); a role for the deployed service (task 7985); pagination; audit of reads.

