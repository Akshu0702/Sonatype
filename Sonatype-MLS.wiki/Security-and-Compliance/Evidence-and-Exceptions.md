# Evidence and Exceptions

How compliance is shown, and how a deviation is recorded.

[[_TOC_]]

## Control register

One row per control. The register lives in `mls-infra` (`compliance/controls.yml`) and this table is its summary. A control is added in the same pull request that creates the thing it protects.

| ID | Standard | Check | Frequency | Evidence | Owner | Status |
|---|---|---|---|---|---|---|
| CTL-01 | CIS Linux benchmark | Inspector scan of each AMI version | Each image build | Scan report in S3 | Platform | Not built |
| CTL-02 | CIS Linux benchmark | Inspector scan of running instances | Continuous | Inspector findings | Platform | Not built |
| CTL-03 | CIS EKS benchmark | kube-bench run | Weekly | Report in S3 | Platform | Not built |
| CTL-04 | CIS AWS Foundations, AWS FSBP | Security Hub standards | Continuous | Security Hub findings | Platform | Not built |
| CTL-05 | Secrets scanning | gitleaks on pull requests | Every build | SARIF | Delivery | Not built |
| CTL-06 | Code and dependency scanning | Semgrep, Trivy | Every build | SARIF | Delivery | Not built |
| CTL-07 | Access review | Recertification of roles and accounts | Quarterly | Review record | Portal lead | Not built |
| CTL-08 | Encryption | Config rules for KMS, TLS and public access | Continuous | Config compliance | Platform | Not built |

## Evidence

Evidence is stored in a versioned S3 bucket, with its source, date and the version of the thing it covers. The Gate 2 validation pack links to these records instead of copying them.

## Exceptions

A control that cannot be met is an exception, not a silent gap.

1. Raise a work item of type **Issue** tagged `exception`.
2. State the control, the reason, the risk, any compensating control, an owner and an **expiry date**, at most 90 days out.
3. The platform lead approves it. The author cannot approve their own exception.
4. Open exceptions are listed in the weekly status report. An exception past its expiry is treated as a finding.
5. Closing the exception, by fixing it or by renewing it with a new decision, is recorded on the same work item.

## Related

[Security and Compliance](/Security-and-Compliance), [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps).
