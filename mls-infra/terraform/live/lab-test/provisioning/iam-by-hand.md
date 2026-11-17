# What must be done by hand in IAM before the first apply

IAM is changed by a person, not by a pipeline. Everything below is in account `096631344162`, region `ap-south-1`.
**Nothing in this folder has been applied.** Do these in order; each step says how to check it.

## 1. Let the deploy role plan and apply this stack

The build agent runs Terraform with the policies `mls-deploy-allow` and `mls-deploy-guardrails`. `mls-deploy-allow` has no Step Functions or Image Builder permission yet, and the Terraform AWS provider calls `states:ValidateStateMachineDefinition` during **plan**, so even a plan fails today (it does, with the read-only profile: "not authorized to perform states:ValidateStateMachineDefinition").

1a. **IAM, Policies, `mls-deploy-allow`, Edit, JSON.** Add the five statements in `mls-deploy-allow-additions.json` (`StepFunctionsByPrefix`, `StepFunctionsListAndValidate`, `LetStepFunctionsDeliverLogs`, `ImageBuilderByPrefix`, `ImageBuilderListAndReadManaged`) to the `Statement` list. Leave every existing statement as it is.

1b. In the **existing** statement `ServiceLinkedRoles`, add `imagebuilder.amazonaws.com` to the list under `iam:AWSServiceName`. (Image Builder creates its service-linked role the first time it is used.)

1c. In the **existing** statement `IamAttachOnlyApprovedPolicies`, add `arn:aws:iam::aws:policy/EC2InstanceProfileForImageBuilder` to the list under `iam:PolicyARN`. (The image build instance's role needs it; the other policy it needs, `AmazonSSMManagedInstanceCore`, is already on the list.)

1d. Save as a new policy version. **Check:** in the Policy simulator choose the agent's role, then test `states:ValidateStateMachineDefinition`, `states:CreateStateMachine` on `arn:aws:states:ap-south-1:096631344162:stateMachine:mls-sfn-dev-lab-001`, `imagebuilder:CreateComponent` on `arn:aws:imagebuilder:ap-south-1:096631344162:component/mls-img-dev-lab-001-harden/1.0.0/1`. All three must be allowed. `mls-deploy-guardrails` needs no change: it has no deny on `states:`, `imagebuilder:` or `logs:`.

## 2. What Terraform creates by itself (no hand step)

These are created under the existing statement `IamRolesAndPoliciesByPrefix` (names start with `mls-`), so nothing is added for them. They are listed so a reviewer knows what to expect:

| Role | Who assumes it | What it may do |
|---|---|---|
| `mls-sfn-dev-lab-001` | The `provision-environment` state machine | Launch one EC2 instance **only** with `project=mls`, `managed-by=provisioning` and the tags owner, ticket, expires-at, ttl, environment; only `t3.small`, `t3.medium`, `t3.large`; only into the named subnets and security group; only from the images in the catalogue; only with encrypted volumes. Terminate only instances tagged `project=mls` and `managed-by=provisioning`. Pass only the environment role, only to EC2. Nothing outside ap-south-1 |
| `mls-sfn-dev-lab-002` | The `ttl-reaper` state machine | Read instances, and terminate only instances tagged `project=mls` and `managed-by=provisioning` |
| `mls-role-dev-lab-001` | The launched instances | Session Manager only (`AmazonSSMManagedInstanceCore`) |
| `mls-role-dev-lab-002` | EventBridge Scheduler | `states:StartExecution` on the reaper |
| `mls-img-dev-lab-001-build` | The image build instance | `AmazonSSMManagedInstanceCore`, `EC2InstanceProfileForImageBuilder`, and `s3:GetObject` on `images/*` of the deploy bucket |

The state machine roles are **not** covered by `mls-deploy-guardrails` (that policy is attached to the deploy role, not to them), so the limits above are in their own policies. They can be read without applying anything: in the plan output, or as the outputs `provision_policy_json` and `reaper_policy_json` of `terraform/modules/provisioning`, which its tests assert (`terraform test`).

## 3. Amazon Inspector (once, only if image scanning stays on)

`scan_images` is true by default. Image Builder hands each new image to Amazon Inspector, which must be turned on for the account first: **Inspector, Get started, Activate** (EC2 scanning) in `ap-south-1`. It bills per scanned image and instance; check the Inspector pricing page for the current figure before activating. To skip scanning for now, set `scan_images = false`; the pipeline then builds and tests without scanning.

## 4. The role the provisioning API will run as (later, with the API)

The API is not built yet. When it is, create its role and give it the policy in the output `api_policy_json` (start the two state machines through their aliases, read executions, read instances, and change only the `expires-at` and `ttl` tags of a managed instance). Nothing to do now.

## 5. Not IAM, but needed before the first image build

- Upload the archive the image build downloads: `tar -czf ansible-1.0.0.tar.gz ansible` from the root of `mls-infra`, then copy it to `s3://mls-deploy-artifacts-096631344162/images/ansible-1.0.0.tar.gz`. The agent role may already write to `mls-*` buckets.
- The build instance needs outbound HTTPS. The lab VPC's subnets do not assign public addresses and there is no NAT, so **a build cannot succeed there yet**. Choose one: a NAT gateway, or a subnet that assigns public addresses on launch (both change the network stack, so they need your decision).

## 6. First environment

Applying and starting the first environment needs your explicit go-ahead. The request, the cost and the clean-up are in the README of this folder.
