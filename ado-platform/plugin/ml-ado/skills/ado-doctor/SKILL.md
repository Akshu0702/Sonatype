---
name: ado-doctor
description: Run and read the Azure DevOps process audit (adoctl audit): overall Green, Amber or Red, drift by severity, what was not covered, and the trend since the last report; then turn the drift into an ordered fix list. Load when asked how the project is doing, about drift, deviation from the process, or the fortnightly audit report.
---

# Reading the audit

Run (read-only): `adoctl audit --manifest ado-project.yml --out audit-out [--previous previous.json]`. It writes `report.json` and `report.md`.

## Overall status

Red: any failed critical check, or ten or more failed major checks. Amber: not red, and anything failed, warned or **not covered**. Green: nothing failed, no warning, every check ran. Something not covered can never be Green.

## How to present it

1. The overall status and the one-line rule.
2. The trend if there is one: new drift, resolved drift, change in critical and major.
3. **Not covered**: the checks the identity could not read (listed as `NOT COVERED`) or that could not run (`COULD NOT RUN`). Say plainly that the report says nothing about them; they are not passes. Offer the fix: give the audit identity read access.
4. Drift, most severe first, each with where, what was found, how to fix and the spec rule.
5. Sort drift into **real drift** (the project deviates from the spec) and **suspected check bug** (the evidence looks wrong, for example the same finding on every repository). Say which you believe and why, and check the evidence before you say a rule is broken.

## Fix list

Group by who can act: the delivery lead (policy, relaxation, go-ahead), repository owners (pipelines, build validation), project managers (stories, tasks, test cases, task states), the DevOps engineer (identity permissions). One line per item, with the check id and the command or Board change that fixes it. Do not fix anything with this skill; the audit is read-only.

## Never

Print values of anything the secrets scan found (the report never has them). Re-run the audit to hide a failure. Change a manifest value to make a check pass: a relaxation is a recorded decision, not an edit.
