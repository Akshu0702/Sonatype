# uat and prod: the Kubernetes environments (code only)

**NOTHING FOR UAT OR PROD EXISTS IN AWS.** These two folders are code that has been written and checked, not environments. Each stack is switched off: `enabled` is `false`, so a plan or an apply creates nothing in AWS. There is no pipeline that applies them: the existing `deploy-mls-infra` pipeline takes any working directory, so **do not point it at `terraform/live/uat` or `terraform/live/prod`** until the delivery lead has approved building the environment.

| Folder | Environment | Shape |
|---|---|---|
| `uat/` | User acceptance testing | Own VPC `10.43.0.0/20` (proposed), two zones, EKS, RDS PostgreSQL single-zone |
| `prod/` | Production | Own VPC `10.44.0.0/20` (proposed), three zones, EKS, RDS PostgreSQL Multi-AZ |

Dev and QA are different: they stay on standalone virtual machines (`lab-test` and its successors).

## Turning one on is a decision, in four steps

1. The delivery lead approves in writing, with the monthly cost in mind (`scripts/cost/k8s-environment-estimate.py`; estimates only).
2. The IAM change and the network, DNS and certificate decisions in `terraform/modules/k8s-environment/iam-by-hand.md` are made.
3. From a workstation, under approval, with `backend.hcl` copied from `backend.hcl.example` (separate state key per environment): `terraform plan -var enabled=true -var confirm=create-<env>-in-aws -var admin_role_arn=<role>`, the plan guard, then apply the plan that was reviewed. The stack refuses to turn on without that exact `confirm` text.
4. Argo CD is installed in the new cluster by hand once, and then loads `argocd/environments/<env>`.

## Account

The provider refuses any account except `account_id` (default `096631344162`). To give an environment its own account later, change that one variable and the backend; nothing else in the code names the account.

## Checked

`terraform fmt`, `validate` and `plan` with read-only credentials (enabled, local throw-away state, nothing applied): UAT 98 to add, Prod 115 to add, no change or destroy. With `enabled = false` the plan holds only the guard (no AWS resource).
