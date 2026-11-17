# Harbor

The container registry of an environment: UAT and Prod each have their own, running inside their own cluster. Status: **Not built.** No Harbor exists; the chart and its values are checked, nothing is installed.

[[_TOC_]]

## What it does

Holds the container images of the platform services, scans them with **Trivy** (built into Harbor) when they are pushed, and serves them to the cluster. This is the platform's registry for UAT and Prod; the lab design's Harbor on Env-2 is a separate installation. Installed by Argo CD from `helm/platform/charts/harbor` (the upstream chart 1.19.4, Harbor 2.15.4, plus the registry's service account and its secrets).

## How it is put together

| Part | Where | Highly available? |
|---|---|---|
| Core, registry, portal | Two replicas each, spread across zones | Yes |
| Image data | An S3 bucket (`mls-s3-<env>-store-001`), encrypted with the environment's key, reached with an IAM role for the registry's service account (no access keys) | Yes (S3) |
| Metadata | The environment's RDS PostgreSQL, database `registry`, own login | Yes (Multi-AZ in Prod) |
| Cache and job queue | Harbor's **bundled cache** (Valkey, Redis-compatible), one instance with a small volume | **No** |
| Job service | One replica, job logs in the database | No |
| Scanner | Trivy, one replica with a volume | No |

## The limit, stated plainly

[ADR-0012](/Decisions/ADR-0012-No-Kafka-Or-Redis-For-Now) excludes a shared Redis from the platform, so Harbor keeps the single bundled cache it ships with. **Harbor is highly available at the application tier but not at the cache.** If the cache pod or its node is lost, pushes and pulls through Harbor can fail until Kubernetes reschedules it (minutes). **No image is lost**, because the image data is in S3 and the metadata is in PostgreSQL; the cache and the job queue are rebuilt.

**Least-bad option (chosen): the bundled single cache**, with these controls: two replicas of the application tier and a pod disruption budget (in the chart); an alarm on Harbor's health check and a restart runbook are still to be written (Not built). This keeps the platform within ADR-0012 and costs nothing extra.

**What would give full high availability:** an external, managed Redis-compatible cache (for example Amazon ElastiCache with a replica), configured as Harbor's external cache. It needs ADR-0012 amended first, adds a service to run and secure, and costs more. Revisit if a cache outage proves unacceptable, or if Prod's registry must survive a node loss without any interruption.

**Backups:** RDS automated backups (point-in-time recovery), S3 versioning with a 30-day expiry of old versions. Image data in Prod may be copied to a second bucket or region later (not built).

## Who pushes and who pulls

| Who | How | Rights |
|---|---|---|
| Build pipeline (`promote-image`) | A **robot account** `ci` in the project `mls`, secret `mls/<env>/harbor/ci-robot` | Push and pull in the project `mls` only, expires in 90 days |
| The cluster | A **robot account** `pull`, secret `mls/<env>/harbor/pull-robot`; External Secrets turns it into the pull secret `harbor-pull` in the `platform` namespace | Pull only |
| People | Single sign-on once Keycloak exists for the environment; until then the administrator account (`mls/<env>/harbor/admin-password`) | Administrators only |

The robot accounts' tokens are created in Harbor and then written to Secrets Manager; they are never in Git or a pipeline variable that is not secret.

## Rules in the project `mls` (set once by an administrator)

- **Scan on push** on.
- **Prevent vulnerable images from running**: block pulls of images with a severity of High or Critical, with a documented exception list.
- **Immutable tags** on (a version is built once and never reused; the promote script also refuses it).
- **Prod receives images by copy** from UAT's Harbor (`promote-image.sh`), so the digest in Prod equals the digest UAT tested.

## Not covered

- Never installed, never run. The chart renders and passes schema checks for both environments; its behaviour on a cluster is untested, including the registry's role for S3, the external database settings and the ingress annotations.
- The vulnerability database comes through the NAT gateway; an air-gapped mirror is not designed.
- Replication of images to the DR region is not designed.

## Related

[Kubernetes Environments](/Infrastructure/Kubernetes-Environments), [Helm and Argo CD](/Infrastructure/Helm-and-Argo-CD).
