# ADR-0001: Custom portal only, with ITSM features

**Status:** Accepted

## Context
Sonatype needs ticketing, approvals, SLA tracking and reports for lab operations. The existing prototype portal covers intake and lifecycle.

## Decision
Extend the custom portal with ITSM features (change, incident, problem, SLA, notifications, CMDB-lite). No synchronisation with an external ITSM tool in this build.

## Consequences
- One system of record and one audit trail.
- Extra features (such as Jira or ServiceNow sync) are a change request.
- The ITSM feature list must stay fixed to protect the schedule.
