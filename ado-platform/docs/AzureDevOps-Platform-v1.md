# Azure DevOps Platform: Project Setup and Delivery Procedure (v1, template)

**Version:** 1 (template), written 2026-11-17. Source: a reference project that ran on Azure DevOps with this procedure (planning, branching, CI, artifact feed, release pipelines, wiki), plus one full engagement where every rule below was applied and tested.

**Purpose.** One procedure for every `<company>` project on Azure DevOps. A project manager who follows it gets the same result in every project: work is planned the same way, code reaches `develop` the same way, an artifact is built once and promoted, a deployment is a decision a named person takes, and every change is traceable from a Board item to a running version. A project is set up from one manifest file (section 2) in a fixed order (section 12), so a new project goes from empty to its first controlled deployment without invention.

**How to use this template.** Copy this file into the new project's infrastructure repository. Replace every `<placeholder>` (listed in the table below), delete options you do not use, and record the date and the person who decided each open item in section 17. Do not change a rule without writing the reason in the revision history. Text in `<angle brackets>` is always a value to fill in; text in `[square brackets]` is a choice between the listed options.

**State labels.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour. Fill the state column of the table below for the new project.

| Area | State in this project |
|---|---|
| Work management, repositories, branch policies, wiki | `<Built / Partly / Not built>` |
| CI, feed, release pipelines (as dry runs) | `<state>` |
| Promote pipeline, release manifest, tag protection, Test Plan per release | `<state>` |
| Project manifest, setup script, audit script, assistant plugin (section 14) | `<state>` |

## Placeholders used in this document

| Placeholder | Meaning | Example form |
|---|---|---|
| `<company>` | The organisation delivering the project | |
| `<organisation>` | The Azure DevOps organisation | |
| `<project>` | The Azure DevOps project name | |
| `<code>` | Short project code, 2 to 5 lower-case letters; prefix of repositories, pipelines and feeds | |
| `<person>`, `<delivery-lead>`, `<project-manager>`, `<approver>` | Named people (never groups that include the author) | |
| `<go-live-date>` | The acceptance or go-live date | `YYYY-MM-DD` |
| `<component>` | One area per repository (`<component-1>`, `<component-2>`) | |
| `<svc>`, `<int>`, `<name>` | Service, interface and pipeline names | |
| `<language>`, `<framework>`, `<build-tool>`, `<package-type>`, `<ui-framework>`, `<database>`, `<identity-provider>`, `<iac-tool>`, `<cloud>`, `<region>`, `<deploy-transport>`, `<container-runtime>` | The technology stack (section 2.2) | |
| `<agent-pool>` | The self-hosted build and deploy pool | |
| `<feed>` | The artifact feed name, normally `<code>-<package-type>` | |
| `<artifact-bucket>`, `<app-host-id>` | The deployment staging store and target host | |

---

## 1. Principles

1. **One task, one repository, one branch, one pull request.** Nothing is merged that is not linked to a work item.
2. **Build once, promote, deploy from the artifact store.** A version is built by CI, stored in the feed, promoted by view, and deployed from the feed. It is never rebuilt for another environment. The tag is the truth.
3. **A deployment is a decision.** No release starts by itself. A named approver approves before the first environment. The first real deployment of a project needs the `<delivery-lead>`'s explicit go-ahead. New release pipelines start as a dry run.
4. **Nobody approves or merges their own change.** An independent review happens before the request is opened, and the description says what was **not** covered.
5. **The wiki is part of the change.** A story is not done until its wiki pages are updated.
6. **Say what is built, partly built or planned.** Never describe a plan as behaviour.
7. **No secrets in Git, in chat, in pipeline variables or in the wiki.** Say where a secret lives, never what it is.
8. **Everything in this document can be checked by a script** (section 15). A rule that cannot be checked is a wish.

---

## 2. The project manifest

Every project has one file, `ado-project.yml`, in its infrastructure repository. It is the only input to setup. It holds names and decisions, never secrets.

### 2.1 Project, people, sprints, repositories, policies, feed, environments

```yaml
project:
  code: <code>
  name: <project>
  organisation: <organisation>
  process: Agile                  # [Agile | Scrum | CMMI]; the rules below assume Epic > Feature > User Story > Task, Test Case, Bug
  delivery_lead: <delivery-lead>  # approves first deployments and promotions
  project_manager: <project-manager>
  go_live: <go-live-date>         # sprints are counted back from it

sprints:
  length_days: <7>
  first_start: <YYYY-MM-DD>
  count: <n>

areas: [<component-1>, <component-2>, Delivery]   # one per repository, plus Delivery for non-code work

repositories:                     # name = <code>-<svc>-<kind>
  - {name: <code>-infra,           kind: infra}
  - {name: <code>-<svc>-api,       kind: api,    area: <component-1>}
  - {name: <code>-<int>-ui-<ui-framework>, kind: ui, area: <component-2>}
  - {name: <code>-commons-lib,     kind: library, area: <component-3>}   # optional

policies:
  min_reviewers: <1>
  author_can_approve: false       # see section 5.2; a project may relax this once, in writing
  reset_votes_on_push: true
  merge_develop: squash
  merge_main: merge-commit
  max_file_size_mb: <10>

feed:
  name: <feed>
  package_type: <package-type>    # [maven | npm | nuget | pypi | universal | container]
  views: [Local, Prerelease, Release]
  upstream: [<public-registry>]

environments:                     # in promotion order
  - {name: <env-1>, approver: <approver>, dry_run_first: true}
  # - {name: <env-2>, approver: <approver>}
  # - {name: <env-3>, approver: <delivery-lead>}

agents:
  pool: <agent-pool>              # self-hosted; one agent may serve CI and deploy (accepted risk, section 11)
```

### 2.2 Technology stack

The stack is a decision recorded once and read by every template, skill and check. Fill every line; write `none` where it does not apply. Rules in this document name the role of a tool (for example "the build tool"), never a product.

```yaml
stack:
  language:           <language>            # e.g. a JVM, .NET, Node, Python or Go version, pinned
  framework:          <framework>           # application framework and its major version, pinned
  build_tool:         <build-tool>
  package_type:       <package-type>        # what the feed stores
  ui:                 <ui-framework>        # or none
  database:           <database>            # engine and major version, pinned
  schema_migration:   <migration-tool>      # or none
  identity_provider:  <identity-provider>   # or none
  test_tools:         {unit: <tool>, api: <tool>, e2e: <tool>, security: <tool>, performance: <tool>}
  scan_tools:         {secrets: <tool>, code: <tool>, dependencies: <tool>, images: <tool>}
  iac_tool:           <iac-tool>            # or none
  cloud:              <cloud>               # provider, account or subscription layout
  region:             <region>              # and any region restriction
  container_runtime:  <container-runtime>   # or none
  deploy_transport:   <deploy-transport>    # [agent-to-host remote command service | SSH | cluster GitOps | cloud-native deploy service]
  artifact_staging:   <artifact-bucket>     # where the deploy step places the package so the host can fetch it
  versions_policy:    <where pinned versions are decided and who reviews upgrades>
```

Rules for the stack: versions are pinned and reviewed; a change of stack is a decision recorded as a decision page in the wiki; every build runs in a pinned container image so results do not depend on the agent; templates in section 7 exist for each combination used.

---

## 3. Work management (Azure Boards)

### 3.1 Hierarchy

| Level | Is | Sized to |
|---|---|---|
| Epic | A business outcome | the engagement |
| Feature | A capability a user can use | one to two weeks |
| User Story | One user-visible change with acceptance criteria | one sprint |
| Task | One unit of work in exactly one repository: one branch, one pull request | one to three days |
| Test Case | A repeatable check of one acceptance criterion | |
| Bug | A defect with a regression Test Case | |

### 3.2 Rules

- **Story title:** "As a `<who>` I `<do what>` so that `<why>`". A story without acceptance criteria is not ready.
- **Task title:** a verb and an object ("Add the deploy scripts"). One repository per task; a story that touches three repositories has at least three tasks.
- **Area path** is the repository's component (`<component>`). Check the real path values before bulk creation, because a wrong path rejects the whole batch. Non-code work is `Delivery`.
- **Iteration path** is `<project>\Sprint N`.
- **Test Cases:** one per acceptance criterion, linked *Tested By*. Automated checks are named with the Test Case id, for example `[TC1234]`.
- **States:** New, Active (the branch exists), Resolved (the pull request is merged), Closed (Test Cases pass and wiki is updated).
- **A feature that adds a user-visible capability** is not done until it has its entry in the feature catalogue (if the product has one), a flag off by default, a default role assignment and its wiki pages.
- **Description limits:** keep a pull request description under 4,000 characters; link the story rather than paste it.

### 3.3 Sprints and cadence

Sprints of `<length>` days counted back from `<go-live-date>`. Each sprint has a focus and an exit (a demo or a gate). The first sprint is kick-off and architecture; the last is acceptance and go-live.

| Event | When | Output |
|---|---|---|
| Planning | Day 1 | Stories pulled into the sprint, each with tasks per repository |
| Stand-up | Daily | Blockers recorded on the task, not in chat |
| Review and demo | Last day | Stories closed or carried with a reason |
| Re-baseline | Every sprint | Remaining work against `<go-live-date>`; a scope cut is proposed in writing when it does not fit |

### 3.4 Helper

The helper `adoctl work start <task>`, `adoctl work pr <task>` and `adoctl work status` (section 14) creates the branch `feature/US<story>-T<task>`, sets the task Active, opens the pull request with the work item linked, and reports the state of every task in the sprint.

---

## 4. Repositories and naming

| Kind | Pattern | Example form |
|---|---|---|
| Infrastructure | `<code>-infra` | Infrastructure code, pipelines and templates, release manifests, end-to-end suites |
| API | `<code>-<svc>-api` | |
| UI | `<code>-<int>-ui-<ui-framework>` | |
| Shared library | `<code>-commons-lib` | |
| Feature branch | `feature/US<story>-T<task>` | `feature/US101-T105` |
| Bugfix / hotfix | `bugfix/US<story>-T<task>`, `hotfix/US<story>-<issue>` | `hotfix/US102-loginBug` |
| Release branch | `release/X.Y.Z` | `release/1.0.0` |
| Release candidate tag | `vX.Y.Z-rc.N` | `v1.0.0-rc.1` |
| Release tag | `vX.Y.Z` | `v1.0.0` |
| CI / CD pipeline | `ci-<code>-<name>`, `cd-<code>-<name>` | |
| Promote pipeline | `promote-<code>-release` | |
| Test pipelines | `test-<code>-e2e`, `-security`, `-performance` | |
| Feed | `<code>-<package-type>` | |

Every repository is created with `main` and `develop` (default branch `develop`), a README, `.gitignore`, a pull request template (`.azuredevops/pull_request_template.md`), and the policies of section 5. Cloud resources follow `<company>`'s nomenclature sheet (`<project>-<resource-type>-<environment>-<function>-<id>` or the sheet's own pattern) and are tagged by automation, never by hand.

---

## 5. Branching and pull request policy

### 5.1 Branches

`main` holds released code only and is never built or deployed. `develop` integrates finished work and is the source of snapshots. Feature, bugfix and hotfix branches build and test but publish nothing. `release/X.Y.Z` stabilises one release. Direct pushes to `main` and `develop` are blocked.

### 5.2 Policies on `main` and `develop` (blocking, every repository)

| Policy | Setting |
|---|---|
| Minimum reviewers | `<1>`, and the author and last pusher cannot count. Votes reset on a new push |
| Comment resolution | All comments resolved before completion |
| Work item linking | Required |
| Build validation | The repository's `ci-*` pipeline, required, valid until the target branch changes |
| Merge strategy | `develop`: squash only. `main`: merge commit only, so the tagged commit stays reachable |
| File size (repository level) | `<10>` MB |

Build validation is added to a repository when its `ci-*` pipeline exists, and the repository's README says so until then.

**Approval relaxation.** If the project has a single reviewer, the `<delivery-lead>` may approve a pull request created by an assistant or another team member. This is recorded in the wiki (page *Pull request approval*) with the date and the person who decided it, and is reversed when a second reviewer is available. Date and decision for this project: `<date, person, or "not relaxed">`.

### 5.3 Pull request rules

1. One task, one pull request, one repository, linked to its work item, targeting `develop`. Pull requests are never stacked on feature branches: a squash merge strands the work above it. Open as `[draft | ready for review]` (choose once for the project; this template recommends ready for review when build validation requires a published request).
2. Tests and documentation, including the wiki page, are in the same pull request.
3. The description has the sections **What**, **Verified**, **Not covered**, and the checklist from the template (Appendix A).
4. No one approves or merges their own pull request. An assistant never approves or merges.
5. Merge order follows dependency: shared templates and library first, then services, then automation, then user interface. A pull request that consumes a file from another pull request is merged **after** it (section 16, item 1).
6. After a dependency is merged, requeue the build validation of the waiting pull requests (section 16, item 2).

### 5.4 Quality gates

Report mode first; blocking once findings are triaged. Gates: build and unit tests, API contract lint, secrets scan, code scan, dependency scan, and image scan when images exist (tools in `stack.scan_tools`).

---

## 6. Wiki

The wiki is a git repository, reviewed and pushed like code. It is the only home for behaviour, architecture, runbooks, decisions and the roadmap; a repository README stays short and links to it.

**Sections (the skeleton every project starts with):** Home, Overview with the status index, Architecture, Services (one page per service and the library), API Reference, Features and Feature Flags, Roles, Frontends, Infrastructure, Engineering (Work Management, Repositories and Naming, Branching and Release, CI/CD Plan, Artifact Feed and Tagging, Testing Strategy, Local Development, Documentation Standards, Pull request approval), Operations (Runbooks, Troubleshooting, Known Issues), Decisions (one page per decision), Roadmap.

**Rules:** one page, one topic; a status label on every capability (Built, Partly, Not built); plain language; no secrets; diagrams as Mermaid; tables for catalogues; a decision gets its own page when it is hard to reverse, was debated or would surprise a newcomer. Sub-pages live in a folder named like the parent; `.order` files order siblings; links use absolute wiki paths. **A pull request that changes behaviour, a workflow, a runbook step, a permission or an environment edits the page in the same request.**

**Page template:** title, one-paragraph purpose, contents, What it does or owns, How it works, Rules and limits, Configuration, Tests, Related.

**Ownership:** each section has a named owner in the wiki: Architecture and Infrastructure `<platform-lead>`; Automation `<automation-lead>`; Product and portal `<product-lead>`; Engineering and Operations `<delivery-lead>`.

---

## 7. Continuous integration

- One pipeline per repository, `ci-<code>-<name>`, a short YAML that calls **shared templates** in `<code>-infra/pipelines/templates`. The pipeline file holds only the repository's parameters.
- Templates per kind, written for the project's stack: `ci-<api-kind>` (set up the language runtime, restore the dependency cache, build the shared library if the repository depends on it, start the database in a container, run the build tool's full verify step, publish test results, stage the artifact), `ci-<ui-kind>` (install, lint, type-check, unit tests, build), `version` (computes the version and the channel from the branch), `publish` (publishes to the feed and promotes release candidates), and scan templates (secrets, code, dependencies, images).
- **Triggers:** `main`, `develop`, `feature/*`, `bugfix/*`, `hotfix/*`, `release/*` and pull requests. Only `develop` and `release/*` publish.
- **Version and channel from the branch:** `develop` gives `X.Y.Z-SNAPSHOT` (or the package type's pre-release form) into the `@Local` view; `release/X.Y.Z` gives `X.Y.Z-rc.N`, promoted to `@Prerelease` and tagged `vX.Y.Z-rc.N`. A version is built once and never reused; a mistake is corrected by the next version.
- **Agents:** a self-hosted pool `<agent-pool>` for builds that need containers. A build runs in a pinned container so that results do not depend on the agent. The pool's identity may deploy only to the project's own environment (accepted risk, section 11).
- A step that publishes uses a short-lived token (`System.AccessToken`), never a stored one.
- CI never deploys. Deployment is release management (section 9).

---

## 8. Artifact management (Azure Artifacts)

| Item | Rule |
|---|---|
| Feed | `<feed>`, project-scoped, with `<public-registry>` as upstream |
| Views | `@Local` (snapshots from `develop`), `@Prerelease` (release candidates), `@Release` (promoted releases) |
| Versions | `X.Y.Z-SNAPSHOT`, `X.Y.Z-rc.N`, `X.Y.Z`. The promoted release is the same bytes as the tested candidate |
| Permissions | The project's build service identity is **Contributor** on the feed; the release identity is **Reader**; people are Readers |
| Container images and charts | The registry used by the runtime, tagged with the same version |
| Retention | Snapshots keep the latest `<N>`; candidates and releases are kept; nothing a deployed version needs is deleted |

The wiki page *Artifact Feed and Tagging* states the feed, the views and the tag rules for the project.

---

## 9. Release management

### 9.1 Pipelines

| Pipeline | Kind | Purpose |
|---|---|---|
| `ci-<code>-<name>` | YAML | Build, test, publish to the feed (section 7) |
| `cd-<code>-<name>` | Classic **Release** | Deploys one package **from the feed** to one environment. Artifacts: the package (a view) and the infrastructure repository (for scripts) |
| `promote-<code>-release` | YAML, approval-gated | Takes the tested candidate, adds the **same bytes** to `@Release`, tags `vX.Y.Z`, opens the pull request `release/X.Y.Z` to `main`, and records the set in `releases/X.Y.Z.yml` |
| `test-<code>-e2e`, `-security`, `-performance` | YAML | End-to-end suites in `<code>-infra/verify`, security and performance runs; every check named with its Test Case id |

### 9.2 Release definition rules

- **No trigger.** A release is created by a person, or by a documented trigger once the `<delivery-lead>` has approved auto-deploy for that environment in writing.
- **A named approver before the first environment**, as a person, not a group that includes the author.
- **A variable `dryRun`, default `true`.** A dry run downloads and checks the artifact, prints its checksum, and says what a real run would do. It makes no call to `<cloud>`. The first real run of a project needs the `<delivery-lead>`'s go-ahead, recorded on the Board.
- **Deploy from the feed**, never from a rebuild. The deploy script checks the checksum, replaces the running version, waits for the health check, and starts the previous version again if the new one is not healthy. It logs who, what and when.
- **Deploy transport (`<deploy-transport>`):** where hosts have no shell key, use the cloud's remote-command service limited by policy to the one host and the one command document; the package travels through `<artifact-bucket>`, which the host's role can read. Where SSH is used, keys are held by the pipeline's secret store and are rotated.
- **Secrets on the target** live in a settings file on the host or in the cloud's secret store, never in the pipeline.

### 9.3 Promotion

`develop` builds a snapshot into `@Local`; a merge to `develop` may create a first-environment release (only after written approval of auto-deploy). A release candidate comes from `release/X.Y.Z`, is tested, and is promoted by the promote pipeline after an approver signs. `releases/X.Y.Z.yml` lists every component, version, commit and tag, and the evidence (end-to-end run, security run, performance run, approver). Tags `v*` are protected: no one can move or delete them.

### 9.4 Release notes

`# Release X.Y.Z (date) [MAJOR|MINOR|PATCH]` with Summary, Upgrade first, What is new (story id, flag, package, wiki page), Fixed, Permissions and roles and flags, Data and migrations, API and contract changes, Components, Evidence, Known issues, Documentation.

### 9.5 Rollback

Rollback is a release of the previous version from the feed: the same pipeline, an older package. The deploy script restarts the previous version automatically if the new one is not healthy. A version is never edited in place.

---

## 10. Testing and evidence

- **Layers:** unit, API (contract), integration with a throwaway database, end-to-end, security, performance (tools in `stack.test_tools`).
- **A Test Case per acceptance criterion**, a Test Plan per release, results attached to the release.
- **Every pull request** states what was verified (with the command and the count) and what was not.
- **A change to a database or authorization rule** includes a test run against a real database as a non-superuser, so that row-level rules are actually applied.
- **Honesty rule:** a feature is "done" only for the paths that were run. Anything run with a stand-in (a fake service, a mocked provider) is labelled so.

---

## 11. Identities, secrets and risk

- **Personal access tokens** are never pasted in chat, committed or stored in a pipeline variable. A person who must give a token to an assistant creates it with the least scopes and the shortest life, and **rotates it afterwards**. Pipelines use `System.AccessToken`; automation uses a service identity.
- **Cloud credentials** come from the agent's instance or workload identity. Policies that create roles or users are created by hand by an administrator; a guardrail policy denies what a pipeline must never do (other regions, the default network, other teams' resources, creating users and access keys).
- **Accepted-risk register** is kept in the wiki: for example the shared CI and deploy agent, a relaxed approval rule, a database version still open. Each has an owner and a date.
- **Environments** are named, tagged and costed; a provisioned environment has a time-to-live, an owner and a ticket tag, and a reaper removes it.
- **Nothing outward-facing without confirmation:** publishing, sending, creating cloud resources or deploying needs the `<delivery-lead>`'s approval unless a standing approval is written down.

---

## 12. Setup sequence (empty project to first controlled deployment)

Each step is idempotent: running it twice changes nothing. A step is done when its check (section 15) passes.

| # | Step | Check |
|---|---|---|
| 1 | Read the manifest; confirm the project, process, stack, people and go-live date | Manifest valid |
| 2 | Create the project (`<process>`), areas, and sprints counted back from `<go-live-date>` | Areas and iterations exist |
| 3 | Create the repositories with `main` and `develop`, README, `.gitignore`, pull request template; set `develop` as default | Each repository has both branches |
| 4 | Apply the branch policies of section 5.2 to `main` and `develop` in every repository (except build validation) | Policy list matches |
| 5 | Create the wiki skeleton (section 6) with the engineering pages filled from this spec | Pages exist with status labels |
| 6 | Create the backlog skeleton: epics from the project's scope, features, and a story *Align delivery with the standard* with tasks for sections 7 to 9 | Hierarchy and area paths valid |
| 7 | Put the shared templates in `<code>-infra/pipelines/templates` (merged **first**) | Templates on `develop` |
| 8 | Create the `ci-*` pipelines, authorise them, then add build validation to the branches | A pull request builds |
| 9 | Create the feed with its views; grant the build identity Contributor; publish the first snapshot | Snapshot in `@Local` |
| 10 | Create the `cd-*` Release pipelines as dry runs (no trigger, approver, `dryRun=true`) | A dry-run release downloads and prints a checksum |
| 11 | Prepare the deployment target (`<artifact-bucket>`, role permissions, remote-command access, host `<app-host-id>`) | Dry-run checks pass; the target is reachable |
| 12 | `<delivery-lead>`'s explicit go-ahead on the Board; set `dryRun=false`; first controlled deployment; health check; log | Deployed version equals the feed version |
| 13 | Record what was verified and what was not on the Board and the wiki | Report written |

---

## 13. What a project manager does

**Daily:** read `adoctl work status` (or the sprint board): tasks without a pull request after two days, pull requests waiting for review more than a day, red builds on `develop`.
**Weekly:** run the audit (section 15) and fix drift; update the status index and the roadmap in the wiki; re-baseline the remaining work against `<go-live-date>`; report in the fixed form (done, in progress, pending, risks, decisions needed).
**Per release:** create the Test Plan; confirm each story has passing Test Cases; check the promote pipeline's evidence; sign off; archive the release notes.
**Never:** approve or merge your own pull request; start a release without the approver; copy a token into chat; describe a plan as built; close a story whose wiki pages are not updated.
**Escalate** to the `<delivery-lead>`: a scope cut, a relaxed policy, a first deployment, any change to the accepted-risk register.

---

## 14. Automation: how the setup and the discipline are enforced

**Aim:** a project manager says "set up project X from this manifest" and gets section 12 done, with checks, without knowing the REST calls; and the weekly audit finds drift without a person looking at every setting.

### 14.1 Options compared

| Option | What it is | Good for | Weakness |
|---|---|---|---|
| **A. Document only** | This spec in the wiki | Shared understanding | Not enforced; every project drifts |
| **B. Script (`adoctl`)** | A small command-line tool that reads `ado-project.yml` and calls the Azure DevOps REST API; `plan` shows what it would change, `apply` does it, `audit` reports drift | Deterministic, testable, works in a pipeline, no AI needed | Someone must keep it current as the API changes |
| **C. Infrastructure as code (`<iac-tool>`, for example the Azure DevOps provider for Terraform)** | Declare the project, repositories, policies, pipelines, feed as code | Repositories, policies, pipelines and permissions with drift detection and state | Coverage of Boards content, wiki pages and classic Release definitions is **not confirmed**; check the provider's resource list before relying on it; needs state storage |
| **D. Azure DevOps CLI (`az devops`)** | Microsoft's CLI | Quick one-off commands | Covers a subset; no policy-set-from-manifest |
| **E. Assistant skills** | `SKILL.md` instructions that load when a task matches | Judgement and procedure: how to write a story, how to write the pull request description, what to check before a release | Instructions, not enforcement; a skill alone cannot guarantee a setting |
| **F. Assistant subagent** | A named agent with its own tools and prompt (a Markdown file with a short header) | Orchestrating a long multi-step setup with approvals; a read-only auditor | Costs tokens; needs the same safeguards as a person |
| **G. Assistant plugin** | A package that bundles skills, agents, slash commands and hooks, installed from a marketplace | Distribution to every project manager with versioning | Needs a repository and a maintainer |
| **H. MCP server for Azure DevOps** | Tools the assistant calls for work items, repositories, pipelines, wiki | Day-to-day reads and writes by the assistant | May not cover policies, feeds and release definitions fully (verify per server) |

### 14.2 Recommendation

**Use B as the engine, G to deliver it, and E and F to make it easy and safe.** Do not rely on the assistant to remember the REST calls: they live in the script, which is tested once. The assistant supplies judgement, reads the manifest, explains the plan and asks for approval.

1. **`adoctl` (script):** `init` (writes a manifest from questions), `plan`, `apply [--step N]`, `audit`, `work start|pr|status`, `release dry-run|promote`. Dry run by default; `apply` needs `--apply`. Reads the token from an environment variable or the system keyring; never writes it anywhere.
2. **A plugin `<company>-ado`, added to the company's plugin marketplace** (the same marketplace that delivers other engineering plugins), so a project manager installs it the same way and gets updates by version. It contains:
   - **Skills:** `ado-project-setup` (sections 2 and 12), `ado-work` (stories, tasks, naming, definition of done), `ado-pr` (description with Verified and Not covered; merge order), `ado-release` (dry run, approval, promotion, rollback), `ado-wiki` (page template and status labels), `ado-doctor` (the audit).
   - **Agents:** `ado-bootstrapper` (runs `plan`, shows it, asks, runs `apply` step by step, stops at each approval), and `ado-auditor` (read-only: runs `audit` and writes the drift report; it has no write tools).
   - **Slash commands:** `/ado-setup`, `/ado-audit`, `/ado-work`, `/ado-release`, each a thin entry to the skill.
   - **Hooks:** a pre-tool check that blocks a command which would put a token into a file, and one that blocks `dryRun=false` unless the Board shows the `<delivery-lead>`'s go-ahead.
3. **Keep infrastructure as code optional.** Consider it for repositories, policies and feeds only after confirming the provider covers them in the version you use; keep Boards, wiki and classic Release definitions in `adoctl`.
4. **Keep this spec in the repository the plugin ships**, so the skills and the document cannot diverge; the plugin's tests compare the policy table in section 5.2 with what `audit` checks.

### 14.3 Safeguards the tooling must keep

- Dry run first, apply second, a person approves each outward-facing step (creating a repository, a pipeline, a feed, a release, a deployment).
- The setup identity is a service identity with only the scopes it needs; it is not an administrator.
- Every action is written to a setup log in the infrastructure repository (what, when, who approved).
- The tool refuses to run against a project that is not in the manifest, and never deletes anything.
- The assistant never approves or merges a pull request, and never sets `dryRun=false`.

### 14.4 Build plan for the tooling (`<state>`)

| Step | Output | Done when |
|---|---|---|
| 1 | `adoctl` with `plan` and `apply` for section 12 steps 2 to 4 | A scratch project is set up from a manifest and `audit` is clean |
| 2 | Steps 5 to 10 (wiki, backlog skeleton, templates, pipelines, feed, release pipelines) | A scratch project reaches a dry-run release |
| 3 | `audit` for every rule in this spec (section 15) | Drift in a deliberately broken project is found |
| 4 | The plugin: skills, two agents, commands, hooks | A project manager sets up a second project without help |
| 5 | A pilot on the next real project; correct this spec | Spec v1.1 |

---

## 15. Audit checklist (what `audit` verifies)

| Area | Check |
|---|---|
| Boards | Areas and sprints exist; every Task has one parent Story and one repository area; every Story has acceptance criteria and a Test Case; no Task Active without a branch; no Story Closed with wiki pages not updated |
| Repositories | Names match section 4; `main` and `develop` exist; default is `develop`; README and pull request template present |
| Policies | The six policies of section 5.2 are enabled and blocking on both branches of every repository; merge strategies correct; build validation present wherever a `ci-*` pipeline exists |
| Pull requests | Every active pull request has a linked work item and targets `develop`; none stacked; descriptions have Verified and Not covered; none older than three days without a review |
| CI | Every repository has a `ci-*` pipeline calling the shared templates; the last run on `develop` is green; pipelines are authorised for the feed |
| Feed | Views exist; the build identity is Contributor; the latest snapshot of each service is in `@Local` |
| Release | Every `cd-*` has no trigger, a named approver and `dryRun` defined; a promote pipeline exists once a release branch exists; `v*` tags protected; `releases/X.Y.Z.yml` exists for each released version |
| Wiki | Skeleton present; every page has a status label; engineering pages match this spec; owners named |
| Security | No token in any repository, variable or wiki page (secrets scan); the accepted-risk page is current |
| Stack | Every pinned version in `stack` matches the build files and the container images used by CI |

---

## 16. Pitfalls already met (do not repeat)

1. **Merge order.** A pull request that calls a template from another pull request broke every consuming build when it was merged first. Merge the template, then the consumers; if it happens, merge the missing piece and requeue.
2. **Requeue after the fix.** A failed build validation does not rerun by itself when its dependency is merged. Requeue it through the policy evaluation (`PATCH policy/evaluations/{id}`) or push an empty commit.
3. **Area paths:** use the exact values the project returns; a wrong path rejects the whole batch.
4. **Work item type names with a space** must be URL-encoded in the REST path (`$User%20Story`).
5. **Descriptions longer than 4,000 characters** are rejected on a pull request.
6. **Pipelines and feeds need permission.** A new feed is not readable by a pipeline until the build identity is Contributor; a new YAML pipeline needs authorisation for the resources it uses (`pipelinepermissions`).
7. **Classic Release definitions** reject unknown fields; create them from a known-good definition and change only names and ids.
8. **Publishing a package** to a feed: use the package type's explicit target-repository option; keep the settings file with the token in a temporary location inside a throwaway container; fix file ownership afterwards so the agent can clean its workspace.
9. **Developer machines on Windows:** set `MSYS_NO_PATHCONV=1` in Git Bash before container commands and paths that start with `/`; some file-mode commands fail on Windows file systems, so create folders and set modes in two steps.
10. **Login flows on `localhost`:** some HTTP libraries drop cookies for `localhost`; test against a real host name or handle cookies by hand.
11. **Hosts without a shell key** cannot be deployed to over SSH; plan the cloud's remote-command service from the start.
12. **Tokens in chat.** A token pasted into a conversation must be rotated, even if it was used correctly.
13. **Database tests as the owner** hide missing row-level rules; test as a non-superuser.
14. **Polling wastes time;** use notifications (builds, pull requests, releases) rather than waiting in a loop.

---

## 17. Open decisions and not built

Fill one row per open item; remove the row when decided and record the decision in the wiki.

| Item | Owner of the decision | Decided on |
|---|---|---|
| Whether infrastructure as code (`<iac-tool>`) manages repositories, policies and feeds | `<delivery-lead>` | |
| Who maintains `adoctl` and the plugin | `<delivery-lead>` | |
| Whether auto-deploy to the first environment is ever enabled (default: never until approved in writing) | `<delivery-lead>` | |
| Tag protection and the promote pipeline | `<delivery-lead>` | |
| A Test Plan per release and the `status` report format | `<project-manager>` | |
| Pinned versions in `stack` and who reviews upgrades | `<platform-lead>` | |

---

## Appendix A. Pull request template

```
## What
<one paragraph; the task and story ids>

## Verified
<commands run, counts, what the result proved>

## Not covered
<what was not run, what is a stand-in, what is left>

## Checklist
- [ ] Linked to a work item
- [ ] Targets develop
- [ ] Tests and wiki page in this pull request
- [ ] No secrets
```

## Appendix B. Story and task templates

```
Story title:  As a <who> I <do what> so that <why>
Acceptance criteria (each becomes a Test Case):
  1. Given <state>, when <action>, then <result>
Tasks (one per repository): <verb> <object> [area: <component>]
Definition of done: Test Cases pass; pull requests merged; wiki pages updated; feature flag and role set (user-visible features).
```

## Appendix C. Release definition skeleton (classic)

Artifacts: the package from `<feed>` (view `@Local`, `@Prerelease` or `@Release`) as the primary, and `<code>-infra` (branch `develop`) as the source of scripts. Environment `<env-1>`: pre-deployment approval by `<approver>`, no gates, one agent phase on `<agent-pool>`, one script step that calls `scripts/deploy/deploy-from-feed.sh` with `SERVICE`, `GROUP`, `ARTIFACT`, `BASEVER`, `PORT`, `HEALTH_PATH`, `FEED_TOKEN=$(System.AccessToken)`, `DRY_RUN=$(dryRun)`, `DEPLOY_BUCKET=<artifact-bucket>`, `APP_HOST_ID=<app-host-id>`. Variables: `dryRun=true`, `deployBucket`, `appHostId`. No trigger.

## Appendix D. Revision history

| Version | Date | Change | By |
|---|---|---|---|
| 1 (template) | 2026-11-17 | First version, written from a reference project and one full engagement | `<person>` |
