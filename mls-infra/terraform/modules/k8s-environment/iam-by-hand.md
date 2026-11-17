# What must be done by hand before UAT or Prod could be built

**Nothing for UAT or Prod exists in AWS, and none of this is needed until the delivery lead decides to build one.** IAM is changed by a person, not by a pipeline. Account `096631344162`, region `ap-south-1`.

## 1. Let the deploy role create what these stacks create

The agent runs Terraform with `mls-deploy-allow` and `mls-deploy-guardrails`. Compared with the statements in `mls-deploy-allow` today, the stacks need **one change**:

| Change | Why |
|---|---|
| In the statement `SecretsByPrefix`, add the resource `arn:aws:secretsmanager:ap-south-1:096631344162:secret:mls/*` next to `secret:mls-*` | The secrets are named `mls/<environment>/<service>/<purpose>` (a slash), which `secret:mls-*` does not match. Without it the secrets step is refused |

Everything else is already allowed by name prefix: EKS (`eks:*` on `*/mls-*` and the create actions), RDS (`db:`, `subnet-group:`, `pg:`, `og:` named `mls-*`), KMS and EC2 in Mumbai (VPC, subnets, NAT gateways, security groups), S3 `mls-*`, CloudWatch log groups `*mls-*`, and IAM roles and policies `mls-*` (the cluster, node, add-on and Harbor roles are all named `mls-...`). The OIDC provider, the service-linked roles for EKS, RDS and load balancing, and the approved managed policies are already on their lists.

**Check:** in the Policy simulator choose the agent's role and test `secretsmanager:CreateSecret` on `arn:aws:secretsmanager:ap-south-1:096631344162:secret:mls/uat/tenants/db-password-AbCdEf` (allowed), `eks:CreateCluster` (allowed) and `rds:CreateDBInstance` on `arn:aws:rds:ap-south-1:096631344162:db:mls-rds-uat-db-001` (allowed). `mls-deploy-guardrails` needs no change.

## 2. Who administers the cluster

The cluster's API is private, and nobody is an administrator by default. Name the role that is (for example the Identity Center administrator role) in `admin_role_arn` when the stack is turned on. That role must already exist and be reachable over the VPN.

## 3. Names that must exist before the services start (not IAM)

- A **private DNS zone** for `*.uat.mls.internal` and `*.prod.mls.internal` (or the names the network team chooses; the values files use these as placeholders), and a **certificate** for the internal load balancer (put its ARN in the values files).
- The **network range** for each VPC (`10.43.0.0/20` UAT, `10.44.0.0/20` Prod are proposals, not yet in the network plan or agreed with the customer's network team).
- Reachability from the **build agent** to the environment's Harbor (a Transit Gateway or VPC peering; not built).

## 4. Create the database users (after the database exists)

The database is private. As an administrator with the cluster's admin role:

```
# 1. fill the secrets (Terraform created them empty): generate each value, never write it down
for s in tenants/db-password itsm/db-password harbor/db-password harbor/admin-password harbor/secret-key; do
  aws secretsmanager put-secret-value --secret-id "mls/<env>/$s" --secret-string "$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)"
done
# harbor/ci-robot and harbor/pull-robot hold {"username": "...", "password": "..."}: fill them after Harbor creates the robot accounts

# 2. a throw-away pod inside the cluster reaches the database
kubectl run psql --rm -it --restart=Never --image=postgres:16 -- bash
# inside: fetch the master password from the secret named by the output database_master_secret_arn,
# then run sql/create-app-databases.sql with the three passwords as psql variables (see the head of that file)
```

**Check:** the last query in the script lists `tenants`, `itsm` and `harbor`, each with `rolsuper = f` and `rolbypassrls = f`.

## 5. What Terraform creates by itself

Roles `mls-role-<env>-api-*` (EBS CSI, load balancer controller, cluster autoscaler, External Secrets), `mls-role-<env>-harbor`, the cluster and node roles, and the OIDC provider. Their limits are in their own policies; the Harbor role may use only its own bucket and its own key.
