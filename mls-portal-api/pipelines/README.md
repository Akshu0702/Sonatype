# Pipelines

CI for `mls-portal-api`. Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Engineering/Branching-and-Release and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Security-and-Compliance/Secure-Development-Standards

**Status: not yet run in Azure DevOps. The scripts were run locally in containers; the YAML was compiled by Azure DevOps without running it.**

## What runs

- **OpenAPI contract:** Lints `openapi/*.yaml` with Redocly; fails on errors
- **Security scans:** gitleaks and Trivy (dependencies, secrets, misconfiguration)

## What does not run yet

**Build and unit tests are not here.** The implementation language and framework for this repository are not decided, so there is nothing honest to build or test yet. Add a `build-and-test` job when they are, and make it a required check.

## Rules

- Scans start in **report mode**: they write SARIF results and do not fail the build. Set `securityEnforce` to `true` once findings are triaged.
- A check with nothing to check yet **prints a warning and says it was skipped**; it never passes silently.
- Tool images are pinned in the scripts; a change is a pull request.
- The pipeline needs an agent with Docker. Hosted `ubuntu-24.04` has it. If hosted minutes are exhausted (they were for the organisation on 2026-11-03), run it with the `agentPool` parameter set to a self-hosted pool.
- The pipeline is **not** yet registered as a required build-validation policy. Register it and add the policy only when an agent is available, or every pull request is blocked.
