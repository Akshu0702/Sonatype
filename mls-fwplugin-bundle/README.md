# mls-fwplugin-bundle

Packaging of the Sonatype Firewall plugin bundle: groovy file, plugin jar, listener jar and SHA-256 manifest.

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
