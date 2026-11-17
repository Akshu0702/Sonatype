# prod (code only)

**NOTHING FOR PRODUCTION EXISTS IN AWS.** This stack is switched off (`enabled = false`); a plan or apply creates nothing in AWS, and no pipeline applies it. Do not point `deploy-mls-infra` at this folder.

Read `../README-k8s-environments.md` (what it is, the four steps to turn it on, the account rule) and `../../modules/k8s-environment/README.md` and `iam-by-hand.md`. Prod is checked: three zones and a Multi-AZ database, or the plan is refused.
