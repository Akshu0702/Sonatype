"""Audit results and the rule for the overall status."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field

SEVERITY_ORDER = {"critical": 0, "major": 1, "minor": 2}
STATUSES = ("pass", "warn", "fail", "not-applicable")

# Overall status rule (also printed in every report):
#   RED    any failed critical check, or 10 or more failed major checks
#   AMBER  not red, and any failed check, any warning, or any check that is not covered (could not run)
#   GREEN  no failed check, no warning, nothing not covered
OVERALL_RULE = (
    "Red: any failed critical check, or ten or more failed major checks. "
    "Amber: not red, and at least one failed check, warning, or check that could not run. "
    "Green: nothing failed, no warning, and every check ran."
)


@dataclass
class Result:
    id: str
    area: str
    title: str
    status: str
    severity: str
    subject: str = "project"
    evidence: str = ""
    fix: str = ""
    rule: str = ""
    blind: bool = False  # true when the check could not run (permission or API error)

    def key(self) -> str:
        return f"{self.id}|{self.subject}"

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class Collector:
    results: list[Result] = field(default_factory=list)

    def add(self, id, area, title, status, severity, rule, subject="project", evidence="", fix="", blind=False):
        assert status in STATUSES, status
        assert severity in SEVERITY_ORDER, severity
        self.results.append(Result(id, area, title, status, severity, subject, evidence, fix, rule, blind))

    def ok(self, id, area, title, rule, subject="project", evidence="", severity="minor"):
        self.add(id, area, title, "pass", severity, rule, subject, evidence)

    def fail(self, id, area, title, severity, rule, evidence, fix, subject="project"):
        self.add(id, area, title, "fail", severity, rule, subject, evidence, fix)

    def warn(self, id, area, title, rule, evidence, fix, subject="project", severity="minor"):
        self.add(id, area, title, "warn", severity, rule, subject, evidence, fix)

    def na(self, id, area, title, rule, evidence, subject="project"):
        self.add(id, area, title, "not-applicable", "minor", rule, subject, evidence)

    def blind(self, id, area, title, rule, evidence, subject="project", denied=False):
        """The check did not run. 401/403 (the identity may not read this) is 'NOT COVERED'; anything else is 'COULD NOT RUN'. Never a failure, never a pass."""
        lead = "NOT COVERED (the identity cannot read this): " if denied else "COULD NOT RUN: "
        self.add(id, area, title, "warn", "minor", rule, subject, lead + evidence,
                 "Give the identity that runs the audit read access to this area, then run again." if denied else "Check the error, then run again.", blind=True)


def overall(results: list[Result]) -> str:
    fails = [r for r in results if r.status == "fail"]
    if any(r.severity == "critical" for r in fails) or sum(1 for r in fails if r.severity == "major") >= 10:
        return "Red"
    if fails or any(r.status == "warn" for r in results):
        return "Amber"
    return "Green"


def counts(results: list[Result]) -> dict:
    c = {s: 0 for s in STATUSES}
    for r in results:
        c[r.status] += 1
    c["blind"] = sum(1 for r in results if r.blind)
    c["critical_fail"] = sum(1 for r in results if r.status == "fail" and r.severity == "critical")
    c["major_fail"] = sum(1 for r in results if r.status == "fail" and r.severity == "major")
    c["minor_fail"] = sum(1 for r in results if r.status == "fail" and r.severity == "minor")
    return c
