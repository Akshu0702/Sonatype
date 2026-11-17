---
name: ado-wiki
description: Write and maintain the project wiki the AzureDevOps-Platform-v1 way: the skeleton, the page template, a status label (Built, Partly, Not built) on every capability page, no secrets, updated in the same pull request as the change. Load when creating or editing wiki pages or when a change alters behaviour, a runbook step, a permission or an environment.
---

# Wiki

The wiki is a git repository; pages change by pull request like code.

## Rules (spec section 6)

- **One page, one topic.** Link instead of repeating. Plain language, short sentences.
- **Status label** on every capability: **Built**, **Partly** (with the gap), **Not built**. Never describe a plan as behaviour.
- **No secrets:** say where a secret lives, never what it is.
- **Update with the change:** a pull request that changes behaviour, a workflow, a runbook step, a permission or an environment edits the page in the same request. A story is not done until its pages are updated.
- Diagrams are Mermaid (`::: mermaid`). Tables for catalogues, prose for reasons. A decision gets its own page when it is hard to reverse, was debated or would surprise a newcomer.
- Sub-pages live in a folder named like the parent; `.order` files order siblings; links use absolute wiki paths.

## Skeleton

Home, Overview (status index), Architecture, Services, API Reference, Features and Feature Flags, Roles, Frontends, Infrastructure, Engineering (Work Management, Repositories and Naming, Branching and Release, CI/CD Plan, Artifact Feed and Tagging, Testing Strategy, Local Development, Documentation Standards, Pull request approval), Operations, Decisions, Roadmap.

## Page template

Title, one-paragraph purpose, `[[_TOC_]]`, What it does or owns, How it works, Rules and limits, Configuration, Tests, Related.

## Checks

`adoctl audit` reports a missing skeleton page (W01), a missing engineering page (W02), pages without a status label (W03), unnamed owners (W04) and a missing accepted-risk page (W05). Fix them in a pull request on the wiki repository.
