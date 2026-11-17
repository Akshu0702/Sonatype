# Documentation Standards

## Where things live

| Kind | Where |
|---|---|
| Behaviour, architecture, runbooks, decisions, roadmap | This wiki |
| Developer reference inside a repository | The repository README (short; links here) |
| Decisions | [Decisions](/Decisions), one page each |

## Rules

1. **Say what is built, partly built or planned.** Never describe a plan as behaviour. The status index is the capability map in [Managed Lab Overview](/Managed-Lab-Overview).
2. **One page, one topic.** Link instead of repeating.
3. **Update with the change.** A pull request that changes behaviour, a workflow, a runbook step, a permission or an environment edits the page in the same request. **A story is not done until its wiki pages are updated.**
4. **Plain language**, short sentences, defined terms.
5. **No secrets.** Say where a secret lives, never what it is.
6. **Diagrams as Mermaid** (`::: mermaid` blocks).
7. **Tables** for catalogues; prose for reasons.
8. **Decisions get a page** when they are hard to reverse, were debated or would surprise a newcomer.
9. **Status labels** on every capability: Built, Partly, Not built.

## Page template

```
# Title
One-paragraph purpose.
[[_TOC_]]
## What it does / owns
## How it works
## Rules and limits
## Configuration
## Tests
## Related
```

## Wiki mechanics

- Pages are Markdown; spaces in titles are hyphens in file names.
- A sub-page lives in a folder named like its parent (`Engineering.md` and `Engineering/Documentation-Standards.md`).
- `.order` files list page names (without `.md`) one per line to order siblings.
- Links use absolute wiki paths (`/Engineering/Documentation-Standards`).
- `[[_TOC_]]` inserts a contents list.
- The wiki is a git repository: changes are reviewed and pushed like code.

## Ownership

| Section | Owner |
|---|---|
| Architecture, Infrastructure | Platform lead |
| Automation, Repositories and Integrations | Automation lead |
| Portal and ITSM | Portal lead |
| Engineering, Operations | Delivery lead |
