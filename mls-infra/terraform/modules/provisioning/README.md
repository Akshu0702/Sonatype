# provisioning

On-demand environments (wiki `API-Reference/Provisioning-API`, `Automation`, ADR-0003, ADR-0004): one EC2 instance per request, from the hardened image, with a time to live.

What it creates: the instance role and profile of an environment (Session Manager only), a security group (no inbound unless named, web traffic out), optional Systems Manager endpoints, the two state machines (`provision-environment`, `ttl-reaper`, through the `step-functions` module), the reaper's timetable (EventBridge Scheduler, every 15 minutes) and the catalogue of configurations a request may name.

**Image:** `ami_id` if given; otherwise the newest image whose name starts with `hardened_ami_name_prefix` (the `image-builder` module's output); otherwise the newest Canonical Ubuntu 24.04, which is **not hardened** and is reported by the output `uses_fallback_image`.

**What the state machines are allowed to do** is in the outputs `provision_policy_json` and `reaper_policy_json` and is asserted by the tests: launch only with the mandatory tags, only the allowed burstable types, only the named subnets and group, only encrypted volumes; terminate only instances tagged `project=mls` and `managed-by=provisioning`.

**Templates:** the definitions are templates with `${name}` placeholders (configurations_json, subnet_ids_json, security_group_id, instance_profile_name, associate_public_ip, environment, default_ttl_hours, max_ttl_hours). A placeholder the module does not fill makes the plan fail, so a broken definition is never deployed.

Tests: `terraform test` (mocked provider; nothing is created).
