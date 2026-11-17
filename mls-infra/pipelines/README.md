# Pipelines

CI for the `mls-infra` repository. Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Engineering/Branching-and-Release and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Security-and-Compliance/Secure-Development-Standards

**Status: not yet run. The scripts and YAML have not been executed in Azure DevOps.**

| File | Purpose |
|---|---|
| `ci-mls-infra.yml` | The pipeline: Terraform checks and security scans |
| `templates/terraform-checks.yml` | `terraform fmt`, `validate` and `test` for every module |
| `templates/security-scan.yml` | gitleaks and Trivy, in report mode |
| `../scripts/ci/terraform-checks.sh` | Runs Terraform from a pinned container image (`hashicorp/terraform:1.9.8`) |
| `../scripts/ci/security-scan.sh` | Runs the scanners and writes SARIF files |

## Rules

- The Terraform version is pinned in the script (`TF_IMAGE`); a change is a pull request.
- Scans start in **report mode**: they write results and do not fail the build. Set `securityEnforce` to `true` once findings are triaged.
- The pipeline needs an agent with Docker. Microsoft-hosted `ubuntu-24.04` has it.
- Pull requests into `main` and `develop` use this pipeline through the branch build-validation policy, which is added after the pipeline is registered.
- The Trivy version is pinned (`aquasec/trivy:0.70.0`) because an older Trivy cannot read the current check bundle and silently skips AWS checks.
- Trivy scans module code, which has no variable values, so resources created with `for_each` are only partly checked. The `live/` environment compositions are scanned with real values once they exist.
- **Agent capacity.** Two runs on Microsoft-hosted agents succeeded (under a minute with no modules, about 4 minutes with eleven modules). A third failed at once with "Your organization has no free minutes remaining". The organisation's free hosted minutes are shared and used up, so this pipeline needs a self-hosted agent in the lab account or purchased hosted minutes before build validation is relied on.

## Java build template

`templates/ci-java-api.yml` builds and tests a Java service or library: it runs the repository's own `scripts/test.sh` (Maven, JDK 21 and a throwaway PostgreSQL 16, all in containers, so the agent needs only Docker), publishes the JUnit results and hands the workspace back to the agent user. A repository's pipeline uses it as a step list after `- checkout: self`, with `mls-infra` declared as a repository resource named `infra`. First user: `ci-mls-commons-lib`.
