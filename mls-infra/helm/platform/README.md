# Platform charts (UAT and Prod)

**No UAT or Prod cluster exists; nothing here has been deployed.** These are the charts and values for the platform's own services and for Harbor, for the two environments that run on Kubernetes. Dev and QA run on virtual machines and do not use them.

| Path | What |
|---|---|
| `charts/mls-service` | One chart for every platform service: deployment, service, HPA, PDB, service account, config files, an ExternalSecret for the service's secrets, an ExternalSecret for the Harbor pull credential, an internal ALB ingress (console only), an optional volume |
| `charts/harbor` | Harbor: the upstream chart (1.19.4) plus the registry's service account (S3 through an IAM role) and the External Secrets for its admin password, database password and key |
| `values/<env>/<service>.yaml` | Per-environment values for `tenants-service`, `itsm-api`, `portal-api`, `console-ui` |
| `values/<env>/harbor.yaml` | Harbor's addresses and account for the environment |

## Rules the chart enforces

Non-root, read-only root file system, no privilege escalation, all capabilities dropped, default seccomp, no service account token mounted, memory limit, three probes, a pull secret (images come from the environment's Harbor only), secrets only through External Secrets, no `latest` tag. `scripts/ci/check-platform-render.py` checks the rendered output for every values file, and its tests prove each rule refuses a violation.

Only the **console** has an address outside the cluster (an internal load balancer). It proxies `/api` and `/itsm/api` to the services inside, so the other services are not reachable except from within.

## How a version gets here

`image.tag` says `unpromoted` until an image is promoted. The promote step builds the image once from the feed artifact, pushes it to the environment's Harbor, and opens a pull request that sets `image.tag` and `image.digest`. Nothing else changes a values file's image. See `scripts/ci/promote-image.sh` and `pipelines/templates/container-image-promote.yml`.

## Known limits

- `itsm-api` keeps uploaded build ZIPs on a volume, so it runs **one replica** until the store moves to S3.
- The addresses (`*.mls.internal`), the certificate, the database address and the keys are placeholders (`REPLACE_*`) until the environment exists.
- `console-ui` is not a jar: its artifact is not in the feed yet, so it is not covered by the promote step.
- The portal API's environments view needs the read-only AWS role (task 7985).
- Keycloak for these environments is not part of these charts.

## Check

```
bash scripts/ci/helm-platform-checks.sh
python3 scripts/ci/test_check_platform_render.py
```
